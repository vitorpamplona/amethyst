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

class BigDecimalCompareTest {
    @Test
    fun ordersByValueNotByScale() {
        assertEquals(0, BigDecimal("1.0").compareToValue(BigDecimal("1.00")))
        assertEquals(0, BigDecimal(0).compareToValue(BigDecimal("0.000")))
    }

    @Test
    fun ordersLargerAfterSmaller() {
        assertTrue(BigDecimal(21).compareToValue(BigDecimal(1000)) < 0)
        assertTrue(BigDecimal(1000).compareToValue(BigDecimal(21)) > 0)
        assertTrue(BigDecimal("-0.5").compareToValue(BigDecimal("0.25")) < 0)
    }

    @Test
    fun sortsDescendingLikeTheZapList() {
        val sorted =
            listOf(BigDecimal(5), BigDecimal(1000), BigDecimal("21.5"), BigDecimal(0))
                .sortedWith { a, b -> b.compareToValue(a) }
        assertEquals(listOf(1000.0, 21.5, 5.0, 0.0), sorted.map { it.toDoubleValue() })
    }
}
