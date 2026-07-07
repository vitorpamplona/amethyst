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
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.LimitsPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RelayLimits
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NostrClientReqBypassingRelayLimitsTest : RelayClientTest() {
    @Test
    fun testDownloadFromRelayReturnsMetadataEvents() =
        runBlocking {
            // Each event needs a unique pubkey so replaceable kind 0 doesn't
            // collapse them all to one row.
            val corpus =
                (1..1000).map {
                    SyntheticEvents.fakeEvent(
                        idSeed = it,
                        kind = MetadataEvent.KIND,
                        pubKey = SyntheticEvents.hexId(it),
                    )
                }
            defaultRelay.preload(corpus)

            val events = mutableListOf<Event>()

            val totalFound =
                client.fetchAllPages(
                    relay = defaultRelayUrl,
                    filters = listOf(Filter(kinds = listOf(MetadataEvent.KIND), limit = 1000)),
                ) { event ->
                    events.add(event)
                }

            assertEquals(1000, totalFound)
            assertEquals(1000, events.size)
            events.forEach { event ->
                assertEquals(MetadataEvent.KIND, event.kind)
            }
        }

    @Test
    fun testDownloadFromRelayReturnsMetadataAndContactListEvents() =
        runBlocking {
            val metadata =
                (1..1000).map {
                    SyntheticEvents.fakeEvent(
                        idSeed = it,
                        kind = MetadataEvent.KIND,
                        pubKey = SyntheticEvents.hexId(it),
                    )
                }
            val contacts =
                (1..1500).map {
                    SyntheticEvents.fakeEvent(
                        idSeed = 100_000 + it,
                        kind = ContactListEvent.KIND,
                        pubKey = SyntheticEvents.hexId(100_000 + it),
                    )
                }
            defaultRelay.preload(metadata + contacts)

            val metadataEvents = mutableListOf<Event>()
            val contactListEvents = mutableListOf<Event>()

            val totalFound =
                client.fetchAllPages(
                    relay = defaultRelayUrl,
                    filters =
                        listOf(
                            Filter(kinds = listOf(MetadataEvent.KIND), limit = 1000),
                            Filter(kinds = listOf(ContactListEvent.KIND), limit = 1500),
                        ),
                ) { event ->
                    if (event.kind == MetadataEvent.KIND) {
                        metadataEvents.add(event)
                    }
                    if (event.kind == ContactListEvent.KIND) {
                        contactListEvents.add(event)
                    }
                }

            assertEquals(2500, totalFound)
            assertEquals(1000, metadataEvents.size)
            assertEquals(1500, contactListEvents.size)
        }

    /**
     * A `search` filter is relevance-ranked, not `created_at`-ordered, so
     * `fetchAllPages` must fetch only its FIRST page and never advance the
     * `until` cursor — otherwise a NIP-50 top-N search silently degrades into a
     * full time-walk of the corpus. A plain (non-search) filter over the same
     * capped relay is the control: it *does* page through everything, proving
     * the per-REQ cap is real and pagination is actually happening.
     */
    @Test
    fun searchFilterIsFetchedAsSingleRelevancePageNotTimeWalked() =
        runBlocking {
            // A relay that returns at most 2 events per REQ (defaultLimit fills in
            // for a filter that gives no limit), so an unbounded filter must
            // paginate to drain a larger set.
            val cappedHub = InProcessRelays(defaultPolicy = { LimitsPolicy(RelayLimits(maxLimit = 2, defaultLimit = 2)) })
            val cappedScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val cappedClient = NostrClient(cappedHub, cappedScope)
            try {
                // Five kind-1 notes, distinct created_at (via idSeed), all matching
                // the FTS term "kotlin".
                cappedHub.getOrCreate(defaultRelayUrl).preload(
                    (1..5).map { SyntheticEvents.fakeEvent(idSeed = it, content = "kotlin note $it") },
                )

                // Control: a non-search filter drains all five across pages and
                // advances the cursor (onNewPage fires) — the cap is real.
                val controlEvents = mutableListOf<Event>()
                var controlPages = 0
                cappedClient.fetchAllPages(
                    relay = defaultRelayUrl,
                    filters = listOf(Filter(kinds = listOf(1))),
                    onNewPage = { controlPages++ },
                ) { controlEvents.add(it) }
                assertEquals(5, controlEvents.size, "non-search filter must page through the whole set")
                assertTrue(controlPages > 0, "non-search filter must advance the until cursor across pages")

                // Search filter: only the first relevance-ranked page is fetched.
                val searchEvents = mutableListOf<Event>()
                var searchPages = 0
                val searchTotal =
                    cappedClient.fetchAllPages(
                        relay = defaultRelayUrl,
                        filters = listOf(Filter(search = "kotlin")),
                        onNewPage = { searchPages++ },
                    ) { searchEvents.add(it) }
                assertEquals(2, searchTotal, "a search filter must be fetched as a single page (the relay's cap)")
                assertEquals(2, searchEvents.size)
                assertEquals(0, searchPages, "a search filter must never advance the until cursor")
            } finally {
                cappedClient.disconnect()
                cappedScope.cancel()
                cappedHub.close()
            }
        }

    /**
     * A page boundary that lands *inside* a `created_at` second must not drop the
     * straddled events. 6 events, 2 per second at t=100/99/98, relay cap 3 — no
     * second exceeds the cap, but the 3rd slot splits the t=99 second. Exclusive
     * `until = oldest - 1` used to skip the sibling; the inclusive re-fetch + dedup
     * must retrieve all 6, once each.
     */
    @Test
    fun boundaryStraddlingASecondIsFullyRetrieved() =
        runBlocking {
            val hub = InProcessRelays(defaultPolicy = { LimitsPolicy(RelayLimits(maxLimit = 3, defaultLimit = 3)) })
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(hub, scope)
            try {
                hub.getOrCreate(defaultRelayUrl).preload(
                    listOf(100L, 100L, 99L, 99L, 98L, 98L).mapIndexed { i, ts ->
                        SyntheticEvents.fakeEvent(idSeed = i + 1, createdAt = ts)
                    },
                )
                val got = mutableListOf<Event>()
                client.fetchAllPages(defaultRelayUrl, listOf(Filter(kinds = listOf(1)))) { got.add(it) }
                assertEquals(6, got.size, "every event must be retrieved despite a boundary inside a second")
                assertEquals(6, got.map { it.id }.toSet().size, "no duplicate deliveries")
            } finally {
                client.disconnect()
                scope.cancel()
                hub.close()
            }
        }

    /**
     * A single second denser than the relay's page cap can't be fully drained (its
     * tail is unreachable — no client-side fix). Paging MUST still step strictly
     * past it, delivering the events newer and older than it, and MUST terminate
     * rather than spin re-fetching the same page. Cap 2; A(1000), four events at
     * t=999, F(998).
     */
    @Test
    fun denseSecondBeyondCapIsSteppedPastWithoutStalling() =
        runBlocking {
            val hub = InProcessRelays(defaultPolicy = { LimitsPolicy(RelayLimits(maxLimit = 2, defaultLimit = 2)) })
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(hub, scope)
            try {
                val a = SyntheticEvents.fakeEvent(idSeed = 1, createdAt = 1000L)
                val dense = (2..5).map { SyntheticEvents.fakeEvent(idSeed = it, createdAt = 999L) }
                val f = SyntheticEvents.fakeEvent(idSeed = 6, createdAt = 998L)
                hub.getOrCreate(defaultRelayUrl).preload(listOf(a) + dense + listOf(f))

                val got = mutableListOf<Event>()
                client.fetchAllPages(defaultRelayUrl, listOf(Filter(kinds = listOf(1)))) { got.add(it) }

                val ids = got.map { it.id }.toSet()
                assertEquals(ids.size, got.size, "no duplicate deliveries")
                assertTrue(a.id in ids, "the event newer than the dense second must be retrieved")
                assertTrue(f.id in ids, "the event older than the dense second must be retrieved (stepped past)")
                assertEquals(2, got.count { it.createdAt == 999L }, "exactly the relay cap of the dense second is reachable")
                assertEquals(4, got.size, "A + 2-of-4 dense + F")
            } finally {
                client.disconnect()
                scope.cancel()
                hub.close()
            }
        }
}
