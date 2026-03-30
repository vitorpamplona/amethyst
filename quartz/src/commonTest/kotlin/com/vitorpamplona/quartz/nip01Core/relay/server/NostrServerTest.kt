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
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NostrServerTest {
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
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        store: IEventStore = EventStore(null),
        policyBuilder: () -> IRelayPolicy = { EmptyPolicy },
    ): NostrServer =
        NostrServer(
            store = store,
            policyBuilder = policyBuilder,
            parentContext = dispatcher,
        )

    private suspend fun RelaySession.insert(event: Event) {
        val cmd = EventCmd(event)
        this.receive(OptimizedJsonMapper.toJson(cmd))
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

    // -- EVENT command ---------------------------------------------------------

    @Test
    fun eventCommandStoresAndRespondsOk() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            val server = createServer(dispatcher, store)
            val collector = MessageCollector()

            val c1 = server.connect(collector.sendCallback)

            val event = testEvent()
            val eventJson = """["EVENT",${event.toJson()}]"""
            c1.receive(eventJson)

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("\"true\""))

            // Event should be in store
            val stored = store.query<Event>(Filter(ids = listOf(event.id)))
            assertEquals(1, stored.size)

            server.close()
        }

    @Test
    fun duplicateEventReturnsOkFalse() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            val server = createServer(dispatcher, store)
            val collector = MessageCollector()

            val c1 = server.connect(collector.sendCallback)

            val event = testEvent()
            val eventJson = """["EVENT",${event.toJson()}]"""
            c1.receive(eventJson)
            c1.receive(eventJson)

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(2, okMessages.size)
            assertTrue(okMessages[0].contains("\"true\""))
            assertTrue(okMessages[1].contains("\"false\""))

            server.close()
        }

    // -- REQ command -----------------------------------------------------------

    @Test
    fun reqReturnsStoredEventsAndEose() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            val server = createServer(dispatcher, store)

            // Pre-populate store
            store.insert(testEvent(hexId(1), kind = 1, createdAt = 100L))
            store.insert(testEvent(hexId(2), kind = 1, createdAt = 200L))
            store.insert(testEvent(hexId(3), kind = 4, createdAt = 300L))

            val collector = MessageCollector()
            val c1 = server.connect(collector.sendCallback)

            val reqJson = """["REQ","sub1",{"kinds":[1]}]"""
            c1.receive(reqJson)

            val parsed = collector.parsedEventMessages()
            val events = parsed.filterIsInstance<EventMessage>()
            val eose = parsed.filterIsInstance<EoseMessage>()

            assertEquals(2, events.size)
            assertEquals(1, eose.size)
            assertEquals("sub1", eose[0].subId)

            // Events should be newest first
            assertTrue(events[0].event.createdAt >= events[1].event.createdAt)

            server.close()
        }

    @Test
    fun reqWithLimitRespectsLimit() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            val server = createServer(dispatcher, store)

            for (i in 1..10) {
                store.insert(testEvent(hexId(i), createdAt = i.toLong()))
            }

            val collector = MessageCollector()
            val c1 = server.connect(collector.sendCallback)

            val reqJson = """["REQ","sub1",{"limit":3}]"""
            c1.receive(reqJson)

            val events = collector.parsedEventMessages().filterIsInstance<EventMessage>()
            assertEquals(3, events.size)

            server.close()
        }

    // -- Live subscription -----------------------------------------------------

    @Test
    fun liveSubscriptionReceivesNewEvents() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)

            val server = createServer(dispatcher)
            val collector1 = MessageCollector()
            val collector2 = MessageCollector()

            val c1 = server.connect(collector1.sendCallback)
            val c2 = server.connect(collector2.sendCallback)

            // Subscribe to kind 1
            val reqJson = """["REQ","sub1",{"kinds":[1]}]"""
            c1.receive(reqJson)

            // After REQ, we should have EOSE
            val countAfterEose = collector1.messages.size

            // Now store a new event — should be pushed to subscription
            c2.insert(testEvent(hexId(1), kind = 1))

            val newMessages = collector1.messages.drop(countAfterEose)
            assertTrue(newMessages.isNotEmpty())
            assertTrue(newMessages[0].contains("\"EVENT\""))

            server.close()
        }

    @Test
    fun liveSubscriptionFiltersNonMatchingEvents() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)

            val server = createServer(dispatcher)
            val collector1 = MessageCollector()
            val collector2 = MessageCollector()

            val c1 = server.connect(collector1.sendCallback)
            val c2 = server.connect(collector2.sendCallback)

            val reqJson = """["REQ","sub1",{"kinds":[1]}]"""
            c1.receive(reqJson)

            val countAfterEose = collector1.messages.size

            // Store a kind 4 event — should NOT match kind 1 subscription
            c2.insert(testEvent(hexId(1), kind = 4))

            assertEquals(countAfterEose, collector1.messages.size)

            server.close()
        }

    // -- CLOSE command ---------------------------------------------------------

    @Test
    fun closeStopsSubscription() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)

            val server = createServer(dispatcher)
            val collector1 = MessageCollector()
            val collector2 = MessageCollector()

            val c1 = server.connect(collector1.sendCallback)
            val c2 = server.connect(collector2.sendCallback)

            val reqJson = """["REQ","sub1",{"kinds":[1]}]"""
            c1.receive(reqJson)

            // Close the subscription
            val closeJson = """["CLOSE","sub1"]"""
            c1.receive(closeJson)

            val countAfterClose = collector1.messages.size

            // New events should NOT reach this subscription
            c2.insert(testEvent(hexId(1), kind = 1))

            assertEquals(countAfterClose, collector1.messages.size)

            server.close()
        }

    @Test
    fun replacingSubscriptionCancelsOld() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher)
            val collector1 = MessageCollector()
            val collector2 = MessageCollector()

            val c1 = server.connect(collector1.sendCallback)
            val c2 = server.connect(collector2.sendCallback)

            // First subscription for kind 1
            c1.receive("""["REQ","sub1",{"kinds":[1]}]""")

            // Replace with kind 4
            c1.receive("""["REQ","sub1",{"kinds":[4]}]""")

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

            server.close()
        }

    // -- COUNT command (NIP-45) ------------------------------------------------

    @Test
    fun countReturnsMatchingEventCount() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            val server = createServer(dispatcher, store)

            store.insert(testEvent(hexId(1), kind = 1))
            store.insert(testEvent(hexId(2), kind = 1))
            store.insert(testEvent(hexId(3), kind = 4))

            val collector = MessageCollector()
            val c1 = server.connect(collector.sendCallback)

            val countJson = """["COUNT","q1",{"kinds":[1]}]"""
            c1.receive(countJson)

            val countMessages = collector.rawMessagesContaining("COUNT")
            assertEquals(1, countMessages.size)
            assertTrue(countMessages[0].contains("\"count\":2"))

            server.close()
        }

    // -- Disconnect ------------------------------------------------------------

    @Test
    fun disconnectCancelsAllSubscriptions() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)

            val server = createServer(dispatcher)
            val collector1 = MessageCollector()
            val collector2 = MessageCollector()

            val c1 = server.connect(collector1.sendCallback)
            val c2 = server.connect(collector2.sendCallback)

            c1.receive("""["REQ","sub1",{"kinds":[1]}]""")
            c1.receive("""["REQ","sub2",{"kinds":[4]}]""")

            c1.close()

            val countAfterDisconnect = collector1.messages.size

            c2.insert(testEvent(hexId(1), kind = 1))
            c2.insert(testEvent(hexId(2), kind = 4))

            assertEquals(countAfterDisconnect, collector1.messages.size)
            assertEquals(2, collector2.messages.size)

            server.close()
        }

    // -- Invalid messages ------------------------------------------------------

    @Test
    fun invalidJsonReturnsNotice() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val c1 = server.connect(collector.sendCallback)
            c1.receive("not valid json")

            assertEquals(1, collector.messages.size)
            assertTrue(collector.messages[0].contains("NOTICE"))

            server.close()
        }
}
