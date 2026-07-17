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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.BlossomUriSegment
import com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser
import com.vitorpamplona.amethyst.commons.richtext.CashuSegment
import com.vitorpamplona.amethyst.commons.richtext.ClinkOfferSegment
import com.vitorpamplona.amethyst.commons.richtext.ConcordInviteLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexEventSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexUserSegment
import com.vitorpamplona.amethyst.commons.richtext.ImageGalleryParagraph
import com.vitorpamplona.amethyst.commons.richtext.InvoiceSegment
import com.vitorpamplona.amethyst.commons.richtext.MathSegment
import com.vitorpamplona.amethyst.commons.richtext.NowhereLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.RelayGroupLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.RelayUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.SecretEmoji
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.commons.richtext.WithdrawSegment
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextInteractions
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextSegmentRenderer
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextInteractions
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextSegmentRenderer
import com.vitorpamplona.amethyst.ui.components.markdown.RenderContentAsMarkdown
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.creators.invoice.ClinkOfferPreview
import com.vitorpamplona.amethyst.ui.note.creators.invoice.MayBeInvoicePreview
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.HalfVertPadding
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextViewer as CommonsRichTextViewer

/**
 * The Android implementation of the shared [RichTextSegmentRenderer] contract: it
 * wires each platform-divergent segment to Amethyst's existing touch-first leaf
 * composables, closing over the [AccountViewModel], [INav], the parent
 * [backgroundColor], the [callbackUri], and the [canPreview] mode that those
 * leaves need but that the cross-platform contract deliberately keeps out of its
 * method signatures.
 *
 * Recreate one per [CommonsBackedRichTextViewer] call (it captures per-call state);
 * it is cheap and [remember]ed against its inputs so the CompositionLocal doesn't
 * churn its readers.
 */
