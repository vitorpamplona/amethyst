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
package com.vitorpamplona.amethyst.commons.relayClient.pagination

import com.vitorpamplona.quartz.utils.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeWindowPaginationTest {
    @Test
    fun bootOpensAWindowThatStartsRecent() {
        val window = 1000L
        val pagination = TimeWindowPagination(initialWindow = window, step = 500L)

        // floor is roughly `now - initialWindow`, never unbounded
        val expected = TimeUtils.now() - window
        assertTrue("floor should be near now - window", kotlin.math.abs(pagination.since - expected) <= 2)
    }

    @Test
    fun loadMoreWidensTheFloorBackwardByOneStep() {
        val pagination = TimeWindowPagination(initialWindow = 1000L, step = 500L)
        val before = pagination.since

        pagination.loadMore()
        assertEquals(before - 500L, pagination.since)

        pagination.loadMore()
        assertEquals(before - 1000L, pagination.since)
    }

    @Test
    fun resetReturnsToTheInitialBootWindow() {
        val window = 1000L
        val pagination = TimeWindowPagination(initialWindow = window, step = 500L)
        pagination.loadMore()
        pagination.loadMore()

        pagination.reset()

        val expected = TimeUtils.now() - window
        assertTrue("reset floor should be near now - window", kotlin.math.abs(pagination.since - expected) <= 2)
    }

    @Test
    fun growingStepDoublesTheReachEachLoadMore() {
        val pagination = TimeWindowPagination(initialWindow = 10L, step = 100L, growthFactor = 2L, maxLookback = Long.MAX_VALUE / 2)
        val before = pagination.since

        pagination.loadMore()
        assertEquals("first step is the base step", before - 100L, pagination.since)

        pagination.loadMore()
        assertEquals("second step is doubled", before - 300L, pagination.since)

        pagination.loadMore()
        assertEquals("third step is doubled again", before - 700L, pagination.since)
    }

    @Test
    fun windowIsNotExhaustedWhileWithinLookback() {
        val pagination = TimeWindowPagination(initialWindow = 10L, step = 10L, growthFactor = 2L, maxLookback = 100L)
        assertTrue("a fresh window is not exhausted", !pagination.isExhausted())

        pagination.loadMore() // -> now-20
        assertTrue("still within the 100s lookback", !pagination.isExhausted())
    }

    @Test
    fun windowBecomesExhaustedAndClampsAtMaxLookback() {
        val maxLookback = 100L
        val pagination = TimeWindowPagination(initialWindow = 10L, step = 10L, growthFactor = 2L, maxLookback = maxLookback)

        // Geometric reach 10,20,40,80 crosses the 100s floor within a handful of steps.
        repeat(6) { pagination.loadMore() }

        assertTrue("window should report exhausted at the floor", pagination.isExhausted())
        val floor = TimeUtils.now() - maxLookback
        assertTrue("since must not go past the floor", pagination.since >= floor - 2 && pagination.since <= floor + 2)
    }

    @Test
    fun resetClearsStepGrowth() {
        val pagination = TimeWindowPagination(initialWindow = 10L, step = 100L, growthFactor = 2L, maxLookback = Long.MAX_VALUE / 2)
        pagination.loadMore() // step grows to 200
        pagination.loadMore() // step grows to 400

        pagination.reset()
        val before = pagination.since
        pagination.loadMore()
        assertEquals("after reset the step is back to the base", before - 100L, pagination.since)
    }
}
