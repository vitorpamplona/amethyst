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
package com.vitorpamplona.amethyst.desktop.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.ui.markdown.RenderMarkdown
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.richtext.DefaultRichTextSegmentRenderer
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextPlatform
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextSegmentRenderer
import com.vitorpamplona.amethyst.commons.ui.uriToRoute
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * Desktop rich text: the shared segment renderer, and the shared markdown renderer for long-form
 * content.
 */
object DesktopRichTextPlatform : RichTextPlatform {
    override fun segmentRenderer(
        accountViewModel: AccountViewModel,
        nav: INav,
        backgroundColor: MutableState<Color>,
        callbackUri: String?,
        canPreview: Boolean,
    ): RichTextSegmentRenderer = DefaultRichTextSegmentRenderer(accountViewModel, nav, backgroundColor, callbackUri, canPreview)

    @Composable
    override fun Markdown(
        content: String,
        tags: ImmutableListOfLists<String>,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        callbackUri: String?,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {
        // As on Android: a link the app can show (nostr:, njump and the like) opens in the app, the
        // rest in the system's handler.
        RenderMarkdown(
            content = content,
            onLinkClick = { uri ->
                val route = uriToRoute(uri, accountViewModel.account)
                if (route != null) nav.nav(route) else DesktopBrowser.open(uri)
            },
        )
    }

    @Composable
    override fun SecretMessage(
        content: RichTextViewerState,
        callbackUri: String?,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = RichTextPlatform.Plain.SecretMessage(content, callbackUri, quotesLeft, backgroundColor, accountViewModel, nav)
}
