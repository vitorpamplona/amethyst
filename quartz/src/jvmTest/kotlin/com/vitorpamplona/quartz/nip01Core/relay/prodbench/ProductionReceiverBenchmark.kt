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
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.EventCollector
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test

/**
 * Production measurement harness for the NostrClient receive path.
 *
 * The client's receiver is "single threaded" PER RELAY: OkHttp's socket-reader
 * thread enqueues each raw frame into an unbounded Channel, and one consumer
 * coroutine per connection drains it, doing — inline, serially — the JSON
 * parse, the PoolRequests state machine, every SubscriptionListener callback,
 * and every NostrClient connection listener (in the app that includes
 * LocalCache.justConsume, i.e. Schnorr signature verification of every new
 * event). This harness quantifies what that costs against real relays:
 *
 *  - per-relay throughput (msgs/s, bytes) and time-to-EOSE for realistic filters
 *  - queue delay: how long frames sit in the channel before the consumer
 *    gets to them (the direct symptom of a saturated single consumer)
 *  - processing time per message (parse + dispatch + verify when inline)
 *  - INLINE vs PARALLEL verification (the same strategy the relay-server's
 *    IngestQueue already uses on the write path)
 *  - offline single-thread ceilings for parse and verify on the captured
 *    production frames
 *
 * This test opens live sockets to public relays, so it is gated: it no-ops
 * unless the environment variable PROD_RELAY_BENCH is set.
 *
 * Run with:
 *   PROD_RELAY_BENCH=1 ./gradlew :quartz:jvmTest --tests "*.ProductionReceiverBenchmark"
 */
class ProductionReceiverBenchmark {
    companion object {
        val RELAYS =
            listOf(
                "wss://relay.damus.io",
                "wss://nos.lol",
                "wss://relay.primal.net",
                "wss://nostr.wine",
            )

        // fiatjaf — a pubkey with heavy notification traffic on public relays.
        const val BUSY_PUBKEY = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"

        const val EOSE_TIMEOUT_MS = 30_000L
        const val LIVE_WINDOW_MS = 2_000L
        const val DRAIN_TIMEOUT_MS = 30_000L
    }

    enum class VerifyMode { INLINE, PARALLEL }

    // -----------------------------------------------------------------
    // Instrumented socket: BasicOkHttpWebSocket + timestamps around the
    // per-connection channel so queue delay and processing time are visible.
    // -----------------------------------------------------------------

    class RelayIngestMetrics(
        val url: NormalizedRelayUrl,
    ) {
        val queueDelayNanos = Collections.synchronizedList(ArrayList<Long>(8192))
        val procNanos = Collections.synchronizedList(ArrayList<Long>(8192))
        val msgCount = AtomicLong(0)
        val byteCount = AtomicLong(0)

        @Volatile var firstMsgAtNanos = 0L

        @Volatile var lastMsgAtNanos = 0L

        fun record(
            queueDelay: Long,
            proc: Long,
            bytes: Int,
            now: Long,
        ) {
            queueDelayNanos.add(queueDelay)
            procNanos.add(proc)
            msgCount.incrementAndGet()
            byteCount.addAndGet(bytes.toLong())
            if (firstMsgAtNanos == 0L) firstMsgAtNanos = now
            lastMsgAtNanos = now
        }
    }

