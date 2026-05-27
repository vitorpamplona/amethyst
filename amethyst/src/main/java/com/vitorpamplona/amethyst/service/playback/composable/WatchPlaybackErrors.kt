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
import androidx.compose.runtime.DisposableEffect
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/**
 * Mirrors the MediaController's terminal-error state into [MediaControllerState.playbackError]
 * so [RenderPlaybackError] can show the codec-not-supported overlay with a browser fallback.
 *
 * ExoPlayer enters the ERROR state silently on decoder init / decode / format failures; the
 * ContentFrame then renders nothing and the user is left staring at a blank box. Surfacing the
 * exception gives [RenderVideoPlayer] something to render and lets the user open the URL
 * externally instead.
 */
@Composable
fun WatchPlaybackErrors(controllerState: MediaControllerState) {
    val controller = controllerState.controller
    val errorState = controllerState.playbackError

    DisposableEffect(controllerState) {
        // Prime from the controller's current state — a warm-pool player may already be in ERROR
        // when we attach, in which case onPlayerErrorChanged will not fire again until prepare().
        errorState.value = controller.playerError

        val listener =
            object : Player.Listener {
                override fun onPlayerErrorChanged(error: PlaybackException?) {
                    errorState.value = error
                }

                override fun onPlaybackStateChanged(state: Int) {
                    // Any successful transition out of IDLE/ERROR (typically after a manual retry
                    // via controller.prepare()) clears the overlay so the player can render.
                    if (state == Player.STATE_READY || state == Player.STATE_BUFFERING) {
                        if (errorState.value != null) errorState.value = null
                    }
                }
            }

        controller.addListener(listener)
        onDispose {
            controller.removeListener(listener)
        }
    }
}
