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

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.DONE_REASON_EOSE
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A one-shot fetch against a relay that refuses an oversized `limit` used to come back empty.
 * Measured with amy's plain `fetch` (2026-10 relay surveys): purplepag.es (`blocked: limit too
 * high: 1000 (max 500)`), relay.cxplay.org (`invalid: limitation.max_limit 1000`) and
 * relay.wavefunc.live (`restricted: limit must not exceed 500`) all returned nothing for a query
 * that a relay clamping its pages would have answered with its max. The refused relay is now
 * re-asked at the limit it states (else half), as the paged walk does.
 */
class NostrClientFetchAllWithHooksLimitTest {
    private val relay = RelayUrlNormalizer.normalize("wss://capped.example.com")

    @Test
    fun aRelayThatRefusesTheLimitIsReAskedAtItsStatedMax() =
        runBlocking {
            val client = FakePagingRelay(this, FakePagingRelay.corpus(2_000), maxLimit = 500, refuseAboveMax = true)

            val result = client.fetchAllWithHooks(mapOf(relay to listOf(Filter(kinds = listOf(1), limit = 1_000))), idleTimeoutMs = 2_000) { _, _ -> true }

            assertEquals(500, result.events.size)
            assertEquals(DONE_REASON_EOSE, result.doneReasons[relay])
            assertEquals(listOf<Int?>(1_000, 500), client.requests.map { it.single().limit })
        }

    @Test
    fun aRefusalThatStatesNoMaxIsReAskedAtHalf() =
        runBlocking {
            val client = FakePagingRelay(this, FakePagingRelay.corpus(2_000), maxLimit = 300, refuseAboveMax = true, statesMaxInRefusal = false)

            val result = client.fetchAllWithHooks(mapOf(relay to listOf(Filter(kinds = listOf(1), limit = 1_000))), idleTimeoutMs = 2_000) { _, _ -> true }

            assertEquals(250, result.events.size)
            assertEquals(listOf<Int?>(1_000, 500, 250), client.requests.map { it.single().limit })
        }

    /** The refusal does not say which filter it was about: only the one above the max comes down. */
    @Test
    fun onlyTheFiltersAboveTheStatedMaxAreLowered() =
        runBlocking {
            val client = FakePagingRelay(this, FakePagingRelay.corpus(2_000), maxLimit = 500, refuseAboveMax = true)
            val filters = listOf(Filter(kinds = listOf(1), limit = 1_000), Filter(kinds = listOf(1), since = 999_000, limit = 100))

            client.fetchAllWithHooks(mapOf(relay to filters), idleTimeoutMs = 2_000) { _, _ -> true }

            assertEquals(listOf(listOf<Int?>(1_000, 100), listOf<Int?>(500, 100)), client.requests.map { req -> req.map { it.limit } })
        }

    @Test
    fun aPolicyRefusalIsStillTheEnd() =
        runBlocking {
            val client = FakePagingRelay(this, FakePagingRelay.corpus(100), closeWith = { "blocked: kinds 1 not allowed" })

            val result = client.fetchAllWithHooks(mapOf(relay to listOf(Filter(kinds = listOf(1), limit = 1_000))), idleTimeoutMs = 2_000) { _, _ -> true }

            assertEquals(0, result.events.size)
            assertEquals("closed:blocked: kinds 1 not allowed", result.doneReasons[relay])
            assertEquals(1, client.requests.size)
        }

    /**
     * An AUTH or a reconnect re-sends the subscription the relay already refused, and the relay
     * refuses it again while the re-ask is in flight. That replay must not lower the re-ask.
     */
    @Test
    fun aReplayedRefusalDoesNotOpenAnotherReAsk() =
        runBlocking {
            val relayClient = FakePagingRelay(this, FakePagingRelay.corpus(2_000), maxLimit = 500, refuseAboveMax = true)
            val client = ReplaysTheFirstRefusal(relayClient)

            val result = client.fetchAllWithHooks(mapOf(relay to listOf(Filter(kinds = listOf(1), limit = 1_000))), idleTimeoutMs = 2_000) { _, _ -> true }

            assertEquals(listOf<Int?>(1_000, 500), relayClient.requests.map { it.single().limit })
            assertEquals(500, result.events.size)
            assertEquals(DONE_REASON_EOSE, result.doneReasons[relay])
        }

    /** Refuses the first subscription a second time, as a replay would, just before serving the re-ask. */
    private class ReplaysTheFirstRefusal(
        private val inner: FakePagingRelay,
    ) : INostrClient by inner {
        private var first: Pair<SubscriptionListener?, List<Filter>>? = null

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            val (relay, relayFilters) = filters.entries.single()
            val refused = first
            if (refused == null) {
                first = listener to relayFilters
            } else {
                refused.first?.onClosed("blocked: limit too high: 1000 (max 500)", relay, refused.second)
            }
            inner.subscribe(subId, filters, listener)
        }
    }
}
