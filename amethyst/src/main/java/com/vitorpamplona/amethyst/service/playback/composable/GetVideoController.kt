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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
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
                Log.d("PlaybackService") { "Controller instance: ${state.controller}" }

                // The default ExoPlayer volume is 1f and the MediaSessionPool reset lambda
                // sets it to 0f when the player is acquired, so the controller arrives at 0f.
                // Read first and only push an IPC if the value actually needs to change —
                // with several feed videos preloading at once each volume= write was a
                // round-trip to the service for nothing.
                val targetVolume =
                    when {
                        BackgroundMedia.isPlaying() -> 0f
                        muted -> 0f
                        else -> 1f
                    }
                if (state.controller.volume != targetVolume) {
                    state.controller.volume = targetVolume
                    Log.d("PlaybackService") { "OnEach volume=$targetVolume" }
                }

                if (play) {
                    state.controller.playWhenReady = true
                }

                // Warm-pool fast path: when the underlying ExoPlayer was retained paused-with-
                // buffer for this exact MediaItem, the MediaController's local mirror already
                // shows the matching mediaId. Calling setMediaItem in that case would reset the
                // player and discard the buffer — exactly what the warm pool exists to avoid.
                // We still re-prepare if the player ended up IDLE somehow (e.g. it was demoted
                // to cold and resurfaced, or hit an error before we attached).
                val targetMediaId = mediaItem.item.mediaId
                val needsLoad = state.controller.currentMediaItem?.mediaId != targetMediaId
                if (needsLoad) {
                    state.controller.setMediaItem(mediaItem.item)
                    state.controller.prepare()
                } else if (state.controller.playbackState == Player.STATE_IDLE) {
                    Log.d("PlaybackService") { "Warm controller in STATE_IDLE — re-preparing" }
                    state.controller.prepare()
                }
            }
    }.collectAsState(null)

    controllerState?.let {
        inner(it)
    }
}
