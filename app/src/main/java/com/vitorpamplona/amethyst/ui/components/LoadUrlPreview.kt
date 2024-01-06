/**
 * Copyright (c) 2023 Vitor Pamplona
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

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.HalfVertPadding
import kotlinx.collections.immutable.persistentListOf

@Composable
fun LoadUrlPreview(
    url: String,
    urlText: String,
    accountViewModel: AccountViewModel,
) {
    val automaticallyShowUrlPreview = remember { accountViewModel.settings.showUrlPreview.value }

    if (!automaticallyShowUrlPreview) {
        ClickableUrl(urlText, url)
    } else {
        var urlPreviewState by
            remember(url) {
                mutableStateOf(
                    UrlCachedPreviewer.cache.get(url) ?: UrlPreviewState.Loading,
                )
            }

        // Doesn't use a viewModel because of viewModel reusing issues (too many UrlPreview are
        // created).
        if (urlPreviewState == UrlPreviewState.Loading) {
            LaunchedEffect(url) { accountViewModel.urlPreview(url) { urlPreviewState = it } }
        }

        Crossfade(
            targetState = urlPreviewState,
            animationSpec = tween(durationMillis = 100),
            label = "UrlPreview",
        ) { state ->
            when (state) {
                is UrlPreviewState.Loaded -> {
                    if (state.previewInfo.mimeType.type == "image") {
                        Box(modifier = HalfVertPadding) {
                            ZoomableContentView(
                                ZoomableUrlImage(url),
                                persistentListOf(),
                                roundedCorner = true,
                                accountViewModel,
                            )
                        }
                    } else if (state.previewInfo.mimeType.type == "video") {
                        Box(modifier = HalfVertPadding) {
                            ZoomableContentView(
                                ZoomableUrlVideo(url),
                                persistentListOf(),
                                roundedCorner = true,
                                accountViewModel,
                            )
                        }
                    } else {
                        UrlPreviewCard(url, state.previewInfo)
                    }
                }
                else -> {
                    ClickableUrl(urlText, url)
                }
            }
        }
    }
}
