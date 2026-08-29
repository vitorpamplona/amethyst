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
package com.vitorpamplona.amethyst.commons.ui.waveform

import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode

/**
 * How a bar's height is derived from the samples that fall inside it.
 *
 * The waveform has far more samples than it has bars, so each bar summarises a
 * window of them. [Avg] reads as the overall loudness, [Max] as the peaks.
 */
enum class AmplitudeType { Avg, Max, Min }

/** Where a bar sits inside the track's height. */
enum class WaveformAlignment { Top, Bottom, Center }

/**
 * A linear gradient that slides sideways forever, for the played portion of a
 * waveform.
 *
 * [width] is the gradient's period in pixels; the brush tiles, and the animation
 * walks one full period per cycle, so the seam never shows.
 */
@Composable
fun Brush.Companion.infiniteLinearGradient(
    colors: List<Color>,
    animation: DurationBasedAnimationSpec<Float> = tween(durationMillis = 6000, easing = LinearEasing),
    width: Float = 128f,
): Brush {
    val transition = rememberInfiniteTransition(label = "infiniteLinearGradient")
    val offset by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = width,
            animationSpec = infiniteRepeatable(animation, RepeatMode.Restart),
            label = "offset",
        )

    return linearGradient(
        colors = colors,
        start = Offset(offset, 0f),
        end = Offset(offset + width, 0f),
        tileMode = TileMode.Repeated,
    )
}
