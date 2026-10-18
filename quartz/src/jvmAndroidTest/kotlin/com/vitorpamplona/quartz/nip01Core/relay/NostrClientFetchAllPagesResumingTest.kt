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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PageRetryBackoff
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PagedFetchResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPagesResuming
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * relay.ditto.pub, measured exporting its 3M kind 3s: it hangs up every few hundred thousand
 * events, and the next page then cannot be asked, so a walk ended `CANNOT_CONNECT` with most of
 * the corpus below it; once it answered a deep page empty, and the walk reported the end of the
 * corpus with 1.58M events left. A resuming walk asks again from where it got to.
 *
 * The backoffs' sleeps are recorded, never spent. A relay serving 100-event pages walks the
 * 2,000-event corpus in 21 pages, deep enough to be confirmed; 500-event pages, in 5, are not.
 */
class NostrClientFetchAllPagesResumingTest {
    private val relay = RelayUrlNormalizer.normalize("wss://hangs-up.example.com")
    private val corpus = FakePagingRelay.corpus(2_000)
    private val all = Filter(kinds = listOf(1))

    private val waits: MutableList<Long> = Collections.synchronizedList(mutableListOf())
    private val resume = PageRetryBackoff(PageRetryBackoff.RESUME.delaysMs) { waits.add(it) }
    private val noThrottleReAsks = PageRetryBackoff(emptyList())

    private suspend fun walk(
        client: FakePagingRelay,
        vararg filters: Filter = arrayOf(all),
    ): Pair<PagedFetchResult, List<HexKey>> {
        val got = Collections.synchronizedList(mutableListOf<HexKey>())
        val result = client.fetchAllPagesResuming(relay, filters.toList(), idleTimeoutMs = 2_000, throttleBackoff = noThrottleReAsks, resumeBackoff = resume) { got.add(it.id) }
        return result to got
    }

    /** How many REQs a plain [fetchAllPages] spends on [all] with [maxLimit]-event pages. */
    private suspend fun CoroutineScope.plainRequests(maxLimit: Int): Int {
        val plain = FakePagingRelay(this, corpus, maxLimit = maxLimit)
        plain.fetchAllPages(relay, listOf(all), idleTimeoutMs = 2_000, throttleBackoff = noThrottleReAsks) { }
        return plain.requests.size
    }

    @Test
    fun aConnectionThatComesBackIsReadToTheEnd() =
        runBlocking {
            // REQs 3 and 4 meet a socket that is down; the fifth gets through.
            val client = FakePagingRelay(this, corpus, maxLimit = 100, dropAfter = { req -> if (req == 3 || req == 4) 0 else null })
            val (result, got) = walk(client)

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(corpus.size, result.downloaded)
            assertEquals(corpus.map { it.id }.toSet(), got.toSet())
            assertEquals(got.size, got.toSet().size, "nothing delivered twice across a resume")
            // Two re-asks for the drop, then one to confirm the end.
            assertEquals(listOf(2_000L, 5_000L, 2_000L), waits)
        }

    @Test
    fun aConnectionThatNeverComesBackEndsAfterTheBackoff() =
        runBlocking {
            val client = FakePagingRelay(this, corpus, maxLimit = 500, dropAfter = { req -> if (req >= 3) 0 else null })
            val (result, got) = walk(client)

            // Two pages of 500, the second repeating the first's boundary event: `until` is inclusive.
            assertEquals(PagedFetchResult.End.CANNOT_CONNECT, result.end)
            assertEquals(999, result.downloaded)
            assertEquals(999, got.size)
            assertEquals(PageRetryBackoff.RESUME.delaysMs, waits, "bounded re-asks, never a loop")
        }

    @Test
    fun anEmptyAnswerAboveTheBottomIsCheckedOnce() =
        runBlocking {
            // REQ 12 comes back empty though 900 events remain below its cursor.
            val client = FakePagingRelay(this, corpus, maxLimit = 100, answer = { req, honest -> if (req == 12) emptyList() else honest })
            val (result, got) = walk(client)

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(corpus.size, got.toSet().size)
            assertEquals(got.size, got.toSet().size)
            assertEquals(listOf(2_000L, 2_000L), waits, "the early end, then the real one")
        }

