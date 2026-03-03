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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.ui.components.DisplayBlurHash
import com.vitorpamplona.amethyst.ui.components.ImageUrlWithDownloadButton
import com.vitorpamplona.amethyst.ui.note.DownloadForOfflineIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size75dp
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.amethyst.ui.theme.videoGalleryModifier
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag

@Immutable
class WaveformData(
    val wave: List<Float>,
)

@Composable
fun VideoView(
    videoUri: String,
    mimeType: String?,
    title: String? = null,
    thumb: VideoThumb? = null,
    roundedCorner: Boolean,
    gallery: Boolean = false,
    contentScale: ContentScale,
    waveform: WaveformData? = null,
    artworkUri: String? = null,
    authorName: String? = null,
    dimensions: DimensionTag? = null,
    blurhash: String? = null,
    nostrUriCallback: String? = null,
    onDialog: (() -> Unit)? = null,
    accountViewModel: AccountViewModel,
    alwaysShowVideo: Boolean = false,
) {
    val borderModifier =
        if (roundedCorner) {
            MaterialTheme.colorScheme.imageModifier
        } else if (gallery) {
            MaterialTheme.colorScheme.videoGalleryModifier
        } else {
            Modifier
        }

    VideoView(videoUri, mimeType, title, thumb, borderModifier, contentScale, waveform, artworkUri, authorName, dimensions, blurhash, nostrUriCallback, onDialog, alwaysShowVideo, accountViewModel = accountViewModel)
}

@Composable
fun VideoView(
    videoUri: String,
    mimeType: String?,
    title: String? = null,
    thumb: VideoThumb? = null,
    borderModifier: Modifier,
    contentScale: ContentScale,
    waveform: WaveformData? = null,
    artworkUri: String? = null,
    authorName: String? = null,
    dimensions: DimensionTag? = null,
    blurhash: String? = null,
    nostrUriCallback: String? = null,
    onDialog: (() -> Unit)? = null,
    alwaysShowVideo: Boolean = false,
    showControls: Boolean = true,
    accountViewModel: AccountViewModel,
) {
    val automaticallyStartPlayback =
        remember {
            mutableStateOf<Boolean>(
                if (alwaysShowVideo) true else accountViewModel.settings.startVideoPlayback(),
            )
        }

    if (blurhash == null) {
        val ratio = dimensions?.aspectRatio() ?: MediaAspectRatioCache.get(videoUri)

        val modifier =
            if (ratio != null && automaticallyStartPlayback.value) {
                Modifier.aspectRatio(ratio)
            } else {
                Modifier
            }

        Box(modifier) {
            if (!automaticallyStartPlayback.value) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ImageUrlWithDownloadButton(url = videoUri, showImage = automaticallyStartPlayback)
                }
            } else {
                VideoViewInner(
                    videoUri = videoUri,
                    mimeType = mimeType,
                    aspectRatio = ratio,
                    title = title,
                    thumb = thumb,
                    borderModifier = borderModifier,
                    contentScale = contentScale,
                    waveform = waveform,
                    artworkUri = artworkUri,
                    authorName = authorName,
                    nostrUriCallback = nostrUriCallback,
                    automaticallyStartPlayback = automaticallyStartPlayback.value,
                    onZoom = onDialog,
                    accountViewModel = accountViewModel,
                    showControls = showControls,
                )
            }
        }
    } else {
        val ratio = dimensions?.aspectRatio() ?: MediaAspectRatioCache.get(videoUri)

        val modifier =
            if (ratio != null) {
                Modifier.aspectRatio(ratio)
            } else {
                Modifier
            }

        Box(modifier, contentAlignment = Alignment.Center) {
            // Always displays Blurharh to avoid size flickering
            DisplayBlurHash(
                blurhash,
                null,
                contentScale,
                if (ratio != null) borderModifier.aspectRatio(ratio) else borderModifier,
            )

            if (!automaticallyStartPlayback.value) {
                IconButton(
                    modifier = Modifier.size(Size75dp),
                    onClick = { automaticallyStartPlayback.value = true },
                ) {
                    DownloadForOfflineIcon(Size75dp, Color.White)
                }
            } else {
                VideoViewInner(
                    videoUri = videoUri,
                    mimeType = mimeType,
                    aspectRatio = ratio,
                    title = title,
                    thumb = thumb,
                    borderModifier = borderModifier,
                    contentScale = contentScale,
                    waveform = waveform,
                    artworkUri = artworkUri,
                    authorName = authorName,
                    nostrUriCallback = nostrUriCallback,
                    automaticallyStartPlayback = automaticallyStartPlayback.value,
                    onZoom = onDialog,
                    accountViewModel = accountViewModel,
                    showControls = showControls,
                )
            }
        }
    }
}
