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

import androidx.annotation.OptIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.vitorpamplona.amethyst.service.playback.composable.controls.BottomGradientOverlay
import com.vitorpamplona.amethyst.service.playback.composable.controls.RenderAnimatedBottomInfo
import com.vitorpamplona.amethyst.service.playback.composable.controls.RenderCenterButtons
import com.vitorpamplona.amethyst.service.playback.composable.controls.RenderTopButtons
import com.vitorpamplona.amethyst.service.playback.composable.controls.TopGradientOverlay
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.LoadedMediaItem
import com.vitorpamplona.amethyst.service.playback.composable.wavefront.Waveform
import com.vitorpamplona.amethyst.service.playback.diskCache.isLiveStreaming
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

internal const val SKIP_SECONDS = 10
internal const val SKIP_MILLIS = SKIP_SECONDS * 1000L

internal fun Player.seekBackward() {
    seekTo((currentPosition - SKIP_MILLIS).coerceAtLeast(0))
}

internal fun Player.skipForward() {
    val newPosition = currentPosition + SKIP_MILLIS
    seekTo(if (duration > 0) newPosition.coerceAtMost(duration) else newPosition)
}

private fun getVideoSizeDp(player: Player): Size? {
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
@OptIn(UnstableApi::class)
fun RenderVideoPlayer(
    mediaItem: LoadedMediaItem,
    controllerState: MediaControllerState,
    thumbData: VideoThumb?,
    showControls: Boolean = true,
    contentScale: ContentScale,
    borderModifier: Modifier,
    videoModifier: Modifier,
    onDialog: (() -> Unit)? = null,
    controllerVisible: MutableState<Boolean> = remember { mutableStateOf(false) },
    accountViewModel: AccountViewModel,
) {
    val containerSize = remember { mutableStateOf(IntSize.Zero) }
    val isLive = isLiveStreaming(mediaItem.src.videoUri)

    Box(
        modifier =
            borderModifier
                .onSizeChanged { containerSize.value = it }
                .pointerInput(isLive, controllerState) {
                    detectTapGestures(
                        onTap = { controllerVisible.value = !controllerVisible.value },
                        onDoubleTap = { offset ->
                            if (!isLive) {
                                val isLeftSide = offset.x < containerSize.value.width / 2
                                if (isLeftSide) {
                                    controllerState.controller.seekBackward()
                                } else {
                                    controllerState.controller.skipForward()
                                }
                            }
                        },
                    )
                },
    ) {
        ContentFrame(
            player = controllerState.controller,
            modifier = videoModifier,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = contentScale,
        )

        mediaItem.src.waveformData?.let { Waveform(it, controllerState, Modifier.align(Alignment.Center)) }

        if (showControls) {
            TopGradientOverlay(
                controllerVisible = controllerVisible,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            BottomGradientOverlay(
                controllerVisible = controllerVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            RenderTopButtons(
                mediaData = mediaItem.src,
                controllerState = controllerState,
                controllerVisible = controllerVisible,
                onZoomClick = onDialog,
                modifier = Modifier.align(Alignment.TopEnd),
                accountViewModel = accountViewModel,
            )

            RenderCenterButtons(
                controllerState = controllerState,
                controllerVisible = controllerVisible,
                modifier = Modifier.align(Alignment.Center),
                isLiveStream = isLive,
            )

            RenderAnimatedBottomInfo(controllerState, controllerVisible, Modifier.align(Alignment.BottomCenter))
        }
    }
}
