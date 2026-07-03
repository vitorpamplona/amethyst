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
package com.vitorpamplona.quartz.nip01Core.relay.prodbench

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.test.Test

/**
 * Parallel fetch-by-id against one production relay, measured properly:
 *
 *  - the FULL corpus, not a sample: every kind-[KIND] id the relay holds is
 *    enumerated first (until-cursor paging, untimed), then each cell of the
 *    matrix re-downloads the complete set by id batches;
 *  - a matrix over (connections × concurrent REQs per connection), the two
 *    axes the user report disputes: "concurrency within one connection stops
 *    helping past ~4 REQs; throughput scales with connections";
 *  - connections are pre-established before the clock starts (a keep-alive
 *    sub pins them), so cells time steady-state download, not TLS handshakes;
 *  - per-cell accounting of missing ids and timed-out batches, so a cell that
 *    hit a relay-side subscription cap (e.g. 16 concurrent REQs rejected)
 *    reads as a shortfall instead of fake speed.
 *
 * Batches are [BATCH] ids each, shuffled with a fixed seed so every cell gets
 * the same work in the same order. Events are counted, not retained.
 *
 * Gated (opens live sockets, downloads the corpus once per cell):
 *   ./gradlew :quartz:jvmTest --tests "*.ByIdFetchBenchmark" -PprodRelayBench=1
 */
class ByIdFetchBenchmark {
    companion object {
        const val RELAY = "wss://nip85.nosfabrica.com"
        const val KIND = 30382
        const val BATCH = 250
        const val BATCH_TIMEOUT_MS = 30_000L
        const val ENUM_PAGE_TIMEOUT_MS = 30_000L

        /** A never-matching id (all f's) to pin connections open. */
        val KEEP_ALIVE_ID = "f".repeat(64)
    }

    class CellResult(
        val label: String,
        val received: Long,
        val bytes: Long,
        val uniques: Int,
        val requested: Int,
        val timedOutBatches: Int,
        val wallNanos: Long,
    ) {
        override fun toString(): String =
            "  %-22s %,8d events  %6.1f MB  wall=%7.0fms  -> %,7.0f events/s%s%s"
                .format(
                    label,
                    received,
                    bytes / 1e6,
                    wallNanos / 1e6,
                    received * 1e9 / wallNanos,
                    if (uniques < requested) "  MISSING=${requested - uniques}" else "",
                    if (timedOutBatches > 0) "  TIMED-OUT-BATCHES=$timedOutBatches" else "",
                )
    }

