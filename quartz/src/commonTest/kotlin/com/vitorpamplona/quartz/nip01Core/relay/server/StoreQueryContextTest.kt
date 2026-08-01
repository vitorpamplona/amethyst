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
package com.vitorpamplona.quartz.nip01Core.relay.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.FullAuthPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.RawEvent
import com.vitorpamplona.quartz.nip01Core.store.StoreQueryContext
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The caller-identity seam: `LiveEventStore` must install a
 * [StoreQueryContext] with the connection's NIP-42-authenticated pubkeys
 * around every REQ-driven store call — and must NOT install one for
 * unauthenticated connections — so observer-relative stores can read who
 * is asking straight off the coroutine context.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoreQueryContextTest {
    private val pubkey = "46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d"
    private val sig = "4aa5264965018fa12a326686ad3d3bd8beae3218dcc83689b19ca1e6baeb791531943c15363aa6707c7c0c8b2d601deca1f20c32078b2872d356cdca03b04cce"
    private val relayUrl = NormalizedRelayUrl("wss://relay.example.com/")

    private fun hexId(n: Int): String = n.toString().padStart(64, '0')

    /**
     * Delegates everything to a real SQLite store but records the
     * [StoreQueryContext] visible during each query — both the decoding
     * and the zero-decode replay path, since the session may use either.
     */
    private class ContextRecordingStore(
        private val inner: IEventStore,
    ) : IEventStore by inner {
        val contexts = mutableListOf<StoreQueryContext?>()

        override suspend fun <T : Event> query(
            filters: List<Filter>,
            onEach: (T) -> Unit,
        ) {
            contexts.add(coroutineContext[StoreQueryContext])
            inner.query(filters, onEach)
        }

        override suspend fun rawQuery(
            filters: List<Filter>,
            onEach: (RawEvent) -> Unit,
        ) {
            contexts.add(coroutineContext[StoreQueryContext])
            inner.rawQuery(filters, onEach)
        }
    }

    private fun createServer(
        dispatcher: CoroutineDispatcher,
        store: IEventStore,
        policyBuilder: () -> IRelayPolicy,
    ): NostrServer =
        NostrServer(
            store = store,
            policyBuilder = policyBuilder,
            parentContext = dispatcher,
        )

    @Test
    fun unauthenticatedReqSeesNoStoreQueryContext() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = ContextRecordingStore(EventStore(null))
            val server = createServer(dispatcher, store) { EmptyPolicy }
            val session = server.connect { }

            session.receive("""["REQ","sub1",{"kinds":[1]}]""")

            assertEquals(1, store.contexts.size, "the REQ must have reached the store")
            assertNull(store.contexts[0], "no NIP-42 auth → no StoreQueryContext element")

            server.close()
        }

    @Test
    fun authenticatedReqCarriesTheObserverToTheStore() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = ContextRecordingStore(EventStore(null))
            val server = createServer(dispatcher, store) { FullAuthPolicy(relayUrl) }
            val messages = mutableListOf<String>()
            val session = server.connect { messages.add(it) }

            // NIP-42 handshake, mirroring NostrServerAuthTest.
            val challenge = (OptimizedJsonMapper.fromJsonToMessage(messages[0]) as AuthMessage).challenge
            val auth =
                RelayAuthEvent(
                    id = hexId(99),
                    pubKey = pubkey,
                    createdAt = TimeUtils.now(),
                    tags =
                        arrayOf(
                            arrayOf("relay", relayUrl.url),
                            arrayOf("challenge", challenge),
                        ),
                    content = "",
                    sig = sig,
                )
            session.receive("""["AUTH",${auth.toJson()}]""")
            assertTrue(session.requestContext.authenticatedUsers.contains(pubkey))

            session.receive("""["REQ","sub1",{"kinds":[1]}]""")

            assertEquals(1, store.contexts.size, "the REQ must have reached the store")
            val seen = store.contexts[0]
            assertEquals(setOf(pubkey), seen?.authenticatedUsers)
            assertEquals(pubkey, seen?.observer)

            server.close()
        }
}
