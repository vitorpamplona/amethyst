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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.compose.produceCachedState
import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.Base64Segment
import com.vitorpamplona.amethyst.commons.richtext.BechSegment
import com.vitorpamplona.amethyst.commons.richtext.CashuSegment
import com.vitorpamplona.amethyst.commons.richtext.EmailSegment
import com.vitorpamplona.amethyst.commons.richtext.EmojiSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexEventSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexUserSegment
import com.vitorpamplona.amethyst.commons.richtext.HashTagSegment
import com.vitorpamplona.amethyst.commons.richtext.ImageGalleryParagraph
import com.vitorpamplona.amethyst.commons.richtext.ImageSegment
import com.vitorpamplona.amethyst.commons.richtext.InvoiceSegment
import com.vitorpamplona.amethyst.commons.richtext.LinkSegment
import com.vitorpamplona.amethyst.commons.richtext.ParagraphState
import com.vitorpamplona.amethyst.commons.richtext.PhoneSegment
import com.vitorpamplona.amethyst.commons.richtext.RegularTextSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.SchemelessUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.SecretEmoji
import com.vitorpamplona.amethyst.commons.richtext.VideoSegment
import com.vitorpamplona.amethyst.commons.richtext.WithdrawSegment
import com.vitorpamplona.amethyst.model.HashtagIcon
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.checkForHashtagWithIcon
import com.vitorpamplona.amethyst.service.CachedRichTextParser
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserInfo
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.markdown.RenderContentAsMarkdown
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.creators.invoice.MayBeInvoicePreview
import com.vitorpamplona.amethyst.ui.note.toShortDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.theme.CashuCardBorders
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.inlinePlaceholder
import com.vitorpamplona.amethyst.ui.theme.innerPostModifier
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun isMarkdown(content: String): Boolean =
    content.startsWith("> ") ||
        content.startsWith("# ") ||
        content.contains("##") ||
        content.contains("__") ||
        content.contains("**") ||
        content.contains("```") ||
        content.contains("](")

@Composable
fun RichTextViewer(
    content: String,
    canPreview: Boolean,
    quotesLeft: Int,
    modifier: Modifier,
    tags: ImmutableListOfLists<String>,
    backgroundColor: MutableState<Color>,
    callbackUri: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(modifier = modifier) {
        if (remember(content) { isMarkdown(content) }) {
            RenderContentAsMarkdown(content, tags, canPreview, quotesLeft, backgroundColor, callbackUri, accountViewModel, nav)
        } else {
            RenderRegular(content, tags, canPreview, quotesLeft, backgroundColor, callbackUri, accountViewModel, nav)
        }
    }
}

@Preview
@Composable
fun RenderStrangeNamePreview() {
    Column(modifier = Modifier.padding(10.dp)) {
        RenderRegular(
            "If you want to stream or download the music from  nostr:npub1sctag667a7np6p6ety2up94pnwwxhd2ep8n8afr2gtr47cwd4ewsvdmmjm can you here",
            EmptyTagList,
        ) { paragraph, state, modifier ->
            RenderTextParagraph(paragraph, modifier, state, canPreview = false, quotesLeft = 0, backgroundColor = remember { mutableStateOf(Color.Transparent) }, callbackUri = null, accountViewModel = mockAccountViewModel(), nav = EmptyNav())
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
        ) { paragraph, state, modifier ->
            RenderTextParagraph(paragraph, modifier, state, canPreview = false, quotesLeft = 0, backgroundColor = remember { mutableStateOf(Color.Transparent) }, callbackUri = null, accountViewModel = mockAccountViewModel(), nav = EmptyNav())
        }
    }
}

@Preview
@Composable
fun RenderRegularPreview2() {
    RenderRegular(
        "#Amethyst v0.84.1: ncryptsec support (NIP-49)",
        EmptyTagList,
    ) { paragraph, state, modifier ->
        RenderTextParagraph(paragraph, modifier, state, canPreview = false, quotesLeft = 0, backgroundColor = remember { mutableStateOf(Color.Transparent) }, callbackUri = null, accountViewModel = mockAccountViewModel(), nav = EmptyNav())
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
    ) { paragraph, state, modifier ->
        RenderTextParagraph(paragraph, modifier, state, canPreview = true, quotesLeft = 1, backgroundColor = remember { mutableStateOf(Color.Transparent) }, callbackUri = null, accountViewModel = accountViewModel, nav = EmptyNav())
    }
}