    class InstrumentedOkHttpWebSocket(
        val url: NormalizedRelayUrl,
        val httpClient: OkHttpClient,
        val out: WebSocketListener,
        val metrics: RelayIngestMetrics,
        val frameSink: ((String) -> Unit)?,
    ) : WebSocket {
        class Frame(
            val text: String,
            val enqueuedAtNanos: Long,
        )

        private var socket: okhttp3.WebSocket? = null

        override fun needsReconnect() = socket == null

        override fun connect() {
            val request = Request.Builder().url(url.url).build()

            val listener =
                object : okhttp3.WebSocketListener() {
                    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                    val incoming: Channel<Frame> = Channel(Channel.UNLIMITED)
                    val job =
                        scope.launch {
                            // Mirrors BasicOkHttpWebSocket/OkHttpWebSocket: ONE
                            // consumer per connection; everything downstream of
                            // out.onMessage runs serially right here.
                            for (frame in incoming) {
                                val start = System.nanoTime()
                                frameSink?.invoke(frame.text)
                                out.onMessage(frame.text)
                                val end = System.nanoTime()
                                metrics.record(
                                    queueDelay = start - frame.enqueuedAtNanos,
                                    proc = end - start,
                                    bytes = frame.text.length,
                                    now = end,
                                )
                            }
                        }

                    override fun onOpen(
                        webSocket: okhttp3.WebSocket,
                        response: Response,
                    ) = out.onOpen(
                        (response.receivedResponseAtMillis - response.sentRequestAtMillis).toInt(),
                        response.headers["Sec-WebSocket-Extensions"]?.contains("permessage-deflate") ?: false,
                    )

                    override fun onMessage(
                        webSocket: okhttp3.WebSocket,
                        text: String,
                    ) {
                        incoming.trySendBlocking(Frame(text, System.nanoTime()))
                    }

                    override fun onClosed(
                        webSocket: okhttp3.WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        incoming.close()
                        job.cancel()
                        scope.cancel()
                        out.onClosed(code, reason)
                    }

                    override fun onFailure(
                        webSocket: okhttp3.WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        incoming.close()
                        job.cancel()
                        scope.cancel()
                        out.onFailure(t, response?.code, response?.message)
                    }
                }

            socket = httpClient.newWebSocket(request, listener)
        }

        override fun disconnect() {
            socket?.cancel()
            socket = null
        }

        override fun send(msg: String): Boolean = socket?.send(msg) ?: false
    }

