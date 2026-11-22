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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.awt.DisplayMode
import java.awt.GraphicsEnvironment
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
@OptIn(InternalComposeUiApi::class)
@Composable
fun ProvideFramePrefetch(content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val scheduler = remember(scope) { FramePrefetchScheduler(scope) }
    CompositionLocalProvider(LocalPlatformPrefetchScheduler provides scheduler, content = content)
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
    private val frameIntervalNanos: () -> Long = ::displayFrameIntervalNanos,
    private val afterFrame: suspend () -> Unit = ::afterCurrentEvent,
) : PlatformPrefetchScheduler {
    private val high = ArrayDeque<PlatformPrefetchRequest>()
    private val low = ArrayDeque<PlatformPrefetchRequest>()
    private var running = false

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
                while (high.isNotEmpty() || low.isNotEmpty()) {
                    val frameStart = withFrameNanos { System.nanoTime() }
                    afterFrame()
                    runUntil(frameStart + frameIntervalNanos())
                }
            } finally {
                running = false
            }
        }
    }

    private fun runUntil(deadline: Long) {
        val budget = Budget(deadline)
        while (budget.availableTimeNanos() > 0) {
            val queue = if (high.isNotEmpty()) high else low
            if (queue.isEmpty()) return
            val request = queue.first()
            if (with(request) { budget.execute() }) return
            queue.removeFirst()
        }
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

private fun displayFrameIntervalNanos(): Long {
    val hz =
        if (GraphicsEnvironment.isHeadless()) {
            DEFAULT_REFRESH_HZ
        } else {
            GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .defaultScreenDevice.displayMode.refreshRate
                .takeIf { it != DisplayMode.REFRESH_RATE_UNKNOWN && it > 0 } ?: DEFAULT_REFRESH_HZ
        }
    return 1_000_000_000L / hz
}