@Composable
private fun RenderRegular(
    content: String,
    tags: ImmutableListOfLists<String>,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    callbackUri: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RenderRegular(content, tags, callbackUri) { paragraph, state, modifier ->
        if (paragraph is ImageGalleryParagraph) {
            ImageGallery(
                images = paragraph,
                state = state,
                accountViewModel = accountViewModel,
                modifier = modifier,
                roundedCorner = true,
            )
        } else {
            RenderTextParagraph(
                paragraph = paragraph,
                modifier = modifier,
                state = state,
                canPreview = canPreview,
                quotesLeft = quotesLeft,
                backgroundColor = backgroundColor,
                callbackUri = callbackUri,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun RenderTextParagraph(
    paragraph: ParagraphState,
    modifier: Modifier,
    state: RichTextViewerState,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    callbackUri: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val fontSize = LocalTextStyle.current.fontSize

    val emojiSize = if (fontSize == TextUnit.Unspecified) 20.sp else fontSize.times(1.1f)
    val emojiPlaceholder = Placeholder(emojiSize, emojiSize, PlaceholderVerticalAlign.Center)

    // Fixed-size placeholder for media (images / videos)
    val mediaPlaceholder = Placeholder(300.sp, 200.sp, PlaceholderVerticalAlign.Bottom)

    // Fixed-size placeholder for block-level widgets (URL previews, invoices, note cards, etc.)
    val blockPlaceholder = Placeholder(300.sp, 300.sp, PlaceholderVerticalAlign.Bottom)

    // Inline-text-height placeholder for user / event references shown as links
    val refPlaceholder = Placeholder(150.sp, emojiSize, PlaceholderVerticalAlign.TextCenter)

    // Both the annotated string and the inlineContent map are built in a single pass so
    // their "inline_$idx" keys always match.
    val inlineContent = mutableMapOf<String, InlineTextContent>()

    val annotatedText =
        buildAnnotatedString {
            paragraph.words.forEachIndexed { idx, word ->
                if (idx > 0) append(" ")

                when (word) {
                    is RegularTextSegment -> append(word.segmentText)

                    is EmojiSegment -> {
                        val emojiList =
                            com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
                                .assembleAnnotatedList(word.segmentText, state.customEmoji)
                        if (emojiList != null) {
                            emojiList.forEachIndexed { emojiIdx, renderable ->
                                when (renderable) {
                                    is com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji.TextType ->
                                        append(renderable.text)
                                    is com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji.ImageUrlType -> {
                                        val key = "inline_${idx}_$emojiIdx"
                                        appendInlineContent(key, renderable.url)
                                        val url = renderable.url
                                        inlineContent[key] =
                                            InlineTextContent(emojiPlaceholder) {
                                                AsyncImage(
                                                    model = url,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize().padding(1.dp),
                                                )
                                            }
                                    }
                                }
                            }
                        } else {
                            append(word.segmentText)
                        }
                    }

                    is HashTagSegment -> {
                        val hashtagIcon = checkForHashtagWithIcon(word.hashtag)
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "hashtag_$idx",
                                styles = TextLinkStyles(SpanStyle(color = primaryColor)),
                            ) {
                                nav.nav(Route.Hashtag(word.hashtag.lowercase()))
                            },
                        ) {
                            append("#${word.hashtag}")
                            if (hashtagIcon != null) {
                                val key = "inline_$idx"
                                appendInlineContent(key, "[icon]")
                                val icon = hashtagIcon
                                inlineContent[key] =
                                    InlineTextContent(inlinePlaceholder) {
                                        Icon(
                                            imageVector = icon.icon,
                                            contentDescription = icon.description,
                                            tint = Color.Unspecified,
                                            modifier = icon.modifier,
                                        )
                                    }
                            }
                        }
                        word.extras?.let { withStyle(SpanStyle(color = onBackgroundColor)) { append(it) } }
                    }

                    is ImageSegment -> {
                        val key = "inline_$idx"
                        if (canPreview && state.imagesForPager.containsKey(word.segmentText)) {
                            appendInlineContent(key, "[image]")
                            val url = word.segmentText
                            inlineContent[key] =
                                InlineTextContent(mediaPlaceholder) {
                                    state.imagesForPager[url]?.let { media ->
                                        Box(Modifier.fillMaxSize()) {
                                            ZoomableContentView(
                                                content = media,
                                                images = state.imageList,
                                                roundedCorner = true,
                                                contentScale = ContentScale.Crop,
                                                accountViewModel = accountViewModel,
                                            )
                                        }
                                    }
                                }
                        } else {
                            withLink(
                                LinkAnnotation.Url(
                                    word.segmentText,
                                    TextLinkStyles(SpanStyle(color = primaryColor)),
                                ),
                            ) { append(word.segmentText) }
                        }
                    }

                    is VideoSegment -> {
                        val key = "inline_$idx"
                        if (canPreview && state.imagesForPager.containsKey(word.segmentText)) {
                            appendInlineContent(key, "[video]")
                            val url = word.segmentText
                            inlineContent[key] =
                                InlineTextContent(mediaPlaceholder) {
                                    state.imagesForPager[url]?.let { media ->
                                        Box(Modifier.fillMaxSize()) {
                                            ZoomableContentView(
                                                content = media,
                                                images = state.imageList,
                                                roundedCorner = true,
                                                contentScale = ContentScale.Crop,
                                                accountViewModel = accountViewModel,
                                            )
                                        }
                                    }
                                }
                        } else {
                            withLink(
                                LinkAnnotation.Url(
                                    word.segmentText,
                                    TextLinkStyles(SpanStyle(color = primaryColor)),
                                ),
                            ) { append(word.segmentText) }
                        }
                    }

                    is Base64Segment -> {
                        val key = "inline_$idx"
                        if (canPreview) {
                            appendInlineContent(key, "[image]")
                            val url = word.segmentText
                            inlineContent[key] =
                                InlineTextContent(mediaPlaceholder) {
                                    state.imagesForPager[url]?.let { media ->
                                        Box(Modifier.fillMaxSize()) {
                                            ZoomableContentView(
                                                content = media,
                                                images = state.imageList,
                                                roundedCorner = true,
                                                contentScale = ContentScale.Crop,
                                                accountViewModel = accountViewModel,
                                            )
                                        }
                                    }
                                }
                        } else {
                            append(word.segmentText)
                        }
                    }

                    is LinkSegment -> {
                        val key = "inline_$idx"
                        if (canPreview) {
                            appendInlineContent(key, word.segmentText)
                            val url = word.segmentText
                            inlineContent[key] =
                                InlineTextContent(blockPlaceholder) {
                                    LoadUrlPreview(url, url, callbackUri, accountViewModel)
                                }
                        } else {
                            withLink(
                                LinkAnnotation.Url(
                                    word.segmentText,
                                    TextLinkStyles(SpanStyle(color = primaryColor)),
                                ),
                            ) { append(word.segmentText) }
                        }
                    }

                    is InvoiceSegment -> {
                        if (canPreview) {
                            val key = "inline_$idx"
                            appendInlineContent(key, word.segmentText)
                            val invoice = word.segmentText
                            inlineContent[key] =
                                InlineTextContent(blockPlaceholder) {
                                    MayBeInvoicePreview(invoice, accountViewModel)
                                }
                        } else {
                            append(word.segmentText)
                        }
                    }

                    is WithdrawSegment -> {
                        if (canPreview) {
                            val key = "inline_$idx"
                            appendInlineContent(key, word.segmentText)
                            val withdraw = word.segmentText
                            inlineContent[key] =
                                InlineTextContent(blockPlaceholder) {
                                    MayBeWithdrawal(withdraw, accountViewModel)
                                }
                        } else {
                            append(word.segmentText)
                        }
                    }

                    is CashuSegment -> {
                        if (canPreview) {
                            val key = "inline_$idx"
                            appendInlineContent(key, word.segmentText)
                            val cashu = word.segmentText
                            inlineContent[key] =
                                InlineTextContent(blockPlaceholder) {
                                    CashuPreview(cashu, accountViewModel)
                                }
                        } else {
                            append(word.segmentText)
                        }
                    }

                    is EmailSegment ->
                        withLink(
                            LinkAnnotation.Url(
                                "mailto:${word.segmentText}",
                                TextLinkStyles(SpanStyle(color = primaryColor)),
                            ),
                        ) { append(word.segmentText) }

                    is PhoneSegment ->
                        withLink(
                            LinkAnnotation.Url(
                                "tel:${word.segmentText}",
                                TextLinkStyles(SpanStyle(color = primaryColor)),
                            ),
                        ) { append(word.segmentText) }

                    is SecretEmoji -> {
                        if (canPreview && quotesLeft > 0) {
                            val key = "inline_$idx"
                            appendInlineContent(key, word.segmentText)
                            val segment = word
                            inlineContent[key] =
                                InlineTextContent(blockPlaceholder) {
                                    DisplaySecretEmoji(
                                        segment = segment,
                                        state = state,
                                        callbackUri = callbackUri,
                                        canPreview = true,
                                        quotesLeft = quotesLeft,
                                        backgroundColor = backgroundColor,
                                        accountViewModel = accountViewModel,
                                        nav = nav,
                                    )
                                }
                        } else {
                            append(word.segmentText)
                        }
                    }

                    is BechSegment -> {
                        val key = "inline_$idx"
                        appendInlineContent(key, word.segmentText)
                        val bech = word.segmentText
                        val noteBlockPlaceholder =
                            if (canPreview && quotesLeft > 0) blockPlaceholder else refPlaceholder
                        inlineContent[key] =
                            InlineTextContent(noteBlockPlaceholder) {
                                BechLink(bech, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
                            }
                    }

                    is HashIndexUserSegment -> {
                        val key = "inline_$idx"
                        appendInlineContent(key, word.segmentText)
                        val segment = word
                        inlineContent[key] =
                            InlineTextContent(refPlaceholder) {
                                TagLink(segment, accountViewModel, nav)
                            }
                    }

                    is HashIndexEventSegment -> {
                        val key = "inline_$idx"
                        appendInlineContent(key, word.segmentText)
                        val segment = word
                        val eventPlaceholder =
                            if (canPreview && quotesLeft > 0) blockPlaceholder else refPlaceholder
                        inlineContent[key] =
                            InlineTextContent(eventPlaceholder) {
                                TagLink(segment, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
                            }
                    }

                    is SchemelessUrlSegment -> {
                        withLink(
                            LinkAnnotation.Url(
                                "https://${word.url}",
                                TextLinkStyles(SpanStyle(color = primaryColor)),
                            ),
                        ) { append(word.url) }
                        word.extras?.let { withStyle(SpanStyle(color = onBackgroundColor)) { append(it) } }
                    }
                }
            }
        }

    Text(
        text = annotatedText,
        inlineContent = inlineContent,
        modifier = modifier,
    )
}

@Composable
fun RenderRegular(
    content: String,
    tags: ImmutableListOfLists<String>,
    callbackUri: String? = null,
    renderParagraph: @Composable (ParagraphState, state: RichTextViewerState, modifier: Modifier) -> Unit,
) {
    val state by remember(content, tags) { mutableStateOf(CachedRichTextParser.parseText(content, tags, callbackUri)) }

    val currentTextStyle = LocalTextStyle.current

    val textStyle =
        remember(currentTextStyle) {
            currentTextStyle.copy(
                lineHeight = 1.3.em,
            )
        }

    Column {
        // FlowRow doesn't work well with paragraphs. So we need to split them
        state.paragraphs.forEach { paragraph ->
            CompositionLocalProvider(
                LocalLayoutDirection provides
                    if (paragraph.isRTL) {
                        LayoutDirection.Rtl
                    } else {
                        LayoutDirection.Ltr
                    },
                LocalTextStyle provides textStyle,
            ) {
                renderParagraph(
                    paragraph,
                    state,
                    Modifier.align(if (paragraph.isRTL) Alignment.End else Alignment.Start),
                )
            }
        }
    }
}

@Composable
fun measureSpaceWidth(textStyle: TextStyle): Dp {
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    return remember(fontFamilyResolver, density, layoutDirection, textStyle) {
        val widthPx =
            TextMeasurer(fontFamilyResolver, density, layoutDirection, 1)
                .measure(" ", textStyle)
                .size
                .width
        with(density) { widthPx.toDp() }
    }
}

@Composable
fun BechLink(
    word: String,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val loadedLink by produceCachedState(cache = accountViewModel.bechLinkCache, key = word)

    val baseNote = loadedLink?.baseNote

    if (canPreview && quotesLeft > 0 && baseNote != null) {
        Row {
            DisplayFullNote(
                note = baseNote,
                extraChars = loadedLink?.nip19?.additionalChars?.ifBlank { null },
                quotesLeft = quotesLeft,
                backgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    } else if (loadedLink?.nip19 != null) {
        ClickableRoute(word, loadedLink?.nip19!!, accountViewModel, nav)
    } else {
        val text =
            remember(word) {
                if (word.length > 16) {
                    word.replaceRange(8, word.length - 8, ":")
                } else {
                    word
                }
            }

        Text(text = text, maxLines = 1)
    }
}

@Composable
fun DisplayFullNote(
    note: Note,
    extraChars: String?,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NoteCompose(
        baseNote = note,
        modifier = MaterialTheme.colorScheme.innerPostModifier,
        isQuotedNote = true,
        quotesLeft = quotesLeft - 1,
        parentBackgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    extraChars?.let {
        Text(
            it,
        )
    }
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
        RenderTextParagraph(
            paragraph = localSecretContent.paragraphs[0],
            modifier = Modifier,
            state = localSecretContent,
            canPreview = true,
            quotesLeft = quotesLeft,
            backgroundColor = backgroundColor,
            callbackUri = callbackUri,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    } else if (localSecretContent.paragraphs.size > 1) {
        Column(CashuCardBorders) {
            localSecretContent.paragraphs.forEach { paragraph ->
                val modifier = Modifier.align(if (paragraph.isRTL) Alignment.End else Alignment.Start)

                RenderTextParagraph(
                    paragraph = paragraph,
                    modifier = modifier,
                    state = localSecretContent,
                    canPreview = true,
                    quotesLeft = quotesLeft,
                    backgroundColor = backgroundColor,
                    callbackUri = callbackUri,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
fun HashTag(
    segment: HashTagSegment,
    nav: INav,
) {
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.onBackground
    val hashtagIcon: HashtagIcon? = checkForHashtagWithIcon(segment.hashtag)

    val annotatedTermsString =
        remember(segment.segmentText) {
            buildAnnotatedString {
                withStyle(SpanStyle(color = primary)) {
                    pushStringAnnotation("routeToHashtag", "")
                    append("#${segment.hashtag}")
                    pop()
                }

                if (hashtagIcon != null) {
                    withStyle(SpanStyle(color = primary)) {
                        pushStringAnnotation("routeToHashtag", "")
                        appendInlineContent("inlineContent", "[icon]")
                        pop()
                    }
                }

                segment.extras?.let { withStyle(SpanStyle(color = background)) { append(it) } }
            }
        }

    Text(
        text = annotatedTermsString,
        modifier =
            remember {
                Modifier.clickable {
                    nav.nav(Route.Hashtag(segment.hashtag.lowercase()))
                }
            },
        inlineContent =
            if (hashtagIcon != null) {
                mapOf("inlineContent" to inlineIcon(hashtagIcon))
            } else {
                emptyMap()
            },
    )
}

@Composable
private fun inlineIcon(hashtagIcon: HashtagIcon) =
    InlineTextContent(inlinePlaceholder) {
        Icon(
            imageVector = hashtagIcon.icon,
            contentDescription = hashtagIcon.description,
            tint = Color.Unspecified,
            modifier = hashtagIcon.modifier,
        )
    }

@Composable
fun TagLink(
    word: HashIndexUserSegment,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadUser(baseUserHex = word.hex, accountViewModel) {
        if (it == null) {
            Text(text = word.segmentText)
        } else {
            Row {
                DisplayUserFromTag(it, accountViewModel, nav)
                word.extras?.let { it2 ->
                    Text(text = it2)
                }
            }
        }
    }
}

@Composable
fun LoadNote(
    baseNoteHex: String,
    accountViewModel: AccountViewModel,
    content: @Composable (Note?) -> Unit,
) {
    var note by
        remember(baseNoteHex) { mutableStateOf(accountViewModel.getNoteIfExists(baseNoteHex)) }

    if (note == null) {
        LaunchedEffect(key1 = baseNoteHex) {
            note = accountViewModel.checkGetOrCreateNote(baseNoteHex)
        }
    }

    content(note)
}

@Composable
fun TagLink(
    word: HashIndexEventSegment,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadNote(baseNoteHex = word.hex, accountViewModel) {
        if (it == null) {
            Text(text = remember { word.segmentText.toShortDisplay() })
        } else {
            Row {
                DisplayNoteFromTag(
                    it,
                    word.extras,
                    canPreview,
                    quotesLeft,
                    accountViewModel,
                    backgroundColor,
                    nav,
                )
            }
        }
    }
}

@Preview
@Composable
fun DisplayNoteFromTagPreview() {
    val dummyPost =
        TextNoteEvent(
            id = "0b6d941c46411a95edb1c93da7ad6ca26370497d8c7b7d621f5cb59f48841bad",
            pubKey = "6dd3b72e325da7383b275eef1c66131ba4664326e162bc060527509b4e33ae43",
            createdAt = 1753988264,
            tags = emptyArray(),
            content = "test",
            sig = "ec39e60722a083cccbd2d82d2827e13f5499fa7cbcedac5b76011a844c077473adb629d50d01fab147835ac6c8a3d5ba9aaddd87d6723f0c3c864b9119fc4356",
        )

    LocalCache.justConsume(dummyPost, null, true)
    val note = LocalCache.getOrCreateNote(dummyPost.id)

    ThemeComparisonColumn(
        toPreview = {
            ClickableTextPrimary(
                text = "@${note.idNote().toShortDisplay()}",
                onClick = { },
            )
        },
    )
}

@Composable
private fun DisplayNoteFromTag(
    baseNote: Note,
    addedChars: String?,
    canPreview: Boolean,
    quotesLeft: Int,
    accountViewModel: AccountViewModel,
    backgroundColor: MutableState<Color>,
    nav: INav,
) {
    if (canPreview && quotesLeft > 0) {
        NoteCompose(
            baseNote = baseNote,
            modifier = MaterialTheme.colorScheme.innerPostModifier,
            isQuotedNote = true,
            quotesLeft = quotesLeft - 1,
            parentBackgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    } else {
        ClickableTextPrimary(
            text = "@${baseNote.idNote().toShortDisplay()}",
            onClick = { routeFor(baseNote, accountViewModel.account)?.let { nav.nav(it) } },
        )
    }

    addedChars?.ifBlank { null }?.let { Text(text = it) }
}

@Composable
private fun DisplayUserFromTag(
    baseUser: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val meta by observeUserInfo(baseUser, accountViewModel)

    CrossfadeIfEnabled(targetState = meta, label = "DisplayUserFromTag", accountViewModel = accountViewModel) {
        Row {
            CreateClickableTextWithEmoji(
                clickablePart = remember(meta) { it?.info?.bestName() ?: baseUser.pubkeyDisplayHex() },
                maxLines = 1,
                route = remember(baseUser) { routeFor(baseUser) },
                nav = nav,
                tags = it?.tags,
            )
        }
    }
}
