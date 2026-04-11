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

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import com.vitorpamplona.amethyst.commons.ui.components.DefaultAnimationColors as CommonsDefaultAnimationColors
import com.vitorpamplona.amethyst.commons.ui.components.LoadingAnimation as CommonsLoadingAnimation

// Backward-compat wrappers: LoadingAnimation, DefaultAnimationColors moved to commons

val DefaultAnimationColors = CommonsDefaultAnimationColors

@Composable
fun LoadingAnimation(
    indicatorSize: Dp = 20.dp,
    circleWidth: Dp = 4.dp,
    circleColors: ImmutableList<Color> = CommonsDefaultAnimationColors,
    animationDuration: Int = 1000,
) = CommonsLoadingAnimation(indicatorSize, circleWidth, circleColors, animationDuration)
