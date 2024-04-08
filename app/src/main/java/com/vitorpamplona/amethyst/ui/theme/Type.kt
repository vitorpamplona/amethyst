/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.ui.HeadingStyle

// Set of Material typography styles to start with
val Typography =
    Typography(
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
            ),
    /* Other default text styles to override
    button = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W500,
        fontSize = Font14SP
    ),
    caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    )
     */
    )

val Font12SP = 12.sp
val Font14SP = 14.sp
val Font17SP = 17.sp
val Font18SP = 18.sp

val MarkdownTextStyle = TextStyle(lineHeight = 1.30.em)

val DefaultParagraphSpacing: TextUnit = 18.sp

internal val DefaultHeadingStyle: HeadingStyle = { level, textStyle ->
    when (level) {
        0 ->
            Typography.displayLarge.copy(
                fontSize = 32.sp,
                lineHeight = 40.sp,
            )
        1 ->
            Typography.displayMedium.copy(
                fontSize = 28.sp,
                lineHeight = 36.sp,
            )
        2 ->
            Typography.displaySmall.copy(
                fontSize = 24.sp,
                lineHeight = 32.sp,
            )
        3 ->
            Typography.headlineLarge.copy(
                fontSize = 22.sp,
                lineHeight = 26.sp,
            )
        4 ->
            Typography.headlineMedium.copy(
                fontSize = 20.sp,
                lineHeight = 24.sp,
            )
        5 -> Typography.headlineSmall
        6 -> Typography.titleLarge
        else -> textStyle
    }
}
