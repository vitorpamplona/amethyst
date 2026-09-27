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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * NIP-01: "When `limit` is zero, the relay MUST NOT return stored events for that
 * filter. After the initial queries for all filters are complete, the relay MUST send
 * `EOSE` and MUST keep the subscription active for newly received matching events."
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NostrServerLimitZeroTest {
    private val pubkey = "46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d"
    private val sig = "4aa5264965018fa12a326686ad3d3bd8beae3218dcc83689b19ca1e6baeb791531943c15363aa6707c7c0c8b2d601deca1f20c32078b2872d356cdca03b04cce"

    private val noop: (String) -> Unit = {}

    private fun hexId(n: Int): String = n.toString().padStart(64, '0')

    private fun testEvent(
        id: String,
        kind: Int = 1,
        createdAt: Long = 1000L,
    ) = Event(id, pubkey, createdAt, kind, emptyArray(), "hello", sig)

    private fun server(
        dispatcher: CoroutineDispatcher,
        store: EventStore,
    ) = NostrServer(store = store, policyBuilder = { EmptyPolicy }, parentContext = dispatcher)

    private class Collector {
        val messages = mutableListOf<String>()
        val send: (String) -> Unit = { messages.add(it) }

        fun parsed(): List<Message> =
            messages
                .filter { it.startsWith("[\"EVENT\"") || it.startsWith("[\"EOSE\"") }
                .map { OptimizedJsonMapper.fromJsonToMessage(it) }

        fun events() = parsed().filterIsInstance<EventMessage>()

        fun eoses() = parsed().filterIsInstance<EoseMessage>()
    }

    private suspend fun EventStore.seed(count: Int) {
        for (i in 1..count) insert(testEvent(hexId(i), createdAt = i.toLong()))
    }

    // -- NIP-01: limit 0 ---------------------------------------------------------

    @Test
    fun limitZeroSendsNoStoredEventsThenEoseAndStaysLive() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            store.seed(5)
            val server = server(dispatcher, store)
            val collector = Collector()
            val c1 = server.connect(collector.send)
            val publisher = server.connect(noop)

            c1.receive("""["REQ","sub1",{"kinds":[1],"limit":0}]""")

            assertEquals(0, collector.events().size, "limit 0 MUST NOT return stored events")
            assertEquals(1, collector.eoses().size, "EOSE is still sent")
            assertEquals("sub1", collector.eoses().single().subId)

            publisher.receive(OptimizedJsonMapper.toJson(EventCmd(testEvent(hexId(100), createdAt = 5000L))))

            val live = collector.events()
            assertEquals(1, live.size, "the subscription stays open for new events")
            assertEquals(hexId(100), live.single().event.id)
            assertTrue(collector.messages.indexOfFirst { it.startsWith("[\"EOSE\"") } < collector.messages.indexOfFirst { it.startsWith("[\"EVENT\"") })

            server.close()
        }

    @Test
    fun limitZeroOnlySilencesItsOwnFilter() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            store.seed(3)
            store.insert(testEvent(hexId(50), kind = 7, createdAt = 50L))
            val server = server(dispatcher, store)
            val collector = Collector()
            val c1 = server.connect(collector.send)

            c1.receive("""["REQ","sub1",{"kinds":[1],"limit":0},{"kinds":[7]}]""")

            assertEquals(listOf(hexId(50)), collector.events().map { it.event.id })
            assertEquals(1, collector.eoses().size)

            server.close()
        }
}
