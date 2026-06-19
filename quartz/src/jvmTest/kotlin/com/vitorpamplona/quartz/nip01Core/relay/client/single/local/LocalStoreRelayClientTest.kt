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
package com.vitorpamplona.quartz.nip01Core.relay.client.single.local

import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.client.single.RelayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end: drive a real [NostrClient] against a [LocalStoreRelayClient]
 * backed by an in-memory SQLite [EventStore]. Proves the local store behaves as
 * just another relay in the pool — `subscribe` returns the stored matches plus
 * `EOSE`, honouring the filter — with no socket and no JSON in the path.
 */
class LocalStoreRelayClientTest {
    private val localUrl = NormalizedRelayUrl("ws://localhost/amethyst-local/")

    private val profile =
        MetadataEvent(
            id = "490d7439e530423f2540d4f2bdb73a0a2935f3df9e1f2a6f699a140c7db311fe",
            pubKey = "70a9b3c312a6b83e476739bd29d60ca700da1d5b982cbca87b5f3d27d4038d67",
            createdAt = 1740669816,
            tags = arrayOf(arrayOf("name", "Vitor")),
            content = "{\"name\":\"Vitor\"}",
            sig = "977a6152199f17d103d8d56736ed1b7767054464cf9423d017c01c8cdd2344698f0a5e13da8dff98d01bb1f798837e3b6271e1fd1cac861bb90686f622ae6ef4",
        )

    private val note =
        TextNoteEvent(
            id = "fecb2ecf61a1433d417a784d10bd1e8ec19a916170a53ca8fb3a15fc666a6592",
            pubKey = "f8ff11c7a7d3478355d3b4d174e5a473797a906ea4aa61aa9b6bc0652c1ea17a",
            createdAt = 1747753115,
            tags = arrayOf(),
            content = "hello",
            sig = "12070e663272f1227c639fb834eb2122fc7bb995f4c49e55ebb1dfe2135ef7347d44810bacd2e64fd26b8826fd47d2800ce6c3d3b579bb3afe39088ffd4faa60",
        )

    @Test
    fun servesStoredMatchesThroughNostrClient() =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val store = EventStore(dbName = null, relay = localUrl)
            store.insert(profile)
            store.insert(note)

            // The store is the only relay; build a LocalStoreRelayClient for every URL.
            val relayBuilder =
                RelayBuilder { url, listener ->
                    LocalStoreRelayClient(url, store, listener, scope)
                }
            val client = NostrClient(NoSocket, scope, relayBuilder)

            try {
                // fetchAll subscribes, collects until EOSE, then unsubscribes — exactly the
                // REQ -> EVENT* -> EOSE -> CLOSE round-trip our relay must support. Ask only
                // for kind 0, so the note (kind 1) must be filtered out by the store query.
                val events = client.fetchAll(localUrl, Filter(kinds = listOf(0)), timeoutMs = 5_000)

                assertEquals(listOf(profile.id), events.map { it.id }, "Only the kind-0 profile should match")
            } finally {
                client.close()
                scope.cancel()
                store.close()
            }
        }

    /** Never invoked: the test's RelayBuilder serves every URL from the store. */
    private object NoSocket : WebsocketBuilder {
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket = throw UnsupportedOperationException("no sockets in this test")
    }
}
