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

import com.vitorpamplona.quartz.nip01Core.limits.EventLimits
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.WebSocket as OkHttpWebSocket

// Layer 1 of security review 2026-04-24 §2.3: a hostile relay must not be able to deliver
// frames larger than EventLimits.MAX_RELAY_MESSAGE_LENGTH. Per-event caps in EventDeserializer
// (Layer 2) only fire after the JSON has reached the parser, so a 10 MB JSON message would
// already have allocated 10 MB before parsing begins. Layer 1 closes the socket at the WS
// boundary with RFC 6455 close code 1009 ("Message Too Big").
class BasicOkHttpWebSocketAcceptIncomingTest {
    private class RecordingWebSocket : OkHttpWebSocket {
        var closedCode: Int? = null
        var closedReason: String? = null

        override fun request(): Request = throw NotImplementedError("not used in test")

        override fun queueSize(): Long = 0

        override fun send(text: String): Boolean = throw NotImplementedError("not used in test")

        override fun send(bytes: ByteString): Boolean = throw NotImplementedError("not used in test")

        override fun close(
            code: Int,
            reason: String?,
        ): Boolean {
            closedCode = code
            closedReason = reason
            return true
        }

        override fun cancel() = Unit
    }

    private val noopListener =
        object : WebSocketListener {
            override fun onOpen(
                pingMillis: Int,
                compression: Boolean,
            ) = Unit

            override fun onMessage(text: String) = Unit

            override fun onClosed(
                code: Int,
                reason: String,
            ) = Unit

            override fun onFailure(
                t: Throwable,
                code: Int?,
                response: String?,
            ) = Unit
        }

    private fun newSubject(): BasicOkHttpWebSocket =
        // httpClient and url providers are never invoked because we never call connect().
        BasicOkHttpWebSocket(
            url = NormalizedRelayUrl("wss://example.invalid/"),
            httpClient = { _: NormalizedRelayUrl -> OkHttpClient() },
            out = noopListener,
        )

    @Test
    fun acceptIncomingForwardsMessagesAtAndBelowTheCap() {
        val sut = newSubject()
        val ws = RecordingWebSocket()

        assertTrue(sut.acceptIncoming(ws, "x".repeat(EventLimits.MAX_RELAY_MESSAGE_LENGTH)))
        assertNull(ws.closedCode, "must not close socket on within-limit message")

        assertTrue(sut.acceptIncoming(ws, "x".repeat(1)))
        assertNull(ws.closedCode)
    }

    @Test
    fun acceptIncomingDropsAndClosesOnOversizedMessage() {
        val sut = newSubject()
        val ws = RecordingWebSocket()

        val accepted = sut.acceptIncoming(ws, "x".repeat(EventLimits.MAX_RELAY_MESSAGE_LENGTH + 1))

        assertFalse(accepted, "oversized message must not be forwarded")
        assertEquals(BasicOkHttpWebSocket.CLOSE_MESSAGE_TOO_LARGE, ws.closedCode)
        assertEquals("message too large", ws.closedReason)
    }

    @Test
    fun closeCodeIsRfc6455MessageTooBig() {
        // RFC 6455 §7.4.1 reserves 1009 for "Message Too Big". This guards against the
        // constant accidentally drifting and silencing the wrong category of error.
        assertEquals(1009, BasicOkHttpWebSocket.CLOSE_MESSAGE_TOO_LARGE)
    }
}
