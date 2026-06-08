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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * Renders a LaTeX formula (the inner text of a `$...$` / `$$...$$` span) as an
 * image, tinted to follow the current text color and sized to the current font.
 *
 * JLaTeXMath throws on malformed input, so anything it can't parse falls back to
 * the raw delimited text rather than crashing the feed.
 */
@Composable
fun LatexEquation(
    latex: String,
    displayMode: Boolean,
) {
    val color = LocalContentColor.current.toArgb()
    val density = LocalDensity.current
    val fontSize = LocalTextStyle.current.fontSize

    // Display math renders a touch larger than the surrounding prose, matching
    // the visual weight KaTeX gives block equations.
    val textSizePx =
        with(density) {
            val base = if (fontSize.isUsable()) fontSize.toPx() else 16.sp.toPx()
            if (displayMode) base * 1.2f else base
        }

    val drawable =
        remember(latex, color, textSizePx) {
            runCatching {
                JLatexMathDrawable
                    .builder(latex)
                    .textSize(textSizePx)
                    .color(color)
                    .align(JLatexMathDrawable.ALIGN_LEFT)
                    .build()
            }.getOrNull()
        }

    if (drawable == null) {
        Text(if (displayMode) "$$$latex$$" else "$$latex$")
        return
    }

    val widthDp = with(density) { drawable.intrinsicWidth.toDp() }
    val heightDp = with(density) { drawable.intrinsicHeight.toDp() }

    // Wide display equations can overflow the column; allow them to scroll
    // horizontally instead of being clipped.
    val sizeModifier = Modifier.size(widthDp, heightDp)
    val modifier = if (displayMode) Modifier.horizontalScroll(rememberScrollState()).then(sizeModifier) else sizeModifier

    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
            drawable.draw(canvas.nativeCanvas)
        }
    }
}

private fun TextUnit.isUsable(): Boolean = this != TextUnit.Unspecified && this.type == TextUnitType.Sp
