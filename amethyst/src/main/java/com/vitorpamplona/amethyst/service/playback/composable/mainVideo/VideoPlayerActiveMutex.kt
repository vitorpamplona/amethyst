/**
 * Copyright (c) 2024 Vitor Pamplona
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlin.math.abs

// This keeps the position of all visible videos in the current screen.
val trackingVideos = mutableListOf<VisibilityData>()

@Stable
class VisibilityData {
    var distanceToCenter: Float? = null
}

/**
 * This function selects only one Video to be active. The video that is closest to the center of the
 * screen wins the mutex.
 */
@Composable
fun VideoPlayerActiveMutex(
    controller: String,
    inner: @Composable (Modifier, MutableState<Boolean>) -> Unit,
) {
    val myCache = remember(controller) { VisibilityData() }

    // Is the current video the closest to the center?
    val isClosestToTheCenterOfTheScreen = remember(controller) { mutableStateOf<Boolean>(false) }

    // Keep track of all available videos.
    DisposableEffect(key1 = controller) {
        trackingVideos.add(myCache)
        onDispose { trackingVideos.remove(myCache) }
    }

    val videoModifier =
        remember(controller) {
            Modifier.fillMaxWidth().heightIn(min = 100.dp).onVisiblePositionChanges { distanceToCenter ->
                myCache.distanceToCenter = distanceToCenter

                if (distanceToCenter != null) {
                    // finds out of the current video is the closest to the center.
                    var newActive = true
                    for (video in trackingVideos) {
                        val videoPos = video.distanceToCenter
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

fun Modifier.onVisiblePositionChanges(onVisiblePosition: (Float?) -> Unit): Modifier =
    composed {
        val view = LocalView.current

        onGloballyPositioned { coordinates ->
            onVisiblePosition(coordinates.getDistanceToVertCenterIfVisible(view))
        }
    }

fun LayoutCoordinates.getDistanceToVertCenterIfVisible(view: View): Float? {
    if (!isAttached) return null
    // Window relative bounds of our compose root view that are visible on the screen
    val globalRootRect = Rect()
    if (!view.getGlobalVisibleRect(globalRootRect)) {
        // we aren't visible at all.
        return null
    }

    val bounds = boundsInWindow()

    if (bounds.isEmpty) return null

    // Make sure we are completely in bounds.
    if (
        bounds.top >= globalRootRect.top &&
        bounds.left >= globalRootRect.left &&
        bounds.right <= globalRootRect.right &&
        bounds.bottom <= globalRootRect.bottom
    ) {
        return abs(
            ((bounds.top + bounds.bottom) / 2) - ((globalRootRect.top + globalRootRect.bottom) / 2),
        )
    }

    return null
}
