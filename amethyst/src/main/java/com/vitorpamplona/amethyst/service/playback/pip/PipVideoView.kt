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

import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.rememberMuteButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.WaveformData
import com.vitorpamplona.amethyst.service.playback.composable.controls.PIP_PRESHRINK_MAX_SHORT_SIDE_PX
import com.vitorpamplona.amethyst.service.playback.composable.controls.constrainVideoQualityToViewport
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemData
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.isAudioOnly
import com.vitorpamplona.amethyst.service.playback.composable.wavefront.Waveform
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.theme.VoiceHeightModifier

@OptIn(UnstableApi::class)
@Composable
fun RenderPipVideo(
    controller: MediaControllerState,
    mediaData: MediaItemData,
    waveformData: WaveformData?,
) {
    KeepScreenOnWhilePlaying(controller)

    val isAudio = remember(mediaData) { mediaData.isAudioOnly() }

    val modifier =
        remember(isAudio) {
            // Audio has no frame to size the window by, so it gets a square — the shape cover art
            // is already in. Video keeps whatever ratio the post declared.
            val ratio =
                if (isAudio) {
                    AUDIO_PIP_ASPECT_RATIO
                } else {
                    controller.currentMedia()?.let { MediaAspectRatioCache.get(it) }
                }

            if (ratio != null) {
                Modifier.aspectRatio(ratio)
            } else {
                Modifier
            }
        }

    // processIntentForPiP calls enterPictureInPictureMode from composition (PiPFromIntents), so the
    // first layout pass can measure the activity at full screen before the window shrinks. Cap that
    // measurement instead of skipping it: skipping would leave a pooled player on whatever viewport
    // its previous view pushed if PiP is never actually entered (per-app PiP off, no
    // FEATURE_PICTURE_IN_PICTURE). The shrink relayouts and pushes the real size.
    val activity = LocalActivity.current

    Box(
        modifier.constrainVideoQualityToViewport(
            player = controller.controller,
            maxShortSidePx = {
                if (activity?.isInPictureInPictureMode == true) 0 else PIP_PRESHRINK_MAX_SHORT_SIDE_PX
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (isAudio) {
            RenderPipAudioArtwork(mediaData)
        } else {
            ContentFrame(
                player = controller.controller,
                keepContentOnReset = true,
                contentScale = ContentScale.Crop,
            )
        }

        Row(VoiceHeightModifier, verticalAlignment = Alignment.CenterVertically) {
            waveformData?.let { Waveform(it, controller, Modifier) }
        }
    }
}

/**
 * What a picture-in-picture window shows when there is no picture: the post's cover art, with the
 * title and author over it so the window still says what is playing when the art is missing or has
 * not loaded. The play/pause and mute controls are the window's own RemoteActions, and the shade
 * and lock screen come from the MediaSession the promotion claims — so this is only the visual.
 *
 * Loaded through Coil's singleton loader rather than the shared MyAsyncImage: this activity has no
 * account, by design.
 */
@Composable
private fun RenderPipAudioArtwork(mediaData: MediaItemData) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        mediaData.artworkUri?.let { art ->
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val title = mediaData.title?.ifBlank { null }
        val author = mediaData.authorName?.ifBlank { null }

        if (title != null || author != null) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    // The art is arbitrary, so the text needs its own ground to stay legible.
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                author?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// Cover art is square far more often than not, and a square window reads as "this is a track"
// rather than a video that failed to load.
private const val AUDIO_PIP_ASPECT_RATIO = 1f

@Composable
fun KeepScreenOnWhilePlaying(controller: MediaControllerState) {
    val view = LocalView.current

    DisposableEffect(controller.controller, view) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (view.keepScreenOn != isPlaying) {
                        view.keepScreenOn = isPlaying
                    }
                }
            }

        // Set initial state
        view.keepScreenOn = controller.controller.isPlaying

        controller.controller.addListener(listener)
        onDispose {
            controller.controller.removeListener(listener)
            view.keepScreenOn = false
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

        ContextCompat.registerReceiver(context, receiver, receiver.filterMute, ContextCompat.RECEIVER_EXPORTED)
        ContextCompat.registerReceiver(context, receiver, receiver.filterPlayPause, ContextCompat.RECEIVER_EXPORTED)

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
