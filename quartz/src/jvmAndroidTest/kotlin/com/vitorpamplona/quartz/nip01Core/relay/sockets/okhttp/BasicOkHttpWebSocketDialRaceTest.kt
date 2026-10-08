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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import okhttp3.OkHttpClient
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A [BasicOkHttpWebSocket.disconnect] that lands while [BasicOkHttpWebSocket.connect] is still
 * dialing must end that dial.
 *
 * Measured with amy under load (16 walks at once): one relay client opened two connections to the
 * same relay and every message of its first page arrived twice. Mid-dial, before OkHttp hands
 * back its socket, the adapter reported [BasicOkHttpWebSocket.needsReconnect], so a concurrent
 * reconnect pass tore it down and dialed again; and [BasicOkHttpWebSocket.disconnect] returned
 * early with no socket to close, so the first dial was never ended and opened too. Both sessions
 * fed the same relay client: the REQ went out twice, each page came back twice, and the first
 * connection stayed open on the relay for the life of the process.
 */
class BasicOkHttpWebSocketDialRaceTest {
    /** Accepts WebSocket handshakes and records each connection's end. */
    private class HandshakeServer : AutoCloseable {
        private val server = ServerSocket(0)
        val url = NormalizedRelayUrl("ws://127.0.0.1:${server.localPort}/")
        val accepted = AtomicInteger(0)
        val closedByClient = CountDownLatch(1)

        private val acceptor =
            thread(isDaemon = true, name = "handshake-server") {
                runCatching {
                    while (true) {
                        val s = server.accept()
                        accepted.incrementAndGet()
                        thread(isDaemon = true) {
                            runCatching {
                                val input = s.getInputStream()
                                handshake(input, s.getOutputStream())
                                // Blocks until the client drops the TCP connection.
                                while (input.read() != -1) { }
                                closedByClient.countDown()
                            }
                        }
                    }
                }
            }

        private fun handshake(
            input: InputStream,
            output: OutputStream,
        ) {
            var key: String? = null
            val line = StringBuilder()
            while (true) {
                val c = input.read()
                check(c != -1) { "EOF during handshake" }
                if (c == '\n'.code) {
                    val l = line.toString().trim()
                    if (l.isEmpty()) break
                    if (l.lowercase().startsWith("sec-websocket-key:")) key = l.substring(18).trim()
                    line.setLength(0)
                } else if (c != '\r'.code) {
                    line.append(c.toChar())
                }
            }
            val accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()))
            output.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            output.flush()
        }

        override fun close() {
            runCatching { server.close() }
            acceptor.interrupt()
        }
    }

    private val server = HandshakeServer()
    private val okhttp = OkHttpClient.Builder().build()

    @AfterTest
    fun tearDown() {
        server.close()
        okhttp.dispatcher.executorService.shutdownNow()
        okhttp.connectionPool.evictAll()
    }

    private class RecordingListener : WebSocketListener {
        val opened = CountDownLatch(1)

        override fun onOpen(
            pingMillis: Int,
            compression: Boolean,
        ) = opened.countDown()

        override suspend fun onMessage(text: String) {}

        override fun onClosed(
            code: Int,
            reason: String,
        ) {}

        override fun onFailure(
            t: Throwable,
            code: Int?,
            response: String?,
        ) {}
    }

    @Test
    fun aDialInFlightIsNotReportedAsNeedingAReconnect() {
        val listener = RecordingListener()
        lateinit var socket: BasicOkHttpWebSocket
        var midDial: Boolean? = null
        socket =
            BasicOkHttpWebSocket(server.url, { _ ->
                midDial = socket.needsReconnect()
                okhttp
            }, listener)

        assertTrue(socket.needsReconnect(), "not dialed yet")
        socket.connect()
        assertEquals(false, midDial, "a dial in flight is not a dead socket")
        assertTrue(listener.opened.await(5, TimeUnit.SECONDS))
        assertFalse(socket.needsReconnect())
        socket.disconnect()
    }

    @Test
    fun aDisconnectDuringTheDialEndsIt() {
        val listener = RecordingListener()
        lateinit var socket: BasicOkHttpWebSocket
        socket =
            BasicOkHttpWebSocket(server.url, { _ ->
                // A concurrent teardown landing before OkHttp has handed back the socket.
                socket.disconnect()
                okhttp
            }, listener)

        socket.connect()

        assertFalse(listener.opened.await(2, TimeUnit.SECONDS), "the ended dial must not open a session")
        assertTrue(socket.needsReconnect(), "an ended session needs a fresh dial")
        if (server.accepted.get() > 0) {
            assertTrue(server.closedByClient.await(5, TimeUnit.SECONDS), "and its connection must not stay open on the relay")
        }
    }
}
