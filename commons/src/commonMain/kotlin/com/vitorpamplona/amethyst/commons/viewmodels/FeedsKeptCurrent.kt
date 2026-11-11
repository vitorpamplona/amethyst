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
import com.vitorpamplona.amethyst.commons.feeds.UniverseFeedFilter
import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.relay.RelayTopNavFilter
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
 *   bursts. [list], the selected top-nav list, is one of them.
 * - [hidden]: mutes and spam flags. A change that only hides more (a mute, a flagged spammer) can
 *   only remove notes, so the shown notes are checked again ([FeedContentState.refilterShown])
 *   without walking the cache; anything else (an unmute) rebuilds. Spam flags arrive in waves.
 * - Time: a note dated more than a minute ahead is hidden until then
 *   (`FilterByListParams.isNotInTheFuture`) and nothing new arrives when its time comes, so the
 *   feeds rebuild once at each such moment ([rebuildWhenVisible]).
 * - Relays: with a relay picked as the list, a note qualifies by having been seen on it, and a
 *   cached note seen there again brings no new event. The account hands such notes over
 *   ([seenOnSelectedRelay]) and they join the feeds in small batches.
 */
class FeedsKeptCurrent(
    private val scope: CoroutineScope,
    private val feeds: List<FeedContentState>,
    inputs: List<Flow<*>>,
    hidden: StateFlow<LiveHiddenUsers>,
    /** The group's selected top-nav list, when it has one. */
    val list: StateFlow<IFeedTopNavFilter>? = null,
) {
    // What the feeds could ever show, when every one of them declares it: a future-dated note
    // outside it never needs a rebuild of this group.
    private val universes = feeds.map { (it.localFilter as? UniverseFeedFilter)?.universe }

    private fun couldShow(note: Note) = universes.any { it == null || it.matches(note) }

    init {
        scope.launch(Dispatchers.IO) {
            @OptIn(FlowPreview::class)
            combine(listOfNotNull(list) + inputs) { }
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

    /** The relay this group's list reads from, if a relay is picked. */
    fun selectedRelay(): NormalizedRelayUrl? = (list?.value as? RelayTopNavFilter)?.relayUrl

    private val seenLock = KmpLock()
    private var seenOnRelay = LinkedHashSet<Note>()
    private val seenSignal = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch(Dispatchers.IO) {
            for (signal in seenSignal) {
                // Duplicates arrive in bursts as a relay replays what it has: one pass per burst.
                delay(500)
                val batch = seenLock.withLock { seenOnRelay.also { seenOnRelay = LinkedHashSet() } }
                if (batch.isNotEmpty()) feeds.forEach { it.updateFeedWith(batch) }
            }
        }
    }

    /** [note], already cached, was just seen on [selectedRelay]: the feeds check it again. */
    fun seenOnSelectedRelay(note: Note) {
        if (note.event == null || !couldShow(note)) return
        seenLock.withLock { seenOnRelay.add(note) }
        seenSignal.trySend(Unit)
    }

    private val lock = KmpLock()

    // Creation times (seconds) of hidden future notes this group could show, earliest first,
    // capped: past the cap the latest are dropped, and are caught by the rebuild before them.
    private val pending = ArrayList<Long>()
    private var scheduledAt: Long = Long.MAX_VALUE
    private var visibleJob: Job? = null

    /** Rebuilds once each of [notes] (see [hiddenFuture]) stops being in the future. */
    fun rebuildWhenVisible(notes: List<Note>) {
        lock.withLock {
            notes.forEach { note ->
                if (!couldShow(note)) return@forEach
                val createdAt = note.createdAt() ?: return@forEach
                val at = pending.binarySearch(createdAt)
                if (at < 0) pending.add(-at - 1, createdAt)
            }
            while (pending.size > MAX_PENDING) pending.removeAt(pending.lastIndex)
            scheduleNextLocked()
        }
    }

    private fun scheduleNextLocked() {
        val next = pending.firstOrNull() ?: return
        if (next >= scheduledAt) return
        scheduledAt = next
        visibleJob?.cancel()
        visibleJob =
            scope.launch(Dispatchers.IO) {
                // isNotInTheFuture allows a minute of skew; one more second to be past it.
                delay((next - TimeUtils.oneMinuteFromNow() + 1) * 1000)
                lock.withLock {
                    val visibleUpTo = TimeUtils.oneMinuteFromNow()
                    pending.removeAll { it <= visibleUpTo }
                    scheduledAt = Long.MAX_VALUE
                    // This job is done: scheduling the next one must not cancel it before rebuild().
                    visibleJob = null
                    scheduleNextLocked()
                }
                rebuild()
            }
    }

    companion object {
        private const val MAX_PENDING = 64

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
         * The notes in [notes] that are hidden as being in the future, within a day (further ahead
         * is not clock skew). Usually none.
         */
        fun hiddenFuture(notes: Set<Note>): List<Note> {
            val hiddenUntil = TimeUtils.oneMinuteFromNow()
            val horizon = TimeUtils.now() + TimeUtils.ONE_DAY
            var found: ArrayList<Note>? = null
            notes.forEach { note ->
                val createdAt = note.createdAt() ?: return@forEach
                if (createdAt in (hiddenUntil + 1)..horizon) (found ?: ArrayList<Note>().also { found = it }).add(note)
            }
            return found ?: emptyList()
        }
    }
}
