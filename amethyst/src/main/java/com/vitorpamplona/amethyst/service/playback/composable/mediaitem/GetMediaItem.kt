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
package com.vitorpamplona.amethyst.service.playback.composable.mediaitem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.compose.produceCachedState

val mediaItemCache = MediaItemCache()

@Composable
fun GetMediaItem(
    videoUri: String,
    title: String? = null,
    artworkUri: String? = null,
    authorName: String? = null,
    callbackUri: String? = null,
    mimeType: String? = null,
    aspectRatio: Float? = null,
    proxyPort: Int? = null,
    inner: @Composable (LoadedMediaItem) -> Unit,
) {
    val data =
        remember(videoUri) {
            MediaItemData(
                videoUri = videoUri,
                authorName = authorName,
                title = title,
                artworkUri = artworkUri,
                callbackUri = callbackUri,
                mimeType = mimeType,
                aspectRatio = aspectRatio,
                proxyPort = proxyPort,
            )
        }

    GetMediaItem(data, inner)
}

@Composable
fun GetMediaItem(
    data: MediaItemData,
    inner: @Composable (LoadedMediaItem) -> Unit,
) {
    val mediaItem by produceCachedState(cache = mediaItemCache, key = data)

    mediaItem?.let {
        inner(it)
    }
}
