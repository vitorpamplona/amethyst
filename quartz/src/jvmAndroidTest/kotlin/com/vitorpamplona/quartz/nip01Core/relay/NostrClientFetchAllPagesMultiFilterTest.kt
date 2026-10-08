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
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Several filters on one relay: EventSync's `authors = me` + `#p = me`. A relay pages each
 * filter of a `REQ` on its own, so with one shared cursor the dense filter's page ended
 * hours back while the sparse one's reached weeks back, and the cursor jumped past the
 * dense filter's middle — 4,256 of 5,900 events lost on nostr.mom.
 */
class NostrClientFetchAllPagesMultiFilterTest {
    private val relay = RelayUrlNormalizer.normalize("wss://pages.example.com")

    /** 300 notes one a second, and 20 reactions spread a hundred seconds apart below them. */
    private val dense = FakePagingRelay.corpus(300)
    private val sparse =
        (0 until 20).map { i ->
            val createdAt = 1_000_000L - i * 100
            Event(
                id = "7" + createdAt.toString(16).padStart(63, '0'),
                pubKey = "e".repeat(64),
                createdAt = createdAt,
                kind = 7,
                tags = emptyArray(),
                content = "+",
                sig = "0".repeat(128),
            )
        }

    @Test
    fun aSparseFilterDoesNotDragTheDenseOnePastItsMiddle() =
        runBlocking {
            val client = FakePagingRelay(this, dense + sparse, maxLimit = 50)
            val got = mutableListOf<HexKey>()

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1)), Filter(kinds = listOf(7))), idleTimeoutMs = 2_000) { got.add(it.id) }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(320, got.size)
            assertEquals(320, got.toSet().size)
            assertEquals(320, result.downloaded)
        }

    @Test
    fun anEventBothFiltersMatchIsDeliveredOnce() =
        runBlocking {
            val client = FakePagingRelay(this, dense + sparse, maxLimit = 50)
            val got = mutableListOf<HexKey>()

            // The second filter matches every note the first one walks, and the reactions.
            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1)), Filter(kinds = listOf(1, 7))), idleTimeoutMs = 2_000) { got.add(it.id) }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(320, got.size, "no note delivered twice")
            assertEquals(320, got.toSet().size)
        }

    @Test
    fun aFilterThatStopsShortLeavesItsOlderEventsToTheNext() =
        runBlocking {
            val client = FakePagingRelay(this, dense + sparse, maxLimit = 50)
            val got = mutableListOf<HexKey>()

            // The first filter stops after 100 notes; the second walks every note, so it must
            // deliver the 200 older ones the first never reached, and none of the first 100.
            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1), limit = 100), Filter(kinds = listOf(1))), idleTimeoutMs = 2_000) { got.add(it.id) }

            assertEquals(PagedFetchResult.End.LIMIT_REACHED, result.end, "the first filter's ending, not the drained second's")
            assertEquals(300, got.size)
            assertEquals(300, got.toSet().size)
        }
}
