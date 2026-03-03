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
package com.vitorpamplona.amethyst.service.playback.composable.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val FadeIn = fadeIn()
private val FadeOut = fadeOut()

private val TopGradientColors =
    listOf(
        Color.Black.copy(alpha = 0.6f),
        Color.Black.copy(alpha = 0.3f),
        Color.Transparent,
    )

private val BottomGradientColors =
    listOf(
        Color.Transparent,
        Color.Black.copy(alpha = 0.4f),
        Color.Black.copy(alpha = 0.7f),
    )

@Composable
private fun GradientOverlay(
    controllerVisible: State<Boolean>,
    colors: List<Color>,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = controllerVisible.value,
        modifier = modifier,
        enter = FadeIn,
        exit = FadeOut,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(brush = Brush.verticalGradient(colors = colors)),
        )
    }
}

@Composable
fun TopGradientOverlay(
    controllerVisible: State<Boolean>,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
) = GradientOverlay(controllerVisible, TopGradientColors, height, modifier)

@Composable
fun BottomGradientOverlay(
    controllerVisible: State<Boolean>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
) = GradientOverlay(controllerVisible, BottomGradientColors, height, modifier)
