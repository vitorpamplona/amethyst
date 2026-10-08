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
import kotlin.test.assertTrue

/**
 * relay.damus.io, paged quickly on one connection, starts answering with 3-event pages and
 * then with an EMPTY page for a range that holds ~490 events — a fresh connection asking the
 * same `until` gets them. Taken at its word, that empty EOSE is [PagedFetchResult.End.DRAINED]
 * after ~700 of ~250k events. These tests play that relay and pin the bounded re-ask, and that
 * a relay paging normally never pays for it.
 *
 * The backoff's sleep is recorded, never spent, so nothing here waits on the clock.
 */
class NostrClientFetchAllPagesThrottleTest {
    private val relay = RelayUrlNormalizer.normalize("wss://throttled.example.com")
    private val unbounded = listOf(Filter(kinds = listOf(1)))

    /** A [PageRetryBackoff] with the default delays that records each wait instead of sleeping. */
    private class RecordingBackoff(
        delaysMs: List<Long> = PageRetryBackoff.DEFAULT.delaysMs,
    ) {
        val waits: MutableList<Long> = Collections.synchronizedList(mutableListOf())
        val backoff = PageRetryBackoff(delaysMs) { waits.add(it) }
    }

    @Test
    fun aShortPageThenAnEmptyOneIsReAskedAndTheWalkFinishes() =
        runBlocking {
            val corpus = FakePagingRelay.corpus(3_000)
            val client =
                FakePagingRelay(this, corpus, maxLimit = 500) { req, honest ->
                    when (req) {
                        3 -> honest.take(3) // the relay starts throttling: a 3-event page
                        4 -> emptyList() // ...then nothing, for a range that holds plenty
                        else -> honest // and after a pause it answers again
                    }
                }
            val recorder = RecordingBackoff()
            val got = mutableListOf<HexKey>()

            val result = client.fetchAllPages(relay, unbounded, idleTimeoutMs = 2_000, throttleBackoff = recorder.backoff) { got.add(it.id) }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(3_000, result.downloaded, "nothing was lost to the throttled page")
            assertEquals(3_000, got.distinct().size)
            assertEquals(listOf(2_000L), recorder.waits, "one pause, then the same cursor was asked again")
            assertEquals(client.requests[3].single().until, client.requests[4].single().until, "the re-ask repeats the throttled page's cursor")
        }

    @Test
    fun aRelayThatStaysEmptyIsBelievedAfterTheBoundedReAsks() =
        runBlocking {
            val client =
                FakePagingRelay(this, FakePagingRelay.corpus(3_000), maxLimit = 500) { req, honest ->
                    when {
                        req < 3 -> honest
                        req == 3 -> honest.take(3)
                        else -> emptyList()
                    }
                }
            val recorder = RecordingBackoff()

            val result = client.fetchAllPages(relay, unbounded, idleTimeoutMs = 2_000, throttleBackoff = recorder.backoff) { }

            // 500 + 499 new on page two (one is the boundary repeat) + 2 new of the 3.
            assertEquals(1_001, result.downloaded)
            assertEquals(PagedFetchResult.End.DRAINED, result.end, "the walk ends exactly as it would have, just later")
            assertEquals(listOf(2_000L, 5_000L, 10_000L), recorder.waits, "growing waits, and no more of them than the backoff allows")
            assertEquals(3 + 1 + 3, client.requests.size, "the empty page, then one re-ask per delay — never a loop")
        }

    @Test
    fun twoShortPagesThenAnEmptyPageBelowAStepAreReAsked() =
        runBlocking {
            // The other shape: the throttled pages are short twice in a row, the next only
            // repeats the boundary, and the page after comes back empty. Nothing contradicts the empty page itself — it asked
            // below anything the relay served — but a normal walk never has two short pages.
            val client =
                FakePagingRelay(this, FakePagingRelay.corpus(2_000), maxLimit = 500) { req, honest ->
                    when (req) {
                        2, 3 -> honest.take(3)
                        4 -> honest.take(1) // just the boundary repeat
                        5 -> emptyList()
                        else -> honest
                    }
                }
            val recorder = RecordingBackoff()

            val result = client.fetchAllPages(relay, unbounded, idleTimeoutMs = 2_000, throttleBackoff = recorder.backoff) { }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(2_000, result.downloaded)
            // A pause before the REQ after the second short page; then the boundary-repeat-only
            // page is re-asked rather than stepped past (it could have dropped the rest of that
            // second), and the empty answer to that re-ask is re-asked too.
            assertEquals(listOf(2_000L, 2_000L, 5_000L), recorder.waits)
        }

