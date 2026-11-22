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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalPlatformPrefetchScheduler
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedCacheWindow
import com.vitorpamplona.amethyst.commons.ui.feeds.FirstShownItem
import com.vitorpamplona.amethyst.commons.ui.feeds.LocalFirstShown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A feed that composes a screen ahead ([FeedCacheWindow]) must not treat those rows as seen: what
 * waits for [LocalFirstShown] (marking a note read, its "new" highlight) runs only once a row shows.
 */
@OptIn(InternalComposeUiApi::class, ExperimentalFoundationApi::class)
class FeedCacheWindowTest {
    private val ms = 1_000_000L

    @Test
    fun rowsComposedAheadAreNotSeenUntilTheyScrollIn() {
        val composed = mutableSetOf<Int>()
        val seen = mutableSetOf<Int>()
        val state = LazyListState(FeedCacheWindow, 0, 0)
        lateinit var launcher: CoroutineScope
        // Rows 60 px tall in a 200 px list: rows 0..3 show, a screen ahead reaches row 7.
        val scene =
            ImageComposeScene(100, 200, Density(1f), coroutineContext = Dispatchers.Unconfined) {
                launcher = rememberCoroutineScope()
                val scope = rememberCoroutineScope()
                val scheduler = remember(scope) { FramePrefetchScheduler(scope, frameIntervalNanos = { 1_000 * ms }, afterFrame = {}) }
                CompositionLocalProvider(LocalPlatformPrefetchScheduler provides scheduler) {
                    LazyColumn(Modifier.size(100.dp, 200.dp), state = state) {
                        items(100) { row ->
                            FirstShownItem { shown ->
                                val firstShown = LocalFirstShown.current!!
                                DisposableEffect(row) {
                                    composed += row
                                    onDispose { composed -= row }
                                }
                                LaunchedEffect(row) {
                                    firstShown.await()
                                    seen += row
                                }
                                Box(shown.fillMaxWidth().height(60.dp))
                            }
                        }
                    }
                }
            }
        try {
            var time = 0L

            fun frames(count: Int) = repeat(count) { scene.render(time).also { time += 16 * ms } }

            frames(10)
            assertEquals(setOf(0, 1, 2, 3), seen, "only the rows on screen are seen")
            assertTrue(composed.containsAll(listOf(4, 5, 6)), "the window composed the next screen in idle time: $composed")

            launcher.launch { state.scrollBy(130f) }
            frames(3)
            assertTrue(5 in seen, "a row that scrolled in is seen: $seen")
            assertTrue(8 !in seen, "a row still below the screen is not seen: $seen")
        } finally {
            scene.close()
        }
    }
}
