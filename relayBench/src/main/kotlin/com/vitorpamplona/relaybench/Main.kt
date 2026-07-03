/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.relaybench

import com.vitorpamplona.relaybench.bench.BenchmarkRunner
import com.vitorpamplona.relaybench.corpus.Corpus
import com.vitorpamplona.relaybench.corpus.CorpusDownloader
import com.vitorpamplona.relaybench.corpus.CorpusSource
import com.vitorpamplona.relaybench.corpus.CorpusSpec
import com.vitorpamplona.relaybench.relays.CustomRelay
import com.vitorpamplona.relaybench.relays.GeodeRelay
import com.vitorpamplona.relaybench.relays.RelayUnderTest
import com.vitorpamplona.relaybench.relays.StrfryRelay
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * relaybench — head-to-head Nostr relay benchmark.
 *
 * Boots each relay implementation as a real external process on loopback
 * with equivalent setups (persistent storage, signature verification on,
 * no auth, stock limits), replays the same corpus into each, and measures:
 *
 *  - insertion: receipt➜queryable-by-REQ latency, OK-ack latency, and
 *    corpus replay throughput;
 *  - queries: popular client filters (home feed, thread, notifications,
 *    profiles, hashtag, by-ids, …) — time-to-EOSE and events/second,
 *    single-client and concurrent;
 *  - NIP-77 negentropy sync between every pair of relays.
 *
 * Normally driven via `relayBench/run.sh`, which builds the binaries and
 * passes `--geode-bin`/`--strfry-bin` here.
 */
class Options(
    val geodeBin: String?,
    val strfryBin: String?,
    val customRelays: List<Pair<String, String>>,
    val events: Int,
    val seed: Long,
    val baseTime: Long,
    val corpusFile: File?,
    val downloadFrom: List<String>?,
    val limit: Int,
    val maxEventBytes: Int,
    val maxTags: Int,
    val visibilitySamples: Int,
    val publishers: Int,
    val window: Int,
    val warmupRounds: Int,
    val queryRounds: Int,
    val queryConnections: Int,
    val concurrentRounds: Int,
    val skipSync: Boolean,
    val outDir: File,
    val workDir: File,
    val cacheDir: File,
    val keepData: Boolean,
)

private val USAGE =
    """
    relaybench — benchmark Nostr relay implementations head to head.

    Relays (at least one):
      --geode-bin PATH        geode launcher (from :geode:installDist)
      --strfry-bin PATH       strfry binary
      --relay NAME=CMD        any other relay; CMD may use {port} and {dir}
                              (repeatable)

    Corpus (default: deterministic synthetic):
      --events N              synthetic corpus size            [10000]
      --seed N                synthetic corpus seed            [1]
      --base-time EPOCH|now   newest synthetic timestamp       [fixed 2026-06-01]
      --corpus FILE           NDJSON or JSON-array dump, optionally gzipped
      --limit N               cap corpus events after preparation
      --download [URLS]       build corpus from public relays (comma-separated,
                              defaults to damus/nos.lol/primal)
      --max-event-bytes N     event size ceiling (corpus filter + relay config)
                              [65536]
      --max-tags N            tag count ceiling                [2000]

    Benchmark shape:
      --samples N             visibility (receipt➜REQ) samples [200]
      --publishers N          ingest connections               [4]
      --window N              unacked EVENTs in flight/conn    [200]
      --query-rounds N        measured rounds per scenario     [10]
      --query-conns N         concurrent query connections     [8]
      --no-sync               skip the pairwise NIP-77 sync phase
      --quick                 small corpus + fewer rounds (smoke test)

    Output:
      --out DIR               report directory      [relayBench/results]
      --work DIR              scratch dir for relay data [temp]
      --keep-data             keep relay data dirs after the run
      --no-color              plain terminal output
    """.trimIndent()

fun main(args: Array<String>) {
    val options =
        parseArgs(args) ?: run {
            println(USAGE)
            exitProcess(2)
        }

    val http =
        OkHttpClient
            .Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .socketFactory(NostrSocket.NO_DELAY_SOCKETS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .apply {
                val dispatcher = Dispatcher()
                dispatcher.maxRequests = 10_000
                dispatcher.maxRequestsPerHost = 10_000
                dispatcher(dispatcher)
            }.build()

    val log: (String) -> Unit = { println(it) }

    val relays = buildRelays(options)
    if (relays.isEmpty()) {
        System.err.println("No relays configured. Pass --geode-bin, --strfry-bin or --relay.\n")
        println(USAGE)
        exitProcess(2)
    }

    println()
    println("── corpus ──────────────────────────────────────────────")
    val corpus = loadCorpus(options, http, log)
    if (corpus.events.isEmpty()) {
        System.err.println("Corpus is empty — nothing to benchmark.")
        exitProcess(1)
    }
    val scenarios = Scenarios.derive(corpus)
    println("  ${corpus.events.size} events ready (fingerprint ${corpus.fingerprint}); ${scenarios.size} query scenarios derived")

    val workDir = options.workDir.apply { mkdirs() }
    val startedAt = ZonedDateTime.now()

    val results =
        relays.map { relay ->
            println()
            println("── ${relay.name} ─────────────────────────────────────────")
            BenchmarkRunner.runSingle(relay, corpus, scenarios, options, workDir, http, log)
        }

    val syncs =
        if (options.skipSync || relays.size < 2) {
            emptyList()
        } else {
            println()
            println("── NIP-77 sync pairs ──────────────────────────────────")
            BenchmarkRunner.runSyncPairs(relays, corpus, options, workDir, http, log)
        }

    val run = BenchRun(startedAt, corpus, options, results, syncs)

    // Reports.
    val stamp = startedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))
    val outDir = File(options.outDir, stamp).apply { mkdirs() }
    File(outDir, "report.md").writeText(Report.markdown(run))
    File(outDir, "results.json").writeText(Report.json(run))

    println(Report.terminal(run))
    println("  full report: ${File(outDir, "report.md").path}")
    println("  raw data:    ${File(outDir, "results.json").path}")
    println()

    if (!options.keepData) workDir.deleteRecursively()
    http.dispatcher.executorService.shutdown()
    http.connectionPool.evictAll()

    if (results.any { it.error != null }) exitProcess(1)
}

