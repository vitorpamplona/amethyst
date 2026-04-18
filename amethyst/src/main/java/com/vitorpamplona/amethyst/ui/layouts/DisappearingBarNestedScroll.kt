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
 *  - onPreScroll: on "hide" deltas, consumes exactly the amount used to shift the bars
 *    (never over-claims the available delta). This avoids the list "swallowing" pixels
 *    at the edge of the bars' travel range.
 *  - onPostScroll: on "reveal" deltas still available after the list consumed its share,
 *    moves the bars back in; again consumes only what it used.
 *  - onPostFling: snaps mid-way bars to the nearest edge. No additional decay. No
 *    phantom velocity is returned upward.
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
        val deltaY = if (reverseLayout) -available.y else available.y
        // Only hide on "hide" direction in the pre-scroll phase.
        if (deltaY >= 0f) return Offset.Zero

        val consumed = applyDelta(deltaY)
        if (consumed == 0f) return Offset.Zero
        return Offset(0f, if (reverseLayout) -consumed else consumed)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (!canScroll()) return Offset.Zero
        val deltaY = if (reverseLayout) -available.y else available.y
        // Only reveal on "reveal" direction in the post-scroll phase.
        if (deltaY <= 0f) return Offset.Zero

        val applied = applyDelta(deltaY)
        if (applied == 0f) return Offset.Zero
        return Offset(0f, if (reverseLayout) -applied else applied)
    }

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity {
        if (canScroll()) state.snapToNearestEdge()
        // Do not propagate phantom velocity back up the nested-scroll tree.
        return Velocity.Zero
    }

    /**
     * Applies the given delta (in "content-space" – negative hides, positive reveals) to
     * both bar offsets, clamped to their travel range.
     *
     * Returns the delta that was actually absorbed (in the same sign convention) so the
     * caller can report accurate consumption upward.
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

        // If either bar moved we consider that portion consumed. Use the largest absorbed
        // magnitude so we don't claim more than one bar's worth of pixels.
        val topDelta = newTop - prevTop
        val bottomDelta = newBottom - prevBottom
        return if (deltaY < 0f) minOf(topDelta, bottomDelta) else maxOf(topDelta, bottomDelta)
    }
}
