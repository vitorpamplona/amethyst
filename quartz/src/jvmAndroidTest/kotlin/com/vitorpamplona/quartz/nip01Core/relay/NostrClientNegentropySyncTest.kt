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
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.NegentropyLocalIndex
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.NegentropySyncException
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySync
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySyncEvents
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySyncOrFetch
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PassThroughPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyResult
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
     * Forces the relay to split its NEG-MSG responses into many small frames
     * (`frameSizeLimit` at the library floor) so reconciliation spans many rounds,
     * and downloads through a small, bounded pipeline (`fetchBatch`/`maxConcurrentReqs`).
     * Exercises the streaming + back-pressure path end to end: every event must still
     * be delivered exactly once with nothing accumulated.
     */
    @Test
    fun multiRoundReconcileStreamsEveryEventThrough() =
        runBlocking {
            val hub = InProcessRelays(negentropySettings = NegentropySettings(frameSizeLimit = 4096))
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(hub, scope)
            try {
                val url = RelayUrlNormalizer.normalize("ws://127.0.0.1:7784/")
                hub.getOrCreate(url).preload(SyntheticEvents.batch(1500, kind = 1))

                val got = mutableListOf<Event>()
                val result =
                    withTimeout(60_000) {
                        client.negentropySync(
                            relay = url,
                            filter = Filter(kinds = listOf(1)),
                            fetchBatch = 50,
                            maxConcurrentReqs = 4,
                        ) { got.add(it) }
                    }

                assertEquals(1500, got.size, "every event delivered across many reconcile rounds")
                assertEquals(1500, got.map { it.id }.toSet().size, "each exactly once")
                assertEquals(1500, result.downloaded)
                assertEquals(1500, result.needCount)
            } finally {
                client.disconnect()
                scope.cancel()
                hub.close()
            }
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
     *
     * Every event here shares one `created_at`, so the whole filter IS the
     * un-reconcilable window: it is drained as one paged window rather than by
     * abandoning the sync, which is why `fallbackCause` is null. On a filter
     * spanning more than this second, everything outside it still reconciles.
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
                assertTrue(result.pagedFallback, "part of the range came over REQ, so this was not a clean reconcile")
                assertEquals(1, result.pagedWindows, "exactly the one un-reconcilable window was paged")
                assertNull(result.fallbackCause, "the sync was not abandoned — one window was drained by paging")
            } finally {
                client.disconnect()
                scope.cancel()
                hub.close()
            }
        }

    /**
     * A relay that refuses negentropy with a `blocked: …` NEG-ERR that is NOT an
     * over-cap overflow (here: NIP-77 disabled) but still serves plain REQ — the
     * live shape of `wss://nostr-pr02.redscrypt.org`.
     *
     * Regression for the window-split storm: the refusal string starts with
     * `blocked`, which [isOverflow] used to treat as a `max_sync_events` overflow.
     * Every window then re-opened, was refused again, and the splitter fanned out
     * across the whole `created_at` range (~2^31 windows) — so it neither threw
     * [NegentropySyncException] (no paging fallback) nor tripped the idle watchdog
     * (the relay answered every NEG-OPEN promptly), and the call hung indefinitely.
     * With the fix the refusal is a hard failure: negentropy aborts on the first
     * window and [negentropySyncOrFetch] pages instead, delivering every event.
     */
    @Test
    fun orFetchPagesWhenNegentropyRefusedWithBlockedError() =
        runBlocking {
            // geode routes NEG-OPEN and REQ through the same accept(ReqCmd) hook, so
            // we refuse only the NEG-OPEN / window filters (kinds, no limit) and let
            // the paging fallback REQ through — it always carries a `limit`.
            val negDisabled =
                object : PassThroughPolicy() {
                    override fun accept(cmd: ReqCmd): PolicyResult<ReqCmd> =
                        if (cmd.filters.any { it.kinds != null && it.limit == null && it.ids == null }) {
                            PolicyResult.Rejected("blocked: Negentropy sync is disabled")
                        } else {
                            PolicyResult.Accepted(cmd)
                        }
                }

            val hub = InProcessRelays(defaultPolicy = { negDisabled })
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(hub, scope)
            try {
                val url = RelayUrlNormalizer.normalize("ws://127.0.0.1:7785/")
                // Spread across created_at so a naive splitter would have many
                // windows to churn through; the fix must abort on the first one.
                // Kind 1 (regular, not replaceable) so all 10 distinct events persist.
                val events = (1..10).map { SyntheticEvents.fakeEvent(idSeed = it, kind = 1, createdAt = it * 1_000_000L) }
                hub.getOrCreate(url).preload(events)

                val got = mutableListOf<Event>()
                val result =
                    withTimeout(30_000) {
                        client.negentropySyncOrFetch(
                            relay = url,
                            filter = Filter(kinds = listOf(1)),
                            maxEvents = 5000,
                            idleTimeoutMs = 10_000L,
                        ) { got.add(it) }
                    }

                assertTrue(result.pagedFallback, "a non-overflow blocked NEG-ERR must fail over to paging, not window-split")
                assertEquals(
                    NegentropySyncException.Reason.UNAVAILABLE,
                    result.fallbackCause?.reason,
                    "the refusal is a hard failure, not an over-cap overflow",
                )
                assertEquals(10, got.map { it.id }.toSet().size, "every event delivered via the paging fallback")
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

    /**
     * The caller's own count splits a window BEFORE the relay is asked for it.
     *
     * Nothing here overflows — the relay would have reconciled the whole filter
     * in one NEG-OPEN — so every split is driven by [NegentropyLocalIndex.count]
     * against `targetWindow`. That is what bounds the entries a caller has to
     * materialise: without it the first (and only) window is the whole filter,
     * and the local set for it is the whole corpus.
     */
    @Test
    fun targetWindowSplitsFromTheLocalCountAlone() =
        runBlocking {
            // 40 seconds of history, one event each; we already hold the even ones.
            val all = (0 until 40).map { SyntheticEvents.fakeEvent(idSeed = it + 1, kind = 1, createdAt = 1000L + it) }
            defaultRelay.preload(all)
            val ours = all.filterIndexed { i, _ -> i % 2 == 0 }.map { IdAndTime(it.createdAt, it.id) }

            val asked = mutableListOf<Filter>()
            val index =
                object : NegentropyLocalIndex {
                    val inner = NegentropyLocalIndex.of(ours)

                    override suspend fun count(window: Filter): Int {
                        asked += window
                        return inner.count(window) ?: 0
                    }

                    override suspend fun entriesFor(window: Filter) = inner.entriesFor(window)
                }

            val got = mutableListOf<Event>()
            val result =
                withTimeout(60_000) {
                    client.negentropySync(
                        relay = defaultRelayUrl,
                        filter = Filter(kinds = listOf(1)),
                        localIndex = index,
                        targetWindow = 5,
                    ) { got.add(it) }
                }

            assertEquals(20, got.map { it.id }.toSet().size, "only the half we lacked comes down")
            assertTrue(result.windows > 1, "the local count alone must have split the filter")
            assertTrue(asked.isNotEmpty(), "windows must be counted before they are asked for")
            assertNull(result.peerCap, "nothing was refused, so there is no cap to report")
        }

    /** Passing no target keeps the old shape: one window until the relay objects. */
    @Test
    fun withoutATargetTheLocalCountIsNeverConsulted() =
        runBlocking {
            defaultRelay.preload(SyntheticEvents.batch(20, kind = 1))
            var counted = 0
            val index =
                object : NegentropyLocalIndex {
                    override suspend fun count(window: Filter): Int {
                        counted++
                        return 1_000_000
                    }

                    override suspend fun entriesFor(window: Filter) = emptyList<IdAndTime>()
                }

            val result =
                withTimeout(20_000) {
                    client.negentropySync(
                        relay = defaultRelayUrl,
                        filter = Filter(kinds = listOf(1)),
                        localIndex = index,
                    ) { }
                }

            assertEquals(0, counted, "targetWindow = 0 must not ask the store anything")
            assertEquals(1, result.windows)
            assertEquals(20, result.downloaded)
        }

    /**
     * A relay that refuses for size states its cap, and the client reports it —
     * so the next sync can start at a window that fits instead of rediscovering
     * it by halving.
     */
    @Test
    fun theRelaysCapIsReportedBack() =
        runBlocking {
            val hub = InProcessRelays(negentropySettings = NegentropySettings(maxSyncEvents = 3))
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(hub, scope)
            try {
                val url = RelayUrlNormalizer.normalize("ws://127.0.0.1:7786/")
                hub.getOrCreate(url).preload((0 until 12).map { SyntheticEvents.fakeEvent(idSeed = it + 1, kind = 1, createdAt = 1000L + it) })

                val result =
                    withTimeout(60_000) {
                        client.negentropySync(relay = url, filter = Filter(kinds = listOf(1))) { }
                    }

                assertEquals(12, result.downloaded)
                assertTrue(result.windows > 1)
                assertEquals(3L, result.peerCap, "the relay stated its own max_sync_events")
            } finally {
                client.disconnect()
                scope.cancel()
                hub.close()
            }
        }

    /**
     * One second the relay will not reconcile at any window size costs that
     * second, not the sync.
     *
     * The whole point of the [NegentropyOrFetchResult.pagedWindows] path: the
     * dense second is drained over REQ while everything around it still
     * reconciles. Before, the exception from that one window abandoned the whole
     * sync and re-paged the entire filter — on a large corpus, exactly the cost
     * negentropy was there to avoid.
     */
    @Test
    fun oneUnreconcilableSecondDoesNotCostTheRestOfTheFilter() =
        runBlocking {
            val hub = InProcessRelays(negentropySettings = NegentropySettings(maxSyncEvents = 3))
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(hub, scope)
            try {
                val url = RelayUrlNormalizer.normalize("ws://127.0.0.1:7787/")
                // Ten events crammed into one second — no created_at window can
                // separate them — plus five ordinary seconds around them.
                val dense = (1..10).map { SyntheticEvents.fakeEvent(idSeed = it, kind = 1, createdAt = 1000L) }
                val sparse = (0 until 5).map { SyntheticEvents.fakeEvent(idSeed = 100 + it, kind = 1, createdAt = 2000L + it) }
                hub.getOrCreate(url).preload(dense + sparse)

                val got = mutableListOf<Event>()
                val result =
                    withTimeout(60_000) {
                        client.negentropySyncOrFetch(
                            relay = url,
                            filter = Filter(kinds = listOf(1)),
                        ) { got.add(it) }
                    }

                assertEquals(15, got.map { it.id }.toSet().size, "everything is delivered, by whichever route")
                assertEquals(1, result.pagedWindows, "only the dense second is paged")
                assertNull(result.fallbackCause, "the sync itself was never abandoned")
                val negentropy = assertNotNull(result.negentropy, "the rest of the range still reconciled")
                assertTrue(negentropy.windows > 1)
            } finally {
                client.disconnect()
                scope.cancel()
                hub.close()
            }
        }
}
