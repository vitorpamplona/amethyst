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
package com.vitorpamplona.amethyst.commons.feeds

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.StubCache
import com.vitorpamplona.amethyst.commons.notifications.CardFeedContentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The account-wide feeds are lazy: nothing is built, and new events are skipped, until a screen
 * shows the feed or a subscription that fills it wakes it. Before, the first event bundle built
 * all ~60 of them by scanning the cache, and every bundle after updated them all.
 */
class LazyFeedContentStateTest {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val a = Note("a".repeat(64))
    private val b = Note("b".repeat(64))

    @AfterTest
    fun tearDown() = scope.cancel()

    /** Shows whatever the "cache" holds and counts how often it was scanned. */
    private class CountingFilter(
        var all: List<Note>,
    ) : AdditiveFeedFilter<Note>() {
        val scans = AtomicInteger(0)

        /** Notes the filter now rejects, as a report or a mute would make it. */
        var rejected = emptySet<Note>()

        override fun feedKey(): Any = "feed"

        override fun feed(): List<Note> {
            scans.incrementAndGet()
            return all
        }

        override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems - rejected

        override fun sort(items: Set<Note>): List<Note> = items.sortedBy { it.idHex }
    }

    private fun waitUntil(
        what: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out waiting for $what")
            Thread.sleep(20)
        }
    }

    @Test
    fun aDormantFeedIgnoresEventsAndInvalidations() {
        val filter = CountingFilter(listOf(a))
        val state = FeedContentState(filter, scope, StubCache(), lazy = true)

        state.updateFeedWith(setOf(b))
        state.invalidateData()
        state.checkKeysInvalidateDataAndSendToTop()
        // Longer than the bundlers' 250 ms, so a build would have happened by now.
        Thread.sleep(800)

        assertFalse(state.isActive())
        assertIs<FeedState.Loading>(state.feedContent.value)
        assertEquals(0, filter.scans.get(), "a dormant feed scanned the cache")
    }

    @Test
    fun activatingBuildsFromTheCacheAndThenTakesNewEvents() {
        val filter = CountingFilter(listOf(a))
        val state = FeedContentState(filter, scope, StubCache(), lazy = true)

        state.activate()
        waitUntil("the first build") { state.visibleNotes() == listOf(a) }

        state.updateFeedWith(setOf(b))
        waitUntil("the new event") { state.visibleNotes() == listOf(a, b) }

        // Idempotent: waking it again does not rebuild.
        val scans = filter.scans.get()
        state.activate()
        Thread.sleep(500)
        assertEquals(scans, filter.scans.get())
    }

    @Test
    fun aScreenCollectingTheFeedWakesIt() {
        val filter = CountingFilter(listOf(a))
        val state = FeedContentState(filter, scope, StubCache(), lazy = true)

        runBlocking {
            withTimeout(5_000) { state.feedContent.first { it is FeedState.Loaded } }
        }

        assertTrue(state.isActive())
        assertEquals(listOf(a), state.visibleNotes())
    }

    @Test
    fun anEagerFeedStillBuildsOnTheFirstEvent() {
        val filter = CountingFilter(listOf(a))
        val state = FeedContentState(filter, scope, StubCache())

        state.updateFeedWith(setOf(b))
        waitUntil("the build") { state.visibleNotes() == listOf(a) }
    }

    @Test
    fun aDormantCardFeedIgnoresEventsUntilWoken() {
        val filter = CountingFilter(listOf(a))
        val state = CardFeedContentState(filter, scope, lazy = true)

        state.updateFeedWith(setOf(b))
        state.invalidateDataAndSendToTop(false)
        Thread.sleep(1_500) // the card bundlers wait 1 s

        assertIs<CardFeedState.Loading>(state.feedContent.value)
        assertEquals(0, filter.scans.get(), "a dormant card feed scanned the cache")

        state.activate()
        waitUntil("the card build") { state.visibleNotes().size == 1 }
    }

    /**
     * A notifications filter that shows nothing (a new account, a quiet list) is Empty, and Empty
     * used to send every event bundle to a full rebuild: a walk over the whole cache about once a
     * second. It now takes new events the additive way, like Loaded.
     */
    @Test
    fun anEmptyCardFeedTakesNewEventsWithoutRescanningTheCache() {
        val filter = CountingFilter(emptyList())
        val state = CardFeedContentState(filter, scope)

        state.invalidateData()
        waitUntil("the empty build") { state.feedContent.value is CardFeedState.Empty }
        assertEquals(1, filter.scans.get())

        // The note reaches the cache and arrives in the next bundle.
        filter.all = listOf(a)
        state.updateFeedWith(setOf(a))
        waitUntil("the new card") { state.visibleNotes().size == 1 }

        assertEquals(1, filter.scans.get(), "an event for an empty feed rescanned the cache")
    }

    /**
     * Home kept rebuilding on every return to the screen (four cache walks). A feed that stays
     * current on its own skips the rebuild once it is built, and is still built if it never was.
     */
    @Test
    fun aFeedKeptCurrentElsewhereDoesNotRebuildOnResume() {
        val filter = CountingFilter(listOf(a))
        val state = FeedContentState(filter, scope, StubCache(), refreshOnResume = false)

        state.onResume()
        waitUntil("the first build") { state.visibleNotes() == listOf(a) }
        assertEquals(1, filter.scans.get())

        state.onResume()
        state.onResume()
        Thread.sleep(800)
        assertEquals(1, filter.scans.get(), "a built feed rescanned the cache on resume")

        // The default still rebuilds, as every other screen expects.
        val everyTime = CountingFilter(listOf(a))
        val other = FeedContentState(everyTime, scope, StubCache())
        other.onResume()
        waitUntil("the default build") { other.visibleNotes() == listOf(a) }
        // ignoreIfDoing: a resume within the bundler's window of the last build is dropped.
        Thread.sleep(800)
        other.onResume()
        waitUntil("the default rebuild") { everyTime.scans.get() == 2 }
    }

    /** A report or a mute only removes: the shown list is checked again, the cache is not walked. */
    @Test
    fun refilteringDropsWhatNoLongerPassesWithoutRescanning() {
        val filter = CountingFilter(listOf(a, b))
        val state = FeedContentState(filter, scope, StubCache())
        state.invalidateData()
        waitUntil("the build") { state.visibleNotes() == listOf(a, b) }
        val scans = filter.scans.get()

        filter.rejected = setOf(b)
        state.refilterShown()

        assertEquals(listOf(a), state.visibleNotes())
        assertEquals(scans, filter.scans.get(), "refiltering walked the cache")
    }
}
