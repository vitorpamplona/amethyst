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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BasicRelayClientMessageSizeTest {
    private class RecordingListener : RelayConnectionListener {
        val messages = mutableListOf<Message>()

        override fun onIncomingMessage(
            relay: IRelayClient,
            msgStr: String,
            msg: Message,
        ) {
            messages.add(msg)
        }
    }

    private data class Harness(
        val socketListener: WebSocketListener,
        val recordingListener: RecordingListener,
    )

    private fun connect(): Harness {
        val builder = FakeWebsocketBuilder()
        val listener = RecordingListener()
        val client =
            BasicRelayClient(
                NormalizedRelayUrl("wss://user.kindpag.es/"),
                builder,
                listener,
            )
        client.connect()
        return Harness(builder.lastListener, listener)
    }

    @Test
    fun oversizedMessageIsDropped() {
        val (socket, listener) = connect()

        val oversized = "x".repeat(BasicRelayClient.MAX_MESSAGE_BYTES + 1)
        socket.onMessage(oversized)

        assertEquals(0, listener.messages.size)
    }

    @Test
    fun messageBelowLimitIsNotDropped() {
        val (socket, listener) = connect()

        // Minimal valid EVENT message
        val json =
            """["EVENT","sub1",{"id":"0000000000000000000000000000000000000000000000000000000000000001","pubkey":"0000000000000000000000000000000000000000000000000000000000000002","created_at":1000000000,"kind":1,"tags":[],"content":"hi","sig":"${"00".repeat(64)}"}]"""
        assertTrue(json.length < BasicRelayClient.MAX_MESSAGE_BYTES)

        socket.onMessage(json)

        assertEquals(1, listener.messages.size)
    }

    /**
     * Regression test for the OOM crash reported against wss://user.kindpag.es/:
     * a kind:3 contact list with ~2000 "p" tags (~146 KB wire JSON) must parse
     * successfully and must not be rejected by the MAX_MESSAGE_BYTES guard.
     */
    @Test
    fun kind3With2000FollowsParsesSuccessfully() {
        val (socket, listener) = connect()

        // Build a wire message that matches the shape of the real event reported:
        // same id / pubkey / sig, ~2000 sequential dummy p-tags.
        val tags =
            (1..2000).joinToString(",") { i ->
                val hex = i.toString(16).padStart(64, '0')
                """["p","$hex"]"""
            }
        val json =
            """["EVENT","test_sub",{"id":"d424a8e70ff23b7ac1712a79b2ad1653eefae43e194fb33140539039b50bdd66","pubkey":"106239448252d78fdda07a145bf797556dbdf65e1fa9807e58171a638872fe04","created_at":1782271624,"kind":3,"tags":[$tags],"content":"","sig":"1db13653a51384a3bc1aa0152768b05ab7833bc56f18d0d829e6151aac028c09633d8684be834204ef0b2415515772cc0c2ad71ebaa8a6ed2f19ef7cfd57cd16"}]"""

        // Confirm the event is within the allowed size (it should be ~146 KB)
        assertTrue(json.length < BasicRelayClient.MAX_MESSAGE_BYTES, "Event should be under size limit but was ${json.length} chars")

        socket.onMessage(json)

        assertEquals(1, listener.messages.size)
        val msg = assertIs<EventMessage>(listener.messages[0])
        assertEquals("d424a8e70ff23b7ac1712a79b2ad1653eefae43e194fb33140539039b50bdd66", msg.event.id)
        assertEquals("106239448252d78fdda07a145bf797556dbdf65e1fa9807e58171a638872fe04", msg.event.pubKey)
        assertEquals(3, msg.event.kind)
        assertEquals(2000, msg.event.tags.size)
    }
}