    /** One REQ for [batch] ids on [client]; returns when EOSE/closed/timeout. */
    private suspend fun fetchBatch(
        client: NostrClient,
        relay: NormalizedRelayUrl,
        batch: List<HexKey>,
        onEvent: (Event) -> Unit,
    ): Boolean {
        val subId = newSubId()
        val done = Channel<Boolean>(Channel.CONFLATED)
        val listener =
            object : SubscriptionListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) = onEvent(event)

                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    done.trySend(true)
                }

                override fun onClosed(
                    message: String,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    done.trySend(false)
                }

                override fun onCannotConnect(
                    relay: NormalizedRelayUrl,
                    message: String,
                    forFilters: List<Filter>?,
                ) {
                    done.trySend(false)
                }
            }
        return try {
            client.subscribe(subId, mapOf(relay to listOf(Filter(ids = batch))), listener)
            withTimeoutOrNull(BATCH_TIMEOUT_MS) { done.receive() } ?: false
        } finally {
            client.unsubscribe(subId)
            done.close()
        }
    }

    /**
     * Runs one matrix cell: [connections] independent NostrClients (one socket
     * each) to the same relay, [reqsPerConn] workers per client pulling id
     * batches off a shared queue. Clients are connected before timing starts.
     */
    private suspend fun runCell(
        label: String,
        relay: NormalizedRelayUrl,
        httpClient: OkHttpClient,
        connections: Int,
        reqsPerConn: Int,
        batches: List<List<HexKey>>,
        quiet: Boolean = false,
    ): CellResult? {
        val clients = List(connections) { NostrClient(BasicOkHttpWebSocket.Builder { httpClient }) }
        val keepAlives = clients.map { it to newSubId() }
        try {
            // Pre-connect every socket before the clock starts.
            keepAlives.forEach { (client, subId) ->
                client.subscribe(subId, mapOf(relay to listOf(Filter(ids = listOf(KEEP_ALIVE_ID)))), null)
            }
            for (client in clients) {
                val ok = withTimeoutOrNull(20_000L) { client.connectedRelaysFlow().first { relay in it } }
                if (ok == null) {
                    println("  !! $label: could not pre-connect all sockets, skipping cell")
                    return null
                }
            }

            val received = AtomicLong(0)
            val bytes = AtomicLong(0)
            val uniques = ConcurrentHashMap.newKeySet<HexKey>(batches.size * BATCH)
            val timedOut = AtomicLong(0)

            val queue = Channel<List<HexKey>>(Channel.UNLIMITED)
            batches.forEach { queue.trySend(it) }
            queue.close()

            val start = System.nanoTime()
            coroutineScope {
                clients.forEach { client ->
                    repeat(reqsPerConn) {
                        launch(Dispatchers.IO) {
                            for (batch in queue) {
                                val ok =
                                    fetchBatch(client, relay, batch) { event ->
                                        received.incrementAndGet()
                                        // content only — cheap; kind-30382 payloads live in
                                        // tags, so this is a floor, not wire bytes
                                        bytes.addAndGet(event.content.length.toLong())
                                        uniques.add(event.id)
                                    }
                                if (!ok) timedOut.incrementAndGet()
                            }
                        }
                    }
                }
            }
            val wall = System.nanoTime() - start

            val result =
                CellResult(
                    label = label,
                    received = received.get(),
                    bytes = bytes.get(),
                    uniques = uniques.size,
                    requested = batches.sumOf { it.size },
                    timedOutBatches = timedOut.get().toInt(),
                    wallNanos = wall,
                )
            if (!quiet) println(result)
            return result
        } finally {
            keepAlives.forEach { (client, subId) -> client.unsubscribe(subId) }
            clients.forEach { it.close() }
        }
    }

    /** Enumerates every id of [KIND] on the relay via until-cursor paging (untimed). */
    private suspend fun enumerateIds(
        relay: NormalizedRelayUrl,
        httpClient: OkHttpClient,
    ): List<HexKey> {
        val client = NostrClient(BasicOkHttpWebSocket.Builder { httpClient })
        return try {
            val ids = LinkedHashSet<HexKey>(64_000)
            val t = System.nanoTime()
            client.fetchAllPages(
                relay = relay,
                filters = listOf(Filter(kinds = listOf(KIND))),
                timeoutMs = ENUM_PAGE_TIMEOUT_MS,
            ) { event -> ids.add(event.id) }
            println("  enumerated ${ids.size} ids in %.1fs (paged, untimed baseline for the matrix)".format((System.nanoTime() - t) / 1e9))
            ids.toList()
        } finally {
            client.close()
        }
    }

    @Test
    fun parallelByIdMatrix() {
        if (System.getenv("PROD_RELAY_BENCH") == null && System.getProperty("prodRelayBench") == null) {
            println("ByIdFetchBenchmark skipped. Run with -PprodRelayBench=1 to enable.")
            return
        }

        val httpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()

        val relay = RELAY.normalizeRelayUrl()

        runBlocking {
            println("=== PARALLEL FETCH-BY-ID MATRIX: $RELAY kinds=[$KIND], batch=$BATCH ids/REQ ===")

            val ids = enumerateIds(relay, httpClient)
            if (ids.size < 1_000) {
                println("  !! only ${ids.size} ids — relay unreachable or corpus gone; aborting")
                return@runBlocking
            }

            // Same shuffled batching for every cell.
            val batches = ids.shuffled(Random(42)).chunked(BATCH)
            println("  corpus=${ids.size} ids in ${batches.size} batches; every cell downloads the full corpus\n")

            // Warmup: JIT + server page cache, untimed.
            runCell("warmup", relay, httpClient, connections = 2, reqsPerConn = 4, batches = batches.take(12), quiet = true)

            println("  --- axis 1: concurrent REQs on ONE connection ---")
            for (reqs in listOf(1, 2, 4, 8, 16)) {
                runCell("1 conn x $reqs reqs", relay, httpClient, connections = 1, reqsPerConn = reqs, batches = batches)
            }

            println("  --- axis 2: connections (4 REQs each) ---")
            for (conns in listOf(2, 4, 8)) {
                runCell("$conns conns x 4 reqs", relay, httpClient, connections = conns, reqsPerConn = 4, batches = batches)
            }
        }

        httpClient.dispatcher.executorService.shutdown()
    }
}
