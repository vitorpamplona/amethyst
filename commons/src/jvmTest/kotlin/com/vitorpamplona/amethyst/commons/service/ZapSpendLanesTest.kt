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
package com.vitorpamplona.amethyst.commons.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * The zap's cashu lanes must outlive any timer.
 *
 * The incident these tests guard against: the lanes ran through Quartz's `launchAndWaitAll`,
 * which cancels whatever is still running after fifteen seconds. On a remote signer (Amber over
 * NIP-55, a bunker) every step of a melt is a prompt, so a lane always took longer: the melt was
 * cancelled with the invoice paid and the proofs spent at the mint, and the change, delete and
 * history events that record it were never written.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZapSpendLanesTest {
    @Test
    fun aLaneOutlivesTheOldFifteenSecondCap() =
        runTest {
            val finished = mutableListOf<Int>()

            // Five prompts at eight seconds each is a fast user on a remote signer.
            runSpendLanes(listOf(0, 1)) { index ->
                delay(40.seconds)
                finished += index
            }

            assertEquals(listOf(0, 1), finished)
        }

    @Test
    fun lanesStillRunInParallel() =
        runTest {
            runSpendLanes(listOf(0, 1, 2)) { delay(10.seconds) }

            assertEquals(10_000, currentTime)
        }

    @Test
    fun aCancellationStillStopsEveryLane() =
        runTest {
            var finished = false

            val job =
                launch {
                    runSpendLanes(listOf(0, 1)) {
                        delay(TimeUnit.MINUTES.toMillis(5))
                        finished = true
                    }
                }

            runCurrent()
            job.cancelAndJoin()

            assertFalse(finished)
        }
}
