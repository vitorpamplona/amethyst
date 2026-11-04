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
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.amethyst.commons.service.http.OkHttpClientFactoryForRelays
import com.vitorpamplona.amethyst.commons.service.http.OnionLocationCache
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.SurgeDns
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * How long a [NostrClient] takes to START dialing a large relay set, with the relay
 * dispatcher on OkHttp's default executor (a platform thread per call) vs on virtual threads.
 *
 * `subscribe()` dials every relay on the calling thread, and OkHttp's `enqueue` starts the
 * call's thread right there, so the time from `subscribe()` to the last `onConnecting` is the
 * sum of those thread starts. Every relay url is a distinct host that a stubbed DNS sends to
 * one local [ServerSocket], which accepts and holds every connection without answering, so
 * each socket stays in its handshake (and holds its dispatcher thread) for the whole run.
 * Past the dispatcher's `maxRequests` (1024) the remaining dials queue inside OkHttp, so a
 * run above that only waits for the first 1024 to reach the server.
 *
 * A measurement, not a timing assertion: it prints the numbers and only checks that every
 * relay was dialed. Opt in, since it opens thousands of sockets:
 * `./gradlew :cli:test --tests '*RelayDialSpanBenchmark*' -DdialSpanBench=true [-DdialSpanRelays=1000]`
 */
class RelayDialSpanBenchmark {
    private val enabled = System.getProperty("dialSpanBench") == "true"
    private val relays = System.getProperty("dialSpanRelays")?.toInt() ?: 1000

    private class Result(
        val spanMs: Double,
        val acceptedMs: Double,
        val reached: Int,
        val platformThreadsAdded: Int,
    )

    @Test
    fun dialSpanPlatformVsVirtual() {
        if (!enabled) return

        val loopback = InetAddress.getLoopbackAddress()
        val server = ServerSocket(0, 8192, loopback)
        val held = ConcurrentLinkedQueue<Socket>()
        val accepted = AtomicInteger()
        val acceptor =
            thread(isDaemon = true, name = "dial-span-acceptor") {
                // Keep every socket referenced: a collected one closes, and quartz redials it.
                while (!server.isClosed) {
                    val s = runCatching { server.accept() }.getOrNull() ?: break
                    held += s
                    accepted.incrementAndGet()
                }
            }

        try {
            fun run(
                label: String,
                executor: () -> ExecutorService?,
                count: Int,
            ): Result {
                val r = measure(label, executor(), count, server.localPort, accepted, held)
                println(
                    "dial-span %-9s relays=%d  subscribe->last onConnecting=%.0fms  ->%d accepted=%.0fms  platform threads +%d"
                        .format(label, count, r.spanMs, r.reached, r.acceptedMs, r.platformThreadsAdded),
                )
                return r
            }

            // Warm both paths (class loading, JIT) before the counted runs.
            run("warmup-p", { null }, 100)
            run("warmup-v", { Executors.newVirtualThreadPerTaskExecutor() }, 100)
            repeat(2) {
                run("platform", { null }, relays)
                run("virtual", { Executors.newVirtualThreadPerTaskExecutor() }, relays)
            }
        } finally {
            server.close()
            acceptor.join(2_000)
            held.forEach { runCatching { it.close() } }
        }
    }

    private fun measure(
        label: String,
        executor: ExecutorService?,
        count: Int,
        port: Int,
        accepted: AtomicInteger,
        held: ConcurrentLinkedQueue<Socket>,
    ): Result {
        val factory = OkHttpClientFactoryForRelays("amy-dial-span", SurgeDns(), OnionLocationCache(), executor)
        // SurgeDns drops loopback answers for public names (a rebinding guard), so the stub
        // replaces it outright. newBuilder() keeps the factory's dispatcher, the part measured.
        val http =
            factory
                .buildHttpClient(null, OkHttpClientFactoryForRelays.DEFAULT_TIMEOUT_ON_WIFI_SECS)
                .newBuilder()
                .dns { listOf(InetAddress.getLoopbackAddress()) }
                .build()

        val urls = (0 until count).map { "ws://r$it.$label.dialspan.example:$port/".normalizeRelayUrl() }
        val connecting = AtomicInteger()
        val allConnecting = AtomicLong()
        val client = NostrClient(BasicOkHttpWebSocket.Builder { http })
        client.addConnectionListener(
            object : RelayConnectionListener {
                override fun onConnecting(relay: IRelayClient) {
                    // The count-th, not the latest: a dial that later times out redials.
                    if (connecting.incrementAndGet() == count) allConnecting.set(System.nanoTime())
                }
            },
        )

        // Let the client's own startup (pool sample, first reconnect debounce) settle first.
        Thread.sleep(600)

        val threads = ManagementFactory.getThreadMXBean()
        val threadsBefore = threads.threadCount
        val acceptedBefore = accepted.get()
        // A live websocket holds its dispatcher slot for life, so past maxRequests the rest
        // queue until a handshake times out; only that many can reach the server promptly.
        val reachable = minOf(count, http.dispatcher.maxRequests)

        val start = System.nanoTime()
        client.subscribe("dial-span", urls.associateWith { listOf(Filter(kinds = listOf(1), limit = 1)) })

        val deadline = System.nanoTime() + 60_000_000_000L
        while ((connecting.get() < count || accepted.get() - acceptedBefore < reachable) && System.nanoTime() < deadline) {
            Thread.sleep(1)
        }
        val acceptedAt = System.nanoTime()
        val threadsAdded = threads.threadCount - threadsBefore

        val reached = accepted.get() - acceptedBefore
        assertTrue(connecting.get() >= count, "$label: every relay dialed")
        assertTrue(reached >= reachable, "$label: $reached of $reachable dials reached the server")

        client.close()
        http.dispatcher.cancelAll()
        http.dispatcher.executorService.shutdown()
        held.forEach { runCatching { it.close() } }
        held.clear()
        // Let the cancelled readers wind down before the next run counts threads.
        Thread.sleep(1_000)

        return Result(
            spanMs = (allConnecting.get() - start) / 1e6,
            acceptedMs = (acceptedAt - start) / 1e6,
            reached = reached,
            platformThreadsAdded = threadsAdded,
        )
    }
}
