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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.asDrawable
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.icons.symbols.rememberMaterialSymbolPainter
import com.vitorpamplona.amethyst.commons.robohash.CachedRobohash
import com.vitorpamplona.amethyst.commons.ui.components.ProfilePictureUrl
import com.vitorpamplona.amethyst.ui.theme.isLight
import com.vitorpamplona.amethyst.ui.theme.onBackgroundColorFilter

@Composable
fun RobohashAsyncImage(
    robot: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    loadRobohash: Boolean,
) {
    if (loadRobohash) {
        Image(
            imageVector = CachedRobohash.get(robot, MaterialTheme.colorScheme.isLight),
            contentDescription = contentDescription,
            modifier = modifier,
        )
    } else {
        Image(
            painter = rememberMaterialSymbolPainter(MaterialSymbols.Face),
            contentDescription = contentDescription,
            colorFilter = MaterialTheme.colorScheme.onBackgroundColorFilter,
            modifier = modifier,
        )
    }
}

@Composable
fun RobohashFallbackAsyncImage(
    robot: String,
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
    autoPlayGif: Boolean = true,
) {
    if (model != null && loadProfilePicture && isGifUrl(model)) {
        GifProfilePicture(
            userHex = robot,
            userPicture = model,
            contentDescription = contentDescription,
            modifier = modifier,
            loadRobohash = loadRobohash,
            autoPlay = autoPlayGif,
        )
    } else if (model != null && loadProfilePicture) {
        val painter =
            if (loadRobohash) {
                rememberVectorPainter(
                    image = CachedRobohash.get(robot, MaterialTheme.colorScheme.isLight),
                )
            } else {
                forwardingPainter(
                    painter = rememberMaterialSymbolPainter(MaterialSymbols.Face),
                    colorFilter = MaterialTheme.colorScheme.onBackgroundColorFilter,
                )
            }

        AsyncImage(
            model = ProfilePictureUrl(model),
            contentDescription = contentDescription,
            modifier = modifier,
            placeholder = painter,
            fallback = painter,
            error = painter,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
            filterQuality = filterQuality,
        )
    } else {
        if (loadRobohash) {
            Image(
                imageVector = CachedRobohash.get(robot, MaterialTheme.colorScheme.isLight),
                contentDescription = contentDescription,
                modifier = modifier,
                alignment = alignment,
                contentScale = contentScale,
                colorFilter = colorFilter,
            )
        } else {
            Image(
                painter = rememberMaterialSymbolPainter(MaterialSymbols.Face),
                contentDescription = contentDescription,
                colorFilter = MaterialTheme.colorScheme.onBackgroundColorFilter,
                modifier = modifier,
                alignment = alignment,
                contentScale = contentScale,
            )
        }
    }
}

@Composable
fun GifProfilePicture(
    userHex: String,
    userPicture: String,
    contentDescription: String?,
    modifier: Modifier,
    loadRobohash: Boolean,
    autoPlay: Boolean,
) {
    val resources = LocalContext.current.resources
    val fallbackPainter =
        if (loadRobohash) {
            rememberVectorPainter(image = CachedRobohash.get(userHex, MaterialTheme.colorScheme.isLight))
        } else {
            forwardingPainter(
                painter = rememberMaterialSymbolPainter(MaterialSymbols.Face),
                colorFilter = MaterialTheme.colorScheme.onBackgroundColorFilter,
            )
        }

    Box(modifier = modifier) {
        SubcomposeAsyncImage(
            model = userPicture,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        ) {
            val state by painter.state.collectAsState()
            val successState = state as? AsyncImagePainter.State.Success
            val drawable = successState?.result?.image?.asDrawable(resources)

            LaunchedEffect(drawable, autoPlay) {
                if (drawable is Animatable) {
                    if (autoPlay) drawable.start() else drawable.stop()
                }
            }

            when (state) {
                is AsyncImagePainter.State.Success -> {
                    SubcomposeAsyncImageContent()
                }

                else -> {
                    Image(
                        painter = fallbackPainter,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}
