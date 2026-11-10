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

import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Account feeds that do not rebuild each time their screen comes back (built with
 * `refreshOnResume = false`), because this keeps them current instead. Every event bundle
 * already reaches them; what that misses, and a rebuild on resume used to catch, is handled here:
 *
 * - [inputs]: what the feeds read besides the events (the people or topics the selected list
 *   resolves to, settings). Any change rebuilds them, settled for 2 s since follow edits come in
 *   bursts.
 * - [hidden]: mutes and spam flags. A change that only hides more (a mute, a flagged spammer) can
 *   only remove notes, so the shown notes are checked again ([FeedContentState.refilterShown])
 *   without walking the cache; anything else (an unmute) rebuilds. Spam flags arrive in waves.
 * - Time: a note dated more than a minute ahead is hidden until then
 *   (`FilterByListParams.isNotInTheFuture`) and nothing new arrives when its time comes, so the
 *   feeds rebuild once at the earliest such moment ([rebuildWhenVisible]).
 */
class FeedsKeptCurrent(
    private val scope: CoroutineScope,
    private val feeds: List<FeedContentState>,
    inputs: List<Flow<*>>,
    hidden: StateFlow<LiveHiddenUsers>,
) {
    init {
        scope.launch(Dispatchers.IO) {
            @OptIn(FlowPreview::class)
            combine(inputs) { }
                .drop(1)
                .debounce(2_000)
                .collect { rebuild() }
        }
        scope.launch(Dispatchers.IO) {
            var applied = hidden.value
            @OptIn(FlowPreview::class)
            hidden
                .drop(1)
                .debounce(2_000)
                .collect { now ->
                    if (now.onlyHidesMoreThan(applied)) refilter() else rebuild()
                    applied = now
                }
        }
    }

    fun rebuild() = feeds.forEach { it.invalidateData() }

    private fun refilter() = feeds.forEach { it.refilterShown() }

    private val lock = KmpLock()
    private var nextVisibleAt: Long = Long.MAX_VALUE
    private var visibleJob: Job? = null

    /** Rebuilds once when a note created at [createdAt] (seconds) stops being in the future. */
    fun rebuildWhenVisible(createdAt: Long) {
        lock.withLock {
            if (createdAt >= nextVisibleAt) return
            nextVisibleAt = createdAt
            visibleJob?.cancel()
            visibleJob =
                scope.launch(Dispatchers.IO) {
                    // isNotInTheFuture allows a minute of skew; one more second to be past it.
                    delay((createdAt - TimeUtils.oneMinuteFromNow() + 1) * 1000)
                    lock.withLock { nextVisibleAt = Long.MAX_VALUE }
                    rebuild()
                }
        }
    }

    companion object {
        /** Whether [this] hides everything [before] did, and only adds to it. */
        fun LiveHiddenUsers.onlyHidesMoreThan(before: LiveHiddenUsers): Boolean =
            showSensitiveContent == before.showSensitiveContent &&
                maxHashtagLimit == before.maxHashtagLimit &&
                hiddenUsers.containsAll(before.hiddenUsers) &&
                spammers.containsAll(before.spammers) &&
                hiddenWords.containsAll(before.hiddenWords) &&
                mutedThreads.containsAll(before.mutedThreads) &&
                hiddenHashtags.containsAll(before.hiddenHashtags)

        /**
         * The earliest creation time in [notes] that is hidden as being in the future, within a
         * day (further ahead is not clock skew), or null.
         */
        fun earliestHiddenFuture(notes: Set<Note>): Long? {
            val hiddenUntil = TimeUtils.oneMinuteFromNow()
            val horizon = TimeUtils.now() + TimeUtils.ONE_DAY
            var earliest = Long.MAX_VALUE
            notes.forEach { note ->
                val createdAt = note.createdAt() ?: return@forEach
                if (createdAt in (hiddenUntil + 1)..horizon && createdAt < earliest) earliest = createdAt
            }
            return earliest.takeIf { it != Long.MAX_VALUE }
        }
    }
}
