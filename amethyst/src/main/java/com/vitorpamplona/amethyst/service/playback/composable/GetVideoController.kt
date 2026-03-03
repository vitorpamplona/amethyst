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
package com.vitorpamplona.amethyst.service.playback.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.LoadedMediaItem
import com.vitorpamplona.amethyst.service.playback.pip.BackgroundMedia
import com.vitorpamplona.amethyst.service.playback.service.PlaybackServiceClient
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.onEach

@Composable
fun GetVideoController(
    mediaItem: LoadedMediaItem,
    muted: Boolean = false,
    play: Boolean = false,
    inner: @Composable (mediaControllerState: MediaControllerState) -> Unit,
) {
    val context = LocalContext.current
    val controllerState by remember(mediaItem) {
        PlaybackServiceClient
            .controllerAsFlow(
                videoUri = mediaItem.src.videoUri,
                proxyPort = mediaItem.src.proxyPort,
                keepPlaying = mediaItem.src.keepPlaying,
                context = context,
            ).onEach { state ->
                Log.d("PlaybackService", "Controller instance: ${state.controller}")

                if (BackgroundMedia.isPlaying()) {
                    // There is a video playing, start this one on mute.
                    state.controller.volume = 0f
                    Log.d("PlaybackService", "OnEach Muted due to BackgroundMedia.isPlaying")
                } else {
                    // There is no other video playing. Use the default mute state to
                    // decide if sound is on or not.
                    state.controller.volume = if (muted) 0f else 1f
                    Log.d("PlaybackService", "OnEach $muted")
                }

                if (play) {
                    state.controller.playWhenReady = true
                }

                state.controller.setMediaItem(mediaItem.item)
                state.controller.prepare()
            }
    }.collectAsStateWithLifecycle(null)

    controllerState?.let {
        inner(it)
    }
}
