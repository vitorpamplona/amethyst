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
package com.vitorpamplona.quartz.nip01Core.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The point of waiting rather than out-stamping: `created_at` is whole seconds, so one second is the
 * real floor on how often an address can be replaced. A client that replaces one faster has to wait
 * for the clock — inventing a timestamp instead drifts a second further into the future per
 * republish, and relays reject events too far ahead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AwaitCreatedAtToSupersedeTest {
    @Test
    fun stampsTheClockWhenItHasAlreadyMovedPastTheNewestVersion() =
        runTest {
            advanceTimeBy(10_000)
            val clock = { testScheduler.currentTime / 1000 }

            val stamp = awaitCreatedAtToSupersede(newestKnown = 5, now = clock)

            assertEquals(10L, stamp)
            assertEquals(10_000L, testScheduler.currentTime, "must not wait when there is nothing to wait for")
        }

    @Test
    fun waitsOutTheSecondTheNewestVersionClaimedInsteadOfStampingTheFuture() =
        runTest {
            advanceTimeBy(10_000)
            val clock = { testScheduler.currentTime / 1000 }

            val stamp = awaitCreatedAtToSupersede(newestKnown = 10, now = clock)

            assertEquals(11L, stamp)
            assertEquals(11_000L, testScheduler.currentTime, "should have waited exactly the one second out")
            assertTrue(stamp <= clock(), "a stamp must never be ahead of the clock")
        }

    @Test
    fun aBurstNeverRunsAheadOfTheClock() =
        runTest {
            advanceTimeBy(10_000)
            val clock = { testScheduler.currentTime / 1000 }

            // Five back-to-back republishes, each superseding the one before it.
            var newest = 10L
            repeat(5) {
                newest = awaitCreatedAtToSupersede(newestKnown = newest, now = clock)
                assertTrue(newest <= clock(), "republish $it stamped the future")
            }

            assertEquals(15L, newest)
        }

    @Test
    fun outStampsAVersionTooFarAheadToWaitOut() =
        runTest {
            advanceTimeBy(10_000)
            val clock = { testScheduler.currentTime / 1000 }

            // Another device's skewed clock. Waiting it out would mean sleeping for hours, so the
            // only way to supersede it is still to out-stamp it.
            val stamp = awaitCreatedAtToSupersede(newestKnown = 10 + MAX_SUPERSEDE_WAIT_SECONDS + 1, now = clock)

            assertEquals(17L, stamp)
            assertEquals(10_000L, testScheduler.currentTime, "must not wait out an implausible skew")
        }
}