    @Test
    fun aRelayPagingNormallyNeverWaits() =
        runBlocking {
            // Full pages, one short tail page, the boundary repeat, the step, the empty page:
            // the end of every honest walk. It must cost exactly what it always did.
            val client = FakePagingRelay(this, FakePagingRelay.corpus(1_234), maxLimit = 500)
            val recorder = RecordingBackoff()

            val result = client.fetchAllPages(relay, unbounded, idleTimeoutMs = 2_000, throttleBackoff = recorder.backoff) { }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(1_234, result.downloaded)
            assertTrue(recorder.waits.isEmpty(), "an honest relay never triggers a re-ask: ${recorder.waits}")
        }

    @Test
    fun anEmptyPageAfterAFullOneIsBelieved() =
        runBlocking {
            // No short page came first, so there is no evidence of throttling — the relay may
            // simply have lost the boundary event in between. Same answer as before.
            val client =
                FakePagingRelay(this, FakePagingRelay.corpus(3_000), maxLimit = 500) { req, honest ->
                    if (req == 1) honest else emptyList()
                }
            val recorder = RecordingBackoff()

            val result = client.fetchAllPages(relay, unbounded, idleTimeoutMs = 2_000, throttleBackoff = recorder.backoff) { }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(500, result.downloaded)
            assertTrue(recorder.waits.isEmpty())
            assertEquals(2, client.requests.size)
        }

    @Test
    fun noBackoffKeepsTheOldReading() =
        runBlocking {
            val client =
                FakePagingRelay(this, FakePagingRelay.corpus(3_000), maxLimit = 500) { req, honest ->
                    when {
                        req < 3 -> honest
                        req == 3 -> honest.take(3)
                        else -> emptyList()
                    }
                }

            val result = client.fetchAllPages(relay, unbounded, idleTimeoutMs = 2_000, throttleBackoff = PageRetryBackoff.NONE) { }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(1_001, result.downloaded)
            assertEquals(4, client.requests.size, "the empty page was taken at its word")
        }

    @Test
    fun theReAskBudgetIsPerStallNotPerWalk() =
        runBlocking {
            // Two separate stalls, each recovered on its first re-ask. Progress in between
            // restores the budget, so the second stall is not cut short by the first.
            val client =
                FakePagingRelay(this, FakePagingRelay.corpus(3_000), maxLimit = 500) { req, honest ->
                    when (req) {
                        2 -> honest.take(3)
                        3 -> emptyList()
                        5 -> honest.take(3)
                        6 -> emptyList()
                        else -> honest
                    }
                }
            val recorder = RecordingBackoff()

            val result = client.fetchAllPages(relay, unbounded, idleTimeoutMs = 2_000, throttleBackoff = recorder.backoff) { }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(3_000, result.downloaded)
            assertEquals(listOf(2_000L, 2_000L), recorder.waits, "each stall starts from the first, shortest wait")
        }

    // ---- the CLOSED form of the same throttle ------------------------------------

    private val budget = "ERROR: read bandwidth budget exhausted (1048570 bytes/min per IP) (retry in 30ms)"

    @Test
    fun shrinkingPagesThenABudgetCloseAreReAsked() =
        runBlocking {
            // relay.damus.io as measured: 500, 500, 131, 3, then a CLOSED about its read budget.
            val client =
                FakePagingRelay(
                    this,
                    FakePagingRelay.corpus(3_000),
                    maxLimit = 500,
                    closeWith = { req -> budget.takeIf { req == 5 } },
                ) { req, honest ->
                    when (req) {
                        3 -> honest.take(131)
                        4 -> honest.take(3)
                        else -> honest
                    }
                }
            val recorder = RecordingBackoff()

            val result = client.fetchAllPages(relay, unbounded, idleTimeoutMs = 2_000, throttleBackoff = recorder.backoff) { }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(3_000, result.downloaded)
            // A pause before the REQ after the second short page, then the CLOSED's re-ask.
            assertEquals(listOf(2_000L, 2_000L), recorder.waits)
        }

    @Test
    fun aBudgetCloseThatNeverLiftsStillEndsClosed() =
        runBlocking {
            val client =
                FakePagingRelay(
                    this,
                    FakePagingRelay.corpus(3_000),
                    maxLimit = 500,
                    closeWith = { req -> budget.takeIf { req >= 3 } },
                ) { req, honest -> if (req == 2) honest.take(3) else honest }
            val recorder = RecordingBackoff()

            val result = client.fetchAllPages(relay, unbounded, idleTimeoutMs = 2_000, throttleBackoff = recorder.backoff) { }

            assertEquals(PagedFetchResult.End.CLOSED, result.end, "a refusal stays a refusal — never a drain")
            assertEquals(budget, result.message)
            assertEquals(502, result.downloaded)
            assertEquals(listOf(2_000L, 5_000L, 10_000L), recorder.waits)
        }

