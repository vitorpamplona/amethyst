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
package com.vitorpamplona.amethyst.service.playback.composable.wavefront

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val DEFAULT_GRAPHICS_LAYER_ALPHA = 0.99F
private const val MIN_BAR_FRACTION = 0.08f
private const val TWO_PI = 2f * Math.PI.toFloat()

@Composable
fun AudioPlayingAnimation(
    controllerState: MediaControllerState,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember { mutableStateOf(controllerState.controller.isPlaying) }

    DisposableEffect(controllerState.controller) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            }
        controllerState.controller.addListener(listener)
        onDispose { controllerState.controller.removeListener(listener) }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "audioAnimation")

    // Three animation drivers at different speeds for organic layered motion
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "phase1",
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "phase2",
    )

    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2700, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "phase3",
    )

    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "gradientAnimation",
    )

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .requiredHeight(48.dp)
                .padding(start = 10.dp, end = 10.dp)
                .graphicsLayer(alpha = DEFAULT_GRAPHICS_LAYER_ALPHA),
    ) {
        val barWidth = 3.dp.toPx()
        val barPadding = 2.dp.toPx()
        val barRadius = 2.dp.toPx()
        val totalBarWidth = barWidth + barPadding
        val barCount = (size.width / totalBarWidth).toInt()
        if (barCount <= 0) return@Canvas

        val barBrush =
            Brush.linearGradient(
                colors = listOf(Color(0xff2598cf), Color(0xff652d80)),
                start = Offset(gradientOffset * size.width, 0f),
                end = Offset(gradientOffset * size.width + 128.dp.toPx(), 0f),
            )

        for (i in 0 until barCount) {
            val normalized = i.toFloat() / barCount

            // Bell-curve envelope: bars in the center are taller than edges
            val distFromCenter = abs(normalized - 0.5f) * 2f // 0 at center, 1 at edges
            val envelope = 1f - distFromCenter * distFromCenter * 0.6f

            val barFraction =
                if (isPlaying) {
                    // Combine three sine waves at different frequencies and phases
                    // to produce organic, non-repeating-looking motion
                    val w1 = sin(phase1 + normalized * 7f) * 0.4f
                    val w2 = sin(phase2 + normalized * 11f) * 0.3f
                    val w3 = cos(phase3 + normalized * 5f) * 0.3f
                    val combined = (w1 + w2 + w3 + 1f) / 2f // normalize to 0..1
                    (MIN_BAR_FRACTION + combined * envelope * (1f - MIN_BAR_FRACTION))
                        .coerceIn(MIN_BAR_FRACTION, 1f)
                } else {
                    MIN_BAR_FRACTION
                }

            val barHeight = size.height * barFraction
            val x = i * totalBarWidth
            val y = (size.height - barHeight) / 2f

            drawRoundRect(
                brush = barBrush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barRadius, barRadius),
                style = Fill,
            )
        }
    }
}
