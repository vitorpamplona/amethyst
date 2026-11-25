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
package com.vitorpamplona.amethyst.commons.ui.playback.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.ui.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.commons.ui.playback.player.LocalPlaybackPlatform
import com.vitorpamplona.amethyst.commons.ui.playback.player.rememberPlayPauseButtonState

/**
 * The feed's "this is a video, tap to play" affordance. With autoplay off, a loaded player
 * renders its current frame exactly like a still image, and the transport controls only
 * appear after a tap. This draws the same center play/pause button the controls use, on its
 * own, whenever the video is paused with its controls hidden — so the video advertises itself
 * in the feed and a single tap starts it. RenderVideoPlayer gates the whole thing behind
 * showPausedOverlay so feeds that autoplay never see it.
 *
 * [showPlay] is false while a video buffers into an autoplaying start (playWhenReady set),
 * so this never flashes on videos that begin on their own; it shows for paused (including
 * held-back-until-tap) and ended videos. Hidden whenever the full controls are up — their center
 * button is the same button — and in fullscreen, where the user opened the video deliberately.
 * Audio (voice notes, podcasts) squares its own player with a visualizer, so it is skipped too.
 *
 * While this video is casting (playing on another device), the local player is held paused and every play attempt is
 * re-paused (see RenderTopButtons), so a Play tap could do nothing — the same casting guard
 * RenderCenterButtons uses keeps this hidden until the cast ends.
 */
@Composable
fun PausedPlayOverlay(
    controllerState: MediaControllerState,
    controllerVisible: MutableState<Boolean>,
    isFullscreen: Boolean,
    isAudio: Boolean,
    videoUri: String,
    modifier: Modifier = Modifier,
) {
    if (isFullscreen || isAudio) return

    val isCastingThisVideo = LocalPlaybackPlatform.current.isPlayingExternally(videoUri)

    val state = rememberPlayPauseButtonState(controllerState.controller)

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = !isCastingThisVideo && !controllerVisible.value && state.isEnabled && state.showPlay,
            enter = remember { fadeIn() },
            exit = remember { fadeOut() },
        ) {
            PlayPauseButton(isPlaying = false) { state.onClick() }
        }
    }
}