private fun buildRelays(options: Options): List<RelayUnderTest> =
    buildList {
        options.geodeBin?.let { add(GeodeRelay(it)) }
        options.strfryBin?.let { add(StrfryRelay(it, options.maxEventBytes, options.maxTags)) }
        options.customRelays.forEach { (name, cmd) -> add(CustomRelay(name, cmd)) }
    }

private fun loadCorpus(
    options: Options,
    http: OkHttpClient,
    log: (String) -> Unit,
): Corpus =
    when {
        options.corpusFile != null ->
            CorpusSource.fromFile(
                options.corpusFile,
                options.limit,
                options.cacheDir,
                log,
                options.maxEventBytes,
                options.maxTags,
            )
        options.downloadFrom != null ->
            CorpusDownloader.download(
                options.downloadFrom.ifEmpty { CorpusDownloader.DEFAULT_RELAYS },
                if (options.limit > 0) options.limit else options.events,
                options.cacheDir,
                http,
                log,
            )
        else ->
            CorpusSource.synthetic(
                CorpusSpec(
                    seed = options.seed,
                    events = options.events,
                    baseTime = options.baseTime,
                    spanSeconds = CorpusSpec.DEFAULT_SPAN_SECONDS,
                ),
                options.cacheDir,
                log,
            )
    }

private fun parseArgs(args: Array<String>): Options? {
    val map = HashMap<String, MutableList<String>>()
    val flags = HashSet<String>()
    var i = 0
    val valueOptions =
        setOf(
            "--geode-bin",
            "--strfry-bin",
            "--relay",
            "--events",
            "--seed",
            "--base-time",
            "--corpus",
            "--limit",
            "--download",
            "--max-event-bytes",
            "--max-tags",
            "--samples",
            "--publishers",
            "--window",
            "--query-rounds",
            "--query-conns",
            "--out",
            "--work",
        )
    while (i < args.size) {
        val arg = args[i]
        when {
            arg == "--help" || arg == "-h" -> return null
            arg.contains('=') && arg.substringBefore('=') in valueOptions -> {
                map.getOrPut(arg.substringBefore('=')) { mutableListOf() }.add(arg.substringAfter('='))
            }
            arg in valueOptions -> {
                // --download may appear with no value (use default relays).
                val next = args.getOrNull(i + 1)
                if (next != null && !next.startsWith("--")) {
                    map.getOrPut(arg) { mutableListOf() }.add(next)
                    i++
                } else if (arg == "--download") {
                    map.getOrPut(arg) { mutableListOf() }.add("")
                } else {
                    System.err.println("Missing value for $arg")
                    return null
                }
            }
            arg.startsWith("--") -> flags.add(arg)
            else -> {
                System.err.println("Unknown argument: $arg")
                return null
            }
        }
        i++
    }

    fun one(key: String): String? = map[key]?.lastOrNull()

    fun int(
        key: String,
        default: Int,
    ): Int = one(key)?.toIntOrNull() ?: default

    val quick = "--quick" in flags
    if ("--no-color" in flags) Report.disableColor()

    val moduleDir = File("relayBench").takeIf { it.isDirectory } ?: File(".")
    return Options(
        geodeBin = one("--geode-bin"),
        strfryBin = one("--strfry-bin"),
        customRelays =
            (map["--relay"] ?: emptyList()).map {
                val name = it.substringBefore('=')
                val cmd = it.substringAfter('=')
                name to cmd
            },
        events = int("--events", if (quick) 2000 else 10_000),
        seed = one("--seed")?.toLongOrNull() ?: 1L,
        baseTime =
            when (val t = one("--base-time")) {
                null -> CorpusSpec.DEFAULT_BASE_TIME
                "now" -> System.currentTimeMillis() / 1000
                else -> t.toLongOrNull() ?: CorpusSpec.DEFAULT_BASE_TIME
            },
        corpusFile = one("--corpus")?.let { File(it) },
        downloadFrom = map["--download"]?.lastOrNull()?.split(',')?.filter { it.isNotBlank() },
        limit = int("--limit", 0),
        maxEventBytes = int("--max-event-bytes", CorpusSource.DEFAULT_MAX_EVENT_BYTES),
        maxTags = int("--max-tags", CorpusSource.DEFAULT_MAX_TAGS),
        visibilitySamples = int("--samples", if (quick) 50 else 200),
        publishers = int("--publishers", 4),
        window = int("--window", 200),
        warmupRounds = if (quick) 1 else 2,
        queryRounds = int("--query-rounds", if (quick) 5 else 10),
        queryConnections = int("--query-conns", if (quick) 4 else 8),
        concurrentRounds = if (quick) 2 else 5,
        skipSync = "--no-sync" in flags,
        outDir = one("--out")?.let { File(it) } ?: File(moduleDir, "results"),
        workDir = one("--work")?.let { File(it) } ?: File(System.getProperty("java.io.tmpdir"), "relaybench-${System.nanoTime()}"),
        cacheDir = File(moduleDir, ".corpus-cache"),
        keepData = "--keep-data" in flags,
    )
}
