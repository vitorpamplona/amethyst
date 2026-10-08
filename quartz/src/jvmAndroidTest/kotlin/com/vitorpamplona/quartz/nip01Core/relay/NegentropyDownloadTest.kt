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
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.DownloadPace
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.NegentropySyncResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchByIds
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySync
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.LimitsPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PassThroughPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyResult
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RelayLimits
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * NIP-77 syncs against relays seen losing events in production without an error: the
 * reconcile named every id and the `REQ`s for them came back short, or the reconcile itself
 * covered only part of the set.
 */
class NegentropyDownloadTest {
    private val author = "ab".repeat(32)

    /** relay.nostr.net served 100 of a 500-id `REQ`, then `EOSE`: 1,100 of 5,219 downloaded. */
    @Test
    fun aRelayThatCapsTheIdsReqIsAskedForTheRest() =
        runBlocking {
            val result = sync(30, Filter(kinds = listOf(1))) { LimitsPolicy(RelayLimits(defaultLimit = 5, maxLimit = 10)) }

            assertEquals(30, result.needCount)
            assertEquals(30, result.downloaded, "every id the reconcile named is downloaded, ten at a time")
        }

    /** relay.conduit.market closes an ids-only filter: "wildcard subscriptions are not available". */
    @Test
    fun aRelayThatRefusesBareIdsGetsTheSyncKinds() =
        runBlocking {
            val result = sync(30, Filter(kinds = listOf(1))) { RefusesBareIds() }

            assertEquals(30, result.downloaded)
        }

    @Test
    fun aScopeWithoutKindsAddsItsAuthorsAfterARefusal() =
        runBlocking {
            val result = sync(30, Filter(authors = listOf(author))) { RefusesBareIds() }

            assertEquals(30, result.downloaded)
        }

    /** relay.ohstr.com reconciled its newest 500 of 25,691 events and finished clean. */
    @Test
    fun aRelayThatReconcilesOnlyItsNewestEventsIsAskedForTheOlderOnes() =
        runBlocking {
            val delivered = mutableListOf<String>()
            val result = sync(30, Filter(kinds = listOf(1)), onEvent = { delivered.add(it) }) { ReconcilesNewest(5) }

            assertEquals(30, result.downloaded)
            assertEquals(30, delivered.toSet().size)
        }

    /** The cut can fall inside a second: three events a second, five per reconcile. */
    @Test
    fun aCutInsideASecondDeliversEachEventOnce() =
        runBlocking {
            val events = List(30) { i -> SyntheticEvents.fakeEvent(idSeed = i + 1, pubKey = author, createdAt = 100L + i / 3) }
            val delivered = mutableListOf<String>()
            val result = sync(events, Filter(kinds = listOf(1)), onEvent = { delivered.add(it) }) { ReconcilesNewest(5) }

            assertEquals(30, delivered.size, "no event delivered twice: $delivered")
            assertEquals(30, delivered.toSet().size)
            assertEquals(30, result.downloaded)
        }

    /** Walks back pass by pass to the events we hold, and stops there. */
    @Test
    fun theWalkBackStopsAtTheEventsWeHold() =
        runBlocking {
            val events = SyntheticEvents.batch(30, kind = 1) { author }
            val held = events.take(20).map { IdAndTime(it.createdAt, it.id) }
            val delivered = mutableListOf<String>()
            val result = sync(events, Filter(kinds = listOf(1)), local = held, onEvent = { delivered.add(it) }) { ReconcilesNewest(5) }

            assertEquals(10, result.downloaded)
            assertEquals(events.drop(20).map { it.id }.toSet(), delivered.toSet())
        }

    /**
     * An incremental sync that already holds a truncating relay's newest events downloads
     * nothing in the first pass, and still has to look below them.
     */
    @Test
    fun holdingTheRelaysNewestEventsStillWalksBackPastThem() =
        runBlocking {
            val events = SyntheticEvents.batch(30, kind = 1) { author }
            val held = events.takeLast(20).map { IdAndTime(it.createdAt, it.id) }
            val delivered = mutableListOf<String>()
            val result = sync(events, Filter(kinds = listOf(1)), local = held, onEvent = { delivered.add(it) }) { ReconcilesNewest(5) }

            assertEquals(10, result.downloaded)
            assertEquals(events.take(10).map { it.id }.toSet(), delivered.toSet())
        }