class AmethystRichTextSegmentRenderer(
    private val accountViewModel: AccountViewModel,
    private val nav: INav,
    private val backgroundColor: MutableState<Color>,
    private val callbackUri: String?,
    private val canPreview: Boolean,
) : RichTextSegmentRenderer {
    @Composable
    override fun Media(
        segment: Segment,
        state: RichTextViewerState,
        modifier: Modifier,
    ) {
        if (segment is BlossomUriSegment) {
            if (canPreview) {
                BlossomUriRenderer(segment.segmentText, state, callbackUri, accountViewModel)
            } else {
                BlossomUriRendererNoPreview(segment.segmentText, accountViewModel)
            }
            return
        }

        if (canPreview) {
            state.mediaForPager[segment.segmentText]?.let {
                Box(HalfVertPadding) {
                    ZoomableContentView(
                        content = it,
                        images = state.mediaList,
                        roundedCorner = true,
                        contentScale = ContentScale.FillWidth,
                        accountViewModel = accountViewModel,
                    )
                }
            }
        } else {
            ClickableUrl(segment.segmentText, segment.segmentText)
        }
    }

    @Composable
    override fun Gallery(
        paragraph: ImageGalleryParagraph,
        state: RichTextViewerState,
        modifier: Modifier,
    ) = ImageGallery(paragraph, state, accountViewModel, modifier, roundedCorner = true)

    @Composable
    override fun Equation(
        segment: MathSegment,
        modifier: Modifier,
    ) = LatexEquation(segment.latex, segment.displayMode, segment.leading, segment.trailing)

    @Composable
    override fun NostrEntity(
        bech: String,
        canPreview: Boolean,
        quotesLeft: Int,
        modifier: Modifier,
    ) = BechLink(bech, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)

    @Composable
    override fun QuotedEvent(
        eventHex: String,
        addedChars: String?,
        canPreview: Boolean,
        quotesLeft: Int,
        modifier: Modifier,
    ) {
        val segment = remember(eventHex, addedChars) { HashIndexEventSegment(eventHex, eventHex, addedChars) }
        TagLink(segment, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
    }

    @Composable
    override fun UserMention(
        userHex: String,
        addedChars: String?,
        modifier: Modifier,
    ) {
        val segment = remember(userHex, addedChars) { HashIndexUserSegment(userHex, userHex, addedChars) }
        TagLink(segment, accountViewModel, nav)
    }

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
    override fun LinkPreview(
        url: String,
        modifier: Modifier,
    ) = LoadUrlPreview(url, url, callbackUri, accountViewModel, nav)

    @Composable
    override fun Url(
        url: String,
        displayText: String,
        modifier: Modifier,
    ) = ClickableUrl(displayText, url)

    @Composable
    override fun Email(
        address: String,
        modifier: Modifier,
    ) = ClickableEmail(address)

    @Composable
    override fun Phone(
        number: String,
        modifier: Modifier,
    ) = ClickablePhone(number)

    @Composable
    override fun RelayLink(
        segment: Segment,
        modifier: Modifier,
    ) {
        when (segment) {
            is RelayUrlSegment -> ClickableRelayUrl(segment.segmentText, nav)
            is RelayGroupLinkSegment ->
                if (canPreview) {
                    RelayGroupCard(segment.segmentText, accountViewModel, nav)
                } else {
                    ClickableRelayGroupLink(segment.segmentText, nav)
                }
            is ConcordInviteLinkSegment ->
                if (canPreview) {
                    ConcordInviteCard(segment.segmentText, accountViewModel, nav)
                } else {
                    ClickableConcordInviteLink(segment.segmentText, nav)
                }
            else -> Text(segment.segmentText)
        }
    }

    @Composable
    override fun NowhereLink(
        segment: NowhereLinkSegment,
        canPreview: Boolean,
        modifier: Modifier,
    ) {
        if (canPreview) {
            NowhereLinkCard(segment)
        } else {
            ClickableUrl(segment.segmentText, segment.segmentText)
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
 * Renders rich text through the shared cross-platform [CommonsRichTextViewer] core.
 * This is the production plain-text path that [RichTextViewer] delegates to: it
 * parses with the existing [CachedRichTextParser], keeps the markdown path native,
 * and for plain rich text provides the Android segment renderer
 * ([AmethystRichTextSegmentRenderer]) plus the universal interactions into the two
 * CompositionLocals the core reads.
 *
 * The core renders text, custom emoji, and hashtags (with their shared inline
 * icons) itself; every divergent segment routes back to Amethyst's existing
 * leaves through the renderer. Note: url/email/phone open via the platform URI
 * handler rather than Amethyst's per-type Clickable* composables, so their tap
 * behavior is the generic open action (rendering is unchanged clickable text).
 */
@Composable
fun CommonsBackedRichTextViewer(
    content: String,
    canPreview: Boolean,
    quotesLeft: Int,
    modifier: Modifier,
    tags: ImmutableListOfLists<String>,
    backgroundColor: MutableState<Color>,
    callbackUri: String? = null,
    authorPubKey: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(modifier = modifier) {
        if (remember(content) { CachedRichTextParser.isMarkdown(content) }) {
            RenderContentAsMarkdown(content, tags, canPreview, quotesLeft, backgroundColor, callbackUri, accountViewModel, nav)
            return@Column
        }

        val state by remember(content, tags) {
            mutableStateOf(CachedRichTextParser.parseText(content, tags, callbackUri, authorPubKey))
        }

        val renderer =
            remember(accountViewModel, nav, backgroundColor, callbackUri, canPreview) {
                AmethystRichTextSegmentRenderer(accountViewModel, nav, backgroundColor, callbackUri, canPreview)
            }

        val interactions =
            remember(nav) {
                RichTextInteractions(
                    onClickHashtag = { nav.nav(Route.Hashtag(it.lowercase())) },
                )
            }

        CompositionLocalProvider(
            LocalRichTextSegmentRenderer provides renderer,
            LocalRichTextInteractions provides interactions,
        ) {
            CommonsRichTextViewer(
                state = state,
                canPreview = canPreview,
                quotesLeft = quotesLeft,
            )
        }
    }
}
