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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.FullAuthPolicy
import com.vitorpamplona.quartz.nip45Count.HllBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EventSourceServerTest {
    private val pubkey = "46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d"
    private val sig = "4aa5264965018fa12a326686ad3d3bd8beae3218dcc83689b19ca1e6baeb791531943c15363aa6707c7c0c8b2d601deca1f20c32078b2872d356cdca03b04cce"

    private fun hexId(n: Int): String = n.toString().padStart(64, '0')

    private fun event(
        id: Int,
        content: String = "hit",
    ) = Event(hexId(id), pubkey, 1000L + id, 1, emptyArray(), content, sig)

    private class MessageCollector {
        val messages = mutableListOf<String>()
        val send: (String) -> Unit = { messages.add(it) }

        fun parsed() =
            messages
                .filter { it.startsWith("[\"EVENT\"") || it.startsWith("[\"EOSE\"") }
                .map { OptimizedJsonMapper.fromJsonToMessage(it) }

        fun containing(label: String) = messages.filter { it.contains("\"$label\"") }
    }

    /** A source that returns a fixed list of events for any REQ. */
    private class FixedSource(
        private val events: List<Event>,
    ) : EventSource {
        override fun events(filters: List<Filter>): Flow<Event> = flowOf(*events.toTypedArray())
    }

    @Test
    fun reqStreamsEventsThenEose() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val source = FixedSource(listOf(event(1), event(2)))
            EventSourceServer(source, parentContext = dispatcher).use { server ->
                val collector = MessageCollector()
                val session = server.connect(collector.send)

                session.receive("""["REQ","sub1",{"kinds":[1]}]""")

                val parsed = collector.parsed()
                val events = parsed.filterIsInstance<EventMessage>()
                assertEquals(2, events.size)
                assertEquals(hexId(1), events[0].event.id)
                assertEquals(hexId(2), events[1].event.id)
                // EOSE comes last, after both events.
                assertTrue(parsed.last() is EoseMessage)
            }
        }

    @Test
    fun countUsesSource() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val source = FixedSource(listOf(event(1), event(2), event(3)))
            EventSourceServer(source, parentContext = dispatcher).use { server ->
                val collector = MessageCollector()
                val session = server.connect(collector.send)

                session.receive("""["COUNT","q1",{"kinds":[1]}]""")

                val counts = collector.containing("COUNT")
                assertEquals(1, counts.size)
                assertTrue(counts[0].contains("\"count\":3"))
            }
        }

    @Test
    fun approximateCountWithHllReachesTheWire() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val source =
                object : EventSource {
                    override fun events(filters: List<Filter>): Flow<Event> = flowOf(event(1), event(2))

                    override suspend fun countResult(filters: List<Filter>): CountResult {
                        val hll = HllBuilder(offset = 8)
                        events(filters).collect { hll.add(it.pubKey) }
                        return hll.toCountResult()
                    }
                }
            EventSourceServer(source, parentContext = dispatcher).use { server ->
                val collector = MessageCollector()
                val session = server.connect(collector.send)

                session.receive("""["COUNT","q1",{"kinds":[1]}]""")

                val counts = collector.containing("COUNT")
                assertEquals(1, counts.size)
                assertTrue(counts[0].contains("\"hll\""))
                assertTrue(counts[0].contains("\"approximate\":true"))
            }
        }

    @Test
    fun eventPublishIsRejected() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            EventSourceServer(FixedSource(emptyList()), parentContext = dispatcher).use { server ->
                val collector = MessageCollector()
                val session = server.connect(collector.send)

                session.receive("""["EVENT",${event(1).toJson()}]""")

                val ok = collector.containing("OK")
                assertEquals(1, ok.size)
                assertTrue(ok[0].contains(",false,"))
                assertTrue(ok[0].contains("does not accept events"))
            }
        }

    @Test
    fun sourceErrorBecomesClosed() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val source =
                object : EventSource {
                    override fun events(filters: List<Filter>): Flow<Event> = flow { throw RuntimeException("backend down") }
                }
            EventSourceServer(source, parentContext = dispatcher).use { server ->
                val collector = MessageCollector()
                val session = server.connect(collector.send)

                session.receive("""["REQ","sub1",{"kinds":[1]}]""")

                val closed = collector.containing("CLOSED")
                assertEquals(1, closed.size)
                assertTrue(closed[0].contains("error:"))
                assertTrue(closed[0].contains("backend down"))
            }
        }

    @Test
    fun policyGatesTheSource() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val relay = NormalizedRelayUrl("wss://search.example.com/")
            val source = FixedSource(listOf(event(1)))
            EventSourceServer(
                source,
                policyBuilder = { FullAuthPolicy(relay) },
                parentContext = dispatcher,
            ).use { server ->
                val collector = MessageCollector()
                val session = server.connect(collector.send)

                // The engine sent an AUTH challenge on connect.
                assertTrue(collector.messages[0].contains("\"AUTH\""))
                assertTrue(OptimizedJsonMapper.fromJsonToMessage(collector.messages[0]) is AuthMessage)

                // A REQ before auth is rejected by the policy, not the source.
                session.receive("""["REQ","sub1",{"kinds":[1]}]""")
                val closed = collector.containing("CLOSED")
                assertEquals(1, closed.size)
                assertTrue(closed[0].contains("auth-required:"))
            }
        }
}