    /**
     * The first pass against a truncating relay reports every older event of ours as a have:
     * it never looked there. Only what the walk-back confirms the relay lacks is handed back.
     */
    @Test
    fun onlyHavesTheRelayReallyLacksAreHandedBack() =
        runBlocking {
            val events = SyntheticEvents.batch(30, kind = 1) { author }
            val ours = SyntheticEvents.fakeEvent(idSeed = 999, pubKey = author, createdAt = 5)
            val held = events.take(10).map { IdAndTime(it.createdAt, it.id) } + IdAndTime(ours.createdAt, ours.id)
            val haves = mutableListOf<String>()

            val hub = InProcessRelays(defaultPolicy = { ReconcilesNewest(5) })
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(hub, scope)
            try {
                hub.getOrCreate(InProcessRelays.DEFAULT_URL).preload(events)
                val result =
                    withTimeout(20_000) {
                        client.negentropySync(
                            relay = InProcessRelays.DEFAULT_URL,
                            filter = Filter(kinds = listOf(1)),
                            localEntries = held,
                            idleTimeoutMs = 5_000,
                            onHaveIds = { haves.addAll(it) },
                        ) { }
                    }

                assertEquals(listOf(ours.id), haves)
                assertEquals(1, result.haveCount)
                assertEquals(20, result.downloaded)
            } finally {
                client.disconnect()
                scope.cancel()
                hub.close()
            }
        }

    /** A relay that ignores the window's `until` names the same newest events every pass. */
    @Test
    fun aRelayThatIgnoresTheWindowEndsWithoutDeliveringTwice() =
        runBlocking {
            val delivered = mutableListOf<String>()
            sync(30, Filter(kinds = listOf(1)), onEvent = { delivered.add(it) }) { IgnoresUntil(5) }

            assertEquals(delivered.size, delivered.toSet().size, "no event delivered twice: $delivered")
        }

    @Test
    fun eventsTheRelaySendsUnaskedAreNotCollected() =
        runBlocking {
            val events = SyntheticEvents.batch(10, kind = 1) { author }
            val client = DropsFirst(this, events.take(5), drops = 0, extra = events.drop(5))

            val got = client.fetchByIds(InProcessRelays.DEFAULT_URL, events.take(5).map { it.id }, idleTimeoutMs = 5_000)

            assertEquals(events.take(5).map { it.id }.toSet(), got.map { it.id }.toSet())
        }

    /** A relay that will not take a connection stops the sync's downloads instead of idling each batch. */
    @Test
    fun aPaceGivesUpAfterRoundsKeepFailing() =
        runBlocking {
            val events = SyntheticEvents.batch(5, kind = 1) { author }
            val client = DropsFirst(this, events, drops = Int.MAX_VALUE)
            val pace = DownloadPace(pause = {})

            client.fetchByIds(InProcessRelays.DEFAULT_URL, events.map { it.id }, idleTimeoutMs = 5_000, pace = pace)
            client.fetchByIds(InProcessRelays.DEFAULT_URL, events.map { it.id }, idleTimeoutMs = 5_000, pace = pace)

            assertTrue(pace.gaveUp)
            assertEquals(6, client.requests, "four rounds of the first batch, two of the second")
        }

    /** no.str.cr closes the socket under several 500-id downloads at once; every one in flight lost its ids. */
    @Test
    fun aBatchTheRelayDroppedIsAskedForAgainOneAtATime() =
        runBlocking {
            val events = SyntheticEvents.batch(30, kind = 1) { author }
            val client = DropsFirst(this, events, drops = 1)
            val pace = DownloadPace(pause = {})

            val got = client.fetchByIds(InProcessRelays.DEFAULT_URL, events.map { it.id }, idleTimeoutMs = 5_000, pace = pace)

            assertEquals(30, got.size)
            assertEquals(2, client.requests)
            assertFalse(pace.overlapping, "the sync's other batches stop overlapping")
        }

