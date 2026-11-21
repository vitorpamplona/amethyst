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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * `Modifier.clip(shape)`, at a fraction of its cost where Skia draws in software (native Wayland on
 * the desktop) when [shape] is a [RoundedCornerShape] with four equal corners; any other shape clips
 * as usual.
 *
 * An antialiased rounded clip makes software Skia blend every pixel under it through a coverage
 * mask: a photo drawn 1:1 inside one costs ~1.7 ms a frame, inside a plain rectangle ~0.4 ms. Only
 * the four corner squares need the rounded edge, so the rest of the content is drawn through
 * rectangle clips, and the corners through the rounded outline. The result matches the rounded clip
 * but for a few pixels where the corner squares meet the straight edges (off by at most 10%).
 */
fun Modifier.cornerOnlyClip(shape: Shape): Modifier {
    val rounded = shape as? RoundedCornerShape
    if (rounded == null || rounded.topStart != rounded.topEnd || rounded.topStart != rounded.bottomStart || rounded.topStart != rounded.bottomEnd) {
        return clip(shape)
    }
    return drawWithContent {
        val w = size.width
        val h = size.height
        val r =
            rounded.topStart
                .toPx(size, this)
                .roundToInt()
                .toFloat()
                .coerceIn(0f, min(w, h) / 2)
        if (r <= 0f) {
            clipRect { this@drawWithContent.drawContent() }
            return@drawWithContent
        }
        // The cross the four corner squares leave.
        clipRect(0f, r, w, h - r) { this@drawWithContent.drawContent() }
        clipRect(r, 0f, w - r, r) { this@drawWithContent.drawContent() }
        clipRect(r, h - r, w - r, h) { this@drawWithContent.drawContent() }
        // Each corner square, through the rounded outline.
        val outline = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r))) }
        drawCorner(outline, 0f, 0f, r)
        drawCorner(outline, w - r, 0f, r)
        drawCorner(outline, 0f, h - r, r)
        drawCorner(outline, w - r, h - r, r)
    }
}

private fun ContentDrawScope.drawCorner(
    outline: Path,
    left: Float,
    top: Float,
    r: Float,
) {
    clipRect(left, top, left + r, top + r) { clipPath(outline) { this@drawCorner.drawContent() } }
}
