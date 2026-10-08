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
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PageRetryBackoff
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PagedFetchResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.canShareAnEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    /** The first filter drained; a reaction stored after that must still reach the second walk. */
    @Test
    fun anEventStoredAfterAnEarlierWalkDrainedIsStillDelivered() =
        runBlocking {
            val corpus = CopyOnWriteArrayList(dense + sparse)
            val late = sparse.first().let { Event("8" + it.id.drop(1), it.pubKey, it.createdAt + 1, 7, emptyArray(), "+", it.sig) }
            val client = FakePagingRelay(this, corpus, maxLimit = 50)
            val got = mutableListOf<HexKey>()

            val result =
                client.fetchAllPages(relay, listOf(Filter(kinds = listOf(7)), Filter(kinds = listOf(1, 7))), idleTimeoutMs = 2_000) {
                    got.add(it.id)
                    // The first walk has all 20 reactions: the relay stores one more before the second.
                    if (got.size == 20) corpus.add(late)
                }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertTrue(late.id in got, "the late reaction matches the drained first filter, and was never delivered by it")
            assertEquals(321, got.toSet().size)
            assertEquals(321, got.size)
        }

    @Test
    fun aSilentRelayIsNotAskedForTheRemainingFilters() =
        runBlocking {
            val client = FakePagingRelay(this, dense + sparse, silentOn = { true })

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1)), Filter(kinds = listOf(7))), idleTimeoutMs = 300, throttleBackoff = PageRetryBackoff.NONE) { }

            assertEquals(PagedFetchResult.End.IDLE, result.end)
            assertEquals(listOf(listOf(1)), client.requests.map { req -> req.single().kinds }, "one idle window, not one per filter")
        }

    @Test
    fun aRefusedFilterIsNotHiddenBehindAMetLimit() =
        runBlocking {
            // Every REQ for the reactions is refused outright.
            lateinit var client: FakePagingRelay
            client = FakePagingRelay(this, dense + sparse, maxLimit = 50, closeWith = { req -> if (client.requests[req - 1].single().kinds == listOf(7)) "blocked: no reactions here" else null })

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1), limit = 60), Filter(kinds = listOf(7))), idleTimeoutMs = 2_000) { }

            assertEquals(PagedFetchResult.End.CLOSED, result.end)
            assertEquals("blocked: no reactions here", result.message)
        }

    @Test
    fun filtersThatCannotShareAnEventAreNeverCompared() {
        assertFalse(canShareAnEvent(Filter(authors = listOf("a")), Filter(authors = listOf("b"))))
        assertFalse(canShareAnEvent(Filter(kinds = listOf(1)), Filter(kinds = listOf(7))))
        assertFalse(canShareAnEvent(Filter(tags = mapOf("p" to listOf("x"))), Filter(tags = mapOf("p" to listOf("y")))))
        assertFalse(canShareAnEvent(Filter(since = 10, until = 20), Filter(since = 21)))
        assertTrue(canShareAnEvent(Filter(authors = listOf("a")), Filter(tags = mapOf("p" to listOf("a")))))
        assertTrue(canShareAnEvent(Filter(kinds = listOf(1, 7)), Filter(kinds = listOf(7))))
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
