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
package com.vitorpamplona.amethyst.ui.components.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.commonmark.MarkdownParseOptions
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.material3.RichText
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.MarkdownTextStyle
import com.vitorpamplona.amethyst.ui.theme.markdownStyle
import com.vitorpamplona.amethyst.ui.uriToRoute
import com.vitorpamplona.quartz.events.ImmutableListOfLists

@Composable
fun RenderContentAsMarkdown(
    content: String,
    tags: ImmutableListOfLists<String>?,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val uri = LocalUriHandler.current
    val onClick =
        remember {
            { link: String ->
                val route = uriToRoute(link)
                if (route != null) {
                    nav(route)
                } else {
                    runCatching { uri.openUri(link) }
                }
                Unit
            }
        }

    ProvideTextStyle(MarkdownTextStyle) {
        val astNode =
            remember(content) {
                CommonmarkAstNodeParser(MarkdownParseOptions.MarkdownWithLinks).parse(content)
            }

        val renderer =
            remember(content) {
                MarkdownMediaRenderer(
                    content.take(100),
                    tags,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    accountViewModel,
                    nav,
                )
            }

        RichText(
            style = MaterialTheme.colorScheme.markdownStyle,
            linkClickHandler = onClick,
            renderer = renderer,
        ) {
            BasicMarkdown(astNode)
        }
    }
}
