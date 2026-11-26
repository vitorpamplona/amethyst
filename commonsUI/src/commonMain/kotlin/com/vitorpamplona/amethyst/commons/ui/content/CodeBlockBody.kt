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
package com.vitorpamplona.amethyst.commons.ui.content

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.copied_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.copy
import com.vitorpamplona.amethyst.commons.richtext.MarkdownChunks
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.markdown.codeColors
import com.vitorpamplona.amethyst.commons.ui.markdown.highlightCode
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What a code block shows inside its box, wherever it is drawn (an article, a wiki page, the
 * coding-agent chat): a header with the language and a copy button, then the code, highlighted
 * when its language is one [MarkdownChunks.isHighlightable] knows.
 *
 * [wrap] folds long lines (the chat's narrow column); otherwise they scroll sideways, so a
 * command keeps its shape. [codeEndPadding] keeps wrapped code off the box's edge where the box
 * gives the header button its own, smaller end padding.
 */
@Composable
fun CodeBlockBody(
    code: String,
    language: String?,
    textStyle: TextStyle,
    wrap: Boolean,
    modifier: Modifier = Modifier,
    codeEndPadding: Dp = 0.dp,
) {
    val colors = MaterialTheme.colorScheme.codeColors
    val text =
        remember(code, language, colors) {
            if (MarkdownChunks.isHighlightable(language, code)) highlightCode(code, language, colors) else AnnotatedString(code)
        }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = language ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.grayText,
                modifier = Modifier.weight(1f),
            )
            CopyCodeButton(code)
        }
        if (wrap) {
            Text(text, style = textStyle, modifier = Modifier.padding(end = codeEndPadding))
        } else {
            Text(
                text = text,
                style = textStyle,
                softWrap = false,
                modifier = Modifier.padding(end = codeEndPadding).horizontalScroll(rememberScrollState()),
            )
        }
    }
}

/** Copies [code], then shows a check for a moment. */
@Composable
private fun CopyCodeButton(code: String) {
    var copied by remember(code) { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_SHOWN_MS)
            copied = false
        }
    }
    val label = stringRes(if (copied) Res.string.copied_to_clipboard else Res.string.copy)
    HoverTooltip(label) {
        IconButton(
            onClick = {
                scope.launch {
                    // Another app can hold the system clipboard open (Windows), or there is none
                    // (headless): a copy that can't happen must not take the app down.
                    try {
                        clipboard.setText(code)
                        copied = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("CodeBlockBody", "Could not copy the code block", e)
                    }
                }
            },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                if (copied) MaterialSymbols.Check else MaterialSymbols.ContentCopy,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.grayText,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private const val COPIED_SHOWN_MS = 1_500L
