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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HexRangeTest {
    private val key = "48a72b485d38338627ec9d427583551f9af4f016c739b8ec0d6313540a8b12cf"

    @Test
    fun checksOnlyTheRange() {
        val address = "30023:$key:my-article"
        assertTrue(Hex.isHex(address, 6, 70))
        assertTrue(Hex.isHex(address.uppercase(), 6, 70))
        // the range includes a ':' on either side
        assertFalse(Hex.isHex(address, 5, 70))
        assertFalse(Hex.isHex(address, 6, 71))
    }

    @Test
    fun oddLengthRangesAreAllowed() {
        assertTrue(Hex.isHex("xabcx", 1, 4))
    }

    @Test
    fun emptyRangeIsValid() {
        assertTrue(Hex.isHex("zz", 1, 1))
    }

    @Test
    fun rejectsNonHexAndWideChars() {
        assertFalse(Hex.isHex("ab0g", 0, 4))
        assertFalse(Hex.isHex("abéf", 0, 4))
        assertFalse(Hex.isHex("ab😀", 0, 4))
    }

    @Test
    fun rejectsRangesOutsideTheString() {
        assertFalse(Hex.isHex("abcd", -1, 2))
        assertFalse(Hex.isHex("abcd", 0, 5))
        assertFalse(Hex.isHex("abcd", 3, 2))
    }
}
