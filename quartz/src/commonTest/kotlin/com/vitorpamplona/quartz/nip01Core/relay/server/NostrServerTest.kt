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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NostrServerTest {
    private val pubkey = "46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d"
    private val pubkey2 = "32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245"
    private val sig = "4aa5264965018fa12a326686ad3d3bd8beae3218dcc83689b19ca1e6baeb791531943c15363aa6707c7c0c8b2d601deca1f20c32078b2872d356cdca03b04cce"
    private val relayUrl = NormalizedRelayUrl("wss://relay.example.com/")

    private fun hexId(n: Int): String = n.toString().padStart(64, '0')

    private fun testEvent(
        id: String = hexId(1),
        kind: Int = 1,
        createdAt: Long = 1000L,
        content: String = "hello",
        tags: Array<Array<String>> = emptyArray(),
    ) = Event(id, pubkey, createdAt, kind, tags, content, sig)

    /**
     * Creates a server using the given dispatcher so coroutines run eagerly
     * in tests (UnconfinedTestDispatcher).
     */
    private fun createServer(
        store: IEventStore = EventStore(null),
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        requireAuth: Boolean = false,
    ): NostrServer =
        NostrServer(
            store = store,
            relayUrl = relayUrl,
            requireAuth = requireAuth,
            parentContext = dispatcher,
            verify = { true },
        )

    private suspend fun RelaySession.insert(event: Event) {
        val cmd = EventCmd(event)
        this.processMessage(OptimizedJsonMapper.toJson(cmd))
    }

    /** Collects sent JSON messages for a connection. */
    private class MessageCollector {
        val messages = mutableListOf<String>()

        val sendCallback: (String) -> Unit = { messages.add(it) }

        /**
         * Parses messages that can be round-tripped (EVENT, EOSE, NOTICE,
         * CLOSED). OkMessage and CountMessage serialization uses formats
         * incompatible with the client-side deserializer, so check those
         * via [rawMessagesContaining].
         */
        fun parsedEventMessages() =
            messages
                .filter { it.startsWith("[\"EVENT\"") || it.startsWith("[\"EOSE\"") }
                .map { OptimizedJsonMapper.fromJsonToMessage(it) }

        fun rawMessagesContaining(label: String) = messages.filter { it.contains("\"$label\"") }
    }

    /**
     * Builds a kind 22242 auth event for testing. Because verify = { true },
     * the id and signature don't need to be real.
     */
    private fun authEvent(
        challenge: String,
        relay: String = relayUrl.url,
        pubKey: String = pubkey,
        createdAt: Long = TimeUtils.now(),
    ) = Event(
        id = hexId(99),
        pubKey = pubKey,
        createdAt = createdAt,
        kind = RelayAuthEvent.KIND,
        tags =
            arrayOf(
                arrayOf("relay", relay),
                arrayOf("challenge", challenge),
            ),
        content = "",
        sig = sig,
    )

    private fun authJson(event: Event) = """["AUTH",${event.toJson()}]"""

    // -- EVENT command ---------------------------------------------------------

    @Test
    fun eventCommandStoresAndRespondsOk() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            val server = createServer(store, dispatcher)
            val collector = MessageCollector()

            val c1 = server.connect(collector.sendCallback)

            val event = testEvent()
            val eventJson = """["EVENT",${event.toJson()}]"""
            c1.processMessage(eventJson)

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("\"true\""))

            // Event should be in store
            val stored = store.query<Event>(Filter(ids = listOf(event.id)))
            assertEquals(1, stored.size)

            server.shutdown()
        }

    @Test
    fun duplicateEventReturnsOkFalse() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            val server = createServer(store, dispatcher)
            val collector = MessageCollector()

            val c1 = server.connect(collector.sendCallback)

            val event = testEvent()
            val eventJson = """["EVENT",${event.toJson()}]"""
            c1.processMessage(eventJson)
            c1.processMessage(eventJson)

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(2, okMessages.size)
            assertTrue(okMessages[0].contains("\"true\""))
            assertTrue(okMessages[1].contains("\"false\""))

            server.shutdown()
        }

    // -- REQ command -----------------------------------------------------------

    @Test
    fun reqReturnsStoredEventsAndEose() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            val server = createServer(store, dispatcher)

            // Pre-populate store
            store.insert(testEvent(hexId(1), kind = 1, createdAt = 100L))
            store.insert(testEvent(hexId(2), kind = 1, createdAt = 200L))
            store.insert(testEvent(hexId(3), kind = 4, createdAt = 300L))

            val collector = MessageCollector()
            val c1 = server.connect(collector.sendCallback)

            val reqJson = """["REQ","sub1",{"kinds":[1]}]"""
            c1.processMessage(reqJson)

            val parsed = collector.parsedEventMessages()
            val events = parsed.filterIsInstance<EventMessage>()
            val eose = parsed.filterIsInstance<EoseMessage>()

            assertEquals(2, events.size)
            assertEquals(1, eose.size)
            assertEquals("sub1", eose[0].subId)

            // Events should be newest first
            assertTrue(events[0].event.createdAt >= events[1].event.createdAt)

            server.shutdown()
        }

    @Test
    fun reqWithLimitRespectsLimit() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            val server = createServer(store, dispatcher)

            for (i in 1..10) {
                store.insert(testEvent(hexId(i), createdAt = i.toLong()))
            }

            val collector = MessageCollector()
            val c1 = server.connect(collector.sendCallback)

            val reqJson = """["REQ","sub1",{"limit":3}]"""
            c1.processMessage(reqJson)

            val events = collector.parsedEventMessages().filterIsInstance<EventMessage>()
            assertEquals(3, events.size)

            server.shutdown()
        }

    // -- Live subscription -----------------------------------------------------

    @Test
    fun liveSubscriptionReceivesNewEvents() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            val server = createServer(store, dispatcher)
            val collector1 = MessageCollector()
            val collector2 = MessageCollector()

            val c1 = server.connect(collector1.sendCallback)
            val c2 = server.connect(collector2.sendCallback)

            // Subscribe to kind 1
            val reqJson = """["REQ","sub1",{"kinds":[1]}]"""
            c1.processMessage(reqJson)

            // After REQ, we should have EOSE
            val countAfterEose = collector1.messages.size

            // Now store a new event — should be pushed to subscription
            c2.insert(testEvent(hexId(1), kind = 1))

            val newMessages = collector1.messages.drop(countAfterEose)
            assertTrue(newMessages.isNotEmpty())
            assertTrue(newMessages[0].contains("\"EVENT\""))

            server.shutdown()
        }

    @Test
    fun liveSubscriptionFiltersNonMatchingEvents() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            val server = createServer(store, dispatcher)
            val collector1 = MessageCollector()
            val collector2 = MessageCollector()

            val c1 = server.connect(collector1.sendCallback)
            val c2 = server.connect(collector2.sendCallback)

            val reqJson = """["REQ","sub1",{"kinds":[1]}]"""
            c1.processMessage(reqJson)

            val countAfterEose = collector1.messages.size

            // Store a kind 4 event — should NOT match kind 1 subscription
            c2.insert(testEvent(hexId(1), kind = 4))

            assertEquals(countAfterEose, collector1.messages.size)

            server.shutdown()
        }

    // -- CLOSE command ---------------------------------------------------------

    @Test
    fun closeStopsSubscription() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            val server = createServer(store, dispatcher)
            val collector1 = MessageCollector()
            val collector2 = MessageCollector()

            val c1 = server.connect(collector1.sendCallback)
            val c2 = server.connect(collector2.sendCallback)

            val reqJson = """["REQ","sub1",{"kinds":[1]}]"""
            c1.processMessage(reqJson)

            // Close the subscription
            val closeJson = """["CLOSE","sub1"]"""
            c1.processMessage(closeJson)

            val countAfterClose = collector1.messages.size

            // New events should NOT reach this subscription
            c2.insert(testEvent(hexId(1), kind = 1))

            assertEquals(countAfterClose, collector1.messages.size)

            server.shutdown()
        }

    @Test
    fun replacingSubscriptionCancelsOld() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(EventStore(null), dispatcher)
            val collector1 = MessageCollector()
            val collector2 = MessageCollector()

            val c1 = server.connect(collector1.sendCallback)
            val c2 = server.connect(collector2.sendCallback)

            // First subscription for kind 1
            c1.processMessage("""["REQ","sub1",{"kinds":[1]}]""")

            // Replace with kind 4
            c1.processMessage("""["REQ","sub1",{"kinds":[4]}]""")

            val countAfterReplace = collector1.messages.size

            // Kind 1 events should not match anymore
            c2.insert(testEvent(hexId(1), kind = 1))

            assertEquals(countAfterReplace, collector1.messages.size)

            // Kind 4 events should match
            c2.insert(testEvent(hexId(2), kind = 4))

            val newMessages = collector1.messages.drop(countAfterReplace)

            assertTrue(newMessages.isNotEmpty())
            assertTrue(newMessages[0].contains("\"EVENT\""))
            assertTrue(newMessages[0].contains(hexId(2)))

            server.shutdown()
        }

    // -- COUNT command (NIP-45) ------------------------------------------------

    @Test
    fun countReturnsMatchingEventCount() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            val server = createServer(store, dispatcher)

            store.insert(testEvent(hexId(1), kind = 1))
            store.insert(testEvent(hexId(2), kind = 1))
            store.insert(testEvent(hexId(3), kind = 4))

            val collector = MessageCollector()
            val c1 = server.connect(collector.sendCallback)

            val countJson = """["COUNT","q1",{"kinds":[1]}]"""
            c1.processMessage(countJson)

            val countMessages = collector.rawMessagesContaining("COUNT")
            assertEquals(1, countMessages.size)
            assertTrue(countMessages[0].contains("\"count\":2"))

            server.shutdown()
        }

    // -- Disconnect ------------------------------------------------------------

    @Test
    fun disconnectCancelsAllSubscriptions() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            val server = createServer(store, dispatcher)
            val collector1 = MessageCollector()
            val collector2 = MessageCollector()

            val c1 = server.connect(collector1.sendCallback)
            val c2 = server.connect(collector2.sendCallback)

            c1.processMessage("""["REQ","sub1",{"kinds":[1]}]""")
            c1.processMessage("""["REQ","sub2",{"kinds":[4]}]""")

            c1.close()

            val countAfterDisconnect = collector1.messages.size

            c2.insert(testEvent(hexId(1), kind = 1))
            c2.insert(testEvent(hexId(2), kind = 4))

            assertEquals(countAfterDisconnect, collector1.messages.size)
            assertEquals(2, collector2.messages.size)

            server.shutdown()
        }

    // -- Invalid messages ------------------------------------------------------

    @Test
    fun invalidJsonReturnsNotice() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val c1 = server.connect(collector.sendCallback)
            c1.processMessage("not valid json")

            assertEquals(1, collector.messages.size)
            assertTrue(collector.messages[0].contains("NOTICE"))

            server.shutdown()
        }

    // -- NIP-42: AUTH ----------------------------------------------------------

    @Test
    fun authChallengeIsSentOnRequest() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)
            session.sendAuthChallenge()

            assertEquals(1, collector.messages.size)
            assertTrue(collector.messages[0].contains("\"AUTH\""))
            assertTrue(collector.messages[0].contains(session.challenge))

            server.shutdown()
        }

    @Test
    fun authSucceedsWithValidEvent() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            val event = authEvent(challenge = session.challenge)
            session.processMessage(authJson(event))

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("\"true\""))
            assertTrue(session.isAuthenticated())
            assertTrue(session.authenticatedPubkeys().contains(pubkey))

            server.shutdown()
        }

    @Test
    fun authFailsWithWrongChallenge() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            val event = authEvent(challenge = "wrong-challenge")
            session.processMessage(authJson(event))

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("\"false\""))
            assertTrue(okMessages[0].contains("challenge"))
            assertFalse(session.isAuthenticated())

            server.shutdown()
        }

    @Test
    fun authFailsWithWrongRelay() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            val event = authEvent(challenge = session.challenge, relay = "wss://wrong.relay.com/")
            session.processMessage(authJson(event))

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("\"false\""))
            assertTrue(okMessages[0].contains("relay url"))
            assertFalse(session.isAuthenticated())

            server.shutdown()
        }

    @Test
    fun authFailsWithExpiredTimestamp() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            val event =
                authEvent(
                    challenge = session.challenge,
                    createdAt = TimeUtils.now() - 1200L, // 20 minutes ago
                )
            session.processMessage(authJson(event))

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("\"false\""))
            assertTrue(okMessages[0].contains("created_at"))
            assertFalse(session.isAuthenticated())

            server.shutdown()
        }

    @Test
    fun authFailsWithWrongKind() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            // Create an event with wrong kind (1 instead of 22242)
            val event =
                Event(
                    id = hexId(99),
                    pubKey = pubkey,
                    createdAt = TimeUtils.now(),
                    kind = 1,
                    tags =
                        arrayOf(
                            arrayOf("relay", relayUrl.url),
                            arrayOf("challenge", session.challenge),
                        ),
                    content = "",
                    sig = sig,
                )
            session.processMessage(authJson(event))

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("\"false\""))
            assertTrue(okMessages[0].contains("wrong event kind"))
            assertFalse(session.isAuthenticated())

            server.shutdown()
        }

    @Test
    fun multipleUsersCanAuthenticate() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            // First user authenticates
            val event1 = authEvent(challenge = session.challenge, pubKey = pubkey)
            session.processMessage(authJson(event1))

            // Second user authenticates on the same session
            val event2 = authEvent(challenge = session.challenge, pubKey = pubkey2)
            session.processMessage(authJson(event2))

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(2, okMessages.size)
            assertTrue(okMessages[0].contains("\"true\""))
            assertTrue(okMessages[1].contains("\"true\""))

            val authedPubkeys = session.authenticatedPubkeys()
            assertEquals(2, authedPubkeys.size)
            assertTrue(authedPubkeys.contains(pubkey))
            assertTrue(authedPubkeys.contains(pubkey2))

            server.shutdown()
        }

    // -- NIP-42: requireAuth ---------------------------------------------------

    @Test
    fun requireAuthRejectsEventWithoutAuth() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher, requireAuth = true)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            val event = testEvent()
            session.processMessage("""["EVENT",${event.toJson()}]""")

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("\"false\""))
            assertTrue(okMessages[0].contains("auth-required:"))

            server.shutdown()
        }

    @Test
    fun requireAuthRejectsReqWithoutAuth() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher, requireAuth = true)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)
            session.processMessage("""["REQ","sub1",{"kinds":[1]}]""")

            val closedMessages = collector.rawMessagesContaining("CLOSED")
            assertEquals(1, closedMessages.size)
            assertTrue(closedMessages[0].contains("auth-required:"))

            server.shutdown()
        }

    @Test
    fun requireAuthRejectsCountWithoutAuth() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher, requireAuth = true)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)
            session.processMessage("""["COUNT","q1",{"kinds":[1]}]""")

            val closedMessages = collector.rawMessagesContaining("CLOSED")
            assertEquals(1, closedMessages.size)
            assertTrue(closedMessages[0].contains("auth-required:"))

            server.shutdown()
        }

    @Test
    fun requireAuthAllowsCommandsAfterAuth() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            val server = createServer(store = store, dispatcher = dispatcher, requireAuth = true)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            // Authenticate first
            val authEv = authEvent(challenge = session.challenge)
            session.processMessage(authJson(authEv))

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("\"true\""))

            // Now EVENT should work
            val event = testEvent()
            session.processMessage("""["EVENT",${event.toJson()}]""")

            val allOk = collector.rawMessagesContaining("OK")
            assertEquals(2, allOk.size)
            assertTrue(allOk[1].contains("\"true\""))

            // REQ should work
            session.processMessage("""["REQ","sub1",{"kinds":[1]}]""")

            val eoseMessages = collector.rawMessagesContaining("EOSE")
            assertTrue(eoseMessages.isNotEmpty())

            server.shutdown()
        }

    @Test
    fun noAuthRequiredAllowsCommandsWithoutAuth() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher, requireAuth = false)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            // EVENT should work without auth when requireAuth is false
            val event = testEvent()
            session.processMessage("""["EVENT",${event.toJson()}]""")

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("\"true\""))

            server.shutdown()
        }
}
