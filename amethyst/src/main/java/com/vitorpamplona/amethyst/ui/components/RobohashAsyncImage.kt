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

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.robohash.CachedRobohash
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
            imageVector = Icons.Default.Face,
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
) {
    if (model != null && loadProfilePicture) {
        val painter =
            if (loadRobohash) {
                rememberVectorPainter(
                    image = CachedRobohash.get(robot, MaterialTheme.colorScheme.isLight),
                )
            } else {
                forwardingPainter(
                    painter =
                        rememberVectorPainter(
                            image = Icons.Default.Face,
                        ),
                    colorFilter = MaterialTheme.colorScheme.onBackgroundColorFilter,
                )
            }

        AsyncImage(
            model = model,
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
                imageVector = Icons.Default.Face,
                contentDescription = contentDescription,
                colorFilter = MaterialTheme.colorScheme.onBackgroundColorFilter,
                modifier = modifier,
                alignment = alignment,
                contentScale = contentScale,
            )
        }
    }
}
