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
package com.vitorpamplona.amethyst.desktop.service.frontpage

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopFrontPageSchedulerTest {
    @Test
    fun aSleepingLaptopStillPrintsOnTimeAfterItWakes() =
        runTest {
            var wall = 1_000L
            val pauses = mutableListOf<Long>()
            // Eight hours to the morning edition; the lid closes for seven of them during the first slice.
            DesktopFrontPageScheduler.untilWallClock(
                atSeconds = wall + 8 * 3600,
                now = { wall },
                pause = { ms ->
                    pauses.add(ms)
                    wall += if (pauses.size == 1) 7 * 3600 else ms / 1000
                },
            )
            assertTrue(pauses.all { it <= 60_000L })
            // One slice slept through seven hours; the hour left took sixty more slices, not seven hours.
            assertEquals(61, pauses.size)
        }

    @Test
    fun aTimeAlreadyPastReturnsAtOnce() =
        runTest {
            var paused = false
            DesktopFrontPageScheduler.untilWallClock(atSeconds = 10, now = { 20 }, pause = { paused = true })
            assertTrue(!paused)
        }
}
