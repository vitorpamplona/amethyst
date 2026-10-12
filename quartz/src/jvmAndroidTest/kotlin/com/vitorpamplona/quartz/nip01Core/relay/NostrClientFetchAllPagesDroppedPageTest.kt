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
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.runBlocking
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * scores.brainstorm.world (strfry), measured: it drops a reader that falls behind mid-page, and a
 * provider signs hundreds of kind 5s with one `created_at`. A drop that landed inside such a
 * second delivered only repeats of it; the walk stepped past the second, lost 457 events, and
 * still reported DRAINED. A page cut short must be re-asked, never read as the second running out.
 *
 * The backoff's sleep is recorded, never spent.
 */
class NostrClientFetchAllPagesDroppedPageTest {
    private val relay = RelayUrlNormalizer.normalize("wss://drops.example.com")
    private val unbounded = listOf(Filter(kinds = listOf(1)))

    private val dense = 2_000_000L

    /** 450 events above [dense], 100 sharing [dense], 500 below it. */
    private val corpus: List<Event> =
        (1..450).map { event(dense + it, "a$it") } +
            (1..100).map { event(dense, "d$it") } +
            (1..500).map { event(dense - it, "b$it") }

    private fun event(
        createdAt: Long,
        tag: String,
    ) = Event(
        id = tag.encodeToByteArray().joinToString("") { "%02x".format(it) }.padStart(64, '0'),
        pubKey = "f".repeat(64),
        createdAt = createdAt,
        kind = 1,
        tags = emptyArray(),
        content = tag,
        sig = "0".repeat(128),
    )

    private class RecordingBackoff {
        val waits: MutableList<Long> = Collections.synchronizedList(mutableListOf())
        val backoff = PageRetryBackoff(PageRetryBackoff.DEFAULT.delaysMs) { waits.add(it) }
    }

    @Test
    fun aDropInsideTheBoundarySecondIsReAskedAndLosesNothing() =
        runBlocking {
            // REQ 1: the 450 newer events and 50 of the dense second. REQ 2 asks that second
            // again and the connection drops after 30 of its repeats.
            val client = FakePagingRelay(this, corpus, maxLimit = 500, dropAfter = { req -> if (req == 2) 30 else null })
            val recorder = RecordingBackoff()
            val got = mutableListOf<HexKey>()

            val result = client.fetchAllPages(relay, unbounded, idleTimeoutMs = 2_000, throttleBackoff = recorder.backoff) { got.add(it.id) }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(corpus.size, result.downloaded, "the dense second's tail was not skipped")
            assertEquals(corpus.size, got.distinct().size)
            assertEquals(client.requests[1].single().until, client.requests[2].single().until, "the cut page's cursor is asked again")
            assertEquals(listOf(2_000L), recorder.waits)
        }

    @Test
    fun aSecondThatKeepsDroppingEndsTheWalkUndrained() =
        runBlocking {
            val client = FakePagingRelay(this, corpus, maxLimit = 500, dropAfter = { req -> if (req >= 2) 30 else null })
            val recorder = RecordingBackoff()

            val result = client.fetchAllPages(relay, unbounded, idleTimeoutMs = 2_000, throttleBackoff = recorder.backoff) { }

            assertEquals(PagedFetchResult.End.CANNOT_CONNECT, result.end, "never DRAINED past a second it did not finish")
            assertEquals(500, result.downloaded)
            assertEquals(listOf(2_000L, 5_000L, 10_000L), recorder.waits, "bounded re-asks, never a loop")
        }

    @Test
    fun aDropThatStillDeliversKeepsPaging() =
        runBlocking {
            // The common case: the drop lands after new events. The walk advances to the oldest
            // one it got and carries on, with no wait.
            val client = FakePagingRelay(this, corpus, maxLimit = 500, dropAfter = { req -> if (req == 2) 80 else null })
            val recorder = RecordingBackoff()

            val result = client.fetchAllPages(relay, unbounded, idleTimeoutMs = 2_000, throttleBackoff = recorder.backoff) { }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(corpus.size, result.downloaded)
            assertEquals(emptyList(), recorder.waits)
        }
}
