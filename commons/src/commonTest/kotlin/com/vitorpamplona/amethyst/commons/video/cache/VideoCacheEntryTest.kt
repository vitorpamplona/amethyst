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
import kotlin.test.assertNull

class VideoCacheEntryTest {
    @Test
    fun budgetIsAFifthOfTheFreeSpaceClamped() {
        val mb = 1024L * 1024
        assertEquals(256 * mb, VideoCacheBudget.forAvailableBytes(0))
        assertEquals(256 * mb, VideoCacheBudget.forAvailableBytes(-1))
        assertEquals(256 * mb, VideoCacheBudget.forAvailableBytes(1000 * mb))
        assertEquals(2000 * mb, VideoCacheBudget.forAvailableBytes(10000 * mb))
        assertEquals(4096 * mb, VideoCacheBudget.forAvailableBytes(1_000_000 * mb))
    }

    @Test
    fun roundTrips() {
        val entry = VideoCacheEntry("https://host/a.mp4?x=1&y=2", 5000, "video/mp4", 1234, ByteRanges.decode("0-100,4000-5000"))
        val back = VideoCacheEntry.decode(entry.encode())!!
        assertEquals(entry.url, back.url)
        assertEquals(5000L, back.length)
        assertEquals("video/mp4", back.mimeType)
        assertEquals(1234L, back.lastAccess)
        assertEquals("0-100,4000-5000", back.ranges.encode())
    }

    @Test
    fun unknownLengthAndTypeStayUnknown() {
        val back = VideoCacheEntry.decode(VideoCacheEntry("https://host/a").encode())!!
        assertNull(back.length)
        assertNull(back.mimeType)
    }

    @Test
    fun rangesPastTheLengthAreDropped() {
        val text = VideoCacheEntry("https://host/a", 150, null, 0, ByteRanges.decode("0-100,120-400")).encode()
        assertEquals("0-100,120-150", VideoCacheEntry.decode(text)!!.ranges.encode())
    }

    @Test
    fun tornOrForeignFilesAreRejected() {
        val full = VideoCacheEntry("https://host/a", 10, null, 0, ByteRanges.decode("0-10")).encode()
        assertNull(VideoCacheEntry.decode(full.substringBefore("ranges=")))
        assertNull(VideoCacheEntry.decode("something else\nurl=x\nranges=0-1\n"))
        assertNull(VideoCacheEntry.decode(""))
    }
}
