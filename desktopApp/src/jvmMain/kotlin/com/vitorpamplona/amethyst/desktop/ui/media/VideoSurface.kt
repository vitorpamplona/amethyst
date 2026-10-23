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
package com.vitorpamplona.amethyst.desktop.ui.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.vitorpamplona.amethyst.desktop.service.media.VideoPixelAspect
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import kotlin.math.roundToInt

/**
 * [VideoPlayerSurface] at [url]'s real shape (see [VideoPixelAspect]). A video with square pixels
 * draws as the engine has it, fitted into [modifier].
 *
 * An anamorphic one gets a box of its display shape. Inside it the engine still draws at the shape
 * it believes in, at the box's full height (it scales its output to fit its surface, so a surface
 * squeezed to the display shape would get a low-resolution frame), and that picture is then scaled
 * horizontally to the box's width.
 */
@Composable
fun VideoSurface(
    playerState: VideoPlayerState,
    url: String?,
    modifier: Modifier = Modifier,
) {
    val engineRatio = playerState.aspectRatio
    val ratio = VideoPixelAspect.displayRatio(url, engineRatio)
    if (ratio == engineRatio) {
        VideoPlayerSurface(playerState = playerState, modifier = modifier, contentScale = ContentScale.Fit)
        return
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        VideoPlayerSurface(
            playerState = playerState,
            modifier =
                Modifier.aspectRatio(ratio).layout { measurable, constraints ->
                    val width = constraints.maxWidth
                    val height = constraints.maxHeight
                    val stored = (height * engineRatio).roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(stored, height))
                    layout(width, height) {
                        placeable.placeWithLayer((width - stored) / 2, 0) { scaleX = width.toFloat() / stored }
                    }
                },
            contentScale = ContentScale.Fit,
        )
    }
}
