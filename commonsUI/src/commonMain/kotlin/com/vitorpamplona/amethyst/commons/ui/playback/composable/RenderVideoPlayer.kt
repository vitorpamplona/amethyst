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

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.ui.playback.audioSquare
import com.vitorpamplona.amethyst.commons.ui.playback.controls.BottomGradientOverlay
import com.vitorpamplona.amethyst.commons.ui.playback.controls.FullscreenSwipeControlsState
import com.vitorpamplona.amethyst.commons.ui.playback.controls.FullscreenSwipeLevelIndicator
import com.vitorpamplona.amethyst.commons.ui.playback.controls.LogVideoQualitySelection
import com.vitorpamplona.amethyst.commons.ui.playback.controls.METERED_MAX_SHORT_SIDE_PX
import com.vitorpamplona.amethyst.commons.ui.playback.controls.PausedPlayOverlay
import com.vitorpamplona.amethyst.commons.ui.playback.controls.RenderAnimatedBottomInfo
import com.vitorpamplona.amethyst.commons.ui.playback.controls.RenderCaptions
import com.vitorpamplona.amethyst.commons.ui.playback.controls.RenderCenterButtons
import com.vitorpamplona.amethyst.commons.ui.playback.controls.RenderTopButtons
import com.vitorpamplona.amethyst.commons.ui.playback.controls.TopGradientOverlay
import com.vitorpamplona.amethyst.commons.ui.playback.controls.applyViewportConstraint
import com.vitorpamplona.amethyst.commons.ui.playback.controls.clampViewportShortSide
import com.vitorpamplona.amethyst.commons.ui.playback.controls.fullscreenSwipeControls
import com.vitorpamplona.amethyst.commons.ui.playback.mediaitem.MediaItemData
import com.vitorpamplona.amethyst.commons.ui.playback.player.AmethystPlayer
import com.vitorpamplona.amethyst.commons.ui.playback.player.LocalPlaybackPlatform
import com.vitorpamplona.amethyst.commons.ui.playback.player.VideoSurfaceStyle
import com.vitorpamplona.amethyst.commons.ui.playback.shouldSquareAudioPlayer
import com.vitorpamplona.amethyst.commons.ui.playback.wavefront.AudioPlayingAnimation
import com.vitorpamplona.amethyst.commons.ui.playback.wavefront.rememberIsAudioTrack
import com.vitorpamplona.amethyst.commons.video.isHlsMedia
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

const val SKIP_SECONDS = 10
const val SKIP_MILLIS = SKIP_SECONDS * 1000L

fun AmethystPlayer.seekBackward() {
    seekTo((currentPosition - SKIP_MILLIS).coerceAtLeast(0))
}

fun AmethystPlayer.skipForward() {
    val newPosition = currentPosition + SKIP_MILLIS
    seekTo(if (duration > 0) newPosition.coerceAtMost(duration) else newPosition)
}

private fun getVideoSizeDp(player: AmethystPlayer): Size? {
    var videoSize = Size(player.videoSize.width.toFloat(), player.videoSize.height.toFloat())

    if (videoSize.width == 0f || videoSize.height == 0f) return null

    val par = player.videoSize.pixelWidthHeightRatio
    if (par < 1.0) {
        videoSize = videoSize.copy(width = videoSize.width * par)
    } else if (par > 1.0) {
        videoSize = videoSize.copy(height = videoSize.height / par)
    }
    return videoSize
}

