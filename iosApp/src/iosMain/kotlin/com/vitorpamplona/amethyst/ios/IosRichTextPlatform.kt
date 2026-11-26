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
package com.vitorpamplona.amethyst.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.richtext.DefaultRichTextSegmentRenderer
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextPlatform
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextSegmentRenderer
import com.vitorpamplona.amethyst.commons.ui.richtext.SecretMessageContent
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * iOS rich text: the shared segment renderer (media, quoted notes, links, payment cards, secret
 * emoji) and the shared secret-message reveal, as on the desktop. Markdown, Djot and AsciiDoc go
 * through the shared document renderer, [RichTextPlatform]'s default `Markdown`, as on Android
 * and the desktop (commons/plans/2026-11-25-content-parsing.md).
 */
object IosRichTextPlatform : RichTextPlatform {
    override fun segmentRenderer(
        accountViewModel: AccountViewModel,
        nav: INav,
        backgroundColor: MutableState<Color>,
        callbackUri: String?,
        canPreview: Boolean,
    ): RichTextSegmentRenderer = DefaultRichTextSegmentRenderer(accountViewModel, nav, backgroundColor, callbackUri, canPreview)

    @Composable
    override fun SecretMessage(
        content: RichTextViewerState,
        callbackUri: String?,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = SecretMessageContent(content, callbackUri, quotesLeft, backgroundColor, accountViewModel, nav)
}
