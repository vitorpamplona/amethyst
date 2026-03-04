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
package com.vitorpamplona.amethyst.service.playback.composable.mainVideo

import android.graphics.Rect
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import kotlin.math.abs

// This keeps the position of all visible videos in the current screen.
val trackingVideos = mutableSetOf<MediaControllerState>()

/**
 * This function selects only one Video to be active. The video that is closest to the center of the
 * screen wins the mutex.
 */
@Composable
fun VideoPlayerActiveMutex(
    controller: MediaControllerState,
    inner: @Composable (Modifier, MutableState<Boolean>) -> Unit,
) {
    // Is the current video the closest to the center?
    val isClosestToTheCenterOfTheScreen = remember(controller) { mutableStateOf(false) }

    // Keep track of all available videos.
    DisposableEffect(key1 = controller) {
        trackingVideos.add(controller)
        onDispose {
            trackingVideos.remove(controller)
        }
    }

    val view = LocalView.current

    val videoModifier =
        remember(controller) {
            Modifier.fillMaxWidth().heightIn(min = 100.dp).onVisiblePositionChanges(view) { bounds, distanceToCenter ->
                controller.visibility.bounds = bounds
                controller.visibility.distanceToCenter = distanceToCenter

                if (distanceToCenter != null) {
                    // finds out of the current video is the closest to the center.
                    var newActive = true
                    for (video in trackingVideos) {
                        val videoPos = video.visibility.distanceToCenter
                        if (videoPos != null && videoPos < distanceToCenter) {
                            newActive = false
                            break
                        }
                    }

                    // marks the current video active
                    if (isClosestToTheCenterOfTheScreen.value != newActive) {
                        isClosestToTheCenterOfTheScreen.value = newActive
                    }
                } else {
                    // got out of screen, marks video as inactive
                    if (isClosestToTheCenterOfTheScreen.value) {
                        isClosestToTheCenterOfTheScreen.value = false
                    }
                }
            }
        }

    inner(videoModifier, isClosestToTheCenterOfTheScreen)
}

fun Modifier.onVisiblePositionChanges(
    view: View,
    onVisiblePosition: (Rect, Float?) -> Unit,
): Modifier =
    onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        val boundRect = Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
        onVisiblePosition(boundRect, coordinates.getDistanceToVertCenterIfVisible(boundRect, view))
    }

fun LayoutCoordinates.getDistanceToVertCenterIfVisible(
    bounds: Rect,
    view: View,
): Float? {
    if (!isAttached) return null
    // Window relative bounds of our compose root view that are visible on the screen
    val globalRootRect = Rect()
    if (!view.getGlobalVisibleRect(globalRootRect)) {
        // we aren't visible at all.
        return null
    }

    if (bounds.isEmpty) return null

    // Make sure we are completely in bounds.
    if (
        bounds.top >= globalRootRect.top &&
        bounds.left >= globalRootRect.left &&
        bounds.right <= globalRootRect.right &&
        bounds.bottom <= globalRootRect.bottom
    ) {
        return abs(
            ((bounds.top + bounds.bottom) / 2.0f) - ((globalRootRect.top + globalRootRect.bottom) / 2.0f),
        )
    }

    return null
}
