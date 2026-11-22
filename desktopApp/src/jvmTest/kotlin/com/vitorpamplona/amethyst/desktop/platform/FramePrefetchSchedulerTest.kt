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
package com.vitorpamplona.amethyst.desktop.platform

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalPlatformPrefetchScheduler
import androidx.compose.ui.platform.PlatformPrefetchRequest
import androidx.compose.ui.platform.PlatformPrefetchRequestScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class FramePrefetchSchedulerTest {
    private val ms = 1_000_000L

    /** Rows 60 px tall in a 200 px list: rows 0..3 are on screen, row 4 (from 240 px) is the next to scroll in. */
    private fun composedRows(prefetch: Boolean): Set<Int> {
        val composed = mutableSetOf<Int>()
        val state = LazyListState()
        lateinit var launcher: CoroutineScope
        val scene =
            ImageComposeScene(100, 200, Density(1f), coroutineContext = Dispatchers.Unconfined) {
                launcher = rememberCoroutineScope()
                Provide(prefetch) {
                    LazyColumn(Modifier.size(100.dp, 200.dp), state = state) {
                        items(100) { row ->
                            DisposableEffect(row) {
                                composed += row
                                onDispose { composed -= row }
                            }
                            Box(Modifier.fillMaxWidth().height(60.dp))
                        }
                    }
                }
            }
        try {
            var time = 0L
            repeat(3) { scene.render(time).also { time += 16 * ms } }
            // A small scroll down: row 4 is still off screen, but the list now expects it next.
            launcher.launch { state.scrollBy(10f) }
            repeat(5) { scene.render(time).also { time += 16 * ms } }
            assertEquals(0, state.firstVisibleItemIndex)
            return composed.toSet()
        } finally {
            scene.close()
        }
    }

    @Composable
    private fun Provide(
        prefetch: Boolean,
        content: @Composable () -> Unit,
    ) {
        if (!prefetch) return content()
        val scope = rememberCoroutineScope()
        // An idle gap long enough for any request, and no Swing queue to wait on.
        val scheduler = remember(scope) { FramePrefetchScheduler(scope, frameIntervalNanos = { 1_000 * ms }, afterFrame = {}) }
        CompositionLocalProvider(LocalPlatformPrefetchScheduler provides scheduler, content = content)
    }

    @Test
    fun theDefaultSchedulerComposesNothingAhead() {
        assertFalse(4 in composedRows(prefetch = false), "Compose's desktop default prefetches after all; this scheduler may be redundant")
    }

    @Test
    fun theRowAboutToScrollInIsComposedAhead() {
        assertTrue(4 in composedRows(prefetch = true), "row 4 was not prefetched")
    }

    @Test
    fun highPriorityRunsFirstAndUnfinishedWorkWaitsForTheNextFrame() {
        val ran = mutableListOf<String>()
        var frames = 0

        fun request(
            name: String,
            moreWorkTimes: Int = 0,
        ): PlatformPrefetchRequest {
            var left = moreWorkTimes
            return object : PlatformPrefetchRequest {
                override fun PlatformPrefetchRequestScope.execute(): Boolean {
                    ran += "$name@$frames"
                    return left-- > 0
                }
            }
        }
        val scene =
            ImageComposeScene(10, 10, Density(1f), coroutineContext = Dispatchers.Unconfined) {
                val compositionScope = rememberCoroutineScope()
                remember(compositionScope) {
                    val scheduler = FramePrefetchScheduler(compositionScope, frameIntervalNanos = { 1_000 * ms }, afterFrame = { frames++ })
                    scheduler.scheduleLowPriorityPrefetch(request("low"))
                    scheduler.scheduleHighPriorityPrefetch(request("high", moreWorkTimes = 1))
                    scheduler
                }
            }
        try {
            var time = 0L
            repeat(4) { scene.render(time).also { time += 16 * ms } }
        } finally {
            scene.close()
        }
        // The high-priority request runs first, asks for more time, and finishes a frame later
        // before the low-priority one runs.
        assertEquals(listOf("high@1", "high@2", "low@2"), ran)
    }

    @Test
    fun aFrameThatRanLateLeavesNoTimeForPrefetch() {
        val ran = mutableListOf<String>()
        val scene =
            ImageComposeScene(10, 10, Density(1f), coroutineContext = Dispatchers.Unconfined) {
                val compositionScope = rememberCoroutineScope()
                remember(compositionScope) {
                    // The next frame is due the moment this one starts: every frame is late.
                    val scheduler = FramePrefetchScheduler(compositionScope, frameIntervalNanos = { 0 }, afterFrame = {})
                    scheduler.scheduleHighPriorityPrefetch(
                        object : PlatformPrefetchRequest {
                            override fun PlatformPrefetchRequestScope.execute(): Boolean {
                                ran += "high"
                                return false
                            }
                        },
                    )
                    scheduler
                }
            }
        try {
            var time = 0L
            repeat(4) { scene.render(time).also { time += 16 * ms } }
        } finally {
            scene.close()
        }
        assertEquals(emptyList(), ran)
    }
}
