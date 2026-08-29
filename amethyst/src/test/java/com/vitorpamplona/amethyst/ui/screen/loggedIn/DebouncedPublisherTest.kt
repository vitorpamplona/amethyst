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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The publish side of the navigation pickers: a configuring session must reach the relays as one
 * event, and no exit path may strand the last toggle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DebouncedPublisherTest {
    private val debounce = 2_000L

    @Test
    fun aBurstOfEditsPublishesOnce() =
        runTest {
            var published = 0
            val publisher = publisher { published++ }

            repeat(10) {
                publisher.schedule()
                advanceTimeBy(200)
            }
            advanceUntilIdle()

            assertEquals(1, published)
        }

    @Test
    fun editsFurtherApartThanTheDebouncePublishSeparately() =
        runTest {
            var published = 0
            val publisher = publisher { published++ }

            publisher.schedule()
            advanceUntilIdle()
            publisher.schedule()
            advanceUntilIdle()

            assertEquals(2, published)
        }

    @Test
    fun flushPublishesWithoutWaitingOutTheDebounce() =
        runTest {
            var published = 0
            val publisher = publisher { published++ }

            publisher.schedule()
            advanceTimeBy(100)
            assertEquals(0, published)

            publisher.flush()
            advanceUntilIdle()

            assertEquals(1, published)
        }

    @Test
    fun flushingTwiceOnTheWayOutDoesNotPublishTwice() =
        runTest {
            // Leaving a picker screen can hit both arms of FlushPickerEditsOnExit (ON_STOP, then
            // onDispose), and onCleared may follow. Only the first one may publish.
            var published = 0
            val publisher = publisher { published++ }

            publisher.schedule()
            publisher.flush()
            advanceUntilIdle()

            publisher.flush()
            advanceUntilIdle()

            assertEquals(1, published)
        }

    @Test
    fun flushWithNothingPendingIsANoOp() =
        runTest {
            var published = 0
            val publisher = publisher { published++ }

            publisher.flush()
            advanceUntilIdle()

            assertEquals(0, published)
        }

    @Test
    fun cancelHandsThePendingEditToTheCaller() =
        runTest {
            // What onCleared relies on: it takes the pending edit off this publisher and republishes
            // it on the account's scope, which must not leave the original to fire as well.
            var published = 0
            val publisher = publisher { published++ }

            publisher.schedule()
            assertTrue(publisher.isPending())

            publisher.cancel()
            advanceUntilIdle()

            assertEquals(0, published)
            assertFalse(publisher.isPending())
        }

    private fun kotlinx.coroutines.test.TestScope.publisher(publish: suspend () -> Unit) =
        DebouncedPublisher(
            debounceMs = debounce,
            launch = { block -> launch { block() } },
            publish = publish,
        )
}
