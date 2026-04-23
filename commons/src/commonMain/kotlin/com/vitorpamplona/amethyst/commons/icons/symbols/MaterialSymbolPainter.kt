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
package com.vitorpamplona.amethyst.commons.icons.symbols

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density

@Composable
fun rememberMaterialSymbolPainter(
    symbol: MaterialSymbol,
    tint: Color = LocalContentColor.current,
    weight: Int = MaterialSymbolsDefaults.WEIGHT,
): Painter {
    val fontFamily = rememberMaterialSymbolsFontFamily(weight)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(symbol, fontFamily, tint, density) {
        MaterialSymbolPainter(symbol, fontFamily, tint, textMeasurer, density)
    }
}

private class MaterialSymbolPainter(
    private val symbol: MaterialSymbol,
    private val fontFamily: FontFamily,
    private val tint: Color,
    private val textMeasurer: TextMeasurer,
    private val density: Density,
) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        val sizePx = size.minDimension
        val fontSize = with(density) { sizePx.toSp() }
        val layout =
            textMeasurer.measure(
                text = symbol.glyph,
                style =
                    TextStyle(
                        fontFamily = fontFamily,
                        fontSize = fontSize,
                        color = tint,
                    ),
            )
        val tx = (size.width - layout.size.width) / 2f
        val ty = (size.height - layout.size.height) / 2f
        translate(tx, ty) { drawText(layout) }
    }
}
