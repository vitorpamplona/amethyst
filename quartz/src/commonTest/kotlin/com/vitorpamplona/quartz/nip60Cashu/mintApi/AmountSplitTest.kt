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
package com.vitorpamplona.quartz.nip60Cashu.mintApi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AmountSplitTest {
    @Test
    fun zero() {
        assertEquals(emptyList(), splitAmountIntoDenominations(0))
    }

    @Test
    fun powerOfTwo() {
        assertEquals(listOf(1L), splitAmountIntoDenominations(1))
        assertEquals(listOf(2L), splitAmountIntoDenominations(2))
        assertEquals(listOf(4L), splitAmountIntoDenominations(4))
        assertEquals(listOf(8L), splitAmountIntoDenominations(8))
        assertEquals(listOf(64L), splitAmountIntoDenominations(64))
    }

    @Test
    fun thirteen() {
        assertEquals(listOf(1L, 4L, 8L), splitAmountIntoDenominations(13))
    }

    @Test
    fun oneHundred() {
        assertEquals(listOf(4L, 32L, 64L), splitAmountIntoDenominations(100))
    }

    @Test
    fun sumsBackToOriginal() {
        for (n in listOf(1, 5, 7, 19, 21, 255, 1024, 2048, 65535, 1_000_000)) {
            val parts = splitAmountIntoDenominations(n.toLong())
            assertEquals(n.toLong(), parts.sum(), "Parts for $n must sum to $n: $parts")
        }
    }

    @Test
    fun allPartsAreDistinctPowersOfTwo() {
        val parts = splitAmountIntoDenominations(73)
        // 73 = 64 + 8 + 1 → [1, 8, 64]
        assertEquals(listOf(1L, 8L, 64L), parts)
        parts.forEach { p ->
            assertTrue((p and (p - 1)) == 0L, "$p should be a power of two")
        }
    }

    @Test
    fun rejectsNegative() {
        assertFailsWith<IllegalArgumentException> { splitAmountIntoDenominations(-1) }
    }
}
