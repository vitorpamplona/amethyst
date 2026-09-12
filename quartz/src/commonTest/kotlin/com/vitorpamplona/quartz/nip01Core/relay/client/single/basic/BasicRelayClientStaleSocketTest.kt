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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A socket this client has retired must not be able to reach back into its state.
 *
 * Socket layers report on their own threads, after the fact: OkHttp delivers `onClosed` from its
 * writer thread once a close handshake completes, and a cancelled socket's failure lands later
 * still. The pool rebuilds a session with `disconnect()` + `connect()`, so a late callback from
 * the old socket used to null the new one out from under the client -- orphaning a live
 * connection and dialing a third one on the next pass.
 */
class BasicRelayClientStaleSocketTest {
    private class Counting : RelayConnectionListener {
        var connected = 0
        var disconnected = 0

        override fun onConnected(
            relay: IRelayClient,
            pingMillis: Int,
            compressed: Boolean,
        ) {
            connected++
        }

        override fun onDisconnected(relay: IRelayClient) {
            disconnected++
        }
    }

    private val url = NormalizedRelayUrl("wss://relay.example.com/")

    @Test
    fun `disconnect reports the teardown itself instead of waiting for the socket layer`() {
        val sockets = FakeWebsocketBuilder()
        val events = Counting()
        val client = BasicRelayClient(url, sockets, events)

        client.connect()
        sockets.lastListener.onOpen(50, false)
        assertTrue(client.isConnected())

        // FakeWebSocket.disconnect() never calls back -- the case OkHttp's cancel() leaves after
        // a relay-initiated close.
        client.disconnect()

        assertFalse(client.isConnected())
        assertEquals(1, events.disconnected, "the client itself must say the session ended")
    }

    @Test
    fun `a late callback from a replaced socket cannot touch the new session`() {
        val sockets = FakeWebsocketBuilder()
        val events = Counting()
        val client = BasicRelayClient(url, sockets, events)

        client.connect()
        val first = sockets.lastListener
        first.onOpen(50, false)

        // What RelayPool.reconnectIfNeedsTo does when the transport changed under a live socket.
        client.disconnect()
        client.connect()
        val second = sockets.lastListener
        second.onOpen(50, false)
        assertTrue(client.isConnected())
        assertEquals(2, events.connected)
        assertEquals(1, events.disconnected)

        // The old socket finally reports, in every way OkHttp can.
        first.onClosed(1000, "late close handshake")
        first.onFailure(RuntimeException("Socket closed"), null, null)
        first.onOpen(50, false)

        assertTrue(client.isConnected(), "the live session must survive its predecessor's callbacks")
        assertEquals(2, events.connected, "no phantom connect")
        assertEquals(1, events.disconnected, "no phantom disconnect")
    }

    @Test
    fun `a callback after disconnect is not a second disconnect`() {
        val sockets = FakeWebsocketBuilder()
        val events = Counting()
        val client = BasicRelayClient(url, sockets, events)

        client.connect()
        val socket = sockets.lastListener
        socket.onOpen(50, false)
        client.disconnect()

        // The cancel's own failure arriving afterwards, as it does for a healthy socket.
        socket.onFailure(RuntimeException("Canceled"), null, null)

        assertEquals(1, events.disconnected, "disconnect() already reported this session")
    }
}
