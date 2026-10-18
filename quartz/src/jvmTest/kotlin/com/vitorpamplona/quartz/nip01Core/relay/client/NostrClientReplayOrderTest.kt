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
package com.vitorpamplona.quartz.nip01Core.relay.client

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A fresh connection replays every subscription and pending EVENT ([NostrClient.syncFilters]).
 * It used to do that from a coroutine, so anything sent directly once the socket was up could
 * reach the relay ahead of a REQ registered while it was opening. NIP-46 makes that fatal:
 * kind 24133 is ephemeral, so a request that reaches the relay before the bunker's REQ is handed
 * to nobody (NostrSignerRemotePrivateZapTest timed out on a loaded iOS simulator this way).
 *
 * The client runs on a paused dispatcher here, so nothing it launches runs: what is on the wire
 * is what was sent inline.
 */
class NostrClientReplayOrderTest {
    private val url = NormalizedRelayUrl("wss://order.example.com/")
    private val note: Event = NostrSignerSync(KeyPair()).sign(TextNoteEvent.build("request"))

    /** Records every frame; opens at once on connect unless [openOnConnect] is false. */
    private class RecordingSockets(
        val openOnConnect: Boolean,
    ) : WebsocketBuilder {
        val sent: MutableList<String> = Collections.synchronizedList(mutableListOf())
        lateinit var listener: WebSocketListener
        var onSend: (String) -> Unit = {}

        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket {
            listener = out
            return object : WebSocket {
                override fun needsReconnect() = false

                override fun connect() {
                    if (openOnConnect) out.onOpen(0, false)
                }

                override fun disconnect() {}

                override fun send(msg: String): Boolean {
                    sent.add(msg)
                    onSend(msg)
                    return true
                }
            }
        }

        fun kinds(): List<String> = synchronized(sent) { sent.map { it.substringAfter("[\"").substringBefore('"') + ":" + it.split('"').getOrElse(3) { "" } } }
    }

    @Test
    fun aSubscriptionThatOpensTheConnectionIsSentBeforeWhatFollowsIt() {
        val sockets = RecordingSockets(openOnConnect = true)
        val client = NostrClient(sockets, CoroutineScope(StandardTestDispatcher()))

        client.subscribe("bunker", mapOf(url to listOf(Filter(kinds = listOf(24133)))), null)
        client.subscribe("remote", mapOf(url to listOf(Filter(kinds = listOf(24133)))), null)
        client.publish(note, setOf(url))

        val wire = sockets.kinds()
        assertEquals(listOf("REQ:bunker", "REQ:remote"), wire.take(2), "$wire")
        assertTrue(wire.drop(2).all { it.startsWith("EVENT") }, "$wire")
    }

    @Test
    fun aSendMadeWhileTheReplayRunsGoesOutAfterIt() {
        val sockets = RecordingSockets(openOnConnect = false)
        val client = NostrClient(sockets, CoroutineScope(StandardTestDispatcher()))

        // Both registered while the socket is still opening, so only the replay sends them.
        client.subscribe("first", mapOf(url to listOf(Filter(kinds = listOf(1)))), null)
        client.subscribe("second", mapOf(url to listOf(Filter(kinds = listOf(2)))), null)
        assertEquals(emptyList(), sockets.kinds())

        // A publish landing in the middle of the replay, as one from another thread can.
        var published = false
        sockets.onSend = { msg ->
            if (!published && msg.contains("\"first\"")) {
                published = true
                client.publish(note, setOf(url))
            }
        }
        sockets.listener.onOpen(0, false)

        val wire = sockets.kinds()
        val firstEvent = wire.indexOfFirst { it.startsWith("EVENT") }
        assertTrue(published, "the replay sent the first REQ: $wire")
        assertTrue(firstEvent > wire.indexOf("REQ:second"), "the publish waited for the replay: $wire")
    }
}
