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
package com.vitorpamplona.amethyst.commons.compose.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import com.halilibo.richtext.commonmark.CommonMarkdownParseOptions
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText

private val ALLOWED_SCHEMES = setOf("https", "http", "nostr", "lightning")

@Composable
fun RenderMarkdown(
    content: String,
    mediaRenderer: ArticleMediaRenderer,
    modifier: Modifier = Modifier,
) {
    val astNode =
        remember(content) {
            CommonmarkAstNodeParser(CommonMarkdownParseOptions.MarkdownWithLinks).parse(content)
        }

    val uriHandler =
        remember(mediaRenderer) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    val scheme = uri.substringBefore(":").lowercase()
                    if (scheme in ALLOWED_SCHEMES) {
                        mediaRenderer.onLinkClick(uri)
                    }
                }
            }
        }

    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        RichText(
            modifier = modifier,
            style = RichTextStyle(),
        ) {
            BasicMarkdown(astNode)
        }
    }
}
