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
package com.vitorpamplona.amethyst.commons.video.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ByteRangesTest {
    @Test
    fun overlappingAndTouchingSpansMerge() {
        val ranges = ByteRanges()
        ranges.add(100, 200)
        ranges.add(300, 400)
        assertEquals("100-200,300-400", ranges.encode())
        ranges.add(200, 300)
        assertEquals("100-400", ranges.encode())
        ranges.add(50, 120)
        ranges.add(390, 500)
        assertEquals("50-500", ranges.encode())
        assertEquals(450, ranges.cachedBytes)
    }

    @Test
    fun aSpanInsideAnotherChangesNothing() {
        val ranges = ByteRanges()
        ranges.add(0, 1000)
        ranges.add(10, 20)
        assertEquals("0-1000", ranges.encode())
        assertEquals(1000, ranges.cachedBytes)
    }

    @Test
    fun aSpanBridgingSeveralMergesThemAll() {
        val ranges = ByteRanges()
        ranges.add(0, 10)
        ranges.add(20, 30)
        ranges.add(40, 50)
        ranges.add(60, 70)
        ranges.add(5, 65)
        assertEquals("0-70", ranges.encode())
        assertEquals(70, ranges.cachedBytes)
    }

    @Test
    fun runEndAndNextStart() {
        val ranges = ByteRanges.decode("100-200,300-400")
        assertEquals(50, ranges.runEnd(50))
        assertEquals(200, ranges.runEnd(100))
        assertEquals(200, ranges.runEnd(199))
        assertEquals(200, ranges.runEnd(200))
        assertEquals(400, ranges.runEnd(350))
        assertEquals(100L, ranges.nextStartAfter(0))
        assertEquals(300L, ranges.nextStartAfter(150))
        assertEquals(300L, ranges.nextStartAfter(200))
        assertNull(ranges.nextStartAfter(300))
        assertNull(ranges.nextStartAfter(450))
    }

    @Test
    fun covers() {
        val ranges = ByteRanges.decode("0-100,200-300")
        assertTrue(ranges.covers(0, 100))
        assertTrue(ranges.covers(210, 290))
        assertFalse(ranges.covers(50, 150))
        assertFalse(ranges.covers(100, 101))
        assertTrue(ranges.covers(5, 5))
    }

    @Test
    fun truncateDropsAndCuts() {
        val ranges = ByteRanges.decode("0-100,200-300,400-500")
        ranges.truncate(250)
        assertEquals("0-100,200-250", ranges.encode())
        assertEquals(150, ranges.cachedBytes)
        ranges.truncate(0)
        assertTrue(ranges.isEmpty)
        assertEquals(0, ranges.cachedBytes)
    }

    @Test
    fun decodeSkipsGarbage() {
        assertEquals("0-10,20-30", ByteRanges.decode("0-10,x-y,,20-30,-5-6,9-3").encode())
        assertTrue(ByteRanges.decode("").isEmpty)
    }
}
