/**
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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.vitorpamplona.amethyst.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.note.DownloadForOfflineIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size40dp
import com.vitorpamplona.amethyst.ui.theme.Size6dp
import com.vitorpamplona.amethyst.ui.theme.Size75dp

@Composable
fun MyAsyncImage(
    imageUrl: String,
    contentDescription: String?,
    contentScale: ContentScale,
    mainImageModifier: Modifier,
    loadedImageModifier: Modifier,
    accountViewModel: AccountViewModel,
    onError: @Composable () -> Unit,
) {
    val ratio = MediaAspectRatioCache.get(imageUrl)
    val showImage = remember { mutableStateOf(accountViewModel.settings.showImages.value) }

    CrossfadeIfEnabled(targetState = showImage.value, contentAlignment = Alignment.Center, accountViewModel = accountViewModel) {
        if (it) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = mainImageModifier,
            ) {
                val state by painter.state.collectAsState()
                when (state) {
                    is AsyncImagePainter.State.Loading -> {
                        if (ratio != null) {
                            Box(loadedImageModifier.aspectRatio(ratio), contentAlignment = Alignment.Center) {
                                LoadingAnimation(Size40dp, Size6dp)
                            }
                        } else {
                            WaitAndDisplay {
                                DisplayUrlWithLoadingSymbol(imageUrl)
                            }
                        }
                    }
                    is AsyncImagePainter.State.Error -> {
                        onError()
                    }
                    is AsyncImagePainter.State.Success -> {
                        SubcomposeAsyncImageContent(loadedImageModifier)

                        SideEffect {
                            val drawable = (state as AsyncImagePainter.State.Success).result.image
                            MediaAspectRatioCache.add(imageUrl, drawable.width, drawable.height)
                        }
                    }
                    else -> {}
                }
            }
        } else {
            if (ratio != null) {
                Box(loadedImageModifier.aspectRatio(ratio), contentAlignment = Alignment.Center) {
                    IconButton(
                        modifier = Modifier.size(Size75dp),
                        onClick = { showImage.value = true },
                    ) {
                        DownloadForOfflineIcon(Size75dp, MaterialTheme.colorScheme.onBackground)
                    }
                }
            } else {
                Box(loadedImageModifier.aspectRatio(16 / 9.0f), contentAlignment = Alignment.Center) {
                    IconButton(
                        modifier = Modifier.size(Size75dp),
                        onClick = { showImage.value = true },
                    ) {
                        DownloadForOfflineIcon(Size75dp, MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
    }
}