    @Test
    fun aLimitHoldsAcrossAResume() =
        runBlocking {
            val client = FakePagingRelay(this, corpus, maxLimit = 500, dropAfter = { req -> if (req == 2) 0 else null })
            val (result, got) = walk(client, all.copy(limit = 700))

            assertEquals(PagedFetchResult.End.LIMIT_REACHED, result.end)
            assertEquals(700, got.size)
            assertEquals(corpus.take(700).map { it.id }, got)
        }

    @Test
    fun anHonestRelayCostsOneConfirmingWalk() =
        runBlocking {
            val plain = plainRequests(maxLimit = 100)
            val client = FakePagingRelay(this, corpus, maxLimit = 100)
            val (result, got) = walk(client)

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(corpus.size, got.size)
            assertEquals(listOf(2_000L), waits)
            // The confirmation reads the bottom second, reads it again to see nothing new, then steps
            // past it to an empty page.
            assertEquals(plain + 3, client.requests.size)
            assertEquals(corpus.last().createdAt, client.requests[plain].single().until)
        }

    @Test
    fun aShallowWalkIsNotConfirmed() =
        runBlocking {
            val plain = plainRequests(maxLimit = 500)
            val client = FakePagingRelay(this, corpus, maxLimit = 500)
            val (result, got) = walk(client)

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(corpus.size, got.size)
            assertEquals(emptyList(), waits)
            assertEquals(plain, client.requests.size)
        }

    @Test
    fun aConfirmationThatCannotConnectKeepsTheDrainedEnd() =
        runBlocking {
            val plain = plainRequests(maxLimit = 100)
            val client = FakePagingRelay(this, corpus, maxLimit = 100, dropAfter = { req -> if (req > plain) 0 else null })
            val (result, got) = walk(client)

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(corpus.size, result.downloaded)
            assertEquals(corpus.size, got.size)
            assertEquals(listOf(2_000L), waits, "one confirmation, not a backoff")
        }

    @Test
    fun aRelayDownFromTheStartIsAskedOnceMore() =
        runBlocking {
            val client = FakePagingRelay(this, corpus, maxLimit = 500, dropAfter = { 0 })
            val (result, got) = walk(client)

            assertEquals(PagedFetchResult.End.CANNOT_CONNECT, result.end)
            assertEquals(0, got.size)
            assertEquals(listOf(2_000L), waits, "one delay, not the whole backoff")
            assertEquals(2, client.requests.size)
        }

    @Test
    fun aRelayThatHangsUpBeforeTheFirstEventIsReadOnTheNextConnection() =
        runBlocking {
            val client = FakePagingRelay(this, corpus, maxLimit = 500, dropAfter = { req -> if (req == 1) 0 else null })
            val (result, got) = walk(client)

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(corpus.map { it.id }.toSet(), got.toSet())
            assertEquals(got.size, got.toSet().size)
            assertEquals(listOf(2_000L), waits)
        }

    @Test
    fun aRelaySilentFromTheStartIsNotWaitedOn() =
        runBlocking {
            val client = FakePagingRelay(this, corpus, maxLimit = 500, silentOn = { true })
            val (result, got) = walk(client)

            assertEquals(PagedFetchResult.End.IDLE, result.end)
            assertEquals(0, got.size)
            assertEquals(emptyList(), waits)
            assertEquals(1, client.requests.size)
        }

    @Test
    fun eachFilterResumesFromItsOwnCursor() =
        runBlocking {
            // Two overlapping filters: the older 1,000 events, and the newest 1,500. The first
            // walk's connection drops after one page, inside the second filter's range too: a
            // cursor shared between them would resume the second filter at the first's bottom
            // and never ask for its newest 1,000.
            val older = all.copy(until = corpus[1_000].createdAt)
            val newer = all.copy(since = corpus[1_499].createdAt)
            val client = FakePagingRelay(this, corpus, maxLimit = 500, dropAfter = { req -> if (req == 2) 0 else null })
            val (result, got) = walk(client, older, newer)

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(corpus.map { it.id }.toSet(), got.toSet())
            assertEquals(got.size, got.toSet().size, "the overlap delivered once")
            assertEquals(corpus.size, result.downloaded)
        }
}
