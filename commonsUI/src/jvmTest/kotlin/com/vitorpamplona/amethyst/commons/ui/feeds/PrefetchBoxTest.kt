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
package com.vitorpamplona.amethyst.commons.ui.feeds

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When a cached copy is big enough that the feed prefetch need not decode another one. */
class PrefetchBoxTest {
    // A single image in a 600 x 900 list: fitted.
    private val fit = PrefetchBox(600, 900, fill = false)

    // A note with several images: a 600 px square, covered.
    private val fill = PrefetchBox(600, 600, fill = true)

    @Test
    fun theOriginalAlwaysCovers() {
        assertTrue(fit.isCoveredBy(34, 34, sampled = false))
        assertTrue(fill.isCoveredBy(34, 34, sampled = false))
    }

    @Test
    fun aCopyDrawnSmallerElsewhereDoesNotCover() {
        // The notification panel's 270 px copy of a 4:3 photo; the feed wants 600 x 450.
        assertFalse(fit.isCoveredBy(270, 203, sampled = true))
        // The card's own 511 px copy is smaller than the 600 px the prefetch would make too.
        assertFalse(fit.isCoveredBy(511, 383, sampled = true))
    }

    @Test
    fun aCopyAsBigAsTheFitCovers() {
        assertTrue(fit.isCoveredBy(600, 450, sampled = true))
        // The decoder rounds: a pixel short still counts.
        assertTrue(fit.isCoveredBy(599, 449, sampled = true))
        // A tall image is limited by the height: 450 x 900.
        assertTrue(fit.isCoveredBy(450, 900, sampled = true))
        assertFalse(fit.isCoveredBy(300, 600, sampled = true))
    }

    @Test
    fun aCroppedCellNeedsTheShortSideCovered() {
        // A 4:1 panorama in a 600 px square crop needs 2400 x 600, not the 600 x 150 a fit gives.
        assertFalse(fill.isCoveredBy(600, 150, sampled = true))
        assertTrue(fill.isCoveredBy(2400, 600, sampled = true))
        // A portrait image is limited by the width.
        assertTrue(fill.isCoveredBy(600, 800, sampled = true))
        assertFalse(fill.isCoveredBy(450, 600, sampled = true))
    }

    @Test
    fun anEmptyBoxOrImageCoversNothing() {
        assertFalse(PrefetchBox(0, 900, fill = false).isCoveredBy(600, 450, sampled = true))
        assertFalse(fit.isCoveredBy(0, 0, sampled = true))
    }
}