    @Test
    fun aRelayThatKeepsDroppingIsGivenUpOn() =
        runBlocking {
            val events = SyntheticEvents.batch(5, kind = 1) { author }
            val client = DropsFirst(this, events, drops = Int.MAX_VALUE)

            val got = client.fetchByIds(InProcessRelays.DEFAULT_URL, events.map { it.id }, idleTimeoutMs = 5_000, pace = DownloadPace(pause = {}))

            assertEquals(0, got.size)
            assertEquals(4, client.requests, "the first ask and three re-asks")
        }

    /** Drops the connection under its first [drops] `REQ`s, then serves [corpus] by id. */
    private class DropsFirst(
        private val scope: CoroutineScope,
        private val corpus: List<Event>,
        private val drops: Int,
        private val extra: List<Event> = emptyList(),
    ) : INostrClient by EmptyNostrClient() {
        @Volatile var requests = 0

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            val (relay, relayFilters) = filters.entries.single()
            val req = ++requests
            scope.launch {
                if (req <= drops) {
                    listener?.onCannotConnect(relay, "WebSocket Failure: EOFException", relayFilters)
                } else {
                    (corpus.filter { e -> relayFilters.any { it.match(e) } } + extra).forEach { listener?.onEvent(it, false, relay, relayFilters) }
                    listener?.onEose(relay, relayFilters)
                }
            }
        }
    }

    private suspend fun sync(
        count: Int,
        filter: Filter,
        onEvent: (String) -> Unit = {},
        policy: () -> IRelayPolicy,
    ): NegentropySyncResult = sync(SyntheticEvents.batch(count, kind = 1) { author }, filter, onEvent = onEvent, policy = policy)

    private suspend fun sync(
        events: List<Event>,
        filter: Filter,
        local: List<IdAndTime> = emptyList(),
        onEvent: (String) -> Unit = {},
        policy: () -> IRelayPolicy,
    ): NegentropySyncResult {
        val hub = InProcessRelays(defaultPolicy = policy)
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val client = NostrClient(hub, scope)
        try {
            hub.getOrCreate(InProcessRelays.DEFAULT_URL).preload(events)
            return withTimeout(20_000) {
                client.negentropySync(relay = InProcessRelays.DEFAULT_URL, filter = filter, localEntries = local, idleTimeoutMs = 5_000) { onEvent(it.id) }
            }
        } finally {
            client.disconnect()
            scope.cancel()
            hub.close()
        }
    }

    /** Builds every reconcile from its newest [n] matches, as ohstr does with its `max_limit`. */
    private class ReconcilesNewest(
        private val n: Int,
    ) : PassThroughPolicy() {
        override fun accept(cmd: NegOpenCmd): PolicyResult<NegOpenCmd> = PolicyResult.Accepted(NegOpenCmd(cmd.subId, cmd.filter.copy(limit = n), cmd.initialMessage))
    }

    /** Reconciles its newest [n] matches whatever window it is asked for. */
    private class IgnoresUntil(
        private val n: Int,
    ) : PassThroughPolicy() {
        override fun accept(cmd: NegOpenCmd): PolicyResult<NegOpenCmd> = PolicyResult.Accepted(NegOpenCmd(cmd.subId, cmd.filter.copy(until = null, limit = n), cmd.initialMessage))
    }

    /** Refuses a `REQ` filter that names ids and nothing else, as conduit does. */
    private class RefusesBareIds : PassThroughPolicy() {
        override fun accept(cmd: ReqCmd): PolicyResult<ReqCmd> =
            if (cmd.filters.any { it.ids != null && it.kinds == null && it.authors == null && it.tags == null }) {
                PolicyResult.Rejected("blocked: wildcard subscriptions are not available when private messages are enabled")
            } else {
                PolicyResult.Accepted(cmd)
            }
    }
}
