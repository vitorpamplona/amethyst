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
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
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
class RelayServerListenerTest {
    private class RecordingListener : RelayServerListener {
        val connected = mutableListOf<Long>()
        val disconnected = mutableListOf<Long>()

        override fun onConnect(connectionId: Long) {
            connected.add(connectionId)
        }

        override fun onDisconnect(connectionId: Long) {
            disconnected.add(connectionId)
        }
    }

    private val emptySource =
        object : EventSource {
            override fun events(filters: List<Filter>): Flow<Event> = emptyFlow()
        }

    @Test
    fun firesConnectAndDisconnectWithStableIds() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val listener = RecordingListener()
            val server = EventSourceServer(emptySource, parentContext = dispatcher, listener = listener)

            val a = server.connect {}
            val b = server.connect {}

            assertEquals(listOf(a.id, b.id), listener.connected)
            assertTrue(a.id != b.id, "connection ids must be unique")
            assertEquals(2L, server.activeConnections)

            a.close()
            assertEquals(listOf(a.id), listener.disconnected)
            assertEquals(1L, server.activeConnections)

            b.close()
            assertEquals(listOf(a.id, b.id), listener.disconnected)
            assertEquals(0L, server.activeConnections)

            server.close()
        }

    @Test
    fun doubleCloseIsAccountedOnce() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val listener = RecordingListener()
            val server = EventSourceServer(emptySource, parentContext = dispatcher, listener = listener)

            val s = server.connect {}
            s.close()
            s.close()

            assertEquals(1, listener.disconnected.size)
            assertEquals(0L, server.activeConnections)

            server.close()
        }

    @Test
    fun serverCloseDisconnectsRemaining() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val listener = RecordingListener()
            val server = EventSourceServer(emptySource, parentContext = dispatcher, listener = listener)

            val s = server.connect {}
            assertEquals(1L, server.activeConnections)

            server.close()

            assertEquals(listOf(s.id), listener.disconnected)
            assertEquals(0L, server.activeConnections)
        }

    @Test
    fun nostrServerReportsActiveConnections() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val listener = RecordingListener()
            NostrServer(
                store = EventStore(null),
                policyBuilder = { EmptyPolicy },
                parentContext = dispatcher,
                listener = listener,
            ).use { server ->
                val s = server.connect {}
                assertEquals(1L, server.activeConnections)
                assertEquals(listOf(s.id), listener.connected)

                s.close()
                assertEquals(0L, server.activeConnections)
                assertEquals(listOf(s.id), listener.disconnected)
            }
        }
}
