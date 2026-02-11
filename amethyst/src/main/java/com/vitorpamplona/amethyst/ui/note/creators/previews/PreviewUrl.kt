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
package com.vitorpamplona.amethyst.ui.note.creators.previews

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.compose.produceCachedState
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer
import com.vitorpamplona.amethyst.service.playback.composable.VideoView
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.components.DisplayUrlWithLoadingSymbol
import com.vitorpamplona.amethyst.ui.components.UrlPreviewCard
import com.vitorpamplona.amethyst.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.ui.components.WaitAndDisplay
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun PreviewUrl(
    myUrlPreview: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (RichTextParser.isValidURL(myUrlPreview)) {
        if (RichTextParser.isImageUrl(myUrlPreview)) {
            AsyncImage(
                model = myUrlPreview,
                contentDescription = myUrlPreview,
                contentScale = ContentScale.FillHeight,
                modifier = Modifier.fillMaxHeight().aspectRatio(1f),
            )
        } else if (RichTextParser.isVideoUrl(myUrlPreview)) {
            VideoView(
                myUrlPreview,
                mimeType = null,
                roundedCorner = false,
                gallery = false,
                contentScale = ContentScale.FillHeight,
                accountViewModel = accountViewModel,
            )
        } else {
            MyLoadUrlPreviewDirect(myUrlPreview, myUrlPreview, accountViewModel)
        }
    } else if (RichTextParser.startsWithNIP19Scheme(myUrlPreview)) {
        val bgColor = MaterialTheme.colorScheme.background
        val backgroundColor = remember { mutableStateOf(bgColor) }

        BechLinkPreview(
            word = myUrlPreview,
            canPreview = true,
            quotesLeft = 1,
            backgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    } else if (RichTextParser.isUrlWithoutScheme(myUrlPreview)) {
        MyLoadUrlPreviewDirect("https://$myUrlPreview", myUrlPreview, accountViewModel)
    }
}

@Composable
fun PreviewUrlFillWidth(
    myUrlPreview: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (RichTextParser.isValidURL(myUrlPreview)) {
        if (RichTextParser.isImageUrl(myUrlPreview)) {
            AsyncImage(
                model = myUrlPreview,
                contentDescription = myUrlPreview,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxHeight().aspectRatio(1f),
            )
        } else if (RichTextParser.isVideoUrl(myUrlPreview)) {
            VideoView(
                myUrlPreview,
                mimeType = null,
                roundedCorner = false,
                gallery = false,
                contentScale = ContentScale.FillWidth,
                accountViewModel = accountViewModel,
            )
        } else {
            MyLoadUrlPreviewDirectFillWidth(myUrlPreview, myUrlPreview, accountViewModel)
        }
    } else if (RichTextParser.startsWithNIP19Scheme(myUrlPreview)) {
        val bgColor = MaterialTheme.colorScheme.background
        val backgroundColor = remember { mutableStateOf(bgColor) }

        BechLinkPreview(
            word = myUrlPreview,
            canPreview = true,
            quotesLeft = 1,
            backgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    } else if (RichTextParser.isUrlWithoutScheme(myUrlPreview)) {
        MyLoadUrlPreviewDirectFillWidth("https://$myUrlPreview", myUrlPreview, accountViewModel)
    }
}

@Composable
private fun BechLinkPreview(
    word: String,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val loadedLink by produceCachedState(cache = accountViewModel.bechLinkCache, key = word)

    val baseNote = loadedLink?.baseNote

    if (canPreview && quotesLeft > 0 && baseNote != null) {
        Row {
            NoteCompose(
                baseNote = baseNote,
                modifier = Modifier.aspectRatio(1f),
                isQuotedNote = true,
                quotesLeft = quotesLeft - 1,
                parentBackgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    } else {
        val text =
            if (word.length > 16) {
                word.replaceRange(8, word.length - 8, ":")
            } else {
                word
            }

        Text(text = text, maxLines = 1)
    }
}

@Composable
private fun MyLoadUrlPreviewDirect(
    url: String,
    urlText: String,
    accountViewModel: AccountViewModel,
) {
    @Suppress("ProduceStateDoesNotAssignValue")
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
                if (state.previewInfo.mimeType.startsWith("image")) {
                    AsyncImage(
                        model = state.previewInfo.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                    )
                } else if (state.previewInfo.mimeType.startsWith("video")) {
                    VideoView(
                        state.previewInfo.url,
                        mimeType = state.previewInfo.mimeType,
                        roundedCorner = false,
                        gallery = false,
                        contentScale = ContentScale.Crop,
                        accountViewModel = accountViewModel,
                    )
                } else {
                    Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.aspectRatio(1f)) {
                        AsyncImage(
                            model = state.previewInfo.imageUrlFullPath,
                            contentDescription = state.previewInfo.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )

                        Text(
                            text = state.previewInfo.verifiedUrl?.host ?: state.previewInfo.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            else -> {
                Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.aspectRatio(1f)) {
                    ClickableUrl(urlText, url)
                }
            }
        }
    }
}

@Composable
private fun MyLoadUrlPreviewDirectFillWidth(
    url: String,
    urlText: String,
    accountViewModel: AccountViewModel,
) {
    @Suppress("ProduceStateDoesNotAssignValue")
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
                if (state.previewInfo.mimeType.startsWith("image")) {
                    AsyncImage(
                        model = state.previewInfo.url,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (state.previewInfo.mimeType.startsWith("video")) {
                    VideoView(
                        state.previewInfo.url,
                        mimeType = state.previewInfo.mimeType,
                        roundedCorner = false,
                        gallery = false,
                        contentScale = ContentScale.FillWidth,
                        accountViewModel = accountViewModel,
                    )
                } else {
                    UrlPreviewCard(url, previewInfo = state.previewInfo)
                }
            }

            is UrlPreviewState.Loading -> {
                WaitAndDisplay {
                    DisplayUrlWithLoadingSymbol(url)
                }
            }

            else -> {
                ClickableUrl(urlText, url)
            }
        }
    }
}
