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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.HalfVertPadding

@Composable
fun LoadUrlPreview(
    url: String,
    urlText: String,
    callbackUri: String? = null,
    accountViewModel: AccountViewModel,
) {
    if (!accountViewModel.settings.showUrlPreview.value) {
        ClickableUrl(urlText, url)
    } else {
        val urlPreviewState by
            produceState(
                initialValue = UrlCachedPreviewer.cache.get(url) ?: UrlPreviewState.Loading,
                key1 = url,
            ) {
                if (value == UrlPreviewState.Loading) {
                    accountViewModel.urlPreview(url) { value = it }
                }
            }

        CrossfadeIfEnabled(
            targetState = urlPreviewState,
            label = "UrlPreview",
            accountViewModel = accountViewModel,
        ) { state ->
            when (state) {
                is UrlPreviewState.Loaded -> {
                    RenderLoaded(state, url, callbackUri, accountViewModel)
                }
                else -> {
                    ClickableUrl(urlText, url)
                }
            }
        }
    }
}

@Composable
fun RenderLoaded(
    state: UrlPreviewState.Loaded,
    url: String,
    callbackUri: String? = null,
    accountViewModel: AccountViewModel,
) {
    if (state.previewInfo.mimeType.type == "image") {
        Box(modifier = HalfVertPadding) {
            ZoomableContentView(
                content = MediaUrlImage(url, uri = callbackUri),
                roundedCorner = true,
                isFiniteHeight = false,
                accountViewModel = accountViewModel,
            )
        }
    } else if (state.previewInfo.mimeType.type == "video") {
        Box(modifier = HalfVertPadding) {
            ZoomableContentView(
                content = MediaUrlVideo(url, uri = callbackUri),
                roundedCorner = true,
                isFiniteHeight = false,
                accountViewModel = accountViewModel,
            )
        }
    } else {
        UrlPreviewCard(url, state.previewInfo)
    }
}
