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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.ui.BlockQuoteGutter.BarGutter
import com.halilibo.richtext.ui.HeadingStyle
import com.halilibo.richtext.ui.OrderedMarkers
import com.halilibo.richtext.ui.RichTextScope
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.resolveDefaults
import com.halilibo.richtext.ui.textOrderedMarkers

val DefaultHeadingStyle: HeadingStyle = { level, textStyle ->
    when (level) {
        0 -> {
            Typography.displayLarge.copy(
                fontSize = 32.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            )
        }

        1 -> {
            Typography.displayMedium.copy(
                fontSize = 26.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.25).sp,
            )
        }

        2 -> {
            Typography.displaySmall.copy(
                fontSize = 22.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        3 -> {
            Typography.displaySmall.copy(
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        4 -> {
            Typography.headlineLarge.copy(
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        5 -> {
            Typography.headlineMedium.copy(
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        6 -> {
            Typography.headlineSmall.copy(
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        else -> {
            textStyle
        }
    }
}

val RichTextDefaults = RichTextStyle().resolveDefaults()

/**
 * richtext-ui's own numbering (`1.`, `a.`, `1)`, `a)` by level), for any start: a list may start at
 * 0, which is index -1, and the default letters threw on a negative index.
 */
private val ListNumbers: RichTextScope.() -> OrderedMarkers = {
    textOrderedMarkers(
        { "${it + 1}." },
        { letter(it) + "." },
        { "${it + 1})" },
        { letter(it) + ")" },
    )
}

private fun letter(index: Int): Char = 'a' + index.mod(26)

val MarkDownStyleOnDark =
    RichTextDefaults.copy(
        paragraphSpacing = DefaultParagraphSpacing,
        headingStyle = DefaultHeadingStyle,
        listStyle =
            RichTextDefaults.listStyle?.copy(
                itemSpacing = 10.sp,
                orderedMarkers = ListNumbers,
            ),
        blockQuoteGutter =
            BarGutter(
                startMargin = 4.sp,
                barWidth = 3.sp,
                endMargin = 8.sp,
                color = { DarkColorPalette.primary.copy(alpha = 0.45f) },
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
                        .border(1.dp, DarkSubtleBorder, QuoteBorder)
                        .background(DarkColorPalette.onSurface.copy(alpha = 0.05f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ),
        tableStyle =
            RichTextDefaults.tableStyle?.copy(
                borderColor = DarkSubtleBorder,
                borderStrokeWidth = 1f,
                cellPadding = 10.sp,
            ),
        stringStyle =
            RichTextDefaults.stringStyle?.copy(
                linkStyle =
                    TextLinkStyles(
                        style =
                            SpanStyle(
                                color = DarkColorPalette.primary,
                            ),
                    ),
                codeStyle =
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = Font14SP,
                    ),
            ),
    )

val MarkDownStyleOnLight =
    RichTextDefaults.copy(
        paragraphSpacing = DefaultParagraphSpacing,
        headingStyle = DefaultHeadingStyle,
        listStyle =
            RichTextDefaults.listStyle?.copy(
                itemSpacing = 10.sp,
                orderedMarkers = ListNumbers,
            ),
        blockQuoteGutter =
            BarGutter(
                startMargin = 4.sp,
                barWidth = 3.sp,
                endMargin = 8.sp,
                color = { LightColorPalette.primary.copy(alpha = 0.45f) },
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
                        .border(1.dp, LightSubtleBorder, QuoteBorder)
                        .background(LightColorPalette.onSurface.copy(alpha = 0.05f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ),
        tableStyle =
            RichTextDefaults.tableStyle?.copy(
                borderColor = LightSubtleBorder,
                borderStrokeWidth = 1f,
                cellPadding = 10.sp,
            ),
        stringStyle =
            RichTextDefaults.stringStyle?.copy(
                linkStyle =
                    TextLinkStyles(
                        style =
                            SpanStyle(
                                color = LightColorPalette.primary,
                            ),
                    ),
                codeStyle =
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = Font14SP,
                    ),
            ),
    )

val ColorScheme.markdownStyle: RichTextStyle
    get() = if (isLight) MarkDownStyleOnLight else MarkDownStyleOnDark
