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
package com.vitorpamplona.amethyst.service.playback.composable

import android.graphics.Rect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.media3.session.MediaController
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Stable
class MediaControllerState(
    // each composable has an ID.
    val id: String = Uuid.random().toString(),
    // This is filled after the controller returns from this class
    var controller: MediaController? = null,
    // this set's the stage to keep playing on the background or not when the user leaves the screen
    val pictureInPictureActive: MutableState<Boolean> = mutableStateOf(false),
    // this will be false if the screen leaves before the controller connection comes back from the service.
    val active: MutableState<Boolean> = mutableStateOf(true),
    // this will be set to own when the controller is ready
    val readyToDisplay: MutableState<Boolean> = mutableStateOf(false),
    // isCurrentlyBeingRendered
    var composed: Boolean = false,
    // visibility onscreen
    val visibility: VisibilityData = VisibilityData(),
) {
    fun isPlaying() = controller?.isPlaying == true

    fun currrentMedia() = controller?.currentMediaItem?.mediaId

    fun isActive() = active.value == true

    fun needsController() = controller == null
}

@Stable
class VisibilityData {
    var bounds: Rect? = null
    var distanceToCenter: Float? = null
}
