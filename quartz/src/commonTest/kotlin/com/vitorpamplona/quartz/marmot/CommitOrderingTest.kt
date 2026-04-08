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
package com.vitorpamplona.quartz.marmot

import com.vitorpamplona.quartz.marmot.mip03GroupMessages.CommitOrdering
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for deterministic commit conflict resolution (MIP-03).
 */
class CommitOrderingTest {
    private val groupId = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"

    private fun makeGroupEvent(
        id: String,
        createdAt: Long,
    ): GroupEvent =
        GroupEvent(
            id = id.padEnd(64, '0'),
            pubKey = "a".repeat(64),
            createdAt = createdAt,
            tags = arrayOf(arrayOf("h", groupId)),
            content = "encrypted",
            sig = "s".repeat(128),
        )

    // ===== selectWinner =====

    @Test
    fun testSelectWinner_EmptyList() {
        assertNull(CommitOrdering.selectWinner(emptyList()))
    }

    @Test
    fun testSelectWinner_SingleCommit() {
        val commit = makeGroupEvent("aaa", 1000)
        assertEquals(commit, CommitOrdering.selectWinner(listOf(commit)))
    }

    @Test
    fun testSelectWinner_LowestTimestampWins() {
        val early = makeGroupEvent("bbb", 1000)
        val late = makeGroupEvent("aaa", 2000)

        // Early wins even though its id is "larger"
        assertEquals(early, CommitOrdering.selectWinner(listOf(late, early)))
        assertEquals(early, CommitOrdering.selectWinner(listOf(early, late)))
    }

    @Test
    fun testSelectWinner_SameTimestamp_SmallestIdWins() {
        val smallId = makeGroupEvent("111", 1000) // id starts with 1
        val largeId = makeGroupEvent("fff", 1000) // id starts with f

        assertEquals(smallId, CommitOrdering.selectWinner(listOf(largeId, smallId)))
        assertEquals(smallId, CommitOrdering.selectWinner(listOf(smallId, largeId)))
    }

    @Test
    fun testSelectWinner_ThreeCompetitors() {
        val a = makeGroupEvent("ccc", 1000)
        val b = makeGroupEvent("aaa", 1000) // Same timestamp, smallest id
        val c = makeGroupEvent("bbb", 999) // Earliest timestamp

        // c wins (earliest timestamp)
        assertEquals(c, CommitOrdering.selectWinner(listOf(a, b, c)))
    }

    // ===== isWinner =====

    @Test
    fun testIsWinner() {
        val winner = makeGroupEvent("aaa", 999)
        val loser = makeGroupEvent("bbb", 1000)
        val competitors = listOf(winner, loser)

        assertTrue(CommitOrdering.isWinner(winner, competitors))
        assertFalse(CommitOrdering.isWinner(loser, competitors))
    }

    @Test
    fun testIsWinner_EmptyCompetitors() {
        val commit = makeGroupEvent("aaa", 1000)
        assertFalse(CommitOrdering.isWinner(commit, emptyList()))
    }

    // ===== comparator ordering =====

    @Test
    fun testComparatorSortsCorrectly() {
        val events =
            listOf(
                makeGroupEvent("ccc", 3000),
                makeGroupEvent("aaa", 1000),
                makeGroupEvent("bbb", 1000),
                makeGroupEvent("ddd", 2000),
            )

        val sorted = events.sortedWith(CommitOrdering.comparator)

        // 1. aaa@1000 (lowest timestamp, then smallest id)
        // 2. bbb@1000 (same timestamp, next id)
        // 3. ddd@2000
        // 4. ccc@3000
        assertEquals("aaa", sorted[0].id.take(3))
        assertEquals("bbb", sorted[1].id.take(3))
        assertEquals("ddd", sorted[2].id.take(3))
        assertEquals("ccc", sorted[3].id.take(3))
    }

    // ===== EpochCommitTracker =====

    @Test
    fun testEpochCommitTracker_Basic() {
        val tracker = CommitOrdering.EpochCommitTracker()
        val epoch1Commit1 = makeGroupEvent("bbb", 1000)
        val epoch1Commit2 = makeGroupEvent("aaa", 1001)

        tracker.addCommit(groupId, 1L, epoch1Commit1)
        tracker.addCommit(groupId, 1L, epoch1Commit2)

        assertEquals(2, tracker.pendingForEpoch(groupId, 1L).size)
        assertEquals(0, tracker.pendingForEpoch(groupId, 2L).size)

        // Resolve: epoch1Commit1 wins (earlier timestamp)
        val winner = tracker.resolve(groupId, 1L)
        assertEquals(epoch1Commit1, winner)
    }

    @Test
    fun testEpochCommitTracker_MultipleEpochs() {
        val tracker = CommitOrdering.EpochCommitTracker()
        val e1 = makeGroupEvent("aaa", 1000)
        val e2 = makeGroupEvent("bbb", 2000)

        tracker.addCommit(groupId, 1L, e1)
        tracker.addCommit(groupId, 2L, e2)

        val expectedKeys =
            setOf(
                CommitOrdering.GroupEpochKey(groupId, 1L),
                CommitOrdering.GroupEpochKey(groupId, 2L),
            )
        assertEquals(expectedKeys, tracker.pendingGroupEpochs())

        tracker.clearEpoch(groupId, 1L)
        assertEquals(setOf(CommitOrdering.GroupEpochKey(groupId, 2L)), tracker.pendingGroupEpochs())
    }

    @Test
    fun testEpochCommitTracker_ClearAll() {
        val tracker = CommitOrdering.EpochCommitTracker()
        tracker.addCommit(groupId, 1L, makeGroupEvent("aaa", 1000))
        tracker.addCommit(groupId, 2L, makeGroupEvent("bbb", 2000))

        tracker.clear()

        assertTrue(tracker.pendingGroupEpochs().isEmpty())
        assertNull(tracker.resolve(groupId, 1L))
    }

    @Test
    fun testEpochCommitTracker_ResolveEmpty() {
        val tracker = CommitOrdering.EpochCommitTracker()
        assertNull(tracker.resolve(groupId, 999L))
    }
}
