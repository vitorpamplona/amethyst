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
package com.vitorpamplona.quartz.nip01Core.relay

import com.vitorpamplona.geode.fixtures.SyntheticEvents
import com.vitorpamplona.geode.testing.RelayClientTest
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySyncFanOut
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NostrClientNegentropyFanOutTest : RelayClientTest() {
    // extra connections to the same in-process relay
    private val extraClients = mutableListOf<NostrClient>()

    private fun clients(count: Int): List<NostrClient> =
        buildList {
            add(client)
            repeat(count - 1) {
                add(NostrClient(hub, scope).also { extraClients.add(it) })
            }
        }

    @After
    fun tearDownExtraClients() {
        extraClients.forEach { it.close() }
    }

    private fun events(range: IntRange): List<Event> = range.map { SyntheticEvents.fakeEvent(idSeed = it, kind = 1, createdAt = it.toLong()) }

    @Test
    fun fullDownloadAcrossMultipleConnections() =
        runBlocking {
            val seeded = events(1..60)
            defaultRelay.preload(seeded)

            val got = mutableListOf<Event>()
            val result =
                withTimeout(30_000) {
                    negentropySyncFanOut(
                        clients = clients(3),
                        relay = defaultRelayUrl,
                        filter = Filter(kinds = listOf(1)),
                        fetchBatch = 5,
                        reqsPerClient = 2,
                    ) { got.add(it) }
                }

            assertEquals(60, got.size, "every event delivered")
            assertEquals(60, got.map { it.id }.toSet().size, "no duplicates")
            assertEquals(60, result.needCount)
            assertEquals(60, result.downloaded)
            assertTrue(result.connections >= 1, "at least one connection served batches")
        }

    @Test
    fun maxEventsCapsDeliveryAndStopsTheFanOut() =
        runBlocking {
            defaultRelay.preload(events(1..50))

            val got = mutableListOf<Event>()
            val result =
                withTimeout(30_000) {
                    negentropySyncFanOut(
                        clients = clients(2),
                        relay = defaultRelayUrl,
                        filter = Filter(kinds = listOf(1)),
                        maxEvents = 12,
                        fetchBatch = 4,
                    ) { got.add(it) }
                }

            assertEquals(12, got.size)
            assertEquals(12, result.downloaded)
        }

    @Test
    fun localEntriesSkipWhatWeAlreadyHaveAndReportHaves() =
        runBlocking {
            val relayOnly = events(1..20)
            val shared = events(101..110)
            val localOnly = events(201..205)
            defaultRelay.preload(relayOnly + shared)

            val got = mutableListOf<Event>()
            val result =
                withTimeout(30_000) {
                    negentropySyncFanOut(
                        clients = clients(2),
                        relay = defaultRelayUrl,
                        filter = Filter(kinds = listOf(1)),
                        localEntries = (shared + localOnly).map { IdAndTime(it.createdAt, it.id) },
                        fetchBatch = 6,
                    ) { got.add(it) }
                }

            assertEquals(relayOnly.map { it.id }.toSet(), got.map { it.id }.toSet(), "only relay-only events download")
            assertEquals(20, result.needCount)
            assertEquals(5, result.haveCount, "local-only events reported as haves")
        }

    @Test
    fun singleClientDegradesGracefully() =
        runBlocking {
            defaultRelay.preload(events(1..25))

            val got = mutableListOf<Event>()
            val result =
                withTimeout(30_000) {
                    negentropySyncFanOut(
                        clients = clients(1),
                        relay = defaultRelayUrl,
                        filter = Filter(kinds = listOf(1)),
                        fetchBatch = 10,
                    ) { got.add(it) }
                }

            assertEquals(25, result.downloaded)
            assertEquals(25, got.map { it.id }.toSet().size)
        }
}
