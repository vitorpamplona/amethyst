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
package com.vitorpamplona.amethyst.commons.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.ui.BlockQuoteGutter.BarGutter
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.resolveDefaults
import com.patrykandpatrick.vico.compose.common.VicoTheme
import com.patrykandpatrick.vico.compose.common.VicoTheme.CandlestickCartesianLayerColors

/**
 * Determines if the color scheme is light mode.
 * Based on primary color luminance.
 */
val ColorScheme.isLight: Boolean
    get() = primary.luminance() < 0.5f

/**
 * Color filter for onBackground color (for tinting icons/images).
 */
val ColorScheme.onBackgroundColorFilter: ColorFilter
    get() = ColorFilter.tint(onBackground)

// --- Markdown style ---

private val RichTextDefaults = RichTextStyle().resolveDefaults()

/**
 * Creates a [RichTextStyle] themed to this [ColorScheme].
 * Dynamically derives border/background/link colors from the scheme so it
 * works for any light or dark palette without hard-coding color values.
 */
val ColorScheme.markdownStyle: RichTextStyle
    get() {
        val subtleBorderAlpha = if (isLight) 0.05f else 0.12f
        val codeBackgroundAlpha = if (isLight) 0.12f else 0.22f
        val subtleBorder = onSurface.copy(alpha = subtleBorderAlpha)

        return RichTextDefaults.copy(
            paragraphSpacing = DefaultParagraphSpacing,
            headingStyle = DefaultHeadingStyle,
            listStyle =
                RichTextDefaults.listStyle?.copy(
                    itemSpacing = 10.sp,
                ),
            blockQuoteGutter =
                BarGutter(
                    startMargin = 4.sp,
                    barWidth = 3.sp,
                    endMargin = 8.sp,
                    color = { primary.copy(alpha = 0.45f) },
                ),
            codeBlockStyle =
                RichTextDefaults.codeBlockStyle?.copy(
                    textStyle =
                        TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = Font14SP,
                            lineHeight = 1.45.em,
                        ),
                    modifier =
                        Modifier
                            .padding(vertical = 4.dp)
                            .fillMaxWidth()
                            .clip(shape = QuoteBorder)
                            .border(1.dp, subtleBorder, QuoteBorder)
                            .background(onSurface.copy(alpha = 0.05f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                ),
            tableStyle =
                RichTextDefaults.tableStyle?.copy(
                    borderColor = subtleBorder,
                    borderStrokeWidth = 1f,
                    cellPadding = 10.sp,
                ),
            stringStyle =
                RichTextDefaults.stringStyle?.copy(
                    linkStyle =
                        TextLinkStyles(
                            style =
                                SpanStyle(
                                    color = primary,
                                ),
                        ),
                    codeStyle =
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = Font14SP,
                            background = onSurface.copy(alpha = codeBackgroundAlpha),
                            letterSpacing = 0.3.sp,
                        ),
                ),
        )
    }

// --- Chart style ---

private val chartLightColors =
    VicoTheme(
        candlestickCartesianLayerColors =
            CandlestickCartesianLayerColors(
                Color(0xff0ac285),
                Color(0xff000000),
                Color(0xffe8304f),
            ),
        columnCartesianLayerColors = listOf(Color(0xff3287ff), Color(0xff0ac285), Color(0xffffab02)),
        lineColor = Color(0xffbcbfc2),
        textColor = Color(0xff000000),
    )

private val chartDarkColors =
    VicoTheme(
        candlestickCartesianLayerColors =
            CandlestickCartesianLayerColors(
                Color(0xff0ac285),
                Color(0xffffffff),
                Color(0xffe8304f),
            ),
        columnCartesianLayerColors = listOf(Color(0xff3287ff), Color(0xff0ac285), Color(0xffffab02)),
        lineColor = Color(0xff494c50),
        textColor = Color(0xffffffff),
    )

val ColorScheme.chartStyle: VicoTheme
    get() = if (isLight) chartLightColors else chartDarkColors
