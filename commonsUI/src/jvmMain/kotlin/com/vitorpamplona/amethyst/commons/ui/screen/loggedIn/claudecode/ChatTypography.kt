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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.claudecode

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.ui.CodeBlockStyle
import com.halilibo.richtext.ui.ListStyle
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.TableStyle
import com.halilibo.richtext.ui.string.RichTextStringStyle

/**
 * The chat's reading text: a size up from the feed's body, with room between lines, since a
 * Claude reply is read like a document, not skimmed like a note.
 */
internal val ChatTextStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 23.sp, letterSpacing = 0.1.sp)

/**
 * Markdown for replies: paragraphs spaced like prose, modest headings (a reply's `##` is a
 * section, not a page title), inline code that sits on the line instead of a gray slab, and code
 * blocks on a quiet rounded surface.
 */
@Composable
internal fun rememberChatMarkdownStyle(): RichTextStyle {
    val colors = MaterialTheme.colorScheme
    return remember(colors) {
        RichTextStyle(
            paragraphSpacing = 10.sp,
            headingStyle = { level, base ->
                when (level) {
                    0, 1 -> base.copy(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
                    2 -> base.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                    else -> base.copy(fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            listStyle = ListStyle(itemSpacing = 4.sp),
            tableStyle =
                TableStyle(
                    headerTextStyle = TextStyle(fontWeight = FontWeight.SemiBold),
                    cellPadding = 8.sp,
                    borderColor = colors.outlineVariant,
                    borderStrokeWidth = 1f,
                ),
            codeBlockStyle =
                CodeBlockStyle(
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainerHigh),
                    padding = 12.sp,
                    wordWrap = true,
                ),
            stringStyle =
                RichTextStringStyle(
                    codeStyle =
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.5.sp,
                            background = colors.onSurface.copy(alpha = 0.07f),
                            color = colors.onSurface,
                        ),
                ),
        )
    }
}
