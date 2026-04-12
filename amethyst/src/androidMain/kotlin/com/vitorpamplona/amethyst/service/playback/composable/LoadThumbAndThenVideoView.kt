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

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun LoadThumbAndThenVideoView(
    videoUri: String,
    mimeType: String?,
    title: String? = null,
    thumbUri: String,
    authorName: String? = null,
    roundedCorner: Boolean,
    contentScale: ContentScale,
    nostrUriCallback: String? = null,
    accountViewModel: AccountViewModel,
    onDialog: (() -> Unit)? = null,
) {
    var loadingFinished by remember { mutableStateOf<Pair<Boolean, Drawable?>>(Pair(false, null)) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        accountViewModel.loadThumb(
            context,
            thumbUri,
            onReady = {
                loadingFinished =
                    if (it != null) {
                        Pair(true, it)
                    } else {
                        Pair(true, null)
                    }
            },
            onError = { loadingFinished = Pair(true, null) },
        )
    }

    if (loadingFinished.first) {
        if (loadingFinished.second != null) {
            VideoView(
                videoUri = videoUri,
                mimeType = mimeType,
                title = title,
                thumb = VideoThumb(loadingFinished.second),
                roundedCorner = roundedCorner,
                contentScale = contentScale,
                artworkUri = thumbUri,
                authorName = authorName,
                nostrUriCallback = nostrUriCallback,
                accountViewModel = accountViewModel,
                onDialog = onDialog,
            )
        } else {
            VideoView(
                videoUri = videoUri,
                mimeType = mimeType,
                title = title,
                thumb = null,
                roundedCorner = roundedCorner,
                contentScale = contentScale,
                artworkUri = thumbUri,
                authorName = authorName,
                nostrUriCallback = nostrUriCallback,
                accountViewModel = accountViewModel,
                onDialog = onDialog,
            )
        }
    }
}
