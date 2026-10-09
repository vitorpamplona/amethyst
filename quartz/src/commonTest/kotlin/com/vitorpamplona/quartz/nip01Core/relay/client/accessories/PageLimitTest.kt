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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageLimitTest {
    @Test
    fun anUnboundedFilterSendsNoLimit() {
        assertNull(pageLimit(limit = null, delivered = 10, boundaryRepeats = 2, cap = 500, topUpRefused = false))
    }

    @Test
    fun aPageAsksForTheRemainderPlusTheBoundaryRepeats() {
        assertEquals(1_002, pageLimit(limit = 1_200, delivered = 200, boundaryRepeats = 2, cap = null, topUpRefused = false))
    }

    @Test
    fun aKnownCapBoundsThePage() {
        assertEquals(500, pageLimit(limit = 1_200, delivered = 200, boundaryRepeats = 2, cap = 500, topUpRefused = false))
        assertEquals(12, pageLimit(limit = 1_200, delivered = 1_190, boundaryRepeats = 2, cap = 500, topUpRefused = false))
    }

    @Test
    fun aBoundarySecondThatFillsAPageAsksPastTheCap() {
        assertEquals(1_100, pageLimit(limit = 50_000, delivered = 600, boundaryRepeats = 600, cap = 500, topUpRefused = false))
        // A relay that refused the top-up gets the cap, and the second's tail is lost.
        assertEquals(500, pageLimit(limit = 50_000, delivered = 600, boundaryRepeats = 600, cap = 500, topUpRefused = true))
    }
}
