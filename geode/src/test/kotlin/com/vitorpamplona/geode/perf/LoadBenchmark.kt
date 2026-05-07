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
package com.vitorpamplona.geode.perf

import com.vitorpamplona.geode.LocalRelayServer
import com.vitorpamplona.geode.Relay
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.time.measureTime

/**
 * Single-process load tests that report real numbers for:
 *   - WebSocket connection establishment rate
 *   - Concurrent subscription steady-state count
 *   - Live-event fanout latency to N subscribers
 *   - End-to-end EVENT publish throughput (EVENT → store → OK)
 *
 * Disabled by default — runs only when `runLoadBenchmark` system
 * property is set. Add `-DrunLoadBenchmark=true` to a Gradle test
 * invocation. Skipped under the normal test run because (a) numbers
 * vary on busy CI runners, and (b) some scenarios spin up thousands
 * of sockets which is rude on shared infra.
 */
class LoadBenchmark {
    private val enabled = System.getProperty("runLoadBenchmark") == "true"

    private inline fun benchmark(
        name: String,
        block: () -> Unit,
    ) {
        if (!enabled) {
            println("[skip] $name — set -DrunLoadBenchmark=true to enable")
            return
        }
        println("--- $name ---")
        block()
    }

    /**
     * How many concurrent WebSocket *connections* can we hold open?
     * Each test client opens a raw WS, sends one REQ, expects EOSE.
     * Stays connected after that.
     */
    @Test
    fun connectionsHeldOpen() =
        benchmark("connections held open") {
            for (target in listOf(100, 500, 1_000, 2_000, 5_000, 10_000)) {
                runBenchmarkServer { server, http ->
                    val httpUrl =
                        okhttp3.Request
                            .Builder()
                            .url(server.url.replace("ws://", "http://"))
                            .build()
                    val sockets = java.util.concurrent.CopyOnWriteArrayList<okhttp3.WebSocket>()
                    val opened = AtomicLong()
                    val gotEose = AtomicLong()
                    val opens =
                        measureTime {
                            repeat(target) {
                                val ws =
                                    http.newWebSocket(
                                        httpUrl,
                                        object : okhttp3.WebSocketListener() {
                                            override fun onOpen(
                                                webSocket: okhttp3.WebSocket,
                                                response: okhttp3.Response,
                                            ) {
                                                opened.incrementAndGet()
                                                webSocket.send(
                                                    """["REQ","s",{"kinds":[1],"limit":1}]""",
                                                )
                                            }

                                            override fun onMessage(
                                                webSocket: okhttp3.WebSocket,
                                                text: String,
                                            ) {
                                                if (text.startsWith("[\"EOSE\"")) {
                                                    gotEose.incrementAndGet()
                                                }
                                            }
                                        },
                                    )
                                sockets += ws
                            }
                            // Wait for either EOSE on every connection or 60s deadline.
                            val deadline = System.currentTimeMillis() + 60_000
                            while (gotEose.get() < target && System.currentTimeMillis() < deadline) {
                                Thread.sleep(50)
                            }
                        }
                    // Let activeSessionCount settle.
                    Thread.sleep(200)
                    println(
                        "target=$target opened=${opened.get()} eosed=${gotEose.get()} " +
                            "active=${server.activeSessionCount} elapsedMs=${opens.inWholeMilliseconds}",
                    )
                    sockets.forEach { runCatching { it.cancel() } }
                    if (gotEose.get() < target) {
                        println("  --> degradation at $target; stopping ramp-up")
                        return@runBenchmarkServer
                    }
                }
            }
        }

    /**
     * One publisher sends 10k events serially. Measures the round-trip
     * `EVENT` → `OK true` time, which is dominated by SQLite write
     * throughput + the write side of the policy stack.
     */
    @Test
    fun publishThroughputSingleClient() =
        benchmark("publish throughput single client") {
            runBenchmarkServer { server, http ->
                val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                val client = NostrClient(BasicOkHttpWebSocket.Builder { _ -> http }, scope)
                try {
                    val signer = NostrSignerSync(KeyPair())
                    val relayUrl = server.url.normalizeRelayUrl()

                    val n = 10_000
                    var ok = 0
                    val elapsed =
                        measureTime {
                            runBlocking {
                                repeat(n) { i ->
                                    val event = signer.sign(TextNoteEvent.build("hello $i"))
                                    if (client.publishAndConfirm(event, setOf(relayUrl))) ok++
                                }
                            }
                        }
                    val eps = (n * 1000.0) / elapsed.inWholeMilliseconds
                    println("events=$n ok=$ok elapsedMs=${elapsed.inWholeMilliseconds} eps=${"%.0f".format(eps)}")
                } finally {
                    client.disconnect()
                    scope.cancel()
                }
            }
        }

