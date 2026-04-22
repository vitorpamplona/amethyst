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
package com.vitorpamplona.amethyst.ui.components

import android.graphics.drawable.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.asDrawable
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.vitorpamplona.amethyst.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size75dp
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import kotlinx.coroutines.delay

@Composable
fun GifVideoView(
    videoUri: String,
    contentDescription: String?,
    dimensions: DimensionTag?,
    blurhash: String?,
    roundedCorner: Boolean,
    contentScale: ContentScale,
    onDialog: (() -> Unit)? = null,
    accountViewModel: AccountViewModel,
    thumbhash: String? = null,
) {
    val ratio = dimensions?.aspectRatio() ?: MediaAspectRatioCache.get(videoUri)
    val automaticallyStartPlayback = remember { mutableStateOf(accountViewModel.settings.autoPlayVideos()) }
    val controllerVisible = remember { mutableStateOf(false) }

    LaunchedEffect(controllerVisible.value, automaticallyStartPlayback.value) {
        if (controllerVisible.value && automaticallyStartPlayback.value) {
            delay(3000)
            controllerVisible.value = false
        }
    }

    val borderModifier = if (roundedCorner) MaterialTheme.colorScheme.imageModifier else Modifier
    val context = LocalContext.current

    Box(
        modifier =
            (if (ratio != null) borderModifier.aspectRatio(ratio) else borderModifier)
                .fillMaxWidth()
                .clickable { controllerVisible.value = !controllerVisible.value },
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = videoUri,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        ) {
            val state by painter.state.collectAsState()

            val successState = state as? AsyncImagePainter.State.Success
            val drawable = successState?.result?.image?.asDrawable(context.resources)

            LaunchedEffect(automaticallyStartPlayback.value, drawable) {
                if (drawable is Animatable) {
                    if (automaticallyStartPlayback.value) {
                        drawable.start()
                    } else {
                        drawable.stop()
                    }
                }
            }

            when (state) {
                is AsyncImagePainter.State.Loading -> {
                    DisplayBlurHash(
                        blurhash,
                        contentDescription,
                        contentScale,
                        Modifier.fillMaxSize(),
                        thumbhash = thumbhash,
                    )
                }

                is AsyncImagePainter.State.Success -> {
                    SubcomposeAsyncImageContent(Modifier.fillMaxSize())
                }

                is AsyncImagePainter.State.Error -> {
                    ClickableUrl(urlText = videoUri, url = videoUri)
                }

                else -> {}
            }
        }

        // Zoom button
        AnimatedVisibility(
            visible = controllerVisible.value,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            IconButton(
                onClick = { onDialog?.invoke() },
            ) {
                Icon(
                    imageVector = Icons.Outlined.OpenInFull,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }

        // Play/Pause button
        AnimatedVisibility(
            visible = controllerVisible.value || !automaticallyStartPlayback.value,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(if (!automaticallyStartPlayback.value) Color.Black.copy(alpha = 0.3f) else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    modifier = Modifier.size(Size75dp),
                    onClick = { automaticallyStartPlayback.value = !automaticallyStartPlayback.value },
                ) {
                    Icon(
                        imageVector = if (automaticallyStartPlayback.value) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(Size75dp),
                    )
                }
            }
        }
    }
}
