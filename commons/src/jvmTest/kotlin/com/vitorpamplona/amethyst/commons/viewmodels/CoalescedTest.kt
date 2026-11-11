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
package com.vitorpamplona.amethyst.commons.viewmodels

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The account feeds take every event bundle: a slow collector gets what arrived meanwhile as one
 * bigger set, instead of losing bundles (a shared flow drops the oldest) or queuing them all.
 */
class CoalescedTest {
    @Test
    fun whatArrivesWhileTheCollectorIsBusyComesAsOneSet() =
        runBlocking {
            val upstream = MutableSharedFlow<Set<Int>>(extraBufferCapacity = 16)
            val firstTaken = CompletableDeferred<Unit>()
            val goOn = CompletableDeferred<Unit>()
            val received = mutableListOf<Set<Int>>()

            val collector =
                launch {
                    upstream.coalesced().take(2).collect {
                        received.add(it)
                        if (received.size == 1) {
                            firstTaken.complete(Unit)
                            goOn.await()
                        }
                    }
                }
            // Wait for the collector to subscribe before emitting.
            while (upstream.subscriptionCount.value == 0) yield()

            upstream.emit(setOf(1))
            firstTaken.await()
            upstream.emit(setOf(2, 3))
            upstream.emit(setOf(3, 4))
            upstream.emit(setOf(5))
            // Give the merge a moment to take all three in before the collector asks again.
            delay(100)
            goOn.complete(Unit)
            collector.join()

            assertEquals(listOf(setOf(1), setOf(2, 3, 4, 5)), received)
        }

    @Test
    fun aFinishedUpstreamStillDeliversWhatIsPending() =
        runBlocking {
            val sets = flowOf(setOf(1), setOf(2))
            assertEquals(
                setOf(1, 2),
                sets
                    .coalesced()
                    .toList()
                    .flatten()
                    .toSet(),
            )
        }
}
