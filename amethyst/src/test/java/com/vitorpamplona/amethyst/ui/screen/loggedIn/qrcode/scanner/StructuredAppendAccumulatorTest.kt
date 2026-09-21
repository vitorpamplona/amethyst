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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StructuredAppendAccumulatorTest {
    private fun part(
        text: String,
        index: Int,
        size: Int,
        id: String = "seq",
    ) = ScanResult(text = text, bounds = null, sequenceId = id, sequenceIndex = index, sequenceSize = size)

    @Test
    fun `parts in order join once the last one lands`() {
        val acc = StructuredAppendAccumulator()

        assertNull(acc.add(part("abc", 0, 3), 0))
        assertNull(acc.add(part("def", 1, 3), 100))
        assertEquals("abcdefghi", acc.add(part("ghi", 2, 3), 200))
    }

    @Test
    fun `parts out of order still join in index order`() {
        val acc = StructuredAppendAccumulator()

        assertNull(acc.add(part("ghi", 2, 3), 0))
        assertNull(acc.add(part("abc", 0, 3), 10))
        assertEquals("abcdefghi", acc.add(part("def", 1, 3), 20))
    }

    @Test
    fun `a repeated part does not complete the sequence`() {
        val acc = StructuredAppendAccumulator()

        assertNull(acc.add(part("abc", 0, 3), 0))
        assertNull(acc.add(part("abc", 0, 3), 10))
        assertNull(acc.add(part("abc", 0, 3), 20))
        assertEquals(1, acc.captured)
    }

    @Test
    fun `progress reports captured against total`() {
        val acc = StructuredAppendAccumulator()

        acc.add(part("a", 0, 3), 0)
        assertEquals(1, acc.captured)
        assertEquals(3, acc.total)

        acc.add(part("b", 1, 3), 10)
        assertEquals(2, acc.captured)
    }

    @Test
    fun `a different sequence id restarts rather than splicing`() {
        val acc = StructuredAppendAccumulator()

        acc.add(part("abc", 0, 2, id = "one"), 0)
        assertNull(acc.add(part("xyz", 0, 2, id = "two"), 10))
        assertEquals(1, acc.captured)
        assertEquals("xyzuvw", acc.add(part("uvw", 1, 2, id = "two"), 20))
    }

    @Test
    fun `a part arriving after the timeout restarts the sequence`() {
        val acc = StructuredAppendAccumulator(timeoutMs = 1_000)

        acc.add(part("abc", 0, 2), 0)
        // The second part shows up two seconds late: treat it as the start of a new attempt
        // rather than half of an abandoned one.
        assertNull(acc.add(part("def", 1, 2), 3_000))
        assertEquals(1, acc.captured)
    }

    @Test
    fun `a result that is not part of a sequence is ignored`() {
        val acc = StructuredAppendAccumulator()

        assertNull(acc.add(ScanResult("plain", bounds = null), 0))
        assertEquals(0, acc.captured)
    }

    @Test
    fun `completing a sequence clears the accumulator for the next one`() {
        val acc = StructuredAppendAccumulator()

        acc.add(part("a", 0, 2), 0)
        assertEquals("ab", acc.add(part("b", 1, 2), 10))
        assertEquals(0, acc.captured)
        assertEquals(0, acc.total)
    }
}
