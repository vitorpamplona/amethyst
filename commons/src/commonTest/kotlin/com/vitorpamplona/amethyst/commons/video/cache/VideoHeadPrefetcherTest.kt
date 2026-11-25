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
package com.vitorpamplona.amethyst.commons.video.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoHeadPrefetcherTest {
    private var clock = 0L

    @Test
    fun nearestFirstAndEachOnlyOnce() =
        runTest {
            val fetched = mutableListOf<String>()
            val prefetcher =
                VideoHeadPrefetcher(backgroundScope, workers = 1, now = { clock }) {
                    fetched += it
                    true
                }
            prefetcher.prefetch("feed", listOf("a", "b", "c"))
            runCurrent()
            assertEquals(listOf("a", "b", "c"), fetched)
            prefetcher.prefetch("feed", listOf("b", "d"))
            runCurrent()
            assertEquals(listOf("a", "b", "c", "d"), fetched)
        }

    @Test
    fun theMostRecentFeedGoesFirst() {
        val prefetcher = VideoHeadPrefetcher(CoroutineScope(Job()), workers = 0, now = { clock }) { true }
        prefetcher.prefetch("home", listOf("h1", "shared"))
        prefetcher.prefetch("thread", listOf("t1", "shared"))
        assertEquals("t1", prefetcher.next())
        assertEquals("shared", prefetcher.next())
        assertEquals("h1", prefetcher.next())
        assertNull(prefetcher.next())
    }

    @Test
    fun aFailureIsRetriedOnlyAfterAWhile() =
        runTest {
            var attempts = 0
            val prefetcher =
                VideoHeadPrefetcher(backgroundScope, workers = 1, now = { clock }) {
                    attempts++
                    false
                }
            prefetcher.prefetch("feed", listOf("a"))
            runCurrent()
            assertEquals(1, attempts)
            prefetcher.prefetch("feed", listOf("a"))
            runCurrent()
            assertEquals(1, attempts)
            clock += VideoHeadPrefetcher.RETRY_FAILED_MS
            prefetcher.prefetch("feed", listOf("a"))
            runCurrent()
            assertEquals(2, attempts)
        }

    @Test
    fun everyFeedLeavingCancelsAndAllowsARetry() =
        runTest {
            val started = mutableListOf<String>()
            var gate = CompletableDeferred<Unit>()
            val prefetcher =
                VideoHeadPrefetcher(backgroundScope, workers = 1, now = { clock }) {
                    started += it
                    gate.await()
                    true
                }
            prefetcher.prefetch("feed", listOf("a", "b"))
            runCurrent()
            assertEquals(listOf("a"), started)
            // Another feed's update does not stop the download in flight.
            prefetcher.prefetch("other", listOf("x"))
            runCurrent()
            assertEquals(listOf("a"), started)
            prefetcher.prefetch("feed", emptyList())
            prefetcher.prefetch("other", emptyList())
            runCurrent()
            gate = CompletableDeferred<Unit>().also { it.complete(Unit) }
            prefetcher.prefetch("feed", listOf("a"))
            runCurrent()
            assertEquals(listOf("a", "a"), started)
        }
}
