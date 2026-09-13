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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * A relay-initiated close must be answered, or OkHttp never finishes the handshake.
 *
 * OkHttp fires `onClosed` only once BOTH peers have sent a CLOSE frame, and sending ours is the
 * application's job (its `WebSocketEcho` recipe answers `onClosing` with `close(1000, null)`).
 * Before [BasicOkHttpWebSocket] did that, a relay's CLOSE frame left the socket half-closed: no
 * `onClosed`, no `onFailure`, `send()` still accepted and discarded, and a later `cancel()` silent
 * too. The relay client kept believing it was connected, with its REQs live, until the ping path
 * failed up to two ping intervals later.
 *
 * Driven against a minimal RFC 6455 server on a loopback [ServerSocket] rather than a mock
 * server library, so the test needs no new dependency and controls the exact frames on the wire.
 */
class BasicOkHttpWebSocketCloseHandshakeTest {
    /** Handshakes one client, sends it a CLOSE frame on demand, and records the frames it sends back. */
    private class TinyRelay : AutoCloseable {
        private val server = ServerSocket(0)
        val url = NormalizedRelayUrl("ws://127.0.0.1:${server.localPort}/")

        private val handshaken = CountDownLatch(1)
        val clientCloseFrame = CountDownLatch(1)
        val clientCloseCode = AtomicInteger(-1)

        private var socket: Socket? = null
        private var out: OutputStream? = null

        private val thread =
            thread(isDaemon = true, name = "tiny-relay") {
                runCatching {
                    val s = server.accept()
                    socket = s
                    val input = s.getInputStream()
                    val output = s.getOutputStream()
                    out = output
                    handshake(input, output)
                    handshaken.countDown()
                    readFrames(input)
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
            val accept =
                Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()),
                )
            output.write(
                (
                    "HTTP/1.1 101 Switching Protocols\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: $accept\r\n\r\n"
                ).toByteArray(Charsets.ISO_8859_1),
            )
            output.flush()
        }

        /** Client frames are masked; decode enough to spot a CLOSE and read its status code. */
        private fun readFrames(input: InputStream) {
            while (true) {
                val b0 = input.read()
                if (b0 == -1) return
                val b1 = input.read()
                if (b1 == -1) return
                val opcode = b0 and 0x0F
                var len = b1 and 0x7F
                if (len == 126) {
                    len = (input.read() shl 8) or input.read()
                } else if (len == 127) {
                    len = 0
                    repeat(8) { len = (len shl 8) or input.read() }
                }
                val masked = (b1 and 0x80) != 0
                val mask = if (masked) ByteArray(4) { input.read().toByte() } else ByteArray(4)
                val payload = ByteArray(len) { i -> (input.read() xor mask[i % 4].toInt()).toByte() }
                if (opcode == 0x8) {
                    if (len >= 2) {
                        clientCloseCode.set(((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF))
                    }
                    clientCloseFrame.countDown()
                }
            }
        }

        fun awaitClient() = handshaken.await(5, TimeUnit.SECONDS)

        /** Server-initiated CLOSE, status 1000, unmasked as servers send it. The TCP session stays open. */
        fun sendClose() {
            val output = checkNotNull(out) { "no client yet" }
            output.write(byteArrayOf(0x88.toByte(), 0x02, 0x03, 0xE8.toByte()))
            output.flush()
        }

        override fun close() {
            runCatching { socket?.close() }
            runCatching { server.close() }
            thread.join(2_000)
        }
    }

    private class Recorder : WebSocketListener {
        val opened = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val closedCount = AtomicInteger(0)
        val closedCode = AtomicInteger(-1)
        val failure = AtomicReference<Throwable?>(null)

        override fun onOpen(
            pingMillis: Int,
            compression: Boolean,
        ) = opened.countDown()

        override suspend fun onMessage(text: String) {}

        override fun onClosed(
            code: Int,
            reason: String,
        ) {
            closedCode.set(code)
            closedCount.incrementAndGet()
            closed.countDown()
        }

        override fun onFailure(
            t: Throwable,
            code: Int?,
            response: String?,
        ) {
            failure.set(t)
        }
    }

    @Test
    fun `a relay initiated close is answered and reported as closed`() {
        TinyRelay().use { relay ->
            val recorder = Recorder()
            val client = OkHttpClient()
            val socket = BasicOkHttpWebSocket(relay.url, { client }, recorder)

            socket.connect()
            assertTrue("relay never saw the client", relay.awaitClient())
            assertTrue("no onOpen", recorder.opened.await(5, TimeUnit.SECONDS))

            relay.sendClose()

            // The half of the handshake that is ours to send.
            assertTrue("client never answered the relay's CLOSE frame", relay.clientCloseFrame.await(5, TimeUnit.SECONDS))
            assertEquals(1000, relay.clientCloseCode.get())

            // And the terminal callback the relay client's bookkeeping depends on.
            assertTrue("onClosed never fired", recorder.closed.await(5, TimeUnit.SECONDS))
            assertEquals("the relay's status code is what gets reported", 1000, recorder.closedCode.get())
            assertNull("a clean handshake is not a failure", recorder.failure.get())

            // The session already ended; the usual teardown afterwards must not report it twice.
            socket.disconnect()
            assertEquals("one terminal report per session", 1, recorder.closedCount.get())
            assertTrue("a closed socket needs a fresh dial", socket.needsReconnect())

            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun `disconnect reports the session end once, synchronously, and drops what OkHttp says afterwards`() {
        TinyRelay().use { relay ->
            val recorder = Recorder()
            val client = OkHttpClient()
            val socket = BasicOkHttpWebSocket(relay.url, { client }, recorder)

            socket.connect()
            assertTrue("relay never saw the client", relay.awaitClient())
            assertTrue("no onOpen", recorder.opened.await(5, TimeUnit.SECONDS))

            socket.disconnect()

            // Reported before disconnect() returned: the relay client dials the replacement
            // right after this call and must not hear from the old socket later.
            assertEquals("disconnect() must report synchronously", 1, recorder.closedCount.get())
            assertEquals(1000, recorder.closedCode.get())
            assertTrue(socket.needsReconnect())

            // OkHttp's own reaction to cancel() -- a failure on its reader thread -- and the relay's
            // reaction to the dropped TCP session must both be swallowed.
            Thread.sleep(500)
            assertEquals("no second report", 1, recorder.closedCount.get())
            assertNull("the cancel's failure must not surface", recorder.failure.get())

            client.dispatcher.executorService.shutdown()
        }
    }
}
