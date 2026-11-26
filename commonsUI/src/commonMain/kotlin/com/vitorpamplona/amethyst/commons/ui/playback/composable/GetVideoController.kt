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
package com.vitorpamplona.amethyst.commons.ui.playback.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vitorpamplona.amethyst.commons.ui.playback.mediaitem.MediaItemData
import com.vitorpamplona.amethyst.commons.ui.playback.pip.BackgroundMedia
import com.vitorpamplona.amethyst.commons.ui.playback.player.LocalPlaybackPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val BACKGROUND_RELEASE_TIMEOUT_MS = 30_000L

/**
 * Hands [inner] the platform's player for [mediaItem] (see [com.vitorpamplona.amethyst.commons.ui.playback.player.PlaybackPlatform.GetVideoController]).
 */
@Composable
fun GetVideoController(
    mediaItem: MediaItemData,
    muted: Boolean = false,
    play: Boolean = false,
    // Opt-out for callers whose lifecycle owner is already a background-playback
    // surface (PiP): there, the 30s timer would fire as soon as the activity
    // enters PiP mode and race the controller build / `RegisterBackgroundMedia`
    // registration, killing the just-attached controller and blanking the
    // window. The opt-out skips the timer entirely for those callers.
    releaseOnBackgroundTimeout: Boolean = true,
    inner: @Composable (mediaControllerState: MediaControllerState) -> Unit,
) {
    LocalPlaybackPlatform.current.GetVideoController(mediaItem, muted, play, releaseOnBackgroundTimeout, inner)
}

/**
 * Flips [keepAlive] to `false` after the host activity has been at ON_PAUSE for
 * [timeoutMs], so the gated flow upstream releases the MediaController. ON_RESUME
 * cancels any pending timer and flips it back to `true` so the controller is
 * reacquired.
 *
 * The BackgroundMedia (PiP) controller is exempt — it's opted into background
 * playback and must keep its MediaController alive past the timeout.
 */
@Composable
fun ReleaseControllerWhenBackgroundedFor(
    timeoutMs: Long,
    controllerState: MediaControllerState?,
    keepAlive: MutableState<Boolean>,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentControllerState by rememberUpdatedState(controllerState)

    DisposableEffect(lifecycleOwner, keepAlive) {
        val scope = CoroutineScope(Dispatchers.Main)
        var timeoutJob: Job? = null

        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        timeoutJob?.cancel()
                        timeoutJob =
                            scope.launch {
                                delay(timeoutMs)
                                val cs = currentControllerState
                                if (cs == null || !BackgroundMedia.isMutex(cs)) {
                                    keepAlive.value = false
                                }
                            }
                    }

                    Lifecycle.Event.ON_RESUME -> {
                        timeoutJob?.cancel()
                        timeoutJob = null
                        keepAlive.value = true
                    }

                    else -> Unit
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            timeoutJob?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            scope.cancel()
        }
    }
}
