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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val IconDefaultSize = 24.dp

@Composable
fun Icon(
    symbol: MaterialSymbol,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    weight: Int = MaterialSymbolsDefaults.WEIGHT,
) {
    val fontFamily = rememberMaterialSymbolsFontFamily(weight)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val mirror = symbol.autoMirror && LocalLayoutDirection.current == LayoutDirection.Rtl

    val semanticsModifier =
        if (contentDescription != null) {
            Modifier.semantics {
                this.contentDescription = contentDescription
                this.role = Role.Image
            }
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .defaultMinSize(IconDefaultSize, IconDefaultSize)
                .drawBehind {
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
                    if (mirror) {
                        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(size.width / 2f, size.height / 2f)) {
                            translate(tx, ty) { drawText(layout) }
                        }
                    } else {
                        translate(tx, ty) { drawText(layout) }
                    }
                }.then(semanticsModifier),
    )
}
