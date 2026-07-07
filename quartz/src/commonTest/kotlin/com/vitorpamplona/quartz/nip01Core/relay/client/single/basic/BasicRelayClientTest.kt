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
package com.vitorpamplona.quartz.nip01Core.relay.client.single.basic

import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import kotlin.test.Test
import kotlin.test.assertEquals

class BasicRelayClientTest {
    private class RecordingConnectionListener : RelayConnectionListener {
        val cannotConnectMessages = mutableListOf<String>()

        override fun onCannotConnect(
            relay: IRelayClient,
            errorMessage: String,
        ) {
            cannotConnectMessages.add(errorMessage)
        }
    }

    private class MessagelessException : Exception()

    private data class Harness(
        val socketListener: WebSocketListener,
        val connectionListener: RecordingConnectionListener,
    )

    private fun connectAndCapture(): Harness {
        val builder = FakeWebsocketBuilder()
        val listener = RecordingConnectionListener()
        val client =
            BasicRelayClient(
                NormalizedRelayUrl("wss://relay.example.com/"),
                builder,
                listener,
            )
        client.connect()
        return Harness(builder.lastListener, listener)
    }

    @Test
    fun onFailureWithNullMessageReportsExceptionClassName() {
        val (socket, listener) = connectAndCapture()

        socket.onFailure(MessagelessException(), null, null)

        assertEquals(
            listOf("WebSocket Failure: MessagelessException"),
            listener.cannotConnectMessages,
        )
    }

    @Test
    fun onFailureWithMessageAppendsExceptionClassName() {
        val (socket, listener) = connectAndCapture()

        socket.onFailure(Exception("Connection reset"), null, null)

        // The exception type is appended so listeners can classify the failure by
        // its stable class name rather than by localized message text.
        assertEquals(
            listOf("WebSocket Failure: Connection reset (Exception)"),
            listener.cannotConnectMessages,
        )
    }

    @Test
    fun serverMisconfiguredWithNullMessageReportsExceptionClassName() {
        val (socket, listener) = connectAndCapture()

        socket.onFailure(MessagelessException(), 200, "OK")

        assertEquals(
            listOf("Server Misconfigured. Response: 200 OK. Exception: MessagelessException"),
            listener.cannotConnectMessages,
        )
    }
}
