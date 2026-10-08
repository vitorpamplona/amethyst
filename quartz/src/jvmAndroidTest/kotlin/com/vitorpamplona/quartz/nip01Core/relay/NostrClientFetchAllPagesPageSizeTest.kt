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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PagedFetchResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.lowerLimitAfterRefusal
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A filter's `limit` caps the WALK; each REQ asks for what is still missing. A relay that
 * serves it all answers in one page, one that clamps pages at its own max, and one that
 * refuses outright — purplepag.es: `CLOSED blocked: limit too high: 50000 (max 500)` — is
 * re-asked lower (its stated max, else half) and paged at that from then on.
 */
class NostrClientFetchAllPagesPageSizeTest {
    private val relay = RelayUrlNormalizer.normalize("wss://pages.example.com")

    @Test
    fun aRelayThatRefusesALargeLimitIsReAskedAtItsStatedMaxAndStillStopsAtTheTotal() =
        runBlocking {
            val client = FakePagingRelay(this, FakePagingRelay.corpus(2_000), maxLimit = 500, refuseAboveMax = true)
            val got = mutableListOf<HexKey>()

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1), limit = 1_200)), idleTimeoutMs = 2_000) { got.add(it.id) }

            assertEquals(PagedFetchResult.End.LIMIT_REACHED, result.end, "the walk got going and stopped at the caller's total")
            assertEquals(1_200, result.downloaded)
            assertEquals(1_200, got.distinct().size)
            // 1200 is refused with "(max 500)"; then 500, 500, and the 201 still missing plus the
            // one boundary event the inclusive re-fetch sends again.
            assertEquals(listOf<Int?>(1_200, 500, 500, 202), client.requests.map { it.single().limit })
        }

    @Test
    fun aRelayThatServesTheWholeLimitIsAskedOnce() =
        runBlocking {
            val client = FakePagingRelay(this, FakePagingRelay.corpus(2_000), maxLimit = 5_000)

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1), limit = 1_200)), idleTimeoutMs = 2_000) { }

            assertEquals(PagedFetchResult.End.LIMIT_REACHED, result.end)
            assertEquals(1_200, result.downloaded)
            assertEquals(listOf<Int?>(1_200), client.requests.map { it.single().limit }, "one REQ, no page size imposed")
        }

    @Test
    fun aRelayThatClampsIsPagedAtItsOwnMax() =
        runBlocking {
            // strfry and most relays: an oversized limit is served as their max, never refused.
            val client = FakePagingRelay(this, FakePagingRelay.corpus(2_000), maxLimit = 300)

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1), limit = 1_000)), idleTimeoutMs = 2_000) { }

            assertEquals(PagedFetchResult.End.LIMIT_REACHED, result.end)
            assertEquals(1_000, result.downloaded)
            assertEquals(
                1_000,
                client.requests
                    .first()
                    .single()
                    .limit,
            )
        }

    @Test
    fun aRefusalThatStatesNoMaxIsReAskedAtHalfTheLimit() =
        runBlocking {
            val client = FakePagingRelay(this, FakePagingRelay.corpus(1_000), maxLimit = 400, refuseAboveMax = true, statesMaxInRefusal = false)

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1), limit = 1_600)), idleTimeoutMs = 2_000) { }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(1_000, result.downloaded)
            val wireLimits = client.requests.map { it.single().limit!! }
            assertEquals(listOf(1_600, 800, 400), wireLimits.take(3), "halved until the relay accepts")
            assertTrue(wireLimits.drop(2).all { it <= 400 }, "the accepted cap is kept for the rest of the walk: $wireLimits")
        }

    @Test
    fun aRefusedTopUpOfADenseSecondDoesNotLoopTheWalk() =
        runBlocking {
            // 600 events in one second on a relay that refuses anything above 500: the top-up
            // past the cap is refused, so that second's tail is out of reach, and the walk
            // must step past it and finish rather than re-ask forever.
            val dense =
                FakePagingRelay.corpus(100, newest = 2_000) +
                    (0 until 600).map { i ->
                        Event(("d$i").padStart(64, '0'), "f".repeat(64), 1_000, 1, emptyArray(), "dense $i", "0".repeat(128))
                    } +
                    FakePagingRelay.corpus(100, newest = 900)
            val client = FakePagingRelay(this, dense, maxLimit = 500, refuseAboveMax = true)
            val got = mutableListOf<HexKey>()

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1), limit = 50_000)), idleTimeoutMs = 2_000) { got.add(it.id) }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(got.size, got.distinct().size)
            assertTrue(got.size >= 600, "everything outside the dense second's tail arrived: ${got.size}")
            assertTrue(client.requests.size < 20, "no refusal loop: ${client.requests.size} REQs")
        }

    @Test
    fun theLastPageStillCoversTheBoundarySecond() =
        runBlocking {
            // Three events share the boundary second and the walk is two short. Asking for
            // just the remainder would bring back already-delivered events of that second and
            // nothing new, step past it, and lose the third — so the remainder is topped up
            // by what the re-fetch repeats.
            val dense =
                FakePagingRelay.corpus(10, newest = 2_000) +
                    listOf("a", "b", "c").map { nonce ->
                        Event(("3e8$nonce").padStart(64, '0'), "f".repeat(64), 1_000, 1, emptyArray(), "dense $nonce", "0".repeat(128))
                    } +
                    FakePagingRelay.corpus(10, newest = 900)
            val client = FakePagingRelay(this, dense, maxLimit = 500)
            val got = mutableListOf<HexKey>()

            // Page one (limit 11) ends one event into second 1000; the walk still wants 2 more.
            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1), limit = 13)), idleTimeoutMs = 2_000, pageSize = 11) { got.add(it.id) }

            assertEquals(13, result.downloaded)
            assertEquals(13, got.distinct().size)
            assertEquals(3, got.count { it.endsWith("3e8a") || it.endsWith("3e8b") || it.endsWith("3e8c") }, "all of second 1000 was read: $got")
        }

    @Test
    fun aRelayThatRefusesLimitsAboveItsMaxIsWalkedToTheEnd() =
        runBlocking {
            // purplepag.es, scripted: any limit above 500 is a CLOSED, never a clamp.
            val client = FakePagingRelay(this, FakePagingRelay.corpus(1_234), maxLimit = 500, refuseAboveMax = true)

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1), limit = 50_000)), idleTimeoutMs = 2_000) { }

            assertEquals(PagedFetchResult.End.DRAINED, result.end, "the relay ran out before the 50000 cap did")
            assertEquals(1_234, result.downloaded)
            assertNull(result.message, "nothing was refused")
        }

    @Test
    fun aLimitWithinOnePageIsSentUnchanged() =
        runBlocking {
            val client = FakePagingRelay(this, FakePagingRelay.corpus(1_000))

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1), limit = 100)), idleTimeoutMs = 2_000) { }

            assertEquals(100, result.downloaded)
            assertEquals(listOf<Int?>(100), client.requests.map { it.single().limit })
        }

    @Test
    fun anUnboundedFilterStillSendsNoLimit() =
        runBlocking {
            // Unchanged on purpose: without a caller's limit the relay's own default page is
            // what every relay already served, and the walk drains on an empty page.
            val client = FakePagingRelay(this, FakePagingRelay.corpus(700), maxLimit = 500, defaultLimit = 300)

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1))), idleTimeoutMs = 2_000) { }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(700, result.downloaded)
            assertTrue(client.requests.all { it.single().limit == null }, "an unbounded filter goes out without a limit")
        }

    @Test
    fun aCallerCanPickThePageSize() =
        runBlocking {
            val client = FakePagingRelay(this, FakePagingRelay.corpus(1_000), maxLimit = 5_000)

            val result =
                client.fetchAllPages(
                    relay = relay,
                    filters = listOf(Filter(kinds = listOf(1), limit = 250)),
                    idleTimeoutMs = 2_000,
                    pageSize = 100,
                ) { }

            assertEquals(PagedFetchResult.End.LIMIT_REACHED, result.end)
            assertEquals(250, result.downloaded)
            assertEquals(
                100,
                client.requests
                    .first()
                    .single()
                    .limit,
            )
            assertTrue(client.requests.all { it.single().limit!! <= 100 })
        }

    @Test
    fun severalFiltersAreNotCutToThePageSize() =
        runBlocking {
            // The cursor is shared, so capping each of several filters at a page would let
            // the one whose page ends newer lose the gap down to the other's oldest event.
            // Two kinds interleaved second by second, a relay that serves big pages: every
            // event of both must arrive, as it did before the page size existed.
            val corpus =
                (0 until 4_000).map { i ->
                    val createdAt = 1_000_000L - i
                    Event(createdAt.toString(16).padStart(64, '0'), "f".repeat(64), createdAt, if (i % 2 == 0) 1 else 3, emptyArray(), "e$i", "0".repeat(128))
                }
            val client = FakePagingRelay(this, corpus, maxLimit = 5_000)
            val got = mutableListOf<Event>()

            val result =
                client.fetchAllPages(
                    relay = relay,
                    filters = listOf(Filter(kinds = listOf(1), limit = 1_000), Filter(kinds = listOf(3), limit = 1_500)),
                    idleTimeoutMs = 2_000,
                ) { got.add(it) }

            assertEquals(listOf<Int?>(1_000, 1_500), client.requests.first().map { it.limit }, "each filter asks for its whole remainder")
            assertEquals(2_500, result.downloaded)
            assertEquals(1_000, got.count { it.kind == 1 })
            assertEquals(1_500, got.count { it.kind == 3 })
        }

    @Test
    fun aSecondDenserThanAPageIsStillReadInFullFromARelayThatServesMore() =
        runBlocking {
            // 600 events share one second. A caller's pageSize (500) cannot cover it, but this
            // relay serves up to 5000 per REQ: once the already-seen events of the second fill a
            // page, the re-fetch must ask past pageSize, or the duplicate-only page steps past
            // the second and its last 100 events are lost.
            val dense =
                FakePagingRelay.corpus(100, newest = 2_000) +
                    (0 until 600).map { i ->
                        Event(("d$i").padStart(64, '0'), "f".repeat(64), 1_000, 1, emptyArray(), "dense $i", "0".repeat(128))
                    } +
                    FakePagingRelay.corpus(100, newest = 900)
            val client = FakePagingRelay(this, dense, maxLimit = 5_000)
            val got = mutableListOf<HexKey>()

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1), limit = 50_000)), idleTimeoutMs = 2_000, pageSize = 500) { got.add(it.id) }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(800, got.distinct().size, "every event of the dense second arrived")
        }

    @Test
    fun aRefusalIsReadForTheLimitItStates() {
        assertEquals(500, lowerLimitAfterRefusal("blocked: limit too high: 50000 (max 500)", 50_000))
        assertEquals(25_000, lowerLimitAfterRefusal("blocked: limit too high", 50_000))
        assertEquals(1_000, lowerLimitAfterRefusal("error: limit exceeds max_limit of 1000", 5_000))
        // A stated max that is not lower than what was sent cannot be the reason: halve.
        assertEquals(250, lowerLimitAfterRefusal("blocked: limit too high (max 500)", 500))
        assertNull(lowerLimitAfterRefusal("rate-limited: slow down, limit 10 REQs a second", 500), "throttling, not a cap")
        assertNull(lowerLimitAfterRefusal("error: rate limit exceeded", 500))
        assertNull(lowerLimitAfterRefusal("auth-required: we only serve members", 500))
        assertNull(lowerLimitAfterRefusal("blocked: limit too high", 1), "nothing lower to ask for")
        assertNull(lowerLimitAfterRefusal(null, 500))
    }

    @Test
    fun onlyARefusalOfTheEventLimitLowersIt() {
        // Seen in the 2026-10 survey of 743 production relays: relay.cxplay.org.
        assertEquals(1_000, lowerLimitAfterRefusal("invalid: limitation.max_limit 1000", 5_000))
        // "limit" about something else: halving the event limit would cost a REQ per halving
        // and leave a needlessly small cap, and fix nothing.
        assertNull(lowerLimitAfterRefusal("error: number of subscriptions exceeds limit", 5_000), "too many open subscriptions")
        assertNull(lowerLimitAfterRefusal("blocked: subscription limit reached (20)", 5_000))
        assertNull(lowerLimitAfterRefusal("error: max subscriptions limit of 10 reached", 5_000))
        assertNull(lowerLimitAfterRefusal("error: too many filters, limit is 10", 5_000), "too many filters per REQ")
        assertNull(lowerLimitAfterRefusal("blocked: REQ contains 12 filters, maximum is 10 (limit)", 5_000))
        assertNull(lowerLimitAfterRefusal("ERROR: rate limit exceeded, slow down", 5_000))
        assertNull(lowerLimitAfterRefusal("error: request limit exceeded, too many requests", 5_000))
    }

    @Test
    fun anEventARelayRepeatsOnAPageIsDeliveredAndCountedOnce() =
        runBlocking {
            // purplepag.es, measured: a 500-event page carried 59 to 84 repeats of events already
            // on that page, all of them delivered and counted, so a walk for 3000 stopped at
            // 2716 distinct events.
            val client = FakePagingRelay(this, FakePagingRelay.corpus(2_000), maxLimit = 500, answer = { _, honest -> honest.flatMap { listOf(it, it) } })
            val got = mutableListOf<HexKey>()

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1), limit = 1_200)), idleTimeoutMs = 2_000) { got.add(it.id) }

            assertEquals(PagedFetchResult.End.LIMIT_REACHED, result.end)
            assertEquals(1_200, got.size, "each event delivered once")
            assertEquals(1_200, got.distinct().size, "and the limit counts distinct events")
            assertEquals(1_200, result.downloaded)
        }

    @Test
    fun anEventRepeatedOutOfOrderOnTheFirstPageIsDeliveredOnce() =
        runBlocking {
            // eden.nostr.land, measured: an open-ended first page interleaves events stored while
            // the query ran, out of created_at order, and one came twice in different seconds.
            val client = FakePagingRelay(this, FakePagingRelay.corpus(1_000), maxLimit = 500, answer = { req, honest -> if (req == 1) honest.take(150) + honest.first() + honest.drop(150) else honest })
            val got = mutableListOf<HexKey>()

            client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1), limit = 300)), idleTimeoutMs = 2_000) { got.add(it.id) }

            assertEquals(300, got.size)
            assertEquals(300, got.distinct().size)
        }
}
