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
package com.vitorpamplona.geode

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ktor allocates a frame's whole buffer as soon as the header names its length, so without a cap
 * one header claiming 2 GB costs the relay 2 GB before a byte of payload arrives.
 */
class KtorRelayFrameLimitTest {
    private lateinit var relay: RelayEngine
    private lateinit var server: KtorRelay
    private val httpClient = OkHttpClient.Builder().build()

    @BeforeTest
    fun setup() {
        relay = RelayEngine(url = "ws://127.0.0.1:7771/".normalizeRelayUrl())
        server = KtorRelay(relay, host = "127.0.0.1", port = 0, maxFrameSize = 64 * 1024).start()
    }

    @AfterTest
    fun teardown() {
        server.stop()
        relay.close()
    }

    private class Recorder : WebSocketListener() {
        val messages = mutableListOf<String>()
        val firstMessage = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val closeCode = AtomicReference<Int?>(null)

        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) {
            synchronized(messages) { messages.add(text) }
            firstMessage.countDown()
        }

        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            closeCode.set(code)
            closed.countDown()
        }

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?,
        ) {
            closed.countDown()
        }
    }

    private fun open(listener: Recorder): WebSocket = httpClient.newWebSocket(Request.Builder().url(server.url.replace("ws://", "http://")).build(), listener)

    @Test
    fun aFrameOverTheLimitClosesTheConnection() {
        val listener = Recorder()
        val ws = open(listener)
        ws.send("[\"REQ\",\"big\",{\"search\":\"" + "x".repeat(128 * 1024) + "\"}]")

        assertTrue(listener.closed.await(10, TimeUnit.SECONDS), "the relay must refuse an oversized frame")
        assertEquals(1009, listener.closeCode.get(), "1009 is the close code for a message too big to process")
    }

    @Test
    fun aFrameUnderTheLimitIsServed() {
        val listener = Recorder()
        val ws = open(listener)
        ws.send("[\"REQ\",\"small\",{\"kinds\":[1],\"limit\":1}]")

        assertTrue(listener.firstMessage.await(10, TimeUnit.SECONDS))
        assertTrue(synchronized(listener.messages) { listener.messages.first() }.contains("EOSE"))
        ws.close(1000, null)
    }
}
