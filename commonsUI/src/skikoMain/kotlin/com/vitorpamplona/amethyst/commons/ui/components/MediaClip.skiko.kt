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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import com.vitorpamplona.amethyst.commons.ui.render.SoftwareDrawing
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * In software the content is recorded once and played back through rectangle clips for the cross
 * the four corner squares leave, and through the rounded outline only inside each corner square:
 * the result matches the rounded clip but for a few pixels where the corner squares meet the
 * straight edges (off by at most 10%). On a GPU ([SoftwareDrawing] off) it is one rounded clip.
 */
actual fun Modifier.mediaClip(shape: Shape): Modifier {
    val rounded = shape as? RoundedCornerShape
    if (rounded == null || rounded.topStart != rounded.topEnd || rounded.topStart != rounded.bottomStart || rounded.topStart != rounded.bottomEnd) {
        return clip(shape)
    }
    return drawWithCache {
        val w = size.width
        val h = size.height
        val r =
            rounded.topStart
                .toPx(size, this)
                .roundToInt()
                .toFloat()
                .coerceIn(0f, min(w, h) / 2)
        val outline = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r))) }
        val layer = obtainGraphicsLayer()
        onDrawWithContent {
            when {
                r <= 0f -> clipRect { this@onDrawWithContent.drawContent() }
                !SoftwareDrawing.active -> clipPath(outline) { this@onDrawWithContent.drawContent() }
                else -> {
                    layer.record { this@onDrawWithContent.drawContent() }
                    clipRect(0f, r, w, h - r) { drawLayer(layer) }
                    clipRect(r, 0f, w - r, r) { drawLayer(layer) }
                    clipRect(r, h - r, w - r, h) { drawLayer(layer) }
                    drawCorner(layer, outline, 0f, 0f, r)
                    drawCorner(layer, outline, w - r, 0f, r)
                    drawCorner(layer, outline, 0f, h - r, r)
                    drawCorner(layer, outline, w - r, h - r, r)
                }
            }
        }
    }
}

private fun DrawScope.drawCorner(
    layer: GraphicsLayer,
    outline: Path,
    left: Float,
    top: Float,
    r: Float,
) {
    clipRect(left, top, left + r, top + r) { clipPath(outline) { drawLayer(layer) } }
}
