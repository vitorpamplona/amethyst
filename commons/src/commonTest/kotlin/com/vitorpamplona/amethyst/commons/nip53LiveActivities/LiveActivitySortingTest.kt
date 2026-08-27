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

import com.vitorpamplona.amethyst.commons.nip53LiveActivities.LiveActivitySorting.LiveActivityRank
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveActivitySortingTest {
    private val now = 1_000_000L

    @Test
    fun statusOrderRanksLiveOverPlannedOverEnded() {
        assertEquals(LiveActivitySorting.ORDER_LIVE, LiveActivitySorting.statusOrder(StatusTag.STATUS.LIVE))
        assertEquals(LiveActivitySorting.ORDER_PLANNED, LiveActivitySorting.statusOrder(StatusTag.STATUS.PLANNED))
        assertEquals(LiveActivitySorting.ORDER_ENDED, LiveActivitySorting.statusOrder(StatusTag.STATUS.ENDED))
        assertEquals(LiveActivitySorting.ORDER_ENDED, LiveActivitySorting.statusOrder(null))
    }

    @Test
    fun offlineLiveIsDowngradedToEnded() {
        assertEquals(
            LiveActivitySorting.ORDER_ENDED,
            LiveActivitySorting.statusOrder(StatusTag.STATUS.LIVE, isOfflineNow = true),
        )
    }

    @Test
    fun isLiveAndFreshRespectsFreshnessWindow() {
        // published just now -> fresh
        assertTrue(
            LiveActivitySorting.isLiveAndFresh(StatusTag.STATUS.LIVE, createdAt = now, now = now),
        )
        // published 20 min ago -> stale (default 15-min window)
        assertFalse(
            LiveActivitySorting.isLiveAndFresh(StatusTag.STATUS.LIVE, createdAt = now - 20 * 60, now = now),
        )
        // fresh but offline -> excluded
        assertFalse(
            LiveActivitySorting.isLiveAndFresh(StatusTag.STATUS.LIVE, createdAt = now, now = now, isOfflineNow = true),
        )
        // planned is never "live and fresh"
        assertFalse(
            LiveActivitySorting.isLiveAndFresh(StatusTag.STATUS.PLANNED, createdAt = now, now = now),
        )
    }

    @Test
    fun overduePlannedDetectedAfterGrace() {
        // starts 2h ago, still planned -> overdue
        assertTrue(
            LiveActivitySorting.isOverduePlanned(StatusTag.STATUS.PLANNED, startsAt = now - 2 * 60 * 60, now = now),
        )
        // starts in the future -> not overdue
        assertFalse(
            LiveActivitySorting.isOverduePlanned(StatusTag.STATUS.PLANNED, startsAt = now + 60 * 60, now = now),
        )
        // no start time -> not overdue
        assertFalse(
            LiveActivitySorting.isOverduePlanned(StatusTag.STATUS.PLANNED, startsAt = null, now = now),
        )
        // live is never "overdue planned"
        assertFalse(
            LiveActivitySorting.isOverduePlanned(StatusTag.STATUS.LIVE, startsAt = now - 2 * 60 * 60, now = now),
        )
    }

    @Test
    fun sortOrdersByStatusThenFollowsThenTotalThenTime() {
        val endedFresh = rank(LiveActivitySorting.ORDER_ENDED, follows = 5, total = 5, time = now, id = "a")
        val liveFewFollows = rank(LiveActivitySorting.ORDER_LIVE, follows = 1, total = 1, time = now - 100, id = "b")
        val liveManyFollows = rank(LiveActivitySorting.ORDER_LIVE, follows = 3, total = 3, time = now - 200, id = "c")
        val planned = rank(LiveActivitySorting.ORDER_PLANNED, follows = 9, total = 9, time = now, id = "d")

        val items = listOf(endedFresh, liveFewFollows, planned, liveManyFollows)
        val sorted = LiveActivitySorting.sortDescending(items) { it }

        // live (most follows) > live (fewer) > planned > ended
        assertEquals(listOf(liveManyFollows, liveFewFollows, planned, endedFresh), sorted)
    }

    @Test
    fun sortBreaksTiesByTotalThenTimeThenId() {
        val a = rank(LiveActivitySorting.ORDER_LIVE, follows = 2, total = 10, time = now, id = "aaa")
        val b = rank(LiveActivitySorting.ORDER_LIVE, follows = 2, total = 20, time = now, id = "bbb")
        val c = rank(LiveActivitySorting.ORDER_LIVE, follows = 2, total = 20, time = now + 50, id = "ccc")

        val sorted = LiveActivitySorting.sortDescending(listOf(a, b, c)) { it }
        // higher total first (b,c over a); newer time breaks b vs c
        assertEquals(listOf(c, b, a), sorted)
    }

    @Test
    fun sortIsStableUnderConcurrentKeyMutation() {
        // Simulate a background online-check flipping a stream's status mid-sort: rankOf reads a
        // volatile source, but sortDescending snapshots once, so no TimSort contract violation.
        val ids = (0 until 200).map { it.toString().padStart(3, '0') }
        var flips = 0
        val sorted =
            LiveActivitySorting.sortDescending(ids) { id ->
                // Alternate the status bucket every read; if the comparator read this lazily it would
                // be inconsistent and throw. Snapshotting protects us.
                flips++
                val order = if (flips % 2 == 0) LiveActivitySorting.ORDER_LIVE else LiveActivitySorting.ORDER_ENDED
                LiveActivityRank(order, 0, 0, now, id)
            }
        assertEquals(ids.size, sorted.size)
    }

    private fun rank(
        statusOrder: Int,
        follows: Int,
        total: Int,
        time: Long,
        id: String,
    ) = LiveActivityRank(statusOrder, follows, total, time, id)
}
