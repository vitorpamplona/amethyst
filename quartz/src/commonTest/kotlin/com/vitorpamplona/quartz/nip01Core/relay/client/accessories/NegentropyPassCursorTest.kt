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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NegentropyPassCursorTest {
    private fun event(
        seed: Int,
        createdAt: Long,
    ) = Event(seed.toString().padStart(64, '0'), "a".repeat(64), createdAt, 1, emptyArray(), "", "0".repeat(128))

    @Test
    fun aPassThatReconciledNothingEndsTheWalk() {
        val cursor = NegentropyPassCursor(Filter(kinds = listOf(1)))

        assertNull(cursor.endPass())
    }

    @Test
    fun theNextWindowReachesBackToTheOldestSecondInclusively() {
        val cursor = NegentropyPassCursor(Filter(kinds = listOf(1)))
        listOf(event(1, 50), event(2, 40), event(3, 40)).forEach { assertTrue(cursor.accept(it)) }

        val next = assertNotNull(cursor.endPass())

        assertEquals(40L, next.window.until)
        assertEquals(setOf(event(2, 40).id, event(3, 40).id), next.known)
    }

    @Test
    fun whatAPassDeliveredAtItsEdgeIsCarriedOverAndNotDeliveredAgain() {
        val cursor = NegentropyPassCursor(Filter(kinds = listOf(1)))
        cursor.accept(event(1, 50))
        cursor.accept(event(2, 40))
        cursor.advance(assertNotNull(cursor.endPass()))

        assertEquals(listOf(IdAndTime(40, event(2, 40).id)), cursor.carriedOver())
        assertFalse(cursor.accept(event(2, 40)), "delivered by the previous pass")
        assertFalse(cursor.accept(event(9, 45)), "above the window: a relay ignoring until")
        assertTrue(cursor.accept(event(3, 40)), "the rest of the edge second")
        assertTrue(cursor.accept(event(4, 30)))
    }

    @Test
    fun theOldestEntryOfOursTheRelayMatchedCountsAsReached() {
        val cursor = NegentropyPassCursor(Filter(kinds = listOf(1)))
        cursor.matched(20)
        cursor.matched(35)

        val next = assertNotNull(cursor.endPass(), "nothing downloaded, but the relay reconciled down to 20")

        assertEquals(20L, next.window.until)
        assertTrue(next.known.isEmpty())
    }

    @Test
    fun aPassThatDoesNotReachFurtherBackEndsTheWalk() {
        val cursor = NegentropyPassCursor(Filter(kinds = listOf(1)))
        cursor.accept(event(1, 40))
        cursor.advance(assertNotNull(cursor.endPass()))

        // Same second, nothing new: the relay keeps naming what it named.
        cursor.matched(40)
        assertNull(cursor.endPass())
    }

    @Test
    fun aRelayReachingHigherThanTheWindowEndsTheWalk() {
        val cursor = NegentropyPassCursor(Filter(kinds = listOf(1)))
        cursor.accept(event(1, 40))
        cursor.advance(assertNotNull(cursor.endPass()))

        cursor.matched(45)
        assertNull(cursor.endPass())
    }

    @Test
    fun havesAboveTheReachAreSettledAndTheRestWait() {
        val haves = listOf(IdAndTime(50, "a"), IdAndTime(40, "b"), IdAndTime(30, "c"))

        val (settled, pending) = haves.settledAbove(40)

        assertEquals(listOf("a"), settled.map { it.id })
        assertEquals(listOf("b", "c"), pending.map { it.id })
    }

    @Test
    fun carriedOverEntriesJoinTheLocalIndexInsideTheirWindow() =
        runTest {
            val index = NegentropyLocalIndex.of(listOf(IdAndTime(10, "x"))).plus(listOf(IdAndTime(40, "y")))

            assertEquals(setOf("x", "y"), index.entriesFor(Filter(until = 40)).map { it.id }.toSet())
            assertEquals(listOf("x"), index.entriesFor(Filter(until = 39)).map { it.id })
            assertEquals(2, index.count(Filter()))
        }
}
