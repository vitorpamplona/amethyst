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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.asDrawable
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Font10SP
import com.vitorpamplona.amethyst.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag

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
    // Keys are primitive width/height so a freshly parsed DimensionTag instance for the same
    // event doesn't re-run this lambda — DimensionTag uses reference equality, not structural.
    val dimW = dimensions?.width
    val dimH = dimensions?.height
    val ratio =
        remember(videoUri, dimW, dimH) {
            if (dimW != null && dimH != null && dimH > 0) {
                dimW.toFloat() / dimH.toFloat()
            } else {
                MediaAspectRatioCache.get(videoUri)
            }
        }
    val autoPlay = accountViewModel.settings.autoPlayVideos()
    val borderModifier = if (roundedCorner) MaterialTheme.colorScheme.imageModifier else Modifier
    val context = LocalContext.current

    val containerModifier =
        (if (ratio != null) borderModifier.aspectRatio(ratio) else borderModifier)
            .fillMaxWidth()
            .let { if (onDialog != null) it.clickable { onDialog() } else it }

    Box(
        modifier = containerModifier,
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

            LaunchedEffect(autoPlay, drawable) {
                if (drawable is Animatable) {
                    if (autoPlay) drawable.start() else drawable.stop()
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

                    SideEffect {
                        val image = (state as AsyncImagePainter.State.Success).result.image
                        MediaAspectRatioCache.add(videoUri, image.width, image.height)
                    }
                }

                is AsyncImagePainter.State.Error -> {
                    ClickableUrl(urlText = videoUri, url = videoUri)
                }

                else -> {}
            }
        }

        if (!autoPlay) {
            Text(
                text = stringResource(R.string.gif),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = Font10SP,
                lineHeight = Font10SP,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(SmallBorder)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}
