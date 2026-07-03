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
package com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp

import com.vitorpamplona.geode.KtorRelay
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.geode.fixtures.SyntheticEvents
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The receive channel between OkHttp's reader thread and the consumer
 * coroutine is bounded (see [BasicOkHttpWebSocket.RECEIVE_BUFFER_FRAMES]) so a
 * slow consumer exerts TCP backpressure instead of growing the heap without
 * limit. Bounding must never DROP frames or deadlock — this test forces
 * sustained backpressure (an 8-frame buffer against a consumer that sleeps on
 * every message) and asserts every EVENT plus the trailing EOSE still arrives,
 * in order.
 */
class BoundedReceiveBufferTest {
    companion object {
        const val EVENTS = 600
        const val CONSUMER_DELAY_MS = 3L
    }

    @Test
    fun slowConsumerWithTinyBufferReceivesEverythingInOrder() {
        val placeholder = "ws://127.0.0.1:7771/".normalizeRelayUrl()
        val engine = RelayEngine(url = placeholder)
        runBlocking {
            val events =
                (1..EVENTS).map {
                    SyntheticEvents.fakeEvent(idSeed = it, kind = 1, createdAt = it.toLong())
                }
            events.chunked(500).forEach { engine.store.batchInsert(it) }
        }
        val server = KtorRelay(engine, port = 0).start()
        val httpClient = OkHttpClient.Builder().build()

        try {
            val received = AtomicInteger(0)
            val lastCreatedAt = AtomicInteger(Int.MAX_VALUE) // relays stream newest-first
            val orderViolations = AtomicInteger(0)
            val eose = CountDownLatch(1)

            lateinit var socket: BasicOkHttpWebSocket
            val listener =
                object : WebSocketListener {
                    override fun onOpen(
                        pingMillis: Int,
                        compression: Boolean,
                    ) {
                        socket.send("""["REQ","slow",{"kinds":[1]}]""")
                    }

                    override fun onMessage(text: String) {
                        // Deliberately slower than the socket delivers.
                        Thread.sleep(CONSUMER_DELAY_MS)
                        if (text.startsWith("[\"EVENT\"")) {
                            received.incrementAndGet()
                            val ts = text.substringAfter("\"created_at\":").takeWhile { it.isDigit() }.toIntOrNull()
                            if (ts != null) {
                                if (ts > lastCreatedAt.get()) orderViolations.incrementAndGet()
                                lastCreatedAt.set(ts)
                            }
                        } else if (text.startsWith("[\"EOSE\"")) {
                            eose.countDown()
                        }
                    }

                    override fun onClosed(
                        code: Int,
                        reason: String,
                    ) {}

                    override fun onFailure(
                        t: Throwable,
                        code: Int?,
                        response: String?,
                    ) {
                        eose.countDown()
                    }
                }

            socket =
                BasicOkHttpWebSocket(
                    url = server.url.normalizeRelayUrl(),
                    httpClient = { httpClient },
                    out = listener,
                    receiveBufferFrames = 8,
                )
            socket.connect()

            assertTrue(eose.await(60, TimeUnit.SECONDS), "EOSE must arrive despite sustained backpressure")
            assertEquals(EVENTS, received.get(), "bounding the buffer must never drop frames")
            assertEquals(0, orderViolations.get(), "frame order must be preserved under backpressure")

            socket.disconnect()
        } finally {
            httpClient.dispatcher.executorService.shutdown()
            server.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
            engine.close()
        }
    }
}
