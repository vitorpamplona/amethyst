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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.service.playback.composable.mainVideo.VideoPlayerActiveMutex
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.GetMediaItem
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

public val DEFAULT_MUTED_SETTING = mutableStateOf(true)

@Composable
fun VideoViewInner(
    videoUri: String,
    mimeType: String?,
    aspectRatio: Float? = null,
    title: String? = null,
    thumb: VideoThumb? = null,
    showControls: Boolean = true,
    contentScale: ContentScale,
    borderModifier: Modifier,
    waveform: WaveformData? = null,
    artworkUri: String? = null,
    authorName: String? = null,
    nostrUriCallback: String? = null,
    automaticallyStartPlayback: Boolean,
    controllerVisible: MutableState<Boolean> = mutableStateOf(true),
    onZoom: (() -> Unit)? = null,
    accountViewModel: AccountViewModel,
) {
    // keeps a copy of the value to avoid recompositions here when the DEFAULT value changes
    val muted = remember(videoUri) { DEFAULT_MUTED_SETTING.value }

    GetMediaItem(
        videoUri = videoUri,
        title = title,
        artworkUri = artworkUri,
        authorName = authorName,
        callbackUri = nostrUriCallback,
        mimeType = mimeType,
        aspectRatio = aspectRatio,
        proxyPort = accountViewModel.httpClientBuilder.proxyPortForVideo(videoUri),
        keepPlaying = true,
        waveformData = waveform,
    ) { mediaItem ->
        GetVideoController(
            mediaItem = mediaItem,
            muted = muted,
        ) { controller ->
            VideoPlayerActiveMutex(controller) { videoModifier, isClosestToTheCenterOfTheScreen ->
                ControlWhenPlayerIsActive(controller, automaticallyStartPlayback, isClosestToTheCenterOfTheScreen)
                RenderVideoPlayer(
                    mediaItem = mediaItem,
                    controllerState = controller,
                    thumbData = thumb,
                    showControls = showControls,
                    contentScale = contentScale,
                    borderModifier = borderModifier,
                    videoModifier = videoModifier,
                    controllerVisible = controllerVisible,
                    onDialog = onZoom,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}
