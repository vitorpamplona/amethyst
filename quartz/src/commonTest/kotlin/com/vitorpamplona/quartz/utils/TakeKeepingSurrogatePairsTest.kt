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
package com.vitorpamplona.quartz.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TakeKeepingSurrogatePairsTest {
    private val salute = "🫡" // 🫡 U+1FAE1

    @Test
    fun shorterThanLimitReturnsWhole() {
        assertEquals("hello", "hello".takeKeepingSurrogatePairs(50))
    }

    @Test
    fun cutOnAsciiBoundaryIsExact() {
        assertEquals("abcde", "abcdefghij".takeKeepingSurrogatePairs(5))
    }

    @Test
    fun cutThatWouldSplitSurrogateBacksOff() {
        // "ab" + 🫡; limit 3 would keep the high surrogate only -> back off to "ab".
        val result = ("ab" + salute).takeKeepingSurrogatePairs(3)
        assertEquals("ab", result)
        assertNoLoneSurrogate(result)
    }

    @Test
    fun cutRightAfterSurrogatePairKeepsWholeEmoji() {
        val result = ("ab" + salute).takeKeepingSurrogatePairs(4)
        assertEquals("ab" + salute, result)
        assertNoLoneSurrogate(result)
    }

    @Test
    fun resultAlwaysSurvivesUtf8RoundTrip() {
        for (n in 0..6) {
            val result = ("ab" + salute + "cd").takeKeepingSurrogatePairs(n)
            assertEquals(result, result.encodeToByteArray().decodeToString(), "n=$n")
        }
    }

    private fun assertNoLoneSurrogate(s: String) {
        val lone =
            s.indices.any { i ->
                val c = s[i]
                when {
                    c.isHighSurrogate() -> i + 1 >= s.length || !s[i + 1].isLowSurrogate()
                    c.isLowSurrogate() -> i == 0 || !s[i - 1].isHighSurrogate()
                    else -> false
                }
            }
        assertTrue(!lone, "unexpected lone surrogate in <$s>")
    }
}
