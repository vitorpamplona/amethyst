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
package com.vitorpamplona.amethyst.commons.viewmodels

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.StubCache
import com.vitorpamplona.amethyst.commons.viewmodels.FeedsKeptCurrent.Companion.hiddenFuture
import com.vitorpamplona.amethyst.commons.viewmodels.FeedsKeptCurrent.Companion.onlyHidesMoreThan
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A mute or a flagged spammer can only remove notes, so the kept-current feeds check what they
 * show instead of walking the cache; anything that could bring notes back rebuilds them.
 */
class FeedsKeptCurrentTest {
    private fun hidden(
        users: Set<String> = emptySet(),
        spammers: Set<String> = emptySet(),
        threads: Set<String> = emptySet(),
        showSensitive: Boolean? = null,
    ) = LiveHiddenUsers(
        showSensitiveContent = showSensitive,
        hiddenWordsCase = emptyList(),
        hiddenUsersHashCodes = emptySet(),
        spammersHashCodes = emptySet(),
        hiddenUsers = users,
        spammers = spammers,
        mutedThreads = threads,
    )

    @Test
    fun aMuteOrASpamFlagOnlyHidesMore() {
        val before = hidden(users = setOf("a"))
        assertTrue(hidden(users = setOf("a", "b")).onlyHidesMoreThan(before))
        assertTrue(hidden(users = setOf("a"), spammers = setOf("s")).onlyHidesMoreThan(before))
        assertTrue(hidden(users = setOf("a"), threads = setOf("t")).onlyHidesMoreThan(before))
        assertTrue(before.onlyHidesMoreThan(before))
    }

    @Test
    fun anUnmuteOrASettingChangeNeedsARebuild() {
        val before = hidden(users = setOf("a", "b"), spammers = setOf("s"))
        assertFalse(hidden(users = setOf("a"), spammers = setOf("s")).onlyHidesMoreThan(before))
        assertFalse(hidden(users = setOf("a", "b")).onlyHidesMoreThan(before))
        assertFalse(hidden(users = setOf("a", "b"), spammers = setOf("s"), showSensitive = true).onlyHidesMoreThan(before))
    }

    private fun noteAt(
        id: Char,
        createdAt: Long,
    ) = Note(id.toString().repeat(64)).also { it.event = TextNoteEvent(it.idHex, "a".repeat(64), createdAt, emptyArray(), "hi", "") }

    @Test
    fun everyNoteStillInTheFutureIsFoundNotOnlyTheEarliest() {
        val now = TimeUtils.now()
        val soon = noteAt('1', now + 5 * 60)
        val later = noteAt('2', now + 10 * 60)
        val present = noteAt('3', now)
        val nextWeek = noteAt('4', now + 7 * TimeUtils.ONE_DAY)

        assertEquals(setOf(soon, later), hiddenFuture(setOf(soon, later, present, nextWeek)).toSet())
        assertTrue(hiddenFuture(setOf(present)).isEmpty())
    }

    /** Accepts every note that is not in the future; its cache has none of them. */
    private class FutureCheckingFilter : AdditiveFeedFilter<Note>() {
        val walks = AtomicInteger(0)

        override fun feedKey(): Any = "feed"

        override fun feed(): List<Note> {
            walks.incrementAndGet()
            return emptyList()
        }

        override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { (it.createdAt() ?: 0) <= TimeUtils.oneMinuteFromNow() }

        override fun sort(items: Set<Note>): List<Note> = items.sortedByDescending { it.createdAt() }
    }

    @Test
    fun aFutureNoteIsHeldAndHandedToTheFeedsWhenItsTimeComes() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val filter = FutureCheckingFilter()
            val feed = FeedContentState(filter, scope, StubCache())
            feed.invalidateData()
            waitUntil("the first build") { feed.feedContent.value is FeedState.Empty }
            val group = FeedsKeptCurrent(scope, listOf(feed), emptyList(), MutableStateFlow(hidden()))

            // Hidden for about two seconds more; the cache never had it, as after the GC took it.
            val future = noteAt('5', TimeUtils.oneMinuteFromNow() + 2)
            group.rebuildWhenVisible(listOf(future))

            waitUntil("the note to show") {
                (feed.feedContent.value as? FeedState.Loaded)
                    ?.feed
                    ?.value
                    ?.list
                    ?.contains(future) == true
            }
            assertEquals(1, filter.walks.get(), "handed over, not rebuilt from the cache")
        } finally {
            scope.cancel()
        }
    }

    private fun waitUntil(
        what: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out waiting for $what")
            Thread.sleep(20)
        }
    }
}
