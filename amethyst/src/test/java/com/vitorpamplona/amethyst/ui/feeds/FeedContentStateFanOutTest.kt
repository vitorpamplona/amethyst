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
package com.vitorpamplona.amethyst.ui.feeds

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.feeds.FeedUpdateMeter
import com.vitorpamplona.amethyst.commons.feeds.FeedUpdateOutcome
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the per-bundle behaviour of [FeedContentState.refreshFromOldState] — the
 * function every arriving event bundle runs once per feed, ~48 times over on
 * Android.
 *
 * Two things are under test and they pull in opposite directions: the
 * short-circuit that skips list work a filter cannot possibly have changed, and
 * the deletion path, which must keep working precisely because it is the case
 * where the "nothing matched" shortcut does NOT apply.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedContentStateFanOutTest {
    private val outcomes = mutableListOf<FeedUpdateOutcome>()
    private var fanOuts = 0

    private fun installMeter() {
        FeedUpdateMeter.instance =
            object : FeedUpdateMeter {
                override fun onFeedUpdate(outcome: FeedUpdateOutcome) {
                    outcomes += outcome
                }

                override fun onBundleFanOut(
                    noteCount: Int,
                    elapsedNanos: Long,
                ) {
                    fanOuts++
                }
            }
    }

    @After
    fun tearDown() {
        FeedUpdateMeter.instance = null
    }

    private fun note(id: String) = Note(id.padStart(64, '0'))

    private fun deletion() =
        note("dead").also {
            it.event =
                DeletionEvent(
                    id = "d".repeat(64),
                    pubKey = "a".repeat(64),
                    createdAt = 1,
                    tags = emptyArray(),
                    content = "",
                    sig = "s".repeat(128),
                )
        }

    /** Matches whatever [matches] returns; [top] is what a full rebuild would load. */
    private class FakeFilter(
        var top: List<Note> = emptyList(),
        var matches: (Set<Note>) -> Set<Note> = { emptySet() },
    ) : AdditiveFeedFilter<Note>() {
        var loadTopCalls = 0

        override fun feed(): List<Note> {
            loadTopCalls++
            return top
        }

        override fun feedKey(): Any = "fixed"

        override fun applyFilter(newItems: Set<Note>): Set<Note> = matches(newItems)

        override fun sort(items: Set<Note>): List<Note> = items.sortedBy { it.idHex }
    }

    private fun cacheProvider(deleted: Set<Event> = emptySet()): ICacheProvider =
        mockk<ICacheProvider>(relaxed = true).also {
            every { it.hasBeenDeleted(any<Event>()) } answers { firstArg<Event>() in deleted }
        }

    private fun loadedState(
        filter: FakeFilter,
        scope: kotlinx.coroutines.CoroutineScope,
        cache: ICacheProvider = cacheProvider(),
    ): FeedContentState {
        val state = FeedContentState(filter, scope, cache)
        state.refreshSuspended()
        assertTrue("fixture should start Loaded", state.feedContent.value is FeedState.Loaded)
        outcomes.clear()
        return state
    }

    /**
     * The hot case: a bundle that this feed's filter does not want. Nothing may
     * be allocated, compared, or re-emitted — with ~48 feeds and roughly a
     * bundle a second, this is the path that runs thousands of times an hour,
     * screen on or off.
     */
    @Test
    fun aBundleThatMatchesNothingDoesNoListWork() =
        runTest {
            installMeter()
            val filter = FakeFilter(top = listOf(note("1"), note("2")))
            val state = loadedState(filter, backgroundScope)

            val before = (state.feedContent.value as FeedState.Loaded).feed.value
            state.refreshFromOldState(setOf(note("99")))
            val after = (state.feedContent.value as FeedState.Loaded).feed.value

            assertEquals(listOf(FeedUpdateOutcome.SKIPPED), outcomes)
            // Same LoadedFeedState instance: no new list was built and nothing
            // downstream was asked to recompose.
            assertSame(before, after)
        }

    @Test
    fun aMatchingBundleUpdatesTheFeed() =
        runTest {
            installMeter()
            val filter = FakeFilter(top = listOf(note("1")))
            val state = loadedState(filter, backgroundScope)

            val added = note("2")
            filter.matches = { setOf(added) }
            state.refreshFromOldState(setOf(added))

            assertEquals(listOf(FeedUpdateOutcome.CHANGED), outcomes)
            val list = (state.feedContent.value as FeedState.Loaded).feed.value.list
            assertEquals(listOf(note("1").idHex, added.idHex), list.map { it.idHex })
        }

    /** A filter that re-matches a row already on screen did real work for nothing. */
    @Test
    fun reMatchingAnExistingRowCountsAsUnchanged() =
        runTest {
            installMeter()
            val existing = note("1")
            val filter = FakeFilter(top = listOf(existing))
            val state = loadedState(filter, backgroundScope)

            filter.matches = { setOf(existing) }
            state.refreshFromOldState(setOf(existing))

            assertEquals(listOf(FeedUpdateOutcome.UNCHANGED), outcomes)
        }

    /**
     * The regression this short-circuit could plausibly cause. A deletion in the
     * bundle rebuilds the old list before the filter sees it, so the result is
     * never the on-screen instance and the skip must NOT trigger — otherwise a
     * deleted note would stay visible until the next full rebuild.
     */
    @Test
    fun aDeletionStillPrunesTheFeedEvenWhenTheFilterMatchesNothing() =
        runTest {
            installMeter()
            val doomed = note("1")
            val survivor = note("2")
            doomed.event =
                DeletionEvent(
                    id = doomed.idHex,
                    pubKey = "a".repeat(64),
                    createdAt = 1,
                    tags = emptyArray(),
                    content = "",
                    sig = "s".repeat(128),
                )
            val filter = FakeFilter(top = listOf(doomed, survivor))
            val state = loadedState(filter, backgroundScope, cacheProvider(deleted = setOf(doomed.event!!)))

            // The filter matches nothing; only the deletion sweep may change the list.
            state.refreshFromOldState(setOf(deletion()))

            assertEquals(listOf(FeedUpdateOutcome.CHANGED), outcomes)
            val list = (state.feedContent.value as FeedState.Loaded).feed.value.list
            assertEquals(listOf(survivor.idHex), list.map { it.idHex })
        }

    /**
     * A feed that has never been opened is still in [FeedState.Loading], which
     * fails the additive guard and rebuilds from a full cache scan — the
     * expensive outcome the ledger needs to be able to count.
     */
    @Test
    fun aFeedThatWasNeverOpenedRebuildsFromScratch() =
        runTest {
            installMeter()
            val filter = FakeFilter(top = listOf(note("1")))
            val state = FeedContentState(filter, backgroundScope, cacheProvider())
            assertTrue(state.feedContent.value is FeedState.Loading)

            state.refreshFromOldState(setOf(note("9")))

            assertEquals(listOf(FeedUpdateOutcome.REBUILT), outcomes)
            assertEquals(1, filter.loadTopCalls)
        }

    /** No meter installed (desktop, CLI, tests) must change nothing. */
    @Test
    fun worksWithNoMeterInstalled() =
        runTest {
            FeedUpdateMeter.instance = null
            val filter = FakeFilter(top = listOf(note("1")))
            val state = loadedState(filter, backgroundScope)

            state.refreshFromOldState(setOf(note("99")))

            assertTrue(state.feedContent.value is FeedState.Loaded)
            assertTrue(outcomes.isEmpty())
        }
}