    /**
     * One publisher, N subscribers. Publishes one EVENT and measures
     * fan-out latency: time from publish to last subscriber receiving.
     */
    @Test
    fun fanoutLatency() =
        benchmark("fanout latency") {
            for (subs in listOf(100, 500, 1_000, 2_000)) {
                runBenchmarkServer { server, http ->
                    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                    val subClient = NostrClient(BasicOkHttpWebSocket.Builder { _ -> http }, scope)
                    val pubClient = NostrClient(BasicOkHttpWebSocket.Builder { _ -> http }, scope)
                    try {
                        val relayUrl = server.url.normalizeRelayUrl()
                        val received = AtomicLong()
                        val firstReceiveNs = AtomicLong(-1)
                        val lastReceiveNs = AtomicLong(-1)

                        // Set up `subs` subscribers.
                        val eosed = AtomicLong()
                        repeat(subs) { i ->
                            subClient.subscribe(
                                "fanout-$i",
                                mapOf(relayUrl to listOf(Filter(kinds = listOf(1)))),
                                object : SubscriptionListener {
                                    override fun onEvent(
                                        event: com.vitorpamplona.quartz.nip01Core.core.Event,
                                        isLive: Boolean,
                                        relay: NormalizedRelayUrl,
                                        forFilters: List<Filter>?,
                                    ) {
                                        val now = System.nanoTime()
                                        firstReceiveNs.compareAndSet(-1, now)
                                        lastReceiveNs.set(now)
                                        received.incrementAndGet()
                                    }

                                    override fun onEose(
                                        relay: NormalizedRelayUrl,
                                        forFilters: List<Filter>?,
                                    ) {
                                        eosed.incrementAndGet()
                                    }
                                },
                            )
                        }

                        runBlocking {
                            withTimeout(60_000) {
                                while (eosed.get() < subs) kotlinx.coroutines.delay(50)
                            }
                        }
                        println("$subs subs ready; publishing one event...")

                        val signer = NostrSignerSync(KeyPair())
                        val event = signer.sign(TextNoteEvent.build("fanout"))
                        val publishStart = System.nanoTime()
                        runBlocking {
                            pubClient.publishAndConfirm(event, setOf(relayUrl))
                        }
                        val publishEnd = System.nanoTime()

                        // Wait for fanout to complete.
                        runBlocking {
                            withTimeout(60_000) {
                                while (received.get() < subs) kotlinx.coroutines.delay(10)
                            }
                        }

                        val firstFanoutMs = (firstReceiveNs.get() - publishStart) / 1_000_000.0
                        val lastFanoutMs = (lastReceiveNs.get() - publishStart) / 1_000_000.0
                        val publishMs = (publishEnd - publishStart) / 1_000_000.0
                        println(
                            "subs=$subs publishMs=${"%.1f".format(publishMs)} " +
                                "fanoutFirstMs=${"%.1f".format(firstFanoutMs)} " +
                                "fanoutLastMs=${"%.1f".format(lastFanoutMs)} " +
                                "received=${received.get()}/$subs",
                        )
                    } finally {
                        subClient.disconnect()
                        pubClient.disconnect()
                        scope.cancel()
                    }
                }
            }
        }

    /**
     * Many concurrent publishers, each on their own WebSocket. Tells
     * us whether the SQLite single-writer bottleneck is the floor or
     * if there's contention upstream.
     */
    @Test
    fun publishThroughputConcurrent() =
        benchmark("publish throughput concurrent") {
            for (parallel in listOf(2, 4, 8, 16, 32)) {
                runBenchmarkServer { server, http ->
                    val total = 5_000
                    val perThread = total / parallel
                    val ok = AtomicLong()
                    val elapsed =
                        measureTime {
                            val threads =
                                (0 until parallel).map { tid ->
                                    Thread {
                                        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                                        val client = NostrClient(BasicOkHttpWebSocket.Builder { _ -> http }, scope)
                                        try {
                                            val signer = NostrSignerSync(KeyPair())
                                            val relayUrl = server.url.normalizeRelayUrl()
                                            runBlocking {
                                                repeat(perThread) { i ->
                                                    val ev = signer.sign(TextNoteEvent.build("hi-$tid-$i"))
                                                    if (client.publishAndConfirm(ev, setOf(relayUrl))) ok.incrementAndGet()
                                                }
                                            }
                                        } finally {
                                            client.disconnect()
                                            scope.cancel()
                                        }
                                    }.also { it.start() }
                                }
                            threads.forEach { it.join() }
                        }
                    val eps = (ok.get() * 1000.0) / elapsed.inWholeMilliseconds
                    println(
                        "parallel=$parallel total=${ok.get()}/$total elapsedMs=${elapsed.inWholeMilliseconds} eps=${"%.0f".format(eps)}",
                    )
                }
            }
        }

    /** Spin up an isolated relay + http client per scenario. */
    private inline fun runBenchmarkServer(block: (LocalRelayServer, OkHttpClient) -> Unit) {
        val placeholder = "ws://127.0.0.1:7771/".normalizeRelayUrl()
        val relay = Relay(url = placeholder)
        val server = LocalRelayServer(relay, host = "127.0.0.1", port = 0).start()
        val http =
            OkHttpClient
                .Builder()
                // Don't bottleneck on the OkHttp dispatcher when we
                // open thousands of WS connections from one client.
                .dispatcher(
                    okhttp3.Dispatcher().apply {
                        maxRequests = 100_000
                        maxRequestsPerHost = 100_000
                    },
                ).build()
        try {
            block(server, http)
        } finally {
            http.dispatcher.executorService.shutdownNow()
            server.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
            relay.close()
        }
    }
}