    class InstrumentedBuilder(
        val httpClient: OkHttpClient,
        val metricsFor: (NormalizedRelayUrl) -> RelayIngestMetrics,
        val frameSink: ((String) -> Unit)?,
    ) : WebsocketBuilder {
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ) = InstrumentedOkHttpWebSocket(url, httpClient, out, metricsFor(url), frameSink)
    }

    // -----------------------------------------------------------------
    // Verification sink: mimics what CacheClientConnector/LocalCache do
    // with every event (dedup by id, then Schnorr-verify new ones),
    // either inline on the receiver coroutine (current app behavior) or
    // offloaded to a parallel Default-dispatcher pool.
    // -----------------------------------------------------------------

    class VerifyingSink(
        val mode: VerifyMode,
        scope: CoroutineScope,
    ) {
        private val seen: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val received = AtomicLong(0)
        val unique = AtomicLong(0)
        val verified = AtomicLong(0)
        val invalid = AtomicLong(0)
        val verifyNanosTotal = AtomicLong(0)
        val perRelayEvents = ConcurrentHashMap<NormalizedRelayUrl, AtomicLong>()

        private val queue: Channel<Event> = Channel(Channel.UNLIMITED)
        private val workers =
            if (mode == VerifyMode.PARALLEL) {
                List(Runtime.getRuntime().availableProcessors()) {
                    scope.launch(Dispatchers.Default) {
                        for (event in queue) doVerify(event)
                    }
                }
            } else {
                emptyList()
            }

        fun consume(
            event: Event,
            relay: NormalizedRelayUrl,
        ) {
            received.incrementAndGet()
            perRelayEvents.getOrPut(relay) { AtomicLong(0) }.incrementAndGet()
            if (seen.add(event.id)) {
                unique.incrementAndGet()
                when (mode) {
                    VerifyMode.INLINE -> doVerify(event)
                    VerifyMode.PARALLEL -> queue.trySend(event)
                }
            }
        }

        private fun doVerify(event: Event) {
            val start = System.nanoTime()
            val ok = event.verify()
            verifyNanosTotal.addAndGet(System.nanoTime() - start)
            if (ok) verified.incrementAndGet() else invalid.incrementAndGet()
        }

        suspend fun awaitDrained(timeoutMs: Long): Boolean =
            withTimeoutOrNull(timeoutMs) {
                while (verified.get() + invalid.get() < unique.get()) delay(20)
                true
            } ?: false

        fun close() {
            queue.close()
        }
    }

    // -----------------------------------------------------------------
    // Scenario runner
    // -----------------------------------------------------------------

    class ScenarioReport(
        val capturedEvents: List<Event>,
    )

    private fun percentileLine(nanos: List<Long>): String {
        if (nanos.isEmpty()) return "n=0"
        val sorted = nanos.sorted()

        fun p(q: Double) = sorted[((sorted.size - 1) * q).toInt()]

        fun fmt(n: Long) =
            when {
                n >= 1_000_000 -> "%.1fms".format(n / 1_000_000.0)
                n >= 1_000 -> "%.1fµs".format(n / 1_000.0)
                else -> "${n}ns"
            }
        return "n=${sorted.size} p50=${fmt(p(0.5))} p90=${fmt(p(0.9))} p99=${fmt(p(0.99))} max=${fmt(sorted.last())}"
    }

    private suspend fun runScenario(
        name: String,
        httpClient: OkHttpClient,
        relays: List<NormalizedRelayUrl>,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        mode: VerifyMode,
        captureFrames: MutableList<String>? = null,
        quiet: Boolean = false,
    ): ScenarioReport {
        if (!quiet) println("\n=== SCENARIO: $name [verify=$mode] ===")

        val metrics = ConcurrentHashMap<NormalizedRelayUrl, RelayIngestMetrics>()
        val frameSink: ((String) -> Unit)? =
            captureFrames?.let { list -> { text: String -> if (list.size < 30_000) list.add(text) } }

        val builder =
            InstrumentedBuilder(
                httpClient,
                { url -> metrics.getOrPut(url) { RelayIngestMetrics(url) } },
                frameSink,
            )

        val scenarioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sink = VerifyingSink(mode, scenarioScope)
        val capturedEvents = Collections.synchronizedList(ArrayList<Event>(8192))

        val client = NostrClient(builder)
        val collector =
            EventCollector(client) { event, relay ->
                sink.consume(event, relay.url)
                if (capturedEvents.size < 20_000) capturedEvents.add(event)
            }

        val startNanos = System.nanoTime()
        val reqSentAt = ConcurrentHashMap<String, Long>()
        val firstEventAt = ConcurrentHashMap<NormalizedRelayUrl, Long>()
        val eoseAt = ConcurrentHashMap<NormalizedRelayUrl, Long>()
        val cannotConnect = ConcurrentHashMap<NormalizedRelayUrl, String>()
        val eventsBeforeEose = ConcurrentHashMap<NormalizedRelayUrl, AtomicLong>()
        val eventsAfterEose = ConcurrentHashMap<NormalizedRelayUrl, AtomicLong>()

        val listener =
            object : SubscriptionListener {
                override fun onSubscriptionStarted(
                    relay: String,
                    forFilters: List<Filter>,
                ) {
                    reqSentAt.putIfAbsent(relay, System.nanoTime() - startNanos)
                }

                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    firstEventAt.putIfAbsent(relay, System.nanoTime() - startNanos)
                    val counter = if (eoseAt.containsKey(relay)) eventsAfterEose else eventsBeforeEose
                    counter.getOrPut(relay) { AtomicLong(0) }.incrementAndGet()
                }

                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    eoseAt.putIfAbsent(relay, System.nanoTime() - startNanos)
                }

                override fun onCannotConnect(
                    relay: NormalizedRelayUrl,
                    message: String,
                    forFilters: List<Filter>?,
                ) {
                    cannotConnect.putIfAbsent(relay, message)
                }
            }

        val subId = "bench-" + System.nanoTime().toString(16)
        client.subscribe(subId, filters, listener)

        val allEosed =
            withTimeoutOrNull(EOSE_TIMEOUT_MS) {
                while (!eoseAt.keys.containsAll(relays) && cannotConnect.keys.size + eoseAt.keys.size < relays.size) delay(50)
                true
            } ?: false

        // watch a short live window after EOSE, like the app does
        delay(LIVE_WINDOW_MS)

        client.unsubscribe(subId)

        val drainStart = System.nanoTime()
        val drained = sink.awaitDrained(DRAIN_TIMEOUT_MS)
        val drainNanos = System.nanoTime() - drainStart

        sink.close()
        collector.destroy()
        client.close()
        scenarioScope.cancel()

        // ---- report ----
        if (quiet) return ScenarioReport(ArrayList(capturedEvents).distinctBy { it.id })

        if (!allEosed) println("!! not all relays reached EOSE within ${EOSE_TIMEOUT_MS}ms")
        cannotConnect.forEach { (relay, msg) -> println("!! cannot connect ${relay.url}: $msg") }

        for (relay in relays) {
            val m = metrics[relay] ?: continue
            val req = reqSentAt[relay.url]?.let { "%.0fms".format(it / 1e6) } ?: "-"
            val ttfb = firstEventAt[relay]?.let { "%.0fms".format(it / 1e6) } ?: "-"
            val eose = eoseAt[relay]?.let { "%.0fms".format(it / 1e6) } ?: "-"
            val activeNanos = (m.lastMsgAtNanos - m.firstMsgAtNanos).coerceAtLeast(1)
            val rate = m.msgCount.get() * 1e9 / activeNanos
            val busyPct = m.procNanos.toList().sum() * 100.0 / activeNanos
            println("  ${relay.url}")
            println(
                "    req=$req firstEvent=$ttfb eose=$eose msgs=${m.msgCount.get()} bytes=${m.byteCount.get()} rate=%.0f msg/s consumerBusy=%.0f%%"
                    .format(rate, busyPct),
            )
            println("    events: pre-EOSE=${eventsBeforeEose[relay]?.get() ?: 0} post-EOSE=${eventsAfterEose[relay]?.get() ?: 0}")
            println("    queueDelay: ${percentileLine(m.queueDelayNanos.toList())}")
            println("    procTime:   ${percentileLine(m.procNanos.toList())}")
        }
        val uniq = sink.unique.get()
        val recv = sink.received.get()
        println("  TOTAL: received=$recv unique=$uniq duplicates=${recv - uniq} verified=${sink.verified.get()} invalid=${sink.invalid.get()}")
        if (uniq > 0) {
            println(
                "  verify: total=%.1fms avg=%.1fµs/event drainAfterUnsub=%.1fms drained=%s"
                    .format(
                        sink.verifyNanosTotal.get() / 1e6,
                        sink.verifyNanosTotal.get() / 1e3 / uniq,
                        drainNanos / 1e6,
                        drained,
                    ),
            )
        }

        return ScenarioReport(ArrayList(capturedEvents).distinctBy { it.id })
    }

    // -----------------------------------------------------------------
    // Offline microbenchmarks on captured production frames: what a single
    // receiver coroutine can do per second, with no network in the way.
    // -----------------------------------------------------------------

    private fun offlineParseBench(frames: List<String>) {
        if (frames.isEmpty()) return
        println("\n=== OFFLINE: single-thread JSON parse of ${frames.size} captured frames ===")
        // warmup
        repeat(2) { frames.forEach { runCatching { OptimizedJsonMapper.fromJsonToMessage(it) } } }
        val start = System.nanoTime()
        var parsed = 0
        frames.forEach { if (runCatching { OptimizedJsonMapper.fromJsonToMessage(it) }.isSuccess) parsed++ }
        val nanos = System.nanoTime() - start
        println(
            "  parsed=$parsed in %.1fms -> %.0f msg/s (%.1fµs/msg)"
                .format(nanos / 1e6, parsed * 1e9 / nanos, nanos / 1e3 / parsed),
        )
    }

    private fun offlineVerifyBench(events: List<Event>) {
        if (events.isEmpty()) return
        val sample = events.take(3000)
        println("\n=== OFFLINE: Schnorr verify of ${sample.size} unique production events ===")

        // warmup (JIT + secp tables)
        sample.take(300).forEach { it.verify() }

        val t1 = System.nanoTime()
        sample.forEach { it.verify() }
        val seqNanos = System.nanoTime() - t1
        println(
            "  sequential (1 thread):  %.1fms -> %.0f events/s (%.1fµs/event)"
                .format(seqNanos / 1e6, sample.size * 1e9 / seqNanos, seqNanos / 1e3 / sample.size),
        )

        val cores = Runtime.getRuntime().availableProcessors()
        val parNanos =
            runBlocking(Dispatchers.Default) {
                val start = System.nanoTime()
                sample
                    .chunked((sample.size + cores - 1) / cores)
                    .map { chunk -> async { chunk.forEach { it.verify() } } }
                    .awaitAll()
                System.nanoTime() - start
            }
        println(
            "  parallel ($cores threads): %.1fms -> %.0f events/s (%.1fx speedup)"
                .format(parNanos / 1e6, sample.size * 1e9 / parNanos, seqNanos.toDouble() / parNanos),
        )
    }

    // -----------------------------------------------------------------
    // The gated test
    // -----------------------------------------------------------------

    @Test
    fun productionReceiverBenchmark() {
        if (System.getenv("PROD_RELAY_BENCH") == null && System.getProperty("prodRelayBench") == null) {
            println("ProductionReceiverBenchmark skipped. Set PROD_RELAY_BENCH=1 to run against live relays.")
            return
        }

        val httpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()

        val relays = RELAYS.map { it.normalizeRelayUrl() }

        runBlocking {
            val capturedFrames = Collections.synchronizedList(ArrayList<String>(30_000))

            // Warmup (discarded): JIT-compile the parse/verify/dispatch path and
            // open the OkHttp pools so the first measured scenario isn't paying
            // cold-start costs that the later ones don't.
            runScenario(
                "warmup",
                httpClient,
                relays,
                relays.associateWith { listOf(Filter(kinds = listOf(1), limit = 150)) },
                VerifyMode.INLINE,
                quiet = true,
            )

            // S1: home-feed-style firehose, current app behavior (inline verify)
            val s1 =
                runScenario(
                    "kind-1 firehose, limit 500/relay",
                    httpClient,
                    relays,
                    relays.associateWith { listOf(Filter(kinds = listOf(1), limit = 500)) },
                    VerifyMode.INLINE,
                    captureFrames = capturedFrames,
                )

            // S1b: same filter, verification offloaded to a parallel pool
            runScenario(
                "kind-1 firehose, limit 500/relay",
                httpClient,
                relays,
                relays.associateWith { listOf(Filter(kinds = listOf(1), limit = 500)) },
                VerifyMode.PARALLEL,
            )

            // S2: notifications-style filter for a busy pubkey
            runScenario(
                "notifications for busy pubkey (kinds 1,6,7,9735)",
                httpClient,
                relays,
                relays.associateWith {
                    listOf(
                        Filter(
                            kinds = listOf(1, 6, 7, 9735),
                            tags = mapOf("p" to listOf(BUSY_PUBKEY)),
                            limit = 500,
                        ),
                    )
                },
                VerifyMode.INLINE,
            )

            // S3: metadata burst — the app-startup pattern: fetch kind 0/10002
            // for every author seen in the feed. Big bursts, tiny events.
            val authors =
                s1.capturedEvents
                    .map { it.pubKey }
                    .distinct()
                    .take(300)
            if (authors.isNotEmpty()) {
                runScenario(
                    "metadata burst (kinds 0,10002) for ${authors.size} authors",
                    httpClient,
                    relays,
                    relays.associateWith { listOf(Filter(kinds = listOf(0, 10002), authors = authors)) },
                    VerifyMode.INLINE,
                )
                runScenario(
                    "metadata burst (kinds 0,10002) for ${authors.size} authors",
                    httpClient,
                    relays,
                    relays.associateWith { listOf(Filter(kinds = listOf(0, 10002), authors = authors)) },
                    VerifyMode.PARALLEL,
                )
            }

            // Offline ceilings from the captured production data
            offlineParseBench(ArrayList(capturedFrames))
            offlineVerifyBench(s1.capturedEvents)
        }

        httpClient.dispatcher.executorService.shutdown()
    }
}
