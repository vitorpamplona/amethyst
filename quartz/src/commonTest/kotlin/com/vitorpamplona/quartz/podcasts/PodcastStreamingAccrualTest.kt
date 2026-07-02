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
package com.vitorpamplona.quartz.podcasts

import kotlin.test.Test
import kotlin.test.assertEquals

class PodcastStreamingAccrualTest {
    @Test
    fun `bills a whole minute only once it is fully played`() {
        val accrual = PodcastStreamingAccrual()
        assertEquals(0, accrual.accrue(30_000L)) // 30s played -> nothing billable yet
        assertEquals(30_000L, accrual.pendingMillis())
        assertEquals(1, accrual.accrue(30_000L)) // another 30s -> one full minute
        assertEquals(0L, accrual.pendingMillis())
    }

    @Test
    fun `carries the partial remainder forward instead of rounding up`() {
        val accrual = PodcastStreamingAccrual()
        // 1m 25s played in one go -> bill 1 minute, keep 25s pending.
        assertEquals(1, accrual.accrue(85_000L))
        assertEquals(25_000L, accrual.pendingMillis())
        // 35s more completes the second minute.
        assertEquals(1, accrual.accrue(35_000L))
        assertEquals(0L, accrual.pendingMillis())
    }

    @Test
    fun `many small playing ticks accumulate to a minute`() {
        val accrual = PodcastStreamingAccrual()
        var billed = 0
        repeat(59) { billed += accrual.accrue(1_000L) }
        assertEquals(0, billed) // 59s, still short
        billed += accrual.accrue(1_000L)
        assertEquals(1, billed) // 60th second tips it over
    }

    @Test
    fun `a single long interval bills every full minute it contains`() {
        val accrual = PodcastStreamingAccrual()
        assertEquals(3, accrual.accrue(190_000L)) // 3m 10s -> 3 minutes
        assertEquals(10_000L, accrual.pendingMillis())
    }

    @Test
    fun `non-positive ticks never bill and an abandoned partial minute is simply dropped`() {
        val accrual = PodcastStreamingAccrual()
        assertEquals(0, accrual.accrue(0L))
        assertEquals(0, accrual.accrue(-5_000L))
        assertEquals(0, accrual.accrue(40_000L)) // 40s pending
        assertEquals(40_000L, accrual.pendingMillis())
        // Session ends here: the caller discards the instance, so the 40s is never charged.
    }
}
