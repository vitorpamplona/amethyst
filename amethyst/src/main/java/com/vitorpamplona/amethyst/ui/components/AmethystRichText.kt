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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.CashuSegment
import com.vitorpamplona.amethyst.commons.richtext.ClinkOfferSegment
import com.vitorpamplona.amethyst.commons.richtext.InvoiceSegment
import com.vitorpamplona.amethyst.commons.richtext.MathSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.SecretEmoji
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.commons.richtext.WithdrawSegment
import com.vitorpamplona.amethyst.commons.ui.components.CashuPreview
import com.vitorpamplona.amethyst.commons.ui.components.MayBeWithdrawal
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.creators.invoice.ClinkOfferPreview
import com.vitorpamplona.amethyst.commons.ui.note.creators.invoice.MayBeInvoicePreview
import com.vitorpamplona.amethyst.commons.ui.richtext.DefaultRichTextSegmentRenderer
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextPlatform
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextSegmentRenderer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.ui.components.markdown.RenderContentAsMarkdown

/**
 * Android's leaves on the shared [DefaultRichTextSegmentRenderer]: LaTeX, the payment cards
 * (invoices, withdrawals, Cashu, Clink offers), Blossom URIs resolved and previewed, and secret
 * emoji that can be revealed.
 */
class AmethystRichTextSegmentRenderer(
    accountViewModel: AccountViewModel,
    nav: INav,
    backgroundColor: MutableState<Color>,
    callbackUri: String?,
    canPreview: Boolean,
) : DefaultRichTextSegmentRenderer(accountViewModel, nav, backgroundColor, callbackUri, canPreview) {
    @Composable
    override fun BlossomUri(
        uri: String,
        state: RichTextViewerState,
    ) {
        if (canPreview) {
            BlossomUriRenderer(uri, state, callbackUri, accountViewModel)
        } else {
            BlossomUriRendererNoPreview(uri, accountViewModel)
        }
    }

    @Composable
    override fun Equation(
        segment: MathSegment,
        modifier: Modifier,
    ) = LatexEquation(segment.latex, segment.displayMode, segment.leading, segment.trailing)

    @Composable
    override fun Payment(
        segment: Segment,
        modifier: Modifier,
    ) {
        when (segment) {
            // Matches the original no-preview switchboard: don't surface a pay/withdraw
            // affordance when previews are suppressed — show the raw text instead.
            is InvoiceSegment -> if (canPreview) MayBeInvoicePreview(segment.segmentText, accountViewModel) else Text(segment.segmentText)
            is WithdrawSegment -> if (canPreview) MayBeWithdrawal(segment.segmentText, accountViewModel) else Text(segment.segmentText)
            // Cashu + Clink decode locally (network only on tap), so they render in both modes.
            is CashuSegment -> CashuPreview(segment.segmentText, accountViewModel)
            is ClinkOfferSegment -> ClinkOfferPreview(segment.offer, accountViewModel, nav)
            else -> Text(segment.segmentText)
        }
    }

    @Composable
    override fun SecretMessage(
        segment: SecretEmoji,
        state: RichTextViewerState,
        canPreview: Boolean,
        quotesLeft: Int,
        modifier: Modifier,
    ) = DisplaySecretEmoji(segment, state, callbackUri, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
}

/**
 * Android's [RichTextPlatform]: its segment renderer ([AmethystRichTextSegmentRenderer]) and its
 * markdown renderer. Installed in the app theme, so every screen's rich text gets them.
 */
object AndroidRichTextPlatform : RichTextPlatform {
    override fun segmentRenderer(
        accountViewModel: AccountViewModel,
        nav: INav,
        backgroundColor: MutableState<Color>,
        callbackUri: String?,
        canPreview: Boolean,
    ): RichTextSegmentRenderer = AmethystRichTextSegmentRenderer(accountViewModel, nav, backgroundColor, callbackUri, canPreview)

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
    ) = RenderContentAsMarkdown(content, tags, canPreview, quotesLeft, backgroundColor, callbackUri, accountViewModel, nav)

    @Composable
    override fun SecretMessage(
        content: RichTextViewerState,
        callbackUri: String?,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = CoreSecretMessage(content, callbackUri, quotesLeft, backgroundColor, accountViewModel, nav)
}
