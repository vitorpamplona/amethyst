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

import android.content.Context
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vitorpamplona.amethyst.service.playback.composable.controls.RenderControlButtons
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.LoadedMediaItem
import com.vitorpamplona.amethyst.service.playback.composable.wavefront.Waveform
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.experimental.audio.header.tags.WaveformTag

@Composable
@OptIn(UnstableApi::class)
fun RenderVideoPlayer(
    mediaItem: LoadedMediaItem,
    controllerState: MediaControllerState,
    thumbData: VideoThumb?,
    showControls: Boolean = true,
    contentScale: ContentScale,
    waveform: WaveformTag? = null,
    borderModifier: Modifier,
    videoModifier: Modifier,
    onControllerVisibilityChanged: ((Boolean) -> Unit)? = null,
    onDialog: ((Boolean) -> Unit)?,
    accountViewModel: AccountViewModel,
) {
    val controllerVisible = remember(controllerState) { mutableStateOf(false) }

    Box(modifier = borderModifier) {
        AndroidView(
            modifier = videoModifier,
            factory = { context: Context ->
                PlayerView(context).apply {
                    player = controllerState.controller
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setBackgroundColor(Color.Transparent.toArgb())
                    setShutterBackgroundColor(Color.Transparent.toArgb())

                    controllerAutoShow = false
                    useController = showControls
                    thumbData?.thumb?.let { defaultArtwork = it }
                    hideController()

                    resizeMode =
                        when (contentScale) {
                            ContentScale.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            ContentScale.FillWidth -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                            ContentScale.Crop -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            ContentScale.FillHeight -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                            ContentScale.Inside -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                        }

                    if (showControls) {
                        onDialog?.let { innerOnDialog ->
                            setFullscreenButtonClickListener {
                                controllerState.controller?.pause()
                                innerOnDialog(it)
                            }
                        }
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { visible ->
                                controllerVisible.value = visible == View.VISIBLE
                                onControllerVisibilityChanged?.let { callback -> callback(visible == View.VISIBLE) }
                            },
                        )
                    }
                }
            },
        )

        waveform?.let { Waveform(it, controllerState, Modifier.align(Alignment.Center)) }

        if (showControls) {
            RenderControlButtons(
                mediaItem.src,
                controllerState,
                controllerVisible,
                Modifier.align(Alignment.TopEnd),
                accountViewModel,
            )
        } else {
            controllerState.controller?.volume = 0f
        }
    }
}
