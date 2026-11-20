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
package com.vitorpamplona.amethyst.desktop.service.bench

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class PassStatsTest {
    private fun ms(value: Double) = (value * 1_000_000).toLong()

    // Frames at 0, 16, 32, 48, then one 60 ms late, then 16 again.
    private val starts = longArrayOf(0, ms(16.0), ms(32.0), ms(48.0), ms(108.0), ms(124.0))
    private val busy = longArrayOf(ms(99.0), ms(5.0), ms(6.0), ms(7.0), ms(50.0), ms(8.0))

    @Test
    fun theFirstFrameOnlySetsTheBaseline() {
        val stats = PassStats.of("down", starts, busy, gcCount = 2, gcMs = 30, jitMs = 400)

        assertEquals(6, stats.frames)
        assertEquals(listOf(16.0, 16.0, 16.0, 60.0, 16.0), stats.intervalsMs.toList())
        // The 99 ms before the first frame belongs to no gap of this pass.
        assertEquals(listOf(5.0, 6.0, 7.0, 50.0, 8.0), stats.busyMs.toList())
        assertEquals(0.124, stats.seconds, 1e-9)
    }

    @Test
    fun countsTheFramesThatMissedTheirVsync() {
        val stats = PassStats.of("down", starts, busy, 0, 0, 0)

        assertEquals(20.0, stats.percentOver(PassStats.JANK_MS))
        assertEquals(20.0, stats.percentOver(PassStats.HITCH_MS))
        assertEquals(60.0, stats.intervalPercentile(99.0))
        assertEquals(16.0, stats.intervalPercentile(50.0))
        // 76 ms of UI work in 124 ms.
        assertEquals(100.0 * 76 / 124, stats.uiBusyPercent, 1e-9)
    }

    @Test
    fun nearestRankPercentiles() {
        val values = doubleArrayOf(5.0, 1.0, 4.0, 2.0, 3.0)
        assertEquals(1.0, PassStats.percentile(values, 0.0))
        assertEquals(3.0, PassStats.percentile(values, 50.0))
        assertEquals(5.0, PassStats.percentile(values, 90.0))
        assertEquals(5.0, PassStats.percentile(values, 100.0))
        assertEquals(0.0, PassStats.percentile(DoubleArray(0), 50.0))
    }

    @Test
    fun aPassWithOneFrameHasNoGaps() {
        val stats = PassStats.of("up", longArrayOf(5), longArrayOf(5), 0, 0, 0)
        assertEquals(0, stats.intervalsMs.size)
        assertEquals(0.0, stats.fps)
    }

    @Test
    fun theRowUsesADotForDecimalsInEveryLocale() {
        val saved = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            val row = PassStats.of("down", starts, busy, 1, 2, 3).row()
            assertEquals(PassStats.HEADER.size, row.size)
            assertEquals("0.12", row[2])
            assertEquals("16.0", row[4])
        } finally {
            Locale.setDefault(saved)
        }
    }
}
