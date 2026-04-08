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
package com.vitorpamplona.quartz.utils.secp256k1

import kotlin.test.Test
import kotlin.test.assertEquals

class MultiplyHighTest {
    @Test
    fun testMultiplyHigh() {
        // 0 * 0 = 0
        assertEquals(0L, multiplyHigh(0L, 0L))
        assertEquals(0L, multiplyHighFallback(0L, 0L))

        // 1 * 1 = 1 (high part 0)
        assertEquals(0L, multiplyHigh(1L, 1L))
        assertEquals(0L, multiplyHighFallback(1L, 1L))

        // Long.MAX_VALUE * 2
        // Long.MAX_VALUE = 2^63 - 1
        // (2^63 - 1) * 2 = 2^64 - 2. High part should be 0 (signed).
        assertEquals(0L, multiplyHigh(Long.MAX_VALUE, 2L))
        assertEquals(0L, multiplyHighFallback(Long.MAX_VALUE, 2L))

        // Long.MAX_VALUE * Long.MAX_VALUE
        // (2^63 - 1)^2 = 2^126 - 2^64 + 1
        // High 64 bits of 2^126 is 2^62.
        // (2^63 - 1) * (2^63 - 1) = 0x3FFFFFFFFFFFFFFF_0000000000000001
        assertEquals(0x3FFFFFFFFFFFFFFFL, multiplyHigh(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(0x3FFFFFFFFFFFFFFFL, multiplyHighFallback(Long.MAX_VALUE, Long.MAX_VALUE))

        // Long.MIN_VALUE * Long.MIN_VALUE
        // -2^63 * -2^63 = 2^126.
        // High 64 bits is 0x4000000000000000
        assertEquals(0x4000000000000000L, multiplyHigh(Long.MIN_VALUE, Long.MIN_VALUE))
        assertEquals(0x4000000000000000L, multiplyHighFallback(Long.MIN_VALUE, Long.MIN_VALUE))

        // Long.MIN_VALUE * 1
        // -2^63 * 1 = -2^63. High bits are all 1s if negative? No, high bits of -2^63 are all 1s except it's the 64-bit value itself.
        // In 128-bit: 0xFFFFFFFFFFFFFFFF_8000000000000000
        assertEquals(-1L, multiplyHigh(Long.MIN_VALUE, 1L))
        assertEquals(-1L, multiplyHighFallback(Long.MIN_VALUE, 1L))
    }

    @Test
    fun testUnsignedMultiplyHigh() {
        // 0 * 0 = 0
        assertEquals(0L, unsignedMultiplyHigh(0L, 0L))
        assertEquals(0L, unsignedMultiplyHighFallback(0L, 0L))

        // -1L is 0xFFFFFFFFFFFFFFFF (2^64 - 1)
        // (2^64 - 1) * (2^64 - 1) = 2^128 - 2^65 + 1
        // High 64 bits of (2^128 - 2^65 + 1) is 2^64 - 2?
        // Let's check: (2^64-1)^2 = 2^128 - 2*2^64 + 1 = 2^128 - 2^65 + 1.
        // In 128-bit: 0xFFFFFFFFFFFFFFFE_0000000000000001
        // High part: 0xFFFFFFFFFFFFFFFEL (-2L)
        assertEquals(-2L, unsignedMultiplyHigh(-1L, -1L))
        assertEquals(-2L, unsignedMultiplyHighFallback(-1L, -1L))

        // -1L * 1L = 2^64 - 1. High part 0.
        assertEquals(0L, unsignedMultiplyHigh(-1L, 1L))
        assertEquals(0L, unsignedMultiplyHighFallback(-1L, 1L))

        // (2^63) * 2 = 2^64. High part 1.
        // Long.MIN_VALUE is 2^63 unsigned.
        assertEquals(1L, unsignedMultiplyHigh(Long.MIN_VALUE, 2L))
        assertEquals(1L, unsignedMultiplyHighFallback(Long.MIN_VALUE, 2L))
    }

    @Test
    fun compareImplementations() {
        val values = longArrayOf(0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, 0x1234567890ABCDEFL, -0x1234567890ABCDEFL)
        for (a in values) {
            for (b in values) {
                assertEquals(multiplyHighFallback(a, b), multiplyHigh(a, b), "Signed mismatch for $a * $b")
                assertEquals(unsignedMultiplyHighFallback(a, b), unsignedMultiplyHigh(a, b), "Unsigned mismatch for $a * $b")
            }
        }
    }
}
