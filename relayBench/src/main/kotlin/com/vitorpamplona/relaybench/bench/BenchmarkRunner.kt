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
package com.vitorpamplona.relaybench.bench

import com.vitorpamplona.relaybench.Options
import com.vitorpamplona.relaybench.Scenario
import com.vitorpamplona.relaybench.corpus.Corpus
import com.vitorpamplona.relaybench.relays.Nip11Info
import com.vitorpamplona.relaybench.relays.RelayUnderTest
import com.vitorpamplona.relaybench.relays.RunningRelay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File

/** Everything measured for one relay implementation. */
class RelayResult(
    val name: String,
    val info: Nip11Info?,
    val visibility: IngestBenchmark.VisibilityResult?,
    val throughput: IngestBenchmark.ThroughputResult?,
    val queries: List<QueryBenchmark.ScenarioResult>,
    val storageBytes: Long,
    val error: String? = null,
)

/**
 * Runs the full single-relay pipeline — boot fresh, probe visibility on an
 * idle relay, replay the corpus for throughput, run every query scenario,
 * measure disk footprint, shut down — then the pairwise sync pipeline for
 * every relay combination.
 */
object BenchmarkRunner {
    fun runSingle(
        relay: RelayUnderTest,
        corpus: Corpus,
        scenarios: List<Scenario>,
        options: Options,
        workDir: File,
        http: OkHttpClient,
        log: (String) -> Unit,
    ): RelayResult {
        val running =
            try {
                relay.start(workDir)
            } catch (e: Exception) {
                return RelayResult(relay.name, null, null, null, emptyList(), 0, error = e.message)
            }
        try {
            val info = running.fetchInfo(http)
            log("  ${relay.name} up at ${running.wsUrl} (${info?.software ?: "?"} ${info?.version ?: ""})")

            // Visibility samples: non-replaceable events only (a superseded
            // replaceable would never come back by id and the probe would spin).
            val sampleIds = HashSet<String>()
            val visibilitySamples =
                corpus.events
                    .filter { it.kind == 1 }
                    .ifEmpty { corpus.events.filter { it.kind !in setOf(0, 3) && it.kind !in 10000..19999 && it.kind !in 30000..39999 } }
                    .let { pool ->
                        if (pool.size <= options.visibilitySamples) {
                            pool
                        } else {
                            (0 until options.visibilitySamples).map { pool[it * pool.size / options.visibilitySamples] }
                        }
                    }.onEach { sampleIds.add(it.id) }

            log("  visibility probe: ${visibilitySamples.size} sequential EVENT→REQ round trips…")
            val visibility =
                runBlocking { IngestBenchmark.visibility(running.wsUrl, visibilitySamples, http, log) }
            running.ensureAlive()

            val rest = corpus.events.filter { it.id !in sampleIds }
            log("  ingest throughput: ${rest.size} events over ${options.publishers} connections (window ${options.window})…")
            val throughput =
                runBlocking { IngestBenchmark.throughput(running.wsUrl, rest, options.publishers, options.window, http) }
            log("    ${"%,.0f".format(throughput.eventsPerSec)} events/s (${throughput.accepted} accepted, ${throughput.rejected} rejected)")
            running.ensureAlive()

            val queries =
                scenarios.map { scenario ->
                    log("  query [${scenario.key}] ${options.queryRounds} rounds + ${options.queryConnections}×${options.concurrentRounds} concurrent…")
                    val result =
                        runBlocking {
                            QueryBenchmark.run(
                                running.wsUrl,
                                scenario,
                                warmupRounds = options.warmupRounds,
                                rounds = options.queryRounds,
                                concurrency = options.queryConnections,
                                concurrentRounds = options.concurrentRounds,
                                http = http,
                            )
                        }
                    log(
                        "    ${result.eventsPerRound} events, EOSE p50 ${"%.1f".format(result.timeToEose.p50)} ms, " +
                            "${"%,.0f".format(result.sequentialEventsPerSec)} ev/s seq, ${"%,.0f".format(result.concurrentEventsPerSec)} ev/s @${options.queryConnections}conn",
                    )
                    running.ensureAlive()
                    result
                }

            val storage = running.storageBytes()
            return RelayResult(relay.name, info, visibility, throughput, queries, storage)
        } catch (e: Exception) {
            return RelayResult(
                relay.name,
                null,
                null,
                null,
                emptyList(),
                0,
                error = "${e.message}\nrelay log tail:\n${running.logFile
                    .takeIf { it.exists() }
                    ?.readLines()
                    ?.takeLast(10)
                    ?.joinToString("\n") ?: ""}",
            )
        } finally {
            running.stop()
        }
    }

    fun runSyncPairs(
        relays: List<RelayUnderTest>,
        corpus: Corpus,
        options: Options,
        workDir: File,
        http: OkHttpClient,
        log: (String) -> Unit,
    ): List<SyncBenchmark.PairResult> {
        if (relays.size < 2) return emptyList()
        val effective = SyncBenchmark.effectiveEvents(corpus.events)
        val n = effective.size
        val sliceA = effective.subList(0, n * 4 / 5)
        val sliceB = effective.subList(n / 5, n)

        val results = ArrayList<SyncBenchmark.PairResult>()
        for (i in relays.indices) {
            for (j in i + 1 until relays.size) {
                val a = relays[i]
                val b = relays[j]
                log("  sync pair ${a.name} ⇄ ${b.name}: $n syncable events, 80%/80% slices, 60% overlap")
                val pairDir = File(workDir, "sync-${a.name}-${b.name}").apply { mkdirs() }
                var runningA: RunningRelay? = null
                var runningB: RunningRelay? = null
                try {
                    runningA = a.start(pairDir)
                    runningB = b.start(pairDir)
                    val urlA = runningA.wsUrl
                    val urlB = runningB.wsUrl
                    log("    seeding both sides (${sliceA.size} + ${sliceB.size} events)…")
                    runBlocking {
                        coroutineScope {
                            listOf(
                                async { IngestBenchmark.throughput(urlA, sliceA, options.publishers, options.window, http) },
                                async { IngestBenchmark.throughput(urlB, sliceB, options.publishers, options.window, http) },
                            ).awaitAll()
                        }
                    }
                    results +=
                        runBlocking {
                            SyncBenchmark.runPair(
                                a.name,
                                urlA,
                                b.name,
                                urlB,
                                effective,
                                sliceA,
                                sliceB,
                                options.window,
                                http,
                                log,
                            )
                        }
                } catch (e: Exception) {
                    log("    ! sync pair failed: ${e.message}")
                    results +=
                        SyncBenchmark.PairResult(
                            a.name,
                            b.name,
                            n,
                            emptyMap(),
                            0.0,
                            0,
                            0,
                            emptyMap(),
                            converged = false,
                            error = e.message,
                        )
                } finally {
                    runningA?.stop()
                    runningB?.stop()
                }
            }
        }
        return results
    }
}
