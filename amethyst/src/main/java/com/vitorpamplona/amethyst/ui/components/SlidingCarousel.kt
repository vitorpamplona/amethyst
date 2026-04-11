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

import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.components.DotsIndicator as CommonsDotsIndicator
import com.vitorpamplona.amethyst.commons.ui.components.IndicatorDot as CommonsIndicatorDot
import com.vitorpamplona.amethyst.commons.ui.components.SlidingCarousel as CommonsSlidingCarousel

// Backward-compat wrappers: SlidingCarousel moved to commons

@Composable
fun SlidingCarousel(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    itemContent: @Composable (index: Int) -> Unit,
) = CommonsSlidingCarousel(pagerState, modifier, itemContent)

@Composable
fun DotsIndicator(
    modifier: Modifier = Modifier,
    totalDots: Int,
    selectedIndex: Int,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    unSelectedColor: Color = MaterialTheme.colorScheme.placeholderText,
    dotSize: Dp,
) = CommonsDotsIndicator(modifier, totalDots, selectedIndex, selectedColor, unSelectedColor, dotSize)

@Composable
fun IndicatorDot(
    modifier: Modifier = Modifier,
    size: Dp,
    color: Color,
) = CommonsIndicatorDot(modifier, size, color)
