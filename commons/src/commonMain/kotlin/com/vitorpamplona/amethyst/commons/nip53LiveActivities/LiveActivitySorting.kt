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
package com.vitorpamplona.amethyst.commons.nip53LiveActivities

import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Pure, CLI-safe ranking + freshness logic for NIP-53 live activities, shared by Amethyst Android
 * and Amethyst Desktop so both platforms order live streams identically (no drift).
 *
 * Everything here operates on **primitives / pre-snapshotted values** rather than reading mutable
 * event or online-checker state. This is deliberate: the sort keys (status-after-online-check and
 * `current_participants`) can be mutated by background work mid-sort, and reading them lazily inside
 * a comparator makes the ordering unstable — TimSort then throws
 * *"Comparison method violates its general contract!"*. Callers must snapshot the keys into a
 * [LiveActivityRank] once, before sorting.
 */
object LiveActivitySorting {
    /** Status ordering used across the discover feed, the live bar and profile streams. */
    const val ORDER_LIVE = 2
    const val ORDER_PLANNED = 1
    const val ORDER_ENDED = 0

    /** How recently a `status=live` 30311 must have been re-published to count for the live bar. */
    const val LIVE_BAR_FRESHNESS_SECONDS = 15 * 60L

    /** Grace after a planned `starts` before it is treated as "overdue / never went live". */
    const val OVERDUE_PLANNED_GRACE_SECONDS = 60 * 60L

    /**
     * Maps a live-activity status to a sort bucket. A `status=live` stream whose `.m3u8` is known to
     * be offline right now ([isOfflineNow]) is downgraded to [ORDER_ENDED] so dead streams sink.
     */
    fun statusOrder(
        status: StatusTag.STATUS?,
        isOfflineNow: Boolean = false,
    ): Int =
        when (status) {
            StatusTag.STATUS.LIVE -> if (isOfflineNow) ORDER_ENDED else ORDER_LIVE
            StatusTag.STATUS.PLANNED -> ORDER_PLANNED
            StatusTag.STATUS.ENDED -> ORDER_ENDED
            null -> ORDER_ENDED
        }

    /**
     * True when a stream should surface in the per-column "live now" bar: it is `status=live`, its
     * `.m3u8` is not known-offline, and the 30311 was (re)published within [freshnessSeconds]. A host
     * is expected to re-publish the 30311 continuously while live, so a stale one is a zombie.
     */
    fun isLiveAndFresh(
        status: StatusTag.STATUS?,
        createdAt: Long,
        now: Long = TimeUtils.now(),
        isOfflineNow: Boolean = false,
        freshnessSeconds: Long = LIVE_BAR_FRESHNESS_SECONDS,
    ): Boolean = status == StatusTag.STATUS.LIVE && !isOfflineNow && createdAt >= now - freshnessSeconds

    /**
     * True when a `status=planned` stream's start time has passed by more than the grace window and
     * it never flipped to live — used to relabel / downrank stale "starts in…" cards.
     */
    fun isOverduePlanned(
        status: StatusTag.STATUS?,
        startsAt: Long?,
        now: Long = TimeUtils.now(),
        graceSeconds: Long = OVERDUE_PLANNED_GRACE_SECONDS,
    ): Boolean = status == StatusTag.STATUS.PLANNED && startsAt != null && startsAt < now - graceSeconds

    /**
     * A pre-snapshotted set of comparator keys for one live activity. Build one per item **before**
     * sorting; never read live event/online state inside the comparator.
     */
    data class LiveActivityRank(
        val statusOrder: Int,
        val followParticipants: Int,
        val totalParticipants: Int,
        val startOrCreated: Long,
        val idHex: String,
    )

    /**
     * Descending comparator (best first): live > planned > ended, then more participating follows,
     * then more total participants, then more recent start/creation, then id for a stable tiebreak.
     */
    val RANK_DESCENDING: Comparator<LiveActivityRank> =
        compareByDescending<LiveActivityRank> { it.statusOrder }
            .thenByDescending { it.followParticipants }
            .thenByDescending { it.totalParticipants }
            .thenByDescending { it.startOrCreated }
            .thenByDescending { it.idHex }

    /**
     * Snapshots each item's rank **once** via [rankOf], then sorts. Because the keys are captured up
     * front, [rankOf] may read volatile state (online cache, `current_participants`) safely — the
     * comparator only ever sees the immutable snapshot, so TimSort's contract holds.
     */
    fun <T> sortDescending(
        items: Collection<T>,
        rankOf: (T) -> LiveActivityRank,
    ): List<T> {
        val snapshot = items.associateWith(rankOf)
        return items.sortedWith { a, b -> RANK_DESCENDING.compare(snapshot.getValue(a), snapshot.getValue(b)) }
    }
}
