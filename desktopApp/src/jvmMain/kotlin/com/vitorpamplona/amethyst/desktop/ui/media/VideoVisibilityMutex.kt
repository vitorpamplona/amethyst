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
package com.vitorpamplona.amethyst.desktop.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import kotlin.math.abs

/**
 * The desktop take on Android's `VideoPlayerActiveMutex`: of all the inline videos on screen, the
 * one closest to the middle of the window wins, so a feed has one playing video at a time. A video
 * counts only while (nearly) all of it shows: one cut off by the window edge or by the list's
 * clip has lost.
 *
 * Compose desktop runs these callbacks on the UI thread, so the shared registry needs no locking.
 */
private class TrackedVideo(
    val active: MutableState<Boolean>,
) {
    var distanceToCenter: Float? = null
}

private val trackedVideos = ArrayList<TrackedVideo>()
private var winner: TrackedVideo? = null

/** How much of a video must show for it to count as on screen. */
private const val MIN_VISIBLE_FRACTION = 0.95f

/**
 * Registers the calling video with the mutex. Returns the modifier to put on the video's outer
 * box, which reports where it is, and whether it is the winner now.
 */
@Composable
fun rememberVideoVisibilityWinner(key: Any): Pair<Modifier, State<Boolean>> {
    val active = remember(key) { mutableStateOf(false) }
    val tracked = remember(active) { TrackedVideo(active) }

    DisposableEffect(tracked) {
        trackedVideos.add(tracked)
        onDispose {
            trackedVideos.remove(tracked)
            tracked.distanceToCenter = null
            if (winner === tracked) {
                tracked.active.value = false
                winner = null
                electNewWinner()
            }
        }
    }

    val windowInfo = LocalWindowInfo.current
    val modifier =
        remember(tracked, windowInfo) {
            Modifier.onGloballyPositioned { coordinates ->
                if (!coordinates.isAttached || coordinates.size.height <= 0) {
                    reportPosition(tracked, null)
                    return@onGloballyPositioned
                }
                // boundsInWindow is clipped by every ancestor, so a video half scrolled out of
                // its list measures shorter than it is.
                val shown = coordinates.boundsInWindow()
                val fullyShown =
                    shown.height >= coordinates.size.height * MIN_VISIBLE_FRACTION &&
                        shown.width >= coordinates.size.width * MIN_VISIBLE_FRACTION
                val windowCenter = windowInfo.containerSize.height / 2f
                reportPosition(tracked, if (fullyShown) abs(shown.center.y - windowCenter) else null)
            }
        }

    return modifier to active
}

private fun reportPosition(
    tracked: TrackedVideo,
    distanceToCenter: Float?,
) {
    if (tracked.distanceToCenter == distanceToCenter) return
    tracked.distanceToCenter = distanceToCenter

    if (distanceToCenter == null) {
        if (winner === tracked) {
            tracked.active.value = false
            winner = null
            electNewWinner()
        }
        return
    }

    val current = winner
    when {
        // The winner moved: on the last frame of a scroll it may be farther than one that stopped
        // nearer the center, which reported before and will not report again.
        current === tracked -> {
            val closer = trackedVideos.minByOrNull { it.distanceToCenter ?: Float.MAX_VALUE }
            val closerDistance = closer?.distanceToCenter
            if (closer != null && closer !== tracked && closerDistance != null && closerDistance < distanceToCenter) {
                tracked.active.value = false
                winner = closer
                closer.active.value = true
            }
        }

        current == null -> {
            winner = tracked
            tracked.active.value = true
        }

        else -> {
            val currentDistance = current.distanceToCenter
            if (currentDistance == null || distanceToCenter < currentDistance) {
                current.active.value = false
                winner = tracked
                tracked.active.value = true
            }
        }
    }
}

private fun electNewWinner() {
    var best: TrackedVideo? = null
    var bestDistance = Float.MAX_VALUE
    for (candidate in trackedVideos) {
        val distance = candidate.distanceToCenter ?: continue
        if (distance < bestDistance) {
            bestDistance = distance
            best = candidate
        }
    }
    if (best != null) {
        winner = best
        best.active.value = true
    }
}
