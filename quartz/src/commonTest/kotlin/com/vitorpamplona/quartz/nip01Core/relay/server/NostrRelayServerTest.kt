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
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NostrRelayServerTest {
    private val pubkey = "46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d"
    private val sig = "4aa5264965018fa12a326686ad3d3bd8beae3218dcc83689b19ca1e6baeb791531943c15363aa6707c7c0c8b2d601deca1f20c32078b2872d356cdca03b04cce"

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
        store: EventStore = InMemoryEventStore(),
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): NostrRelayServer =
        NostrRelayServer(
            store = store,
            parentContext = dispatcher,
            eventVerifier = { true },
        )

    /** Collects sent JSON messages for a connection. */
    private class MessageCollector {
        val messages = mutableListOf<String>()

        val sendCallback: suspend (String) -> Unit = { messages.add(it) }

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

    // -- EVENT command ---------------------------------------------------------

    @Test
    fun eventCommandStoresAndRespondsOk() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = InMemoryEventStore()
            val server = createServer(store, dispatcher)
            val collector = MessageCollector()

            server.connect("c1", collector.sendCallback)

            val event = testEvent()
            val eventJson = """["EVENT",${event.toJson()}]"""
            server.processMessage("c1", eventJson)

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("\"true\""))

            // Event should be in store
            val stored = store.query(Filter(ids = listOf(event.id)))
            assertEquals(1, stored.size)

            server.shutdown()
        }

    @Test
    fun duplicateEventReturnsOkFalse() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = InMemoryEventStore()
            val server = createServer(store, dispatcher)
            val collector = MessageCollector()

            server.connect("c1", collector.sendCallback)

            val event = testEvent()
            val eventJson = """["EVENT",${event.toJson()}]"""
            server.processMessage("c1", eventJson)
            server.processMessage("c1", eventJson)

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
            val store = InMemoryEventStore()
            val server = createServer(store, dispatcher)

            // Pre-populate store
            store.store(testEvent(hexId(1), kind = 1, createdAt = 100L))
            store.store(testEvent(hexId(2), kind = 1, createdAt = 200L))
            store.store(testEvent(hexId(3), kind = 4, createdAt = 300L))

            val collector = MessageCollector()
            server.connect("c1", collector.sendCallback)

            val reqJson = """["REQ","sub1",{"kinds":[1]}]"""
            server.processMessage("c1", reqJson)

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
            val store = InMemoryEventStore()
            val server = createServer(store, dispatcher)

            for (i in 1..10) {
                store.store(testEvent(hexId(i), createdAt = i.toLong()))
            }

            val collector = MessageCollector()
            server.connect("c1", collector.sendCallback)

            val reqJson = """["REQ","sub1",{"limit":3}]"""
            server.processMessage("c1", reqJson)

            val events = collector.parsedEventMessages().filterIsInstance<EventMessage>()
            assertEquals(3, events.size)

            server.shutdown()
        }

    // -- Live subscription -----------------------------------------------------

    @Test
    fun liveSubscriptionReceivesNewEvents() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = InMemoryEventStore()
            val server = createServer(store, dispatcher)
            val collector = MessageCollector()

            server.connect("c1", collector.sendCallback)

            // Subscribe to kind 1
            val reqJson = """["REQ","sub1",{"kinds":[1]}]"""
            server.processMessage("c1", reqJson)

            // After REQ, we should have EOSE
            val countAfterEose = collector.messages.size

            // Now store a new event — should be pushed to subscription
            store.store(testEvent(hexId(1), kind = 1))

            val newMessages = collector.messages.drop(countAfterEose)
            assertTrue(newMessages.isNotEmpty())
            assertTrue(newMessages[0].contains("\"EVENT\""))

            server.shutdown()
        }

    @Test
    fun liveSubscriptionFiltersNonMatchingEvents() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = InMemoryEventStore()
            val server = createServer(store, dispatcher)
            val collector = MessageCollector()

            server.connect("c1", collector.sendCallback)

            val reqJson = """["REQ","sub1",{"kinds":[1]}]"""
            server.processMessage("c1", reqJson)

            val countAfterEose = collector.messages.size

            // Store a kind 4 event — should NOT match kind 1 subscription
            store.store(testEvent(hexId(1), kind = 4))

            assertEquals(countAfterEose, collector.messages.size)

            server.shutdown()
        }

    // -- CLOSE command ---------------------------------------------------------

    @Test
    fun closeStopsSubscription() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = InMemoryEventStore()
            val server = createServer(store, dispatcher)
            val collector = MessageCollector()

            server.connect("c1", collector.sendCallback)

            val reqJson = """["REQ","sub1",{"kinds":[1]}]"""
            server.processMessage("c1", reqJson)

            // Close the subscription
            val closeJson = """["CLOSE","sub1"]"""
            server.processMessage("c1", closeJson)

            val countAfterClose = collector.messages.size

            // New events should NOT reach this subscription
            store.store(testEvent(hexId(1), kind = 1))

            assertEquals(countAfterClose, collector.messages.size)

            server.shutdown()
        }

    @Test
    fun replacingSubscriptionCancelsOld() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = InMemoryEventStore()
            val server = createServer(store, dispatcher)
            val collector = MessageCollector()

            server.connect("c1", collector.sendCallback)

            // First subscription for kind 1
            server.processMessage("c1", """["REQ","sub1",{"kinds":[1]}]""")

            // Replace with kind 4
            server.processMessage("c1", """["REQ","sub1",{"kinds":[4]}]""")

            val countAfterReplace = collector.messages.size

            // Kind 1 events should not match anymore
            store.store(testEvent(hexId(1), kind = 1))
            assertEquals(countAfterReplace, collector.messages.size)

            // Kind 4 events should match
            store.store(testEvent(hexId(2), kind = 4))

            val newMessages = collector.messages.drop(countAfterReplace)
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
            val store = InMemoryEventStore()
            val server = createServer(store, dispatcher)

            store.store(testEvent(hexId(1), kind = 1))
            store.store(testEvent(hexId(2), kind = 1))
            store.store(testEvent(hexId(3), kind = 4))

            val collector = MessageCollector()
            server.connect("c1", collector.sendCallback)

            val countJson = """["COUNT","q1",{"kinds":[1]}]"""
            server.processMessage("c1", countJson)

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
            val store = InMemoryEventStore()
            val server = createServer(store, dispatcher)
            val collector = MessageCollector()

            server.connect("c1", collector.sendCallback)

            server.processMessage("c1", """["REQ","sub1",{"kinds":[1]}]""")
            server.processMessage("c1", """["REQ","sub2",{"kinds":[4]}]""")

            server.disconnect("c1")

            val countAfterDisconnect = collector.messages.size

            store.store(testEvent(hexId(1), kind = 1))
            store.store(testEvent(hexId(2), kind = 4))

            assertEquals(countAfterDisconnect, collector.messages.size)

            server.shutdown()
        }

    // -- Invalid messages ------------------------------------------------------

    @Test
    fun invalidJsonReturnsNotice() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            server.connect("c1", collector.sendCallback)
            server.processMessage("c1", "not valid json")

            assertEquals(1, collector.messages.size)
            assertTrue(collector.messages[0].contains("NOTICE"))

            server.shutdown()
        }
}
