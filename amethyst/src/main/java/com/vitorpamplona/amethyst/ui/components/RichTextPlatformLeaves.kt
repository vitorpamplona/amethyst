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

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.richtext.Base64Segment
import com.vitorpamplona.amethyst.commons.richtext.BechSegment
import com.vitorpamplona.amethyst.commons.richtext.BlossomUriSegment
import com.vitorpamplona.amethyst.commons.richtext.BuzzInviteLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser
import com.vitorpamplona.amethyst.commons.richtext.CashuSegment
import com.vitorpamplona.amethyst.commons.richtext.ClinkOfferSegment
import com.vitorpamplona.amethyst.commons.richtext.ConcordInviteLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.EmailSegment
import com.vitorpamplona.amethyst.commons.richtext.EmojiSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexEventSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexUserSegment
import com.vitorpamplona.amethyst.commons.richtext.HashTagSegment
import com.vitorpamplona.amethyst.commons.richtext.ImageSegment
import com.vitorpamplona.amethyst.commons.richtext.InvoiceSegment
import com.vitorpamplona.amethyst.commons.richtext.LinkSegment
import com.vitorpamplona.amethyst.commons.richtext.MathSegment
import com.vitorpamplona.amethyst.commons.richtext.NowhereLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.PdfSegment
import com.vitorpamplona.amethyst.commons.richtext.PhoneSegment
import com.vitorpamplona.amethyst.commons.richtext.RegularTextSegment
import com.vitorpamplona.amethyst.commons.richtext.RelayGroupLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.RelayUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.SchemelessUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.SecretEmoji
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.commons.richtext.VideoSegment
import com.vitorpamplona.amethyst.commons.richtext.WithdrawSegment
import com.vitorpamplona.amethyst.commons.ui.components.AnimatedBorderTextCornerRadius
import com.vitorpamplona.amethyst.commons.ui.components.BechLink
import com.vitorpamplona.amethyst.commons.ui.components.ClickableBuzzInviteLink
import com.vitorpamplona.amethyst.commons.ui.components.ClickableConcordInviteLink
import com.vitorpamplona.amethyst.commons.ui.components.ClickableEmail
import com.vitorpamplona.amethyst.commons.ui.components.ClickablePhone
import com.vitorpamplona.amethyst.commons.ui.components.ClickableRelayGroupLink
import com.vitorpamplona.amethyst.commons.ui.components.ClickableRelayUrl
import com.vitorpamplona.amethyst.commons.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrlOrBlossom
import com.vitorpamplona.amethyst.commons.ui.components.CreateClickableText
import com.vitorpamplona.amethyst.commons.ui.components.HashTag
import com.vitorpamplona.amethyst.commons.ui.components.NowhereLinkCard
import com.vitorpamplona.amethyst.commons.ui.components.RenderCustomEmoji
import com.vitorpamplona.amethyst.commons.ui.components.RenderRegular
import com.vitorpamplona.amethyst.commons.ui.components.RenderTextParagraph
import com.vitorpamplona.amethyst.commons.ui.components.TagLink
import com.vitorpamplona.amethyst.commons.ui.components.measureSpaceWidth
import com.vitorpamplona.amethyst.commons.ui.components.rememberBlossomUriOpener
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.theme.CashuCardBorders
import com.vitorpamplona.amethyst.commons.ui.theme.HalfVertPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.note.creators.invoice.ClinkOfferPreview
import com.vitorpamplona.amethyst.ui.note.creators.invoice.MayBeInvoicePreview
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Preview
@Composable
fun RenderStrangeNamePreview() {
    Column(modifier = Modifier.padding(10.dp)) {
        RenderRegular(
            "If you want to FreeFrom Official \uD80C\uDD66 stream or download the music from  nostr:npub1sctag667a7np6p6ety2up94pnwwxhd2ep8n8afr2gtr47cwd4ewsvdmmjm at wss://relay.damus.io can you here",
            EmptyTagList,
        ) { paragraph, _, spaceWidth, modifier ->
            RenderTextParagraph(paragraph, spaceWidth, modifier) { word ->
                when (word) {
                    is BechSegment -> {
                        Text(
                            "FreeFrom Official \uD80C\uDD66",
                            modifier = Modifier.border(1.dp, Color.Red),
                        )
                    }

                    is RegularTextSegment -> {
                        Text(word.segmentText)
                    }

                    is RelayUrlSegment -> {
                        ClickableRelayUrl(word.segmentText, EmptyNav())
                    }

                    is BlossomUriSegment -> {
                        ClickableRelayUrl(word.segmentText, EmptyNav())
                    }

                    is SchemelessUrlSegment -> {
                        NoProtocolUrlRenderer(word.segmentText)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun RenderRegularPreview() {
    Column(modifier = Modifier.padding(10.dp)) {
        RenderRegular(
            "nostr:npub1e0z776cpe0gllgktjk54fuzv8pdfxmq6smsmh8xd7t8s7n474n9smk0txy but i'm Monthly funding" +
                " 7 other humans vitor@vitorpamplona.com at the moment so spread #test a bit thin, but won't always be the case.",
            EmptyTagList,
        ) { paragraph, state, spaceWidth, modifier ->
            RenderTextParagraph(paragraph, spaceWidth, modifier) { word ->
                when (word) {
                    // is ImageSegment -> ZoomableContentView(word.segmentText, state, accountViewModel)
                    // is LinkSegment -> LoadUrlPreview(word.segmentText, word.segmentText, accountViewModel)
                    is EmojiSegment -> {
                        RenderCustomEmoji(word.segmentText, state)
                    }

                    // is InvoiceSegment -> MayBeInvoicePreview(word.segmentText)
                    // is WithdrawSegment -> MayBeWithdrawal(word.segmentText)
                    // is CashuSegment -> CashuPreview(word.segmentText, accountViewModel)
                    is EmailSegment -> {
                        ClickableEmail(word.segmentText)
                    }

                    is PhoneSegment -> {
                        ClickablePhone(word.segmentText)
                    }

                    is BechSegment -> {
                        CreateClickableText(
                            word.segmentText.substring(0, 10),
                            "",
                            1,
                            route = Route.EventRedirect(word.segmentText),
                            nav = EmptyNav(),
                        )
                    }

                    is HashTagSegment -> {
                        HashTag(word, EmptyNav())
                    }

                    // is HashIndexUserSegment -> TagLink(word, accountViewModel, nav)
                    // is HashIndexEventSegment -> TagLink(word, true, backgroundColorState, accountViewModel, nav)
                    is LinkSegment -> {
                        ClickableUrlOrBlossom(word.segmentText, word.segmentText)
                    }

                    is RegularTextSegment -> {
                        Text(word.segmentText)
                    }

                    is SchemelessUrlSegment -> {
                        NoProtocolUrlRenderer(word.segmentText)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun RenderRegularPreview2() {
    RenderRegular(
        "#Amethyst v0.84.1: ncryptsec support (NIP-49)",
        EmptyTagList,
    ) { paragraph, state, spaceWidth, modifier ->
        RenderTextParagraph(paragraph, spaceWidth, modifier) { word ->
            when (word) {
                // is ImageSegment -> ZoomableContentView(word.segmentText, state, accountViewModel)
                // is LinkSegment -> LoadUrlPreview(word.segmentText, word.segmentText, accountViewModel)
                is EmojiSegment -> RenderCustomEmoji(word.segmentText, state)

                // is InvoiceSegment -> MayBeInvoicePreview(word.segmentText)
                // is WithdrawSegment -> MayBeWithdrawal(word.segmentText)
                // is CashuSegment -> CashuPreview(word.segmentText, accountViewModel)
                is EmailSegment -> ClickableEmail(word.segmentText)

                is PhoneSegment -> ClickablePhone(word.segmentText)

                // is BechSegment -> BechLink(word.segmentText, true, backgroundColor, accountViewModel, nav)
                is HashTagSegment -> HashTag(word, EmptyNav())

                // is HashIndexUserSegment -> TagLink(word, accountViewModel, nav)
                // is HashIndexEventSegment -> TagLink(word, true, backgroundColorState, accountViewModel, nav)
                is LinkSegment -> ClickableUrlOrBlossom(word.segmentText, word.segmentText)

                is RegularTextSegment -> Text(word.segmentText)

                is RelayUrlSegment -> ClickableRelayUrl(word.segmentText, EmptyNav())

                is SchemelessUrlSegment -> NoProtocolUrlRenderer(word.segmentText)
            }
        }
    }
}

@Preview
@Composable
fun RenderRegularPreview3() {
    val tags =
        ImmutableListOfLists(
            arrayOf(
                arrayOf("t", "ioメシヨソイゲーム"),
                arrayOf("emoji", "_ri", "https://media.misskeyusercontent.com/emoji/_ri.png"),
                arrayOf("emoji", "petthex_japanesecake", "https://media.misskeyusercontent.com/emoji/petthex_japanesecake.gif"),
                arrayOf("emoji", "ai_nomming", "https://media.misskeyusercontent.com/misskey/f6294900-f678-43cc-bc36-3ee5deeca4c2.gif"),
                arrayOf("proxy", "https://misskey.io/notes/9q0x6gtdysir03qh", "activitypub"),
            ),
        )
    val accountViewModel = mockAccountViewModel()

    RenderRegular(
        "\u200B:_ri:\u200B\u200B:_ri:\u200Bはﾍﾞｲｸﾄﾞﾓﾁｮﾁｮ\u200B:petthex_japanesecake:\u200Bを食べました\u200B:ai_nomming:\u200B\n" +
            "#ioメシヨソイゲーム\n" +
            "https://misskey.io/play/9g3qza4jow",
        tags,
    ) { paragraph, state, spaceWidth, modifier ->
        RenderTextParagraph(paragraph, spaceWidth, modifier) { word ->
            when (word) {
                // is ImageSegment -> ZoomableContentView(word.segmentText, state, accountViewModel)
                is LinkSegment -> LoadUrlPreview(word.segmentText, word.segmentText, null, accountViewModel)

                is EmojiSegment -> RenderCustomEmoji(word.segmentText, state)

                // is InvoiceSegment -> MayBeInvoicePreview(word.segmentText)
                // is WithdrawSegment -> MayBeWithdrawal(word.segmentText)
                // is CashuSegment -> CashuPreview(word.segmentText, accountViewModel)
                is EmailSegment -> ClickableEmail(word.segmentText)

                is PhoneSegment -> ClickablePhone(word.segmentText)

                // is BechSegment -> BechLink(word.segmentText, true, backgroundColor, accountViewModel, nav)
                is HashTagSegment -> HashTag(word, EmptyNav())

                // is HashIndexUserSegment -> TagLink(word, accountViewModel, nav)
                // is HashIndexEventSegment -> TagLink(word, true, backgroundColorState, accountViewModel, nav)

                is RegularTextSegment -> Text(word.segmentText)

                is RelayUrlSegment -> ClickableRelayUrl(word.segmentText, EmptyNav())

                is SchemelessUrlSegment -> NoProtocolUrlRenderer(word.segmentText)
            }
        }
    }
}

@Composable
private fun RenderWordWithoutPreview(
    word: Segment,
    state: RichTextViewerState,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (word) {
        // Don't preview Images
        is ImageSegment -> ClickableUrlOrBlossom(word.segmentText, word.segmentText)

        // Don't preview Videos
        is VideoSegment -> ClickableUrlOrBlossom(word.segmentText, word.segmentText)

        // Don't preview PDFs
        is PdfSegment -> ClickableUrlOrBlossom(word.segmentText, word.segmentText)

        is LinkSegment -> ClickableUrlOrBlossom(word.segmentText, word.segmentText)

        is NowhereLinkSegment -> ClickableUrlOrBlossom(word.segmentText, word.segmentText)

        is EmojiSegment -> RenderCustomEmoji(word.segmentText, state)

        // Don't offer to pay invoices
        is InvoiceSegment -> Text(word.segmentText)

        // Don't offer to withdraw
        is WithdrawSegment -> Text(word.segmentText)

        // Cashu parsing is purely local + cached (CachedCashuParser),
        // so the preview is safe to render even in the no-preview path
        // — `canPreview = false` is meant to suppress network previews
        // (images, link unfurls, lightning invoice lookups), not local
        // decoding. Suppressing it here was the reason cashuB tokens
        // pasted into DMs and other no-preview contexts only showed
        // as a wall of base64.
        is CashuSegment -> CashuPreview(word.segmentText, accountViewModel)

        // Decoding is local and the network round-trip only fires on the Pay tap,
        // so the offer card is safe to render even in the no-preview path.
        is ClinkOfferSegment -> ClinkOfferPreview(word.offer, accountViewModel, nav)

        is EmailSegment -> ClickableEmail(word.segmentText)

        is SecretEmoji -> Text(word.segmentText)

        is MathSegment -> LatexEquation(word.latex, word.displayMode, word.leading, word.trailing)

        is PhoneSegment -> ClickablePhone(word.segmentText)

        is BechSegment -> BechLink(word.segmentText, false, 0, backgroundColor, accountViewModel, nav)

        is HashTagSegment -> HashTag(word, nav)

        is HashIndexUserSegment -> TagLink(word, accountViewModel, nav)

        is HashIndexEventSegment -> TagLink(word, false, 0, backgroundColor, accountViewModel, nav)

        is RegularTextSegment -> Text(word.segmentText)

        is RelayUrlSegment -> ClickableRelayUrl(word.segmentText, nav)

        is RelayGroupLinkSegment -> ClickableRelayGroupLink(word.segmentText, nav)
        is ConcordInviteLinkSegment -> ClickableConcordInviteLink(word.segmentText, nav)
        is BuzzInviteLinkSegment -> ClickableBuzzInviteLink(word.segmentText, nav)

        is BlossomUriSegment -> BlossomUriRendererNoPreview(word.segmentText, accountViewModel)

        is SchemelessUrlSegment -> NoProtocolUrlRenderer(word.segmentText)
    }
}

@Composable
private fun RenderWordWithPreview(
    word: Segment,
    state: RichTextViewerState,
    backgroundColor: MutableState<Color>,
    quotesLeft: Int,
    callbackUri: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (word) {
        is ImageSegment -> ZoomableContentView(word.segmentText, state, accountViewModel)
        is VideoSegment -> ZoomableContentView(word.segmentText, state, accountViewModel)
        is PdfSegment -> ZoomableContentView(word.segmentText, state, accountViewModel)
        is LinkSegment -> LoadUrlPreview(word.segmentText, word.segmentText, callbackUri, accountViewModel, nav)
        is NowhereLinkSegment -> NowhereLinkCard(word)
        is EmojiSegment -> RenderCustomEmoji(word.segmentText, state)
        is InvoiceSegment -> MayBeInvoicePreview(word.segmentText, accountViewModel)
        is WithdrawSegment -> MayBeWithdrawal(word.segmentText, accountViewModel)
        is CashuSegment -> CashuPreview(word.segmentText, accountViewModel)
        is ClinkOfferSegment -> ClinkOfferPreview(word.offer, accountViewModel, nav)
        is EmailSegment -> ClickableEmail(word.segmentText)
        is SecretEmoji -> DisplaySecretEmoji(word, state, callbackUri, true, quotesLeft, backgroundColor, accountViewModel, nav)
        is MathSegment -> LatexEquation(word.latex, word.displayMode, word.leading, word.trailing)
        is PhoneSegment -> ClickablePhone(word.segmentText)
        is BechSegment -> BechLink(word.segmentText, true, quotesLeft, backgroundColor, accountViewModel, nav)
        is HashTagSegment -> HashTag(word, nav)
        is HashIndexUserSegment -> TagLink(word, accountViewModel, nav)
        is HashIndexEventSegment -> TagLink(word, true, quotesLeft, backgroundColor, accountViewModel, nav)
        is RegularTextSegment -> Text(word.segmentText)
        is Base64Segment -> ZoomableContentView(word.segmentText, state, accountViewModel)
        is RelayUrlSegment -> ClickableRelayUrl(word.segmentText, nav)
        is RelayGroupLinkSegment -> RelayGroupCard(word.segmentText, accountViewModel, nav)
        is ConcordInviteLinkSegment -> ConcordInviteCard(word.segmentText, accountViewModel, nav)
        is BuzzInviteLinkSegment -> ClickableBuzzInviteLink(word.segmentText, nav)
        is BlossomUriSegment -> BlossomUriRenderer(word.segmentText, state, callbackUri, accountViewModel)
        is SchemelessUrlSegment -> NoProtocolUrlRenderer(word.segmentText)
    }
}

@Composable
fun BlossomUriRenderer(
    word: String,
    state: RichTextViewerState,
    callbackUri: String? = null,
    accountViewModel: AccountViewModel,
) {
    val isMedia = state.mediaForPager.contains(word)

    if (isMedia) {
        ZoomableContentView(word, state, accountViewModel)
    } else {
        val serverResultState =
            remember(word) {
                mutableStateOf(Amethyst.instance.blossomResolver.cachedFindServer(word))
            }

        if (serverResultState.value == null) {
            LaunchedEffect(word) {
                serverResultState.value = Amethyst.instance.blossomResolver.findServers(word)
            }
        }

        val serverResult = serverResultState.value
        if (serverResult != null && serverResult.serverUrl.isNotBlank()) {
            LoadUrlPreview(serverResult.serverUrl, serverResult.uri.filename(), callbackUri, accountViewModel)
        } else {
            ClickableBlossomUri(word, accountViewModel)
        }
    }
}

@Composable
fun ClickableBlossomUri(
    blossomUri: String,
    accountViewModel: AccountViewModel,
) {
    val blossomOpener = rememberBlossomUriOpener()

    ClickableTextPrimary(
        text = remember { BlossomUri.parse(blossomUri)?.filename() ?: blossomUri },
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis,
        onClick = { blossomOpener.open(blossomUri) { title, message -> accountViewModel.toastManager.toast(title, message) } },
    )
}

@Composable
fun BlossomUriRendererNoPreview(
    word: String,
    accountViewModel: AccountViewModel,
) {
    val serverResultState =
        remember(word) {
            mutableStateOf(Amethyst.instance.blossomResolver.cachedFindServer(word))
        }

    if (serverResultState.value == null) {
        LaunchedEffect(word) {
            serverResultState.value = Amethyst.instance.blossomResolver.findServers(word)
        }
    }

    val serverResult = serverResultState.value
    if (serverResult != null && serverResult.serverUrl.isNotBlank()) {
        ClickableUrlOrBlossom(serverResult.uri.filename(), serverResult.serverUrl)
    } else {
        ClickableBlossomUri(word, accountViewModel)
    }
}

@Composable
private fun ZoomableContentView(
    word: String,
    state: RichTextViewerState,
    accountViewModel: AccountViewModel,
) {
    state.mediaForPager[word]?.let {
        Box(modifier = HalfVertPadding) {
            ZoomableContentView(it, state.mediaList, roundedCorner = true, contentScale = ContentScale.FillWidth, accountViewModel)
        }
    }
}

@Composable
private fun NoProtocolUrlRenderer(url: String) {
    ClickableUrlOrBlossom(url, "https://$url")
}

@Composable
fun DisplaySecretEmoji(
    segment: SecretEmoji,
    state: RichTextViewerState,
    callbackUri: String?,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (canPreview && quotesLeft > 0) {
        var secretContent by remember {
            mutableStateOf(CachedRichTextParser.cachedText(EmojiCoder.decode(segment.segmentText), state.tags))
        }

        var showPopup by remember {
            mutableStateOf(false)
        }

        if (secretContent == null) {
            LaunchedEffect(segment) {
                launch(Dispatchers.IO) {
                    secretContent =
                        CachedRichTextParser.parseText(
                            EmojiCoder.decode(segment.segmentText),
                            state.tags,
                        )
                }
            }
        }

        val localSecretContent = secretContent

        AnimatedBorderTextCornerRadius(
            segment.segmentText,
            Modifier.clickable {
                showPopup = !showPopup
            },
        )

        if (localSecretContent != null && showPopup) {
            CoreSecretMessage(localSecretContent, callbackUri, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    } else {
        Text(segment.segmentText)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoreSecretMessage(
    localSecretContent: RichTextViewerState,
    callbackUri: String?,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (localSecretContent.paragraphs.size == 1) {
        localSecretContent.paragraphs[0].words.forEach { word ->
            RenderWordWithPreview(
                word = word,
                state = localSecretContent,
                backgroundColor = backgroundColor,
                quotesLeft = quotesLeft,
                callbackUri = callbackUri,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    } else if (localSecretContent.paragraphs.size > 1) {
        val spaceWidth = measureSpaceWidth(LocalTextStyle.current)

        Column(CashuCardBorders) {
            localSecretContent.paragraphs.forEach { paragraph ->
                val modifier = Modifier.align(if (paragraph.isRTL) Alignment.End else Alignment.Start)

                RenderTextParagraph(paragraph, spaceWidth, modifier) { word ->
                    RenderWordWithPreview(
                        word,
                        localSecretContent,
                        backgroundColor,
                        quotesLeft,
                        callbackUri,
                        accountViewModel,
                        nav,
                    )
                }
            }
        }
    }
}
