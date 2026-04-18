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
package com.vitorpamplona.amethyst.ui.layouts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Single nested-scroll connection that hides/reveals the top and bottom bars together.
 *
 * Behaviour:
 *  - onPreScroll: consumes the portion of the scroll delta used to move the bars
 *    (both hide and reveal), returning the exact amount absorbed so the list never
 *    "loses pixels" at the edge of the bars' travel range.
 *  - onPostFling: snaps a mid-way bar to the nearest edge, continuing the fling's
 *    tail velocity so the settle motion feels like part of the fling, not a second
 *    animation after it. No velocity is returned upward.
 */
class DisappearingBarNestedScroll(
    private val state: DisappearingBarState,
    private val canScroll: () -> Boolean,
    private val reverseLayout: Boolean,
) : NestedScrollConnection {
    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (!canScroll()) return Offset.Zero
        if (available.y == 0f) return Offset.Zero

        val deltaY = if (reverseLayout) -available.y else available.y
        val applied = applyDelta(deltaY)
        if (applied == 0f) return Offset.Zero
        return Offset(0f, if (reverseLayout) -applied else applied)
    }

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity {
        if (canScroll()) {
            // Feed the fling's remaining velocity into the settle so the bar keeps
            // moving in the same direction rather than starting a fresh animation.
            val velocityY = if (reverseLayout) -available.y else available.y
            state.settleToNearestEdge(initialVelocityY = velocityY)
        }
        // Swallow any residual velocity so parents don't get a phantom fling kick.
        return Velocity.Zero
    }

    /**
     * Applies the given delta (in content-space – negative hides, positive reveals) to
     * both bar offsets, clamped to their travel range.
     *
     * Returns the delta that was actually absorbed, using the side with the larger
     * absorption so consumption reporting remains accurate when the two bars have
     * different remaining travel.
     */
    private fun applyDelta(deltaY: Float): Float {
        val prevTop = state.topHeightOffset
        val prevBottom = state.bottomHeightOffset
        val topLimit = state.topHeightLimit
        val bottomLimit = state.bottomHeightLimit

        val newTop = (prevTop + deltaY).coerceIn(-topLimit, 0f)
        val newBottom = (prevBottom + deltaY).coerceIn(-bottomLimit, 0f)
        state.topHeightOffset = newTop
        state.bottomHeightOffset = newBottom

        val topDelta = newTop - prevTop
        val bottomDelta = newBottom - prevBottom
        return if (deltaY < 0f) minOf(topDelta, bottomDelta) else maxOf(topDelta, bottomDelta)
    }
}
