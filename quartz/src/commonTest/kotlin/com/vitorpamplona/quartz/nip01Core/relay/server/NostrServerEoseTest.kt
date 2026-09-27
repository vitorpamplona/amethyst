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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The relay engine's opt-in NIP-67 EOSE completeness hints. */
@OptIn(ExperimentalCoroutinesApi::class)
class NostrServerEoseTest {
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
        hints: Boolean = false,
    ) = NostrServer(store = store, policyBuilder = { EmptyPolicy }, parentContext = dispatcher).also {
        it.completenessHints = hints
    }

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

    // -- NIP-67: completeness hints ------------------------------------------------

    @Test
    fun hintsAreOffByDefault() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            store.seed(3)
            val server = server(dispatcher, store)
            val collector = Collector()
            server.connect(collector.send).receive("""["REQ","s",{"kinds":[1]}]""")

            assertTrue(collector.messages.contains("""["EOSE","s"]"""))
            assertNull(collector.eoses().single().hints)
            server.close()
        }

    @Test
    fun truncatedByLimitSendsMore() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            store.seed(10)
            val server = server(dispatcher, store, hints = true)
            val collector = Collector()
            server.connect(collector.send).receive("""["REQ","s",{"kinds":[1],"limit":3}]""")

            // The probe asks the store for 4 but only the newest 3 go out.
            assertEquals(listOf(hexId(10), hexId(9), hexId(8)), collector.events().map { it.event.id })
            assertEquals(listOf(EoseMessage.HINT_MORE), collector.eoses().single().hints)
            server.close()
        }

    @Test
    fun exactlyTheLimitSendsFinish() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            store.seed(10)
            val server = server(dispatcher, store, hints = true)
            val collector = Collector()
            server.connect(collector.send).receive("""["REQ","s",{"kinds":[1],"limit":10}]""")

            assertEquals(10, collector.events().size)
            assertEquals(listOf(EoseMessage.HINT_FINISH), collector.eoses().single().hints)
            server.close()
        }

    @Test
    fun unboundedFilterSendsFinish() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            store.seed(4)
            val server = server(dispatcher, store, hints = true)
            val collector = Collector()
            server.connect(collector.send).receive("""["REQ","s",{"kinds":[1]}]""")

            assertEquals(4, collector.events().size)
            assertEquals(listOf(EoseMessage.HINT_FINISH), collector.eoses().single().hints)
            server.close()
        }

    @Test
    fun multiFilterHintsOnlyWhatTheCountProves() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            store.seed(4)
            val server = server(dispatcher, store, hints = true)

            val under = Collector()
            server.connect(under.send).receive("""["REQ","s",{"kinds":[1],"limit":10},{"kinds":[2],"limit":20}]""")
            assertEquals(4, under.events().size)
            assertEquals(listOf(EoseMessage.HINT_FINISH), under.eoses().single().hints, "4 rows < smallest limit: nothing was cut")

            val ambiguous = Collector()
            server.connect(ambiguous.send).receive("""["REQ","s",{"kinds":[1],"limit":2},{"kinds":[2],"limit":20}]""")
            assertEquals(2, ambiguous.events().size)
            assertNull(ambiguous.eoses().single().hints, "cannot attribute rows to filters cheaply, so no claim")

            server.close()
        }

    @Test
    fun limitZeroNeverGetsAHint() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            store.seed(4)
            val server = server(dispatcher, store, hints = true)
            val collector = Collector()
            server.connect(collector.send).receive("""["REQ","s",{"kinds":[1],"limit":0}]""")

            assertEquals(0, collector.events().size)
            assertNull(collector.eoses().single().hints)
            server.close()
        }

    @Test
    fun probeDoesNotHoldBackLiveEvents() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            store.seed(5)
            val server = server(dispatcher, store, hints = true)
            val collector = Collector()
            server.connect(collector.send).receive("""["REQ","s",{"kinds":[1],"limit":1}]""")
            assertEquals(1, collector.events().size)

            server.connect(noop).receive(OptimizedJsonMapper.toJson(EventCmd(testEvent(hexId(100), createdAt = 9000L))))
            server.connect(noop).receive(OptimizedJsonMapper.toJson(EventCmd(testEvent(hexId(101), createdAt = 9001L))))

            assertEquals(listOf(hexId(5), hexId(100), hexId(101)), collector.events().map { it.event.id })
            server.close()
        }
}
