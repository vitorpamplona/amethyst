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
package com.vitorpamplona.amethyst.service.eventCache

import com.vitorpamplona.amethyst.service.eventCache.MemoryPressureMonitor.Companion.isUnderPressure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryPressureMonitorTest {
    private val mb = 1024L * 1024L

    @Test
    fun belowWatermarkIsNotUnderPressure() {
        assertFalse(isUnderPressure(usedBytes = 400 * mb, maxBytes = 512 * mb, watermark = 0.85))
    }

    @Test
    fun atWatermarkIsUnderPressure() {
        // 0.85 * 512 = 435.2 MB
        assertTrue(isUnderPressure(usedBytes = 436 * mb, maxBytes = 512 * mb, watermark = 0.85))
    }

    @Test
    fun wellAboveWatermarkIsUnderPressure() {
        assertTrue(isUnderPressure(usedBytes = 510 * mb, maxBytes = 512 * mb, watermark = 0.85))
    }

    @Test
    fun unknownMaxHeapNeverReportsPressure() {
        // Runtime.maxMemory() returns Long.MAX_VALUE / 0 / -1 on platforms that can't report a cap.
        assertFalse(isUnderPressure(usedBytes = 1_000 * mb, maxBytes = 0L, watermark = 0.85))
        assertFalse(isUnderPressure(usedBytes = 1_000 * mb, maxBytes = -1L, watermark = 0.85))
    }

    @Test
    fun firesOnPressureOncePerProbeWhenHeapIsHigh() =
        runTest {
            var trims = 0
            val monitor =
                MemoryPressureMonitor(
                    checkIntervalMs = 10L,
                    highWatermark = 0.85,
                    usedHeapBytes = { 500L * mb },
                    maxHeapBytes = { 512L * mb },
                    onPressure = { trims++ },
                )

            // run() loops forever; advance exactly one interval then stop it.
            val job = launch { monitor.run() }
            advanceTimeBy(11L)
            job.cancel()

            assertEquals(1, trims)
        }

    @Test
    fun doesNotTrimWhenHeapIsBelowWatermark() =
        runTest {
            var trims = 0
            val monitor =
                MemoryPressureMonitor(
                    checkIntervalMs = 10L,
                    highWatermark = 0.85,
                    usedHeapBytes = { 100L * mb },
                    maxHeapBytes = { 512L * mb },
                    onPressure = { trims++ },
                )

            val job = launch { monitor.run() }
            advanceTimeBy(11L)
            job.cancel()

            assertEquals(0, trims)
        }
}
