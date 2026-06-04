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
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.EventSource
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.RequestContext
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RelayLimits
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RelayLimitsServerTest {
    private val pubkey = "46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d"
    private val sig = "4aa5264965018fa12a326686ad3d3bd8beae3218dcc83689b19ca1e6baeb791531943c15363aa6707c7c0c8b2d601deca1f20c32078b2872d356cdca03b04cce"

    private fun hexId(n: Int): String = n.toString().padStart(64, '0')

    private val emptySource =
        object : EventSource {
            override fun events(
                ctx: RequestContext,
                filters: List<Filter>,
            ): Flow<Event> = emptyFlow()
        }

    private class Collector {
        val messages = mutableListOf<String>()
        val send: (String) -> Unit = { messages.add(it) }

        fun containing(label: String) = messages.filter { it.contains("\"$label\"") }
    }

    @Test
    fun rejectsOversizedMessageWithNotice() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = EventSourceServer(emptySource, parentContext = dispatcher, limits = RelayLimits(maxMessageLength = 30))
            val collector = Collector()
            val session = server.connect(collector.send)

            session.receive("""["REQ","sub1",{"kinds":[1,2,3,4,5,6,7,8,9,10,11]}]""") // > 30 chars

            val notices = collector.containing("NOTICE")
            assertEquals(1, notices.size)
            assertTrue(notices[0].contains("too large"))
            assertTrue(collector.containing("EOSE").isEmpty())

            server.close()
        }

    @Test
    fun enforcesMaxSubscriptions() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            // An empty flow EOSEs immediately; no need to keep it open.
            val source =
                object : EventSource {
                    override fun events(
                        ctx: RequestContext,
                        filters: List<Filter>,
                    ): Flow<Event> = emptyFlow()
                }
            val server = EventSourceServer(source, parentContext = dispatcher, limits = RelayLimits(maxSubscriptions = 2))
            val collector = Collector()
            val session = server.connect(collector.send)

            session.receive("""["REQ","a",{"kinds":[1]}]""")
            session.receive("""["REQ","b",{"kinds":[1]}]""")
            session.receive("""["REQ","c",{"kinds":[1]}]""") // third new sub -> rejected

            val closed = collector.containing("CLOSED")
            assertEquals(1, closed.size)
            assertTrue(closed[0].contains("\"c\""))
            assertTrue(closed[0].contains("rate-limited:"))

            server.close()
        }

    @Test
    fun clampsFilterLimitEndToEnd() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val seen = mutableListOf<Int?>()
            val source =
                object : EventSource {
                    override fun events(
                        ctx: RequestContext,
                        filters: List<Filter>,
                    ): Flow<Event> {
                        seen.add(filters.single().limit)
                        return emptyFlow()
                    }
                }
            val server = EventSourceServer(source, parentContext = dispatcher, limits = RelayLimits(maxLimit = 100))
            val collector = Collector()
            val session = server.connect(collector.send)

            session.receive("""["REQ","s",{"kinds":[1],"limit":9999}]""")

            assertEquals(listOf<Int?>(100), seen) // the source saw the clamped limit
            server.close()
        }

    @Test
    fun eventLimitsApplyThroughNostrServer() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            NostrServer(
                store = EventStore(null),
                policyBuilder = { EmptyPolicy },
                parentContext = dispatcher,
                limits = RelayLimits(maxContentLength = 5),
            ).use { server ->
                val collector = Collector()
                val session = server.connect(collector.send)

                val big = Event(hexId(1), pubkey, 1000L, 1, emptyArray(), "too much content", sig)
                session.receive("""["EVENT",${big.toJson()}]""")

                val ok = collector.containing("OK")
                assertEquals(1, ok.size)
                assertTrue(ok[0].contains(",false,"))
                assertTrue(ok[0].contains("invalid:"))
            }
        }
}
