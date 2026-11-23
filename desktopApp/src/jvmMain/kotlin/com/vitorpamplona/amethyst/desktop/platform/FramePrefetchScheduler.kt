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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.LocalPlatformPrefetchScheduler
import androidx.compose.ui.platform.PlatformPrefetchRequest
import androidx.compose.ui.platform.PlatformPrefetchRequestScope
import androidx.compose.ui.platform.PlatformPrefetchScheduler
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedCacheWindow
import com.vitorpamplona.amethyst.commons.ui.feeds.LocalLazyListCacheWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.awt.DisplayMode
import java.awt.Window
import javax.swing.SwingUtilities
import kotlin.coroutines.resume

/**
 * Lets lazy lists compose the items about to scroll in between frames, as they do on Android.
 *
 * Compose for desktop ships a scheduler that drops every prefetch request
 * (`NoOpPlatformPrefetchScheduler`), so a card is composed for the first time inside the frame
 * that scrolls it into view: the first pass down a feed spent a third of its UI-thread time in
 * that composition. This one runs the requests after each frame has drawn, in the time left
 * before the next one is due.
 */
@OptIn(InternalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun ProvideFramePrefetch(
    window: Window,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scheduler = remember(scope, window) { FramePrefetchScheduler(scope, frameIntervalNanos = { frameIntervalNanos(window) }) }
    // With prefetches that run, a feed that asks for it composes a screen of notes ahead
    // (rememberForeverLazyListState(cacheAhead)).
    CompositionLocalProvider(
        LocalPlatformPrefetchScheduler provides scheduler,
        LocalLazyListCacheWindow provides FeedCacheWindow,
        content = content,
    )
}

/**
 * Runs prefetch requests in the idle time between frames, the way Android's scheduler runs them
 * after each Choreographer frame: high priority first, while the next frame is not yet due; a
 * request that reports more work waits for the next frame.
 *
 * Unlike Android's, a frame that already ran past the next one's start runs nothing: with Skia
 * drawing in software the first pass down a feed is over budget on most frames, and composing a
 * card after each of them pushed every next frame back (first-pass fps fell 38 to 32). There the
 * list composes the card in the frame that needs it, as it did with no scheduler.
 *
 * Confined to the UI thread: requests arrive during measure and run from [scope], which is the
 * composition's.
 */
@OptIn(InternalComposeUiApi::class)
internal class FramePrefetchScheduler(
    private val scope: CoroutineScope,
    private val frameIntervalNanos: () -> Long,
    private val afterFrame: suspend () -> Unit = ::afterCurrentEvent,
) : PlatformPrefetchScheduler {
    private val high = ArrayDeque<PlatformPrefetchRequest>()
    private val low = ArrayDeque<PlatformPrefetchRequest>()
    private var running = false

    /** Requests waiting for idle time. */
    internal val pending: Int get() = high.size + low.size

    override fun scheduleHighPriorityPrefetch(request: PlatformPrefetchRequest) {
        high.addLast(request)
        start()
    }

    override fun scheduleLowPriorityPrefetch(request: PlatformPrefetchRequest) {
        low.addLast(request)
        start()
    }

    private fun start() {
        if (running) return
        running = true
        scope.launch {
            try {
                // Read once a burst (a scroll), not every frame: on X11 it is a display query.
                val interval = frameIntervalNanos()
                var framesWithoutProgress = 0
                while (high.isNotEmpty() || low.isNotEmpty()) {
                    val frameStart = withFrameNanos { System.nanoTime() }
                    afterFrame()
                    if (runUntil(frameStart + interval)) {
                        framesWithoutProgress = 0
                    } else if (++framesWithoutProgress >= MAX_FRAMES_WITHOUT_PROGRESS) {
                        // No frame leaves enough time (a large window drawn in software, an animation,
                        // items slower to compose than the gaps between frames): stop asking for frames
                        // and let the list compose its items when it needs them, as it does with no
                        // scheduler.
                        high.clear()
                        low.clear()
                    }
                }
            } finally {
                running = false
            }
        }
    }

    /**
     * Runs requests until [deadline]; returns whether one finished. A request declines with "more
     * work" when the time left is shorter than what its item usually takes to compose.
     */
    private fun runUntil(deadline: Long): Boolean {
        val budget = Budget(deadline)
        var finished = false
        while (budget.availableTimeNanos() > 0) {
            val queue = if (high.isNotEmpty()) high else low
            if (queue.isEmpty()) break
            val request = queue.first()
            if (with(request) { budget.execute() }) break
            queue.removeFirst()
            finished = true
        }
        return finished
    }

    private class Budget(
        private val deadline: Long,
    ) : PlatformPrefetchRequestScope {
        override fun availableTimeNanos(): Long = (deadline - System.nanoTime()).coerceAtLeast(0)
    }
}

/** Resumes once the event that is running (the frame being drawn) has finished. */
private suspend fun afterCurrentEvent() = suspendCancellableCoroutine { continuation -> SwingUtilities.invokeLater { continuation.resume(Unit) } }

private const val DEFAULT_REFRESH_HZ = 60

/**
 * Frames in a row in which no request finished, after which the scheduler drops its queue and stops
 * asking for frames. A request split over several frames (a card with a nested list) finishes well
 * within it.
 */
private const val MAX_FRAMES_WITHOUT_PROGRESS = 8

/** The refresh interval of the screen [window] is on; 60 Hz when the screen doesn't say (Xvfb, some Wayland setups). */
private fun frameIntervalNanos(window: Window): Long {
    val hz =
        window.graphicsConfiguration
            ?.device
            ?.displayMode
            ?.refreshRate
            ?.takeIf { it != DisplayMode.REFRESH_RATE_UNKNOWN } ?: DEFAULT_REFRESH_HZ
    return 1_000_000_000L / hz
}