    @Test
    fun aCloseWithoutShrinkingPagesIsNotWaitedOn() =
        runBlocking {
            // A policy refusal mid-walk, after full pages: nothing suggests time would help.
            val client =
                FakePagingRelay(
                    this,
                    FakePagingRelay.corpus(3_000),
                    maxLimit = 500,
                    closeWith = { req -> "blocked: too much".takeIf { req == 3 } },
                )
            val recorder = RecordingBackoff()

            val result = client.fetchAllPages(relay, unbounded, idleTimeoutMs = 2_000, throttleBackoff = recorder.backoff) { }

            assertEquals(PagedFetchResult.End.CLOSED, result.end)
            assertEquals(999, result.downloaded)
            assertTrue(recorder.waits.isEmpty())
            assertEquals(3, client.requests.size)
        }

    @Test
    fun pagesThatShrinkBecauseAFilterFinishedAreNotThrottling() =
        runBlocking {
            // A bounded filter (500) walked at the relay's max of 1000, then an unbounded one at
            // its default of 200. Against the first walk's page those are all "short", but nothing
            // is wrong: the walk must end without a single backoff.
            val client = FakePagingRelay(this, FakePagingRelay.corpus(1_500), maxLimit = 1_000, defaultLimit = 200)
            val recorder = RecordingBackoff()
            val filters = listOf(Filter(kinds = listOf(1), limit = 500), Filter(kinds = listOf(1)))

            val result = client.fetchAllPages(relay, filters, idleTimeoutMs = 2_000, throttleBackoff = recorder.backoff) { }

            // The bounded filter got its 500, so the walk reports that cap, not DRAINED.
            assertEquals(PagedFetchResult.End.LIMIT_REACHED, result.end)
            assertEquals(1_500, result.downloaded)
            assertEquals(emptyList(), recorder.waits, "a filter dropping out is not a throttled relay")
        }

