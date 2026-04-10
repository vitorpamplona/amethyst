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
package com.vitorpamplona.amethyst.ios.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lightweight CommonMark-style markdown renderer for note content.
 *
 * Supports:
 * - **bold**, *italic*, ***bold+italic***
 * - `inline code`
 * - ~~strikethrough~~
 * - # Headers (H1–H6)
 * - > Blockquotes
 * - ``` Code blocks ```
 * - - Bullet lists
 * - 1. Numbered lists
 * - --- Horizontal rules
 * - [links](url)
 * - nostr: references (npub, note, nevent)
 *
 * Does NOT try to be a full CommonMark parser — it's a pragmatic renderer
 * for Nostr note content that usually uses simple formatting.
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val blocks = remember(content) { parseMarkdownBlocks(content) }

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(4.dp))
            when (block) {
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = renderInlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                is MarkdownBlock.Header -> {
                    val style =
                        when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            3 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }
                    Text(
                        text = renderInlineMarkdown(block.text),
                        style = style,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                is MarkdownBlock.CodeBlock -> {
                    Text(
                        text = block.code,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(8.dp)
                                .horizontalScroll(rememberScrollState()),
                    )
                }

                is MarkdownBlock.Blockquote -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(
                            modifier =
                                Modifier
                                    .width(3.dp)
                                    .height(20.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = renderInlineMarkdown(block.text),
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = FontStyle.Italic,
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is MarkdownBlock.ListItem -> {
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = block.bullet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = renderInlineMarkdown(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                is MarkdownBlock.HorizontalRule -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

// ── Block-level parsing ──

sealed class MarkdownBlock {
    data class Paragraph(
        val text: String,
    ) : MarkdownBlock()

    data class Header(
        val level: Int,
        val text: String,
    ) : MarkdownBlock()

    data class CodeBlock(
        val code: String,
        val language: String?,
    ) : MarkdownBlock()

    data class Blockquote(
        val text: String,
    ) : MarkdownBlock()

    data class ListItem(
        val bullet: String,
        val text: String,
    ) : MarkdownBlock()

    data object HorizontalRule : MarkdownBlock()
}

private fun parseMarkdownBlocks(content: String): List<MarkdownBlock> {
    val lines = content.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        when {
            // Fenced code block
            trimmed.startsWith("```") -> {
                val lang = trimmed.removePrefix("```").trim().ifEmpty { null }
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), lang))
                i++ // skip closing ```
            }

            // Horizontal rule
            trimmed.matches(Regex("^[-*_]{3,}$")) -> {
                blocks.add(MarkdownBlock.HorizontalRule)
                i++
            }

            // Header
            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length.coerceAtMost(6)
                val text = trimmed.drop(level).trim()
                blocks.add(MarkdownBlock.Header(level, text))
                i++
            }

            // Blockquote
            trimmed.startsWith(">") -> {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().removePrefix(">").trim())
                    i++
                }
                blocks.add(MarkdownBlock.Blockquote(quoteLines.joinToString(" ")))
            }

            // Unordered list
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                blocks.add(MarkdownBlock.ListItem("•", trimmed.drop(2).trim()))
                i++
            }

            // Ordered list
            trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                val match = Regex("^(\\d+)\\.\\s+(.*)").find(trimmed)
                if (match != null) {
                    blocks.add(MarkdownBlock.ListItem("${match.groupValues[1]}.", match.groupValues[2]))
                }
                i++
            }

            // Empty line — skip
            trimmed.isEmpty() -> {
                i++
            }

            // Paragraph
            else -> {
                val paragraphLines = mutableListOf<String>()
                while (i < lines.size) {
                    val pLine = lines[i].trim()
                    if (pLine.isEmpty() || pLine.startsWith("#") || pLine.startsWith("```") ||
                        pLine.startsWith(">") || pLine.matches(Regex("^[-*_]{3,}$"))
                    ) {
                        break
                    }
                    paragraphLines.add(pLine)
                    i++
                }
                blocks.add(MarkdownBlock.Paragraph(paragraphLines.joinToString(" ")))
            }
        }
    }
    return blocks
}

// ── Inline-level rendering ──

/**
 * Renders inline markdown to AnnotatedString:
 * **bold**, *italic*, `code`, ~~strikethrough~~, [link](url)
 */
private fun renderInlineMarkdown(text: String): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        val len = text.length

        while (i < len) {
            when {
                // Bold+italic ***text***
                i + 2 < len && text[i] == '*' && text[i + 1] == '*' && text[i + 2] == '*' -> {
                    val end = text.indexOf("***", i + 3)
                    if (end > 0) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 3, end))
                        }
                        i = end + 3
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // Bold **text**
                i + 1 < len && text[i] == '*' && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > 0) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // Italic *text*
                text[i] == '*' && (i == 0 || text[i - 1] != '*') -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > 0 && (end + 1 >= len || text[end + 1] != '*')) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // Inline code `text`
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > 0) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                            ),
                        ) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // Strikethrough ~~text~~
                i + 1 < len && text[i] == '~' && text[i + 1] == '~' -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > 0) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // Link [text](url)
                text[i] == '[' -> {
                    val closeBracket = text.indexOf(']', i + 1)
                    if (closeBracket > 0 && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                        val closeParen = text.indexOf(')', closeBracket + 2)
                        if (closeParen > 0) {
                            val linkText = text.substring(i + 1, closeBracket)
                            val linkUrl = text.substring(closeBracket + 2, closeParen)
                            withStyle(
                                SpanStyle(
                                    color =
                                        androidx.compose.ui.graphics
                                            .Color(0xFF2196F3),
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ) {
                                append(linkText)
                            }
                            i = closeParen + 1
                        } else {
                            append(text[i])
                            i++
                        }
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // Regular character
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