@Composable
fun RenderVideoPlayer(
    mediaItem: MediaItemData,
    controllerState: MediaControllerState,
    thumbData: VideoThumb?,
    showControls: Boolean = true,
    showPausedOverlay: Boolean = false,
    contentScale: ContentScale,
    borderModifier: Modifier,
    videoModifier: Modifier,
    onDialog: (() -> Unit)? = null,
    controllerVisible: MutableState<Boolean> = remember { mutableStateOf(false) },
    hasBlurhash: Boolean = false,
    isFullscreen: Boolean = false,
    accountViewModel: AccountViewModel,
) {
    // Hold the container size in a non-state holder so layout passes don't trigger an
    // unnecessary recomposition of the whole player tree just to update a value that is only
    // ever read inside the onDoubleTap callback below.
    val containerWidth = remember { intArrayOf(0) }

    // Last measured player size, kept out of snapshot state for the same reason as containerWidth:
    // it exists only so a connectivity flip can re-push the viewport without a layout pass.
    val lastMeasured = remember { intArrayOf(0, 0) }
    val isMetered by accountViewModel.settings.isMobileOrMeteredConnection.collectAsStateWithLifecycle()
    // Fullscreen is exempt: the cap exists to hold back feeds that autoplay without being asked,
    // and someone who tapped into fullscreen on mobile data asked. Capping there would also put a
    // ceiling the quality menu's "Auto" could not exceed.
    val viewportCeiling = if (isMetered && !isFullscreen) METERED_MAX_SHORT_SIDE_PX else 0
    val isLive = remember(mediaItem.videoUri, mediaItem.mimeType) { isHlsMedia(mediaItem.videoUri, mediaItem.mimeType) }

    val swipeState = remember { FullscreenSwipeControlsState() }
    val platform = LocalPlaybackPlatform.current
    // Brightness is applied to the fullscreen dialog window so it auto-reverts on dismiss.
    // The inline feed player is not inside a dialog, so it can't set it — fine, gated by isFullscreen below.
    val deviceLevels = platform.rememberDeviceLevels()

    // The gesture detectors below run in pointerInput coroutines that outlive recompositions, so
    // they read these through rememberUpdatedState instead of capturing the parameters: the
    // caller can hand in a new controllerVisible, and GetVideoController swaps the controller
    // when late captions rebuild the media item. A captured value would keep the old one, which
    // nothing draws (taps stop showing the controls) or which is released (swipes and double
    // taps do nothing). Reading the current value also avoids restarting the detectors, which
    // would drop a gesture in progress.
    val currentControllerState by rememberUpdatedState(controllerState)
    val currentControllerVisible by rememberUpdatedState(controllerVisible)

    // Sync the per-video mute (the player's volume) with the volume swipe. Mirrors the mute
    // button's handler exactly, so its listener-driven icon flips when the swipe mutes/unmutes,
    // and the global default carries to the next video.
    val isVideoMuted = { currentControllerState.controller.volume < 0.001f }
    val setVideoMuted = { mute: Boolean ->
        DEFAULT_MUTED_SETTING.value = mute
        currentControllerState.controller.volume = if (mute) 0f else 1f
    }

    // Belt-and-suspenders: clear any brightness override when this player leaves composition, so
    // exiting fullscreen never leaves the screen dimmed. releaseBrightness no-ops when there is no
    // window of its own (the inline feed path) or when no override was applied, so this is safe unconditionally.
    DisposableEffect(Unit) {
        onDispose { swipeState.releaseBrightness(deviceLevels) }
    }

    WatchPlaybackErrors(controllerState)
    LogVideoQualitySelection(controllerState.controller)

    // Moving on or off mobile data does not relayout, so the new ceiling has to be pushed by hand.
    // Before the first measurement lastMeasured is still zero and applyViewportConstraint no-ops.
    LaunchedEffect(viewportCeiling, controllerState.controller) {
        val (w, h) = clampViewportShortSide(lastMeasured[0], lastMeasured[1], viewportCeiling)
        applyViewportConstraint(controllerState.controller, w, h)
    }

    // Audio files have no video dimensions, so without this the player collapses to a thin strip and
    // the controls get crammed. Size it square (capped) so the visualizer and controls get room.
    // Voice notes keep their seek-bar strip; the full-screen dialog fills the screen.
    val tracksAreAudio by rememberIsAudioTrack(controllerState.controller)
    // Voice notes, audio-only streams and audio links: they render their own waveform/visualizer
    // UI and stay out of the paused-video play overlay.
    val isAudioContent =
        mediaItem.waveformData != null ||
            mediaItem.mimeType?.startsWith("audio/") == true ||
            tracksAreAudio
    val squarePlayer =
        shouldSquareAudioPlayer(
            isFullscreen = isFullscreen,
            isVoiceNote = mediaItem.waveformData != null,
            isAudioMime = mediaItem.mimeType?.startsWith("audio/") == true,
            tracksAreAudio = tracksAreAudio,
        )
    val playerModifier = if (squarePlayer) borderModifier.audioSquare() else borderModifier

    Box(
        modifier =
            playerModifier
                .onSizeChanged {
                    containerWidth[0] = it.width
                    lastMeasured[0] = it.width
                    lastMeasured[1] = it.height
                    val (w, h) = clampViewportShortSide(it.width, it.height, viewportCeiling)
                    applyViewportConstraint(controllerState.controller, w, h)
                }.pointerInput(isLive) {
                    detectTapGestures(
                        onTap = { currentControllerVisible.value = !currentControllerVisible.value },
                        onDoubleTap = { offset ->
                            if (!isLive) {
                                val isLeftSide = offset.x < containerWidth[0] / 2
                                if (isLeftSide) {
                                    currentControllerState.controller.seekBackward()
                                } else {
                                    currentControllerState.controller.skipForward()
                                }
                            }
                        },
                    )
                }.then(
                    if (isFullscreen) {
                        Modifier.fullscreenSwipeControls(
                            state = swipeState,
                            levels = deviceLevels,
                            isMuted = isVideoMuted,
                            setMuted = setVideoMuted,
                        )
                    } else {
                        Modifier
                    },
                ),
    ) {
        // Fullscreen plays into the platform's fastest surface (Android: a SurfaceView, so the
        // decoder hands HDR frames straight to the compositor). The feed keeps a surface that
        // clips, scales and sits in a lazy list (Android: a TextureView); HDR is only requested
        // where the surface can show it. Either way there is no opaque shutter: the layer
        // beneath (the blurhash backdrop, or the post background) shows through until the first
        // frame, which avoids a black blink when re-entering the feed.
        platform.VideoSurface(
            controllerState = controllerState,
            style = if (isFullscreen) VideoSurfaceStyle.FULLSCREEN else VideoSurfaceStyle.INLINE,
            contentScale = contentScale,
            modifier = videoModifier,
        )

        // Above the picture but below the controls, so the transport bar and the bottom gradient
        // draw over the cues rather than the other way around.
        if (mediaItem.captions.isNotEmpty()) {
            RenderCaptions(
                controllerState = controllerState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        RenderPlaybackError(
            controllerState = controllerState,
            videoUri = mediaItem.videoUri,
            modifier = Modifier.align(Alignment.Center),
        )

        val visualizerStyle by accountViewModel.audioVisualizerFlow().collectAsStateWithLifecycle()
        AudioPlayingAnimation(
            controllerState = controllerState,
            waveform = mediaItem.waveformData,
            mediaId = mediaItem.videoUri,
            style = visualizerStyle,
            isAudio = tracksAreAudio,
            modifier = Modifier.fillMaxSize().align(Alignment.Center),
            hasBlurhash = hasBlurhash,
        )

        if (showControls && controllerState.playbackError.value == null) {
            // A loaded-but-paused video (autoplay off, manual pause, scrolled away and back) looks
            // like a still image until tapped. The center play button, shown on its own, says video.
            // Only where the feed does not autoplay: with autoplay on, paused videos are just
            // waiting for the viewport and the center one would flash the button while it spins up.
            if (showPausedOverlay) {
                PausedPlayOverlay(
                    controllerState = controllerState,
                    controllerVisible = controllerVisible,
                    isFullscreen = isFullscreen,
                    isAudio = isAudioContent,
                    videoUri = mediaItem.videoUri,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            TopGradientOverlay(
                controllerVisible = controllerVisible,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            BottomGradientOverlay(
                controllerVisible = controllerVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            RenderTopButtons(
                mediaData = mediaItem,
                controllerState = controllerState,
                controllerVisible = controllerVisible,
                onZoomClick = onDialog,
                modifier = Modifier.align(Alignment.TopEnd),
                accountViewModel = accountViewModel,
            )

            RenderAnimatedBottomInfo(controllerState, controllerVisible, Modifier.align(Alignment.BottomCenter))

            RenderCenterButtons(
                controllerState = controllerState,
                controllerVisible = controllerVisible,
                videoUri = mediaItem.videoUri,
                modifier = Modifier.align(Alignment.Center),
                isLiveStream = isLive,
            )
        }

        if (isFullscreen) {
            FullscreenSwipeLevelIndicator(swipeState, isMuted = isVideoMuted)
        }
    }
}
