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
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.DEFAULT_PAGE_SIZE
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PagedFetchResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A filter's `limit` caps the WALK; what goes on the wire is the PAGE. They used to be the
 * same number: `--paginate --limit 50000` sent `limit = 50000` on every REQ, which a relay
 * that clamps quietly serves as its own page, but purplepag.es refuses outright —
 * `CLOSED blocked: limit too high: 50000 (max 500)` — so the walk never started.
 */
class NostrClientFetchAllPagesPageSizeTest {
    private val relay = RelayUrlNormalizer.normalize("wss://pages.example.com")

    @Test
    fun aLimitAboveThePageSizeIsAskedForInPagesAndStillStopsAtTheTotal() =
        runBlocking {
            val client = FakePagingRelay(this, FakePagingRelay.corpus(2_000), maxLimit = 500, refuseAboveMax = true)
            val got = mutableListOf<HexKey>()

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1), limit = 1_200)), idleTimeoutMs = 2_000) { got.add(it.id) }

            assertEquals(PagedFetchResult.End.LIMIT_REACHED, result.end, "the walk got going and stopped at the caller's total")
            assertEquals(1_200, result.downloaded)
            assertEquals(1_200, got.distinct().size)
            // 500, 500, then the 201 still missing plus the one boundary event the inclusive
            // re-fetch sends again.
            val wireLimits = client.requests.map { it.single().limit }
            assertTrue(wireLimits.all { it != null && it <= DEFAULT_PAGE_SIZE }, "no REQ asked for more than a page: $wireLimits")
            assertEquals(listOf<Int?>(500, 500, 202), wireLimits)
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
            // 600 events share one second. pageSize (500) cannot cover it, but this relay serves
            // up to 5000 per REQ: once the already-seen events of the second fill a page, the
            // re-fetch must ask past pageSize, or the duplicate-only page steps past the second
            // and its last 100 events are lost.
            val dense =
                FakePagingRelay.corpus(100, newest = 2_000) +
                    (0 until 600).map { i ->
                        Event(("d$i").padStart(64, '0'), "f".repeat(64), 1_000, 1, emptyArray(), "dense $i", "0".repeat(128))
                    } +
                    FakePagingRelay.corpus(100, newest = 900)
            val client = FakePagingRelay(this, dense, maxLimit = 5_000)
            val got = mutableListOf<HexKey>()

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1), limit = 50_000)), idleTimeoutMs = 2_000) { got.add(it.id) }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(800, got.distinct().size, "every event of the dense second arrived")
        }
}
