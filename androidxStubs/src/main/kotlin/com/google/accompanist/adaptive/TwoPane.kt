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
@file:Suppress("ktlint:standard:function-naming")

package com.google.accompanist.adaptive

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.DisplayFeature
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * JVM stand-in for accompanist-adaptive's TwoPane.
 *
 * Implemented for real, not stubbed: a two-pane list/detail is the layout a
 * desktop window wants most of the time, so an inert version would turn the
 * one screen that already knows how to use the width into a blank box.
 *
 * What is *not* carried over is fold awareness. The Android version places the
 * split along a hinge when the device has one; a desktop display has no hinge,
 * so [calculateDisplayFeatures] returns nothing and the strategy's own split
 * position is always the one used. That is the same path the Android build
 * takes on a phone or tablet without a fold.
 */
@Composable
fun TwoPane(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    strategy: TwoPaneStrategy,
    displayFeatures: List<DisplayFeature>,
    modifier: Modifier = Modifier,
    foldAwareConfiguration: FoldAwareConfiguration = FoldAwareConfiguration.AllFolds,
) {
    Layout(
        modifier = modifier,
        content = {
            Box { first() }
            Box { second() }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val split = strategy.calculateSplit(this, width, height)

        val firstConstraints: Constraints
        val secondConstraints: Constraints
        val secondOffsetX: Int
        val secondOffsetY: Int

        if (split.isVertical) {
            val gap = split.gap.roundToPx()
            val firstWidth = (split.fraction * (width - gap)).roundToInt().coerceIn(0, max(0, width - gap))
            val secondWidth = max(0, width - gap - firstWidth)
            firstConstraints = Constraints.fixed(firstWidth, height)
            secondConstraints = Constraints.fixed(secondWidth, height)
            secondOffsetX = firstWidth + gap
            secondOffsetY = 0
        } else {
            val gap = split.gap.roundToPx()
            val firstHeight = (split.fraction * (height - gap)).roundToInt().coerceIn(0, max(0, height - gap))
            val secondHeight = max(0, height - gap - firstHeight)
            firstConstraints = Constraints.fixed(width, firstHeight)
            secondConstraints = Constraints.fixed(width, secondHeight)
            secondOffsetX = 0
            secondOffsetY = firstHeight + gap
        }

        val firstPlaceable = measurables[0].measure(firstConstraints)
        val secondPlaceable = measurables[1].measure(secondConstraints)

        layout(width, height) {
            firstPlaceable.place(0, 0)
            secondPlaceable.place(secondOffsetX, secondOffsetY)
        }
    }
}

/** Where the split falls, in the axis the strategy chose. */
class SplitResult internal constructor(
    internal val isVertical: Boolean,
    internal val fraction: Float,
    internal val gap: Dp,
)

fun interface TwoPaneStrategy {
    fun calculateSplit(
        density: Density,
        width: Int,
        height: Int,
    ): SplitResult
}

/** Side by side: [splitFraction] of the width goes to the first pane. */
fun HorizontalTwoPaneStrategy(
    splitFraction: Float,
    gapWidth: Dp = 0.dp,
): TwoPaneStrategy = TwoPaneStrategy { _, _, _ -> SplitResult(isVertical = true, fraction = splitFraction, gap = gapWidth) }

/** One above the other: [splitFraction] of the height goes to the first pane. */
fun VerticalTwoPaneStrategy(
    splitFraction: Float,
    gapHeight: Dp = 0.dp,
): TwoPaneStrategy = TwoPaneStrategy { _, _, _ -> SplitResult(isVertical = false, fraction = splitFraction, gap = gapHeight) }

/**
 * Which folds a [TwoPane] should snap its split to. Kept so call sites compile
 * and read the same on both platforms; with no folds on a desktop display every
 * value behaves identically here.
 */
enum class FoldAwareConfiguration {
    AllFolds,
    HorizontalFoldsOnly,
    VerticalFoldsOnly,
}

/**
 * Empty, and correctly so: a desktop display has no hinge or fold to report.
 * See [TwoPane].
 */
@Composable
fun calculateDisplayFeatures(activity: Activity?): List<DisplayFeature> = emptyList()