    @Test
    fun pagesThatKeepComingBackShortArePacedNotHammered() =
        runBlocking {
            // relay.damus.io, measured: two full pages, then pages of 1-4 events every ~120 ms
            // for as long as the walk keeps asking; after a 2 s pause it served 500 again. This
            // relay trickles 3 events a page until the walk has paused since its last full page.
            val waits = Collections.synchronizedList(mutableListOf<Long>())
            var waitsAtLastFullPage = 0
            val client =
                FakePagingRelay(this, FakePagingRelay.corpus(3_000), maxLimit = 500) { req, honest ->
                    if (req <= 2 || waits.size > waitsAtLastFullPage) {
                        waitsAtLastFullPage = waits.size
                        honest
                    } else {
                        honest.take(3)
                    }
                }

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1))), idleTimeoutMs = 2_000, throttleBackoff = PageRetryBackoff(listOf(2_000L, 5_000L, 10_000L)) { waits.add(it) }) { }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(3_000, result.downloaded)
            assertTrue(waits.isNotEmpty(), "the walk paused")
            assertTrue(client.requests.size < 60, "paced, not ~1000 trickle REQs: ${client.requests.size}")
        }

    @Test
    fun aPageTheRelayLeavesUnansweredEndsTheWalkWithoutAWait() =
        runBlocking {
            // A page with no answer at all is not re-asked: each ask would cost a whole idle
            // window, and a dead relay is silent too. (The silent pages measured mid-walk were
            // the walk's own dropped connection, fixed in NostrClientFetchAllPagesConnectionTest.)
            val recorder = RecordingBackoff()
            val client = FakePagingRelay(this, FakePagingRelay.corpus(1_500), maxLimit = 500, silentOn = { it == 2 })

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1))), idleTimeoutMs = 300, throttleBackoff = recorder.backoff) { }

            assertEquals(PagedFetchResult.End.IDLE, result.end)
            assertEquals(500, result.downloaded)
            assertEquals(emptyList(), recorder.waits)
            assertEquals(2, client.requests.size)
        }

    @Test
    fun aSilentFirstPageIsNotWaitedOn() =
        runBlocking {
            // Nothing was ever served, so silence is not a throttle reading: no re-ask.
            val recorder = RecordingBackoff()
            val client = FakePagingRelay(this, FakePagingRelay.corpus(100), silentOn = { true })

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1))), idleTimeoutMs = 300, throttleBackoff = recorder.backoff) { }

            assertEquals(PagedFetchResult.End.IDLE, result.end)
            assertEquals(emptyList(), recorder.waits)
        }

    @Test
    fun twoFiltersPagingNormallyNeverWait() =
        runBlocking {
            // A walk with two unbounded filters (EventSync's authors=me + #p=me) where one runs
            // out early: every later page holds only the other filter's events, about half of
            // page one. That is an honest relay, not a trickle, so it must never pace.
            val notes = FakePagingRelay.corpus(5_000)
            val reactions = reactions(500, everySeconds = 1)
            val client = FakePagingRelay(this, notes + reactions, maxLimit = 500)
            val recorder = RecordingBackoff()

            val result =
                client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1)), Filter(kinds = listOf(7))), idleTimeoutMs = 2_000, throttleBackoff = recorder.backoff) { }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(5_500, result.downloaded)
            assertTrue(recorder.waits.isEmpty(), "an honest relay never pays a pause: ${recorder.waits}")
        }

    @Test
    fun twoFiltersOnATricklingRelayAreStillPaced() =
        runBlocking {
            // Both filters keep delivering, but only a few events a page after two full ones:
            // the damus trickle with a second filter. The same filters stay productive, so the
            // evidence holds and the walk pauses instead of hammering the relay.
            val notes = FakePagingRelay.corpus(1_000)
            lateinit var client: FakePagingRelay
            client =
                FakePagingRelay(this, notes + reactions(1_000, everySeconds = 1), maxLimit = 500) { req, honest ->
                    if (req <= 2) {
                        honest
                    } else {
                        // Past the boundary second (its events were delivered already): four events.
                        val until = client.requests[req - 1].first().until
                        honest.filter { until == null || it.createdAt < until }.take(4)
                    }
                }
            val recorder = RecordingBackoff()

            val result =
                client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1)), Filter(kinds = listOf(7))), idleTimeoutMs = 2_000, throttleBackoff = recorder.backoff) { }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(2_000, result.downloaded, "pacing slows the walk, it never drops events")
            assertTrue(recorder.waits.isNotEmpty(), "a trickle across two filters must still be paced")
            assertEquals(2_000L, recorder.waits.first())
        }

    /** [count] kind-7 events, one every [everySeconds] seconds from the corpus' newest second, ids apart from the notes'. */
    private fun reactions(
        count: Int,
        everySeconds: Int,
    ): List<Event> =
        (0 until count).map { i ->
            val createdAt = 1_000_000L - i * everySeconds
            Event(
                id = "7" + createdAt.toString(16).padStart(63, '0'),
                pubKey = "f".repeat(64),
                createdAt = createdAt,
                kind = 7,
                tags = emptyArray(),
                content = "+",
                sig = "0".repeat(128),
            )
        }

    @Test
    fun aCloseThatSaysItIsThrottlingIsReAskedEvenWithoutShrinkingPages() =
        runBlocking {
            // damus.bostr.online, measured: a proxy sharing damus's per-IP read budget closed a
            // walk with `read bandwidth budget exhausted ... (retry in 30ms)` right after its
            // largest page, so there was no shrinking to read it by, and the walk ended there.
            val recorder = RecordingBackoff()
            val client = FakePagingRelay(this, FakePagingRelay.corpus(1_500), maxLimit = 500, closeWith = { req -> budget.takeIf { req == 2 } })

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1))), idleTimeoutMs = 2_000, throttleBackoff = recorder.backoff) { }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(1_500, result.downloaded)
            assertEquals(listOf(2_000L), recorder.waits)
        }

    @Test
    fun aFirstPageSentTwiceDoesNotMakeHonestPagesLookShort() =
        runBlocking {
            // relay.jmoose.rocks, measured: 1002 events for a 500-event first page, then honest
            // pages of 500. Counted with the repeats, the first page set a "largest page" of
            // 1002 that every later page fell short of, and the walk paused seven times.
            val recorder = RecordingBackoff()
            val client = FakePagingRelay(this, FakePagingRelay.corpus(3_000), maxLimit = 500) { req, honest -> if (req == 1) honest + honest else honest }

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1))), idleTimeoutMs = 2_000, throttleBackoff = recorder.backoff) { }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(3_000, result.downloaded)
            assertEquals(emptyList(), recorder.waits, "honest pages are not throttling")
        }

    @Test
    fun anEmptyPageAtTheSinceFloorIsTheEndNotAThrottle() =
        runBlocking {
            // wheat.happytavern.co (grain), measured: a walk bounded by `since` ends on a page
            // asking `until == since`, which this relay answers with nothing even though it just
            // served events in that second. Read as "the relay contradicts itself", the walk
            // re-asked three times (2 s, 5 s, 10 s) before believing the end it had reached.
            // 401 events: a page of 300, a short one of 101 down to `since`, then the floor.
            val since = 999_600L
            val recorder = RecordingBackoff()
            lateinit var client: FakePagingRelay
            client =
                FakePagingRelay(this, FakePagingRelay.corpus(1_000), maxLimit = 300) { req, honest ->
                    val filter = client.requests[req - 1].single()
                    if (filter.until != null && filter.until == filter.since) emptyList() else honest
                }

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1), since = since)), idleTimeoutMs = 2_000, throttleBackoff = recorder.backoff) { }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(401, result.downloaded)
            assertEquals(emptyList(), recorder.waits)
        }

    @Test
    fun aDuplicateOnlyPageWhileThrottledIsReAskedNotSteppedPast() =
        runBlocking {
            // relay.damus.io while trickling, measured: pages of 1-4 events, some of them only
            // the boundary second's already-delivered events. Read as "a second too dense to
            // page", the walk stepped past it and lost whatever of that second it had not seen.
            // Every second holds 3 events; after two full pages the relay trickles two short
            // pages, then answers with just the boundary's first (already delivered) event,
            // and serves honestly again once the walk has paused.
            val corpus =
                (0 until 900).map { i ->
                    val createdAt = 1_000_000L - i / 3
                    Event((i + 1).toString(16).padStart(64, '0'), "f".repeat(64), createdAt, 1, emptyArray(), "e$i", "0".repeat(128))
                }
            val waits = Collections.synchronizedList(mutableListOf<Long>())
            val client =
                FakePagingRelay(this, corpus, maxLimit = 300) { req, honest ->
                    when {
                        req <= 2 -> honest
                        req <= 4 -> honest.take(5)
                        req == 5 -> honest.take(1)
                        else -> honest
                    }
                }
            val got = mutableListOf<HexKey>()

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1))), idleTimeoutMs = 2_000, throttleBackoff = PageRetryBackoff(listOf(2_000L, 5_000L, 10_000L)) { waits.add(it) }) { got.add(it.id) }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(900, got.distinct().size, "no event of a boundary second was skipped")
            assertTrue(waits.isNotEmpty())
        }

    @Test
    fun aThrottleCloseOnABoundaryTopUpIsBackedOffNotReadAsALimitRefusal() =
        runBlocking {
            // A known cap (pageSize 300) and a second denser than it: the walk asks past the cap
            // to finish the second. A relay that then CLOSEs for throttling did not refuse the
            // larger limit; reading it as one gave up that second's tail, for this and every
            // later dense second, and skipped the backoff.
            val dense =
                FakePagingRelay.corpus(100, newest = 2_000) +
                    (0 until 400).map { i ->
                        Event(("d$i").padStart(64, '0'), "f".repeat(64), 1_000, 1, emptyArray(), "dense $i", "0".repeat(128))
                    } +
                    FakePagingRelay.corpus(100, newest = 900)
            val recorder = RecordingBackoff()
            val client = FakePagingRelay(this, dense, maxLimit = 5_000, closeWith = { req -> budget.takeIf { req == 3 } })
            val got = mutableListOf<HexKey>()

            val result = client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1), limit = 50_000)), idleTimeoutMs = 2_000, pageSize = 300, throttleBackoff = recorder.backoff) { got.add(it.id) }

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(600, got.distinct().size, "the dense second was read in full after the throttle lifted")
            assertEquals(listOf(2_000L), recorder.waits)
        }

    @Test
    fun aCursorIsAnnouncedOnceHoweverOftenItIsReAsked() =
        runBlocking {
            // A throttled cursor re-asked after a backoff is several REQs for one page, not
            // several pages: onNewPage feeds progress counters.
            val client =
                FakePagingRelay(this, FakePagingRelay.corpus(3_000), maxLimit = 500) { req, honest ->
                    when (req) {
                        2 -> honest.take(3)
                        3, 4 -> emptyList()
                        else -> honest
                    }
                }
            val announced = mutableListOf<Long>()

            client.fetchAllPages(relay, listOf(Filter(kinds = listOf(1))), idleTimeoutMs = 2_000, onNewPage = { announced.add(it) }, throttleBackoff = RecordingBackoff().backoff) { }

            assertTrue(client.requests.size > announced.distinct().size + 1, "re-asks happened: ${client.requests.size} REQs, ${announced.distinct().size} cursors")
            assertEquals(announced.distinct(), announced, "each cursor announced once: $announced")
        }
}
