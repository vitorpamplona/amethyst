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

import com.vitorpamplona.geode.InProcessRelays
import com.vitorpamplona.geode.fixtures.SyntheticEvents
import com.vitorpamplona.geode.testing.RelayClientTest
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.NegentropySyncException
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySync
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySyncEvents
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySyncOrFetch
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NostrClientNegentropySyncTest : RelayClientTest() {
    @Test
    fun fullDownloadDeliversEveryEvent() =
        runBlocking {
            defaultRelay.preload(SyntheticEvents.batch(20, kind = 1))

            val got = mutableListOf<Event>()
            val result =
                withTimeout(20_000) {
                    client.negentropySync(
                        relay = defaultRelayUrl,
                        filter = Filter(kinds = listOf(1)),
                    ) { got.add(it) }
                }

            assertEquals(20, got.size, "every event should be delivered")
            assertEquals(20, got.map { it.id }.toSet().size, "no duplicates")
            assertEquals(20, result.needCount)
            assertEquals(0, result.haveCount)
            assertEquals(20, result.downloaded)
            assertEquals(1, result.windows, "small set reconciles in a single window")
        }

    @Test
    fun maxEventsCapsDelivery() =
        runBlocking {
            defaultRelay.preload(SyntheticEvents.batch(20, kind = 1))

            val got = mutableListOf<Event>()
            val result =
                withTimeout(20_000) {
                    client.negentropySync(
                        relay = defaultRelayUrl,
                        filter = Filter(kinds = listOf(1)),
                        maxEvents = 10,
                        fetchBatch = 5,
                    ) { got.add(it) }
                }

            assertEquals(10, got.size, "delivery stops at maxEvents")
            assertEquals(10, result.downloaded)
        }

    @Test
    fun cleanTeardownLeavesNoSubscriptions() =
        runBlocking {
            defaultRelay.preload(SyntheticEvents.batch(15, kind = 1))

            withTimeout(20_000) {
                client.negentropySync(
                    relay = defaultRelayUrl,
                    filter = Filter(kinds = listOf(1)),
                    fetchBatch = 4,
                ) { }
            }

            assertTrue(
                client.activeRequests(defaultRelayUrl).isEmpty(),
                "all download subscriptions must be closed after the sync",
            )
        }

    @Test
    fun flowVariantStreamsEachEvent() =
        runBlocking {
            defaultRelay.preload(SyntheticEvents.batch(12, kind = 1))

            val events =
                withTimeout(20_000) {
                    client
                        .negentropySyncEvents(
                            relay = defaultRelayUrl,
                            filter = Filter(kinds = listOf(1)),
                        ).toList()
                }

            assertEquals(12, events.size)
            assertEquals(12, events.map { it.id }.toSet().size)
        }

    /**
     * A relay that caps negentropy below the matched-set size (strfry's
     * `max_sync_events`) but whose events are spread across distinct `created_at`
     * values. Windowing alone resolves the cap — each window ends up under it — so
     * the sync completes purely via negentropy, no exception, no paging.
     */
    @Test
    fun overCapWithSpreadTimestampsSucceedsViaWindowing() =
        runBlocking {
            val hub = InProcessRelays(negentropySettings = NegentropySettings(maxSyncEvents = 3))
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(hub, scope)
            try {
                val url = RelayUrlNormalizer.normalize("ws://127.0.0.1:7781/")
                // 12 events at distinct created_at: windowing can split until each
                // window holds <= the cap.
                hub.getOrCreate(url).preload(SyntheticEvents.batch(12, kind = 1))

                val got = mutableListOf<Event>()
                val result =
                    withTimeout(60_000) {
                        client.negentropySync(
                            relay = url,
                            filter = Filter(kinds = listOf(1)),
                        ) { got.add(it) }
                    }

                assertEquals(12, got.map { it.id }.toSet().size, "all events reconciled via windowing")
                assertEquals(12, result.downloaded)
                assertTrue(result.windows > 1, "the set must be split into multiple created_at windows")
            } finally {
                client.disconnect()
                scope.cancel()
                hub.close()
            }
        }

    /**
     * A relay that caps negentropy below the matched-set size AND whose events all
     * share one `created_at`, so no `created_at` window can separate them. Even the
     * minimal window stays over the cap, so [negentropySync] cannot reconcile it and
     * throws [NegentropySyncException] (reason OVER_MAX_SYNC_EVENTS) rather than
     * silently paging — the fallback is the caller's call.
     */
    @Test
    fun overCapMinimalWindowThrowsInsteadOfPaging() =
        runBlocking {
            val hub = InProcessRelays(negentropySettings = NegentropySettings(maxSyncEvents = 3))
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(hub, scope)
            try {
                val url = RelayUrlNormalizer.normalize("ws://127.0.0.1:7782/")
                val events = (1..10).map { SyntheticEvents.fakeEvent(idSeed = it, kind = 1, createdAt = 1000L) }
                hub.getOrCreate(url).preload(events)

                val thrown =
                    assertFailsWith<NegentropySyncException> {
                        withTimeout(60_000) {
                            client.negentropySync(
                                relay = url,
                                filter = Filter(kinds = listOf(1)),
                            ) { }
                        }
                    }
                assertEquals(NegentropySyncException.Reason.OVER_MAX_SYNC_EVENTS, thrown.reason)

                // And the caller can recover by paging it themselves.
                val paged = mutableListOf<Event>()
                withTimeout(60_000) {
                    client.fetchAllPages(url, listOf(Filter(kinds = listOf(1)))) { paged.add(it) }
                }
                assertEquals(10, paged.map { it.id }.toSet().size)
            } finally {
                client.disconnect()
                scope.cancel()
                hub.close()
            }
        }

    /**
     * The "try negentropy, else page" combinator: against the same over-cap relay
     * where raw [negentropySync] throws, [negentropySyncOrFetch] transparently pages
     * and delivers every event, reporting that it fell back.
     */
    @Test
    fun orFetchPagesWhenNegentropyCannotReconcile() =
        runBlocking {
            val hub = InProcessRelays(negentropySettings = NegentropySettings(maxSyncEvents = 3))
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(hub, scope)
            try {
                val url = RelayUrlNormalizer.normalize("ws://127.0.0.1:7783/")
                val events = (1..10).map { SyntheticEvents.fakeEvent(idSeed = it, kind = 1, createdAt = 1000L) }
                hub.getOrCreate(url).preload(events)

                val got = mutableListOf<Event>()
                val result =
                    withTimeout(60_000) {
                        client.negentropySyncOrFetch(
                            relay = url,
                            filter = Filter(kinds = listOf(1)),
                        ) { got.add(it) }
                    }

                assertEquals(10, got.map { it.id }.toSet().size, "all events delivered via the paging fallback")
                assertEquals(10, result.downloaded)
                assertTrue(result.pagedFallback, "it should have fallen back to paging")
                assertEquals(
                    NegentropySyncException.Reason.OVER_MAX_SYNC_EVENTS,
                    result.fallbackCause?.reason,
                )
            } finally {
                client.disconnect()
                scope.cancel()
                hub.close()
            }
        }

    /**
     * On a relay that reconciles fine, [negentropySyncOrFetch] uses negentropy and
     * does not page.
     */
    @Test
    fun orFetchUsesNegentropyWhenItWorks() =
        runBlocking {
            defaultRelay.preload(SyntheticEvents.batch(8, kind = 1))

            val got = mutableListOf<Event>()
            val result =
                withTimeout(20_000) {
                    client.negentropySyncOrFetch(
                        relay = defaultRelayUrl,
                        filter = Filter(kinds = listOf(1)),
                    ) { got.add(it) }
                }

            assertEquals(8, got.size)
            assertFalse(result.pagedFallback, "negentropy should have handled it")
            assertEquals(8, result.negentropy?.downloaded)
        }
}
