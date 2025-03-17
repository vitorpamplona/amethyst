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
package com.vitorpamplona.amethyst.service.playback.composable

import com.vitorpamplona.amethyst.service.playback.service.PlaybackServiceClient.removeController
import kotlinx.coroutines.flow.MutableStateFlow

object BackgroundMedia {
    // background playing mutex.
    val bgInstance = MutableStateFlow<MediaControllerState?>(null)

    private fun hasInstance() = bgInstance.value != null

    private fun isComposed() = bgInstance.value?.composed?.value == true

    private fun isUri(videoUri: String): Boolean = videoUri == bgInstance.value?.currrentMedia()

    fun isPlaying() = bgInstance.value?.isPlaying() == true

    fun isMutex(controller: MediaControllerState): Boolean = controller.id == bgInstance.value?.id

    fun hasBackgroundButNot(mediaControllerState: MediaControllerState): Boolean = hasInstance() && !isMutex(mediaControllerState)

    fun backgroundOrNewController(videoUri: String): MediaControllerState {
        // allows only the first composable with the url of the video to match.
        return if (isUri(videoUri) && !isComposed()) {
            bgInstance.value ?: MediaControllerState()
        } else {
            MediaControllerState()
        }
    }

    fun removeBackgroundControllerAndReleaseIt() {
        bgInstance.value?.let {
            removeController(it)
            bgInstance.tryEmit(null)
        }
    }

    fun removeBackgroundControllerIfNotComposed() {
        bgInstance.value?.let {
            if (!it.composed.value) {
                removeController(it)
            }
            bgInstance.tryEmit(null)
        }
    }

    fun switchKeepPlaying(mediaControllerState: MediaControllerState) {
        if (hasInstance() && !isMutex(mediaControllerState)) {
            removeBackgroundControllerIfNotComposed()
        }
        bgInstance.tryEmit(mediaControllerState)
    }
}
