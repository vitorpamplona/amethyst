/**
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
package com.vitorpamplona.amethyst.ui.feeds

import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class NotePrefetchManager(
    private val feedState: FeedContentState,
    private val prefetchState: LazyLayoutPrefetchState,
    private val coroutineScope: CoroutineScope,
) {
    private var lastPrefetchedIndex = -1
    private var lastScrollTime = 0L
    private val prefetchThreshold = 25
    private val prefetchBatchSize = 50
    private val scrollDebounceMs = 150L // Debounce fast scrolling

    fun updatePrefetching(
        currentVisibleIndex: Int,
        totalItems: Int,
    ) {
        val currentTime = System.currentTimeMillis()

        // Only prefetch if we're within 25 items of the end and not scrolling too fast
        if (currentVisibleIndex >= totalItems - prefetchThreshold &&
            currentTime - lastScrollTime > scrollDebounceMs
        ) {
            val startIndex = maxOf(lastPrefetchedIndex + 1, totalItems)
            val endIndex = minOf(startIndex + prefetchBatchSize, totalItems + prefetchBatchSize)

            Log.d("NotePrefetchManager", "Prefetching items from $startIndex to $endIndex")

            // Schedule prefetching for upcoming items
            for (index in startIndex until endIndex) {
                prefetchState.schedulePrecomposition(index)
            }

            lastPrefetchedIndex = endIndex - 1
            lastScrollTime = currentTime

            // Trigger data loading for the prefetched range
            coroutineScope.launch {
                feedState.preFetchOlderNotes(currentVisibleIndex + prefetchThreshold)
            }
        }
    }
}
