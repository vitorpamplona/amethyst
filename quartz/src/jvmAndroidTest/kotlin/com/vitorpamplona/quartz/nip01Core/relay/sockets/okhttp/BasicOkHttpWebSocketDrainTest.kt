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
import kotlinx.coroutines.delay
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
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * A relay that hangs up after sending a burst: the messages it already sent are delivered, in
 * order, before the end is reported. The socket used to cancel its delivering coroutine at the
 * end, dropping whatever a slow consumer had not reached yet (measured with amy exporting through
 * a proxy that dropped every ~20 MB: most of each burst thrown away and asked for again) and
 * cancelling the message in hand where it suspended.
 *
 * Driven against a minimal RFC 6455 server on a loopback [ServerSocket], like
 * [BasicOkHttpWebSocketCloseHandshakeTest].
 */
class BasicOkHttpWebSocketDrainTest {
    /** Handshakes one client, then sends it [count] text frames and drops the TCP session, unless [holdOpen]. */
    private class BurstThenHangUp(
        private val count: Int,
        private val holdOpen: Boolean = false,
    ) : AutoCloseable {
        private val released = CountDownLatch(1)

        private val server = ServerSocket(0)
        val url = NormalizedRelayUrl("ws://127.0.0.1:${server.localPort}/")
        val hungUp = CountDownLatch(1)
        private var socket: Socket? = null

        private val thread =
            thread(isDaemon = true, name = "burst-relay") {
                runCatching {
                    val s = server.accept()
                    socket = s
                    val output = s.getOutputStream()
                    handshake(s.getInputStream(), output)
                    repeat(count) { i -> textFrame(output, "[\"NOTICE\",\"m$i\"]") }
                    output.flush()
                    if (holdOpen) released.await(10, TimeUnit.SECONDS)
                    s.close()
                    hungUp.countDown()
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

        /** An unmasked server text frame; every payload here is under 126 bytes. */
        private fun textFrame(
            output: OutputStream,
            text: String,
        ) {
            val payload = text.toByteArray()
            output.write(byteArrayOf(0x81.toByte(), payload.size.toByte()))
            output.write(payload)
        }

        override fun close() {
            released.countDown()
            runCatching { socket?.close() }
            runCatching { server.close() }
            thread.join(2_000)
        }
    }

    /** Takes [perMessageMs] over each message, as a consumer behind a full buffer does. */
    private class SlowRecorder(
        private val perMessageMs: Long,
    ) : WebSocketListener {
        val events: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val ended = CountDownLatch(1)
        val closedCount = AtomicInteger(0)
        val failures = AtomicInteger(0)
        val opened = CountDownLatch(1)

        override fun onOpen(
            pingMillis: Int,
            compression: Boolean,
        ) = opened.countDown()

        override suspend fun onMessage(text: String) {
            delay(perMessageMs)
            events.add(text.substringAfter(",\"").substringBefore('"'))
        }

        override fun onClosed(
            code: Int,
            reason: String,
        ) {
            events.add("closed")
            closedCount.incrementAndGet()
            ended.countDown()
        }

        override fun onFailure(
            t: Throwable,
            code: Int?,
            response: String?,
        ) {
            events.add("failure")
            failures.incrementAndGet()
            ended.countDown()
        }
    }

    @Test
    fun `messages received before the relay hangs up are delivered, then the end`() {
        BurstThenHangUp(count = 50).use { relay ->
            val recorder = SlowRecorder(perMessageMs = 20)
            val client = OkHttpClient()
            val socket = BasicOkHttpWebSocket(relay.url, { client }, recorder)

            socket.connect()
            assertTrue("the relay never hung up", relay.hungUp.await(5, TimeUnit.SECONDS))
            assertTrue("no end reported", recorder.ended.await(10, TimeUnit.SECONDS))

            val expected = (0 until 50).map { "m$it" } + "failure"
            assertEquals("every message, in order, then the end", expected, recorder.events.toList())
            assertTrue("an ended session needs a fresh dial", socket.needsReconnect())

            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun `disconnect while the last messages are delivered ends it at once, reported once`() {
        BurstThenHangUp(count = 50).use { relay ->
            val recorder = SlowRecorder(perMessageMs = 50)
            val client = OkHttpClient()
            val socket = BasicOkHttpWebSocket(relay.url, { client }, recorder)

            socket.connect()
            assertTrue("the relay never hung up", relay.hungUp.await(5, TimeUnit.SECONDS))
            // Well inside the ~2.5 s it takes to deliver the burst.
            Thread.sleep(500)
            assertTrue("still delivering: no reconnect pass may tear it down", !socket.needsReconnect())

            socket.disconnect()
            assertEquals("disconnect() reports synchronously", 1, recorder.closedCount.get())
            val delivered = recorder.events.size

            Thread.sleep(500)
            assertEquals("nothing reaches the listener after disconnect()", delivered, recorder.events.size)
            assertEquals("one terminal report", 1, recorder.closedCount.get())
            assertEquals("the relay's own end is dropped with the queue", 0, recorder.failures.get())
            assertNull(recorder.events.firstOrNull { it == "failure" })
            assertTrue(socket.needsReconnect())

            client.dispatcher.executorService.shutdown()
        }
    }

    // A reconnect pass asks needsReconnect() of every socket: true while the session drained would
    // tear it down and drop what it is still delivering.
    @Test
    fun `a draining session does not ask for a reconnect, and an ended one does`() {
        BurstThenHangUp(count = 50).use { relay ->
            val recorder = SlowRecorder(perMessageMs = 20)
            val socket = BasicOkHttpWebSocket(relay.url, { OkHttpClient() }, recorder)
            socket.connect()

            assertTrue("relay never hung up", relay.hungUp.await(5, TimeUnit.SECONDS))
            Thread.sleep(200)
            assertTrue("still delivering", recorder.ended.count == 1L)
            assertTrue("a draining session must not be torn down", !socket.needsReconnect())

            assertTrue("the end was never reported", recorder.ended.await(10, TimeUnit.SECONDS))
            assertTrue("an ended session is reconnected", socket.needsReconnect())
        }
    }

    // The apps route a relay through Tor or not per URL, and change timeouts with the network: the
    // client the builder would dial with now is compared with the one the session was dialed with.
    @Test
    fun `an open session asks for a reconnect when its client would now dial differently`() {
        BurstThenHangUp(count = 0, holdOpen = true).use { relay ->
            val dialed = OkHttpClient()
            var current = dialed
            val recorder = SlowRecorder(perMessageMs = 0)
            val socket = BasicOkHttpWebSocket(relay.url, { current }, recorder)
            socket.connect()
            assertTrue("never opened", recorder.opened.await(5, TimeUnit.SECONDS))

            assertTrue("same client, no reconnect", !socket.needsReconnect())
            current = dialed.newBuilder().build()
            assertTrue("an equal client is not a change", !socket.needsReconnect())
            current = dialed.newBuilder().readTimeout(42, TimeUnit.SECONDS).build()
            assertTrue("a changed timeout reconnects", socket.needsReconnect())

            socket.disconnect()
        }
    }
}
