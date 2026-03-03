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
package com.vitorpamplona.amethyst.service.playback.pip

import android.content.Context.RECEIVER_EXPORTED
import android.os.Build
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.rememberMuteButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import com.vitorpamplona.amethyst.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.WaveformData
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemData
import com.vitorpamplona.amethyst.service.playback.composable.wavefront.Waveform
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.theme.VoiceHeightModifier

@OptIn(UnstableApi::class)
@Composable
fun RenderPipVideo(
    controller: MediaControllerState,
    waveformData: WaveformData?,
) {
    val modifier =
        remember {
            val ratio =
                controller.currrentMedia()?.let {
                    MediaAspectRatioCache.get(it)
                }

            if (ratio != null) {
                Modifier.aspectRatio(ratio)
            } else {
                Modifier
            }
        }

    Box(modifier, contentAlignment = Alignment.Center) {
        ContentFrame(
            player = controller.controller,
            keepContentOnReset = true,
            contentScale = ContentScale.Crop,
        )

        Row(VoiceHeightModifier, verticalAlignment = Alignment.CenterVertically) {
            waveformData?.let { Waveform(it, controller, Modifier) }
        }
    }
}

@Composable
fun RegisterControllerReceiver(controllerState: MediaControllerState) {
    val context = LocalContext.current

    // Use DisposableEffect to manage the receiver's lifecycle
    DisposableEffect(context) {
        val receiver =
            ActionReceiver(
                onMute = controllerState::toggleMute,
                onPlayPause = controllerState::togglePlayPause,
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, receiver.filterMute, RECEIVER_EXPORTED)
            context.registerReceiver(receiver, receiver.filterPlayPause, RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, receiver.filterMute)
            context.registerReceiver(receiver, receiver.filterPlayPause)
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun WatchControllerForActions(
    mediaItemData: MediaItemData,
    controllerState: MediaControllerState,
) {
    val playPauseState = rememberPlayPauseButtonState(controllerState.controller)
    val muteState = rememberMuteButtonState(controllerState.controller)

    val activity = LocalContext.current.getActivity()
    LaunchedEffect(activity, playPauseState.showPlay, muteState.showMuted) {
        activity.setPictureInPictureParams(
            activity.makePipParams(
                isPlaying = !playPauseState.showPlay,
                isMuted = !muteState.showMuted,
                ratio = mediaItemData.aspectRatio,
                bounds = null,
            ),
        )
    }
}
