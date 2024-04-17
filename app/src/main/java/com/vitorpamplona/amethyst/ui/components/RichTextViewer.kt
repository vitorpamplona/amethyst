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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.compose.produceCachedState
import com.vitorpamplona.amethyst.commons.richtext.BechSegment
import com.vitorpamplona.amethyst.commons.richtext.CashuSegment
import com.vitorpamplona.amethyst.commons.richtext.EmailSegment
import com.vitorpamplona.amethyst.commons.richtext.EmojiSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexEventSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexUserSegment
import com.vitorpamplona.amethyst.commons.richtext.HashTagSegment
import com.vitorpamplona.amethyst.commons.richtext.ImageSegment
import com.vitorpamplona.amethyst.commons.richtext.InvoiceSegment
import com.vitorpamplona.amethyst.commons.richtext.LinkSegment
import com.vitorpamplona.amethyst.commons.richtext.PhoneSegment
import com.vitorpamplona.amethyst.commons.richtext.RegularTextSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.SchemelessUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.commons.richtext.WithdrawSegment
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.HashtagIcon
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.checkForHashtagWithIcon
import com.vitorpamplona.amethyst.service.CachedRichTextParser
import com.vitorpamplona.amethyst.ui.components.markdown.RenderContentAsMarkdown
import com.vitorpamplona.amethyst.ui.note.LoadUser
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.HalfVertPadding
import com.vitorpamplona.amethyst.ui.theme.inlinePlaceholder
import com.vitorpamplona.amethyst.ui.theme.innerPostModifier
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.events.EmptyTagList
import com.vitorpamplona.quartz.events.ImmutableListOfLists
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun isMarkdown(content: String): Boolean {
    return content.startsWith("> ") ||
        content.startsWith("# ") ||
        content.contains("##") ||
        content.contains("__") ||
        content.contains("**") ||
        content.contains("```") ||
        content.contains("](")
}

@Composable
fun RichTextViewer(
    content: String,
    canPreview: Boolean,
    quotesLeft: Int,
    modifier: Modifier,
    tags: ImmutableListOfLists<String>,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Column(modifier = modifier) {
        if (remember(content) { isMarkdown(content) }) {
            RenderContentAsMarkdown(content, tags, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
        } else {
            RenderRegular(content, tags, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

@Preview
@Composable
fun RenderStrangeNamePreview() {
    val nav: (String) -> Unit = {}

    Column(modifier = Modifier.padding(10.dp)) {
        RenderRegular(
            "If you want to stream or download the music from  nostr:npub1sctag667a7np6p6ety2up94pnwwxhd2ep8n8afr2gtr47cwd4ewsvdmmjm can you here",
            EmptyTagList,
        ) { word, state ->
            when (word) {
                is BechSegment -> {
                    Text(
                        "FreeFrom Official \uD80C\uDD66",
                        modifier = Modifier.border(1.dp, Color.Red),
                    )
                }
                is RegularTextSegment -> Text(word.segmentText)
            }
        }
    }
}

@Preview
@Composable
fun RenderRegularPreview() {
    val nav: (String) -> Unit = {}

    Column(modifier = Modifier.padding(10.dp)) {
        RenderRegular(
            "nostr:npub1e0z776cpe0gllgktjk54fuzv8pdfxmq6smsmh8xd7t8s7n474n9smk0txy but i'm Monthly funding" +
                " 7 other humans vitor@vitorpamplona.com at the moment so spread #test a bit thin, but won't always be the case.",
            EmptyTagList,
        ) { word, state ->
            when (word) {
                // is ImageSegment -> ZoomableContentView(word.segmentText, state, accountViewModel)
                // is LinkSegment -> LoadUrlPreview(word.segmentText, word.segmentText, accountViewModel)
                is EmojiSegment -> RenderCustomEmoji(word.segmentText, state)
                is InvoiceSegment -> MayBeInvoicePreview(word.segmentText)
                is WithdrawSegment -> MayBeWithdrawal(word.segmentText)
                // is CashuSegment -> CashuPreview(word.segmentText, accountViewModel)
                is EmailSegment -> ClickableEmail(word.segmentText)
                is PhoneSegment -> ClickablePhone(word.segmentText)
                is BechSegment -> {
                    CreateClickableText(
                        word.segmentText.substring(0, 10),
                        "",
                        1,
                        route = "",
                        nav = nav,
                    )
                }

                is HashTagSegment -> HashTag(word, nav)
                // is HashIndexUserSegment -> TagLink(word, accountViewModel, nav)
                // is HashIndexEventSegment -> TagLink(word, true, backgroundColorState, accountViewModel, nav)
                is SchemelessUrlSegment -> NoProtocolUrlRenderer(word)
                is RegularTextSegment -> Text(word.segmentText)
            }
        }
    }
}

@Preview
@Composable
fun RenderRegularPreview2() {
    val nav: (String) -> Unit = {}
    RenderRegular(
        "#Amethyst v0.84.1: ncryptsec support (NIP-49)",
        EmptyTagList,
    ) { word, state ->
        when (word) {
            // is ImageSegment -> ZoomableContentView(word.segmentText, state, accountViewModel)
            // is LinkSegment -> LoadUrlPreview(word.segmentText, word.segmentText, accountViewModel)
            is EmojiSegment -> RenderCustomEmoji(word.segmentText, state)
            is InvoiceSegment -> MayBeInvoicePreview(word.segmentText)
            is WithdrawSegment -> MayBeWithdrawal(word.segmentText)
            // is CashuSegment -> CashuPreview(word.segmentText, accountViewModel)
            is EmailSegment -> ClickableEmail(word.segmentText)
            is PhoneSegment -> ClickablePhone(word.segmentText)
            // is BechSegment -> BechLink(word.segmentText, true, backgroundColor, accountViewModel, nav)
            is HashTagSegment -> HashTag(word, nav)
            // is HashIndexUserSegment -> TagLink(word, accountViewModel, nav)
            // is HashIndexEventSegment -> TagLink(word, true, backgroundColorState, accountViewModel, nav)
            is SchemelessUrlSegment -> NoProtocolUrlRenderer(word)
            is RegularTextSegment -> Text(word.segmentText)
        }
    }
}

@Composable
fun mockAccountViewModel(): AccountViewModel {
    val sharedPreferencesViewModel: SharedPreferencesViewModel = viewModel()
    sharedPreferencesViewModel.init()

    return AccountViewModel(
        Account(
            // blank keys
            keyPair =
                KeyPair(
                    privKey = Hex.decode("0f761f8a5a481e26f06605a1d9b3e9eba7a107d351f43c43a57469b788274499"),
                    pubKey = Hex.decode("989c3734c46abac7ce3ce229971581a5a6ee39cdd6aa7261a55823fa7f8c4799"),
                    forcePubKeyCheck = false,
                ),
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ),
        sharedPreferencesViewModel.sharedPrefs,
    )
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
    val nav: (String) -> Unit = {}
    val accountViewModel = mockAccountViewModel()

    RenderRegular(
        "\u200B:_ri:\u200B\u200B:_ri:\u200Bはﾍﾞｲｸﾄﾞﾓﾁｮﾁｮ\u200B:petthex_japanesecake:\u200Bを食べました\u200B:ai_nomming:\u200B\n" +
            "#ioメシヨソイゲーム\n" +
            "https://misskey.io/play/9g3qza4jow",
        tags,
    ) { word, state ->
        when (word) {
            // is ImageSegment -> ZoomableContentView(word.segmentText, state, accountViewModel)
            is LinkSegment -> LoadUrlPreview(word.segmentText, word.segmentText, accountViewModel)
            is EmojiSegment -> RenderCustomEmoji(word.segmentText, state)
            is InvoiceSegment -> MayBeInvoicePreview(word.segmentText)
            is WithdrawSegment -> MayBeWithdrawal(word.segmentText)
            // is CashuSegment -> CashuPreview(word.segmentText, accountViewModel)
            is EmailSegment -> ClickableEmail(word.segmentText)
            is PhoneSegment -> ClickablePhone(word.segmentText)
            // is BechSegment -> BechLink(word.segmentText, true, backgroundColor, accountViewModel, nav)
            is HashTagSegment -> HashTag(word, nav)
            // is HashIndexUserSegment -> TagLink(word, accountViewModel, nav)
            // is HashIndexEventSegment -> TagLink(word, true, backgroundColorState, accountViewModel, nav)
            is SchemelessUrlSegment -> NoProtocolUrlRenderer(word)
            is RegularTextSegment -> Text(word.segmentText)
        }
    }
}

@Composable
private fun RenderRegular(
    content: String,
    tags: ImmutableListOfLists<String>,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    RenderRegular(content, tags) { word, state ->
        if (canPreview) {
            RenderWordWithPreview(
                word,
                state,
                backgroundColor,
                quotesLeft,
                accountViewModel,
                nav,
            )
        } else {
            RenderWordWithoutPreview(
                word,
                state,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderRegular(
    content: String,
    tags: ImmutableListOfLists<String>,
    wordRenderer: @Composable (Segment, RichTextViewerState) -> Unit,
) {
    val state by remember(content, tags) { mutableStateOf(CachedRichTextParser.parseText(content, tags)) }

    val spaceWidth = measureSpaceWidth(LocalTextStyle.current)

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
                FlowRow(
                    modifier = Modifier.align(if (paragraph.isRTL) Alignment.End else Alignment.Start),
                    horizontalArrangement = Arrangement.spacedBy(spaceWidth),
                ) {
                    paragraph.words.forEach { word ->
                        wordRenderer(word, state)
                    }
                }
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
private fun RenderWordWithoutPreview(
    word: Segment,
    state: RichTextViewerState,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    when (word) {
        // Don't preview Images
        is ImageSegment -> ClickableUrl(word.segmentText, word.segmentText)
        is LinkSegment -> ClickableUrl(word.segmentText, word.segmentText)
        is EmojiSegment -> RenderCustomEmoji(word.segmentText, state)
        // Don't offer to pay invoices
        is InvoiceSegment -> Text(word.segmentText)
        // Don't offer to withdraw
        is WithdrawSegment -> Text(word.segmentText)
        is CashuSegment -> Text(word.segmentText)
        is EmailSegment -> ClickableEmail(word.segmentText)
        is PhoneSegment -> ClickablePhone(word.segmentText)
        is BechSegment -> BechLink(word.segmentText, false, 0, backgroundColor, accountViewModel, nav)
        is HashTagSegment -> HashTag(word, nav)
        is HashIndexUserSegment -> TagLink(word, accountViewModel, nav)
        is HashIndexEventSegment -> TagLink(word, false, 0, backgroundColor, accountViewModel, nav)
        is SchemelessUrlSegment -> NoProtocolUrlRenderer(word)
        is RegularTextSegment -> Text(word.segmentText)
    }
}

@Composable
private fun RenderWordWithPreview(
    word: Segment,
    state: RichTextViewerState,
    backgroundColor: MutableState<Color>,
    quotesLeft: Int,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    when (word) {
        is ImageSegment -> ZoomableContentView(word.segmentText, state, accountViewModel)
        is LinkSegment -> LoadUrlPreview(word.segmentText, word.segmentText, accountViewModel)
        is EmojiSegment -> RenderCustomEmoji(word.segmentText, state)
        is InvoiceSegment -> MayBeInvoicePreview(word.segmentText)
        is WithdrawSegment -> MayBeWithdrawal(word.segmentText)
        is CashuSegment -> CashuPreview(word.segmentText, accountViewModel)
        is EmailSegment -> ClickableEmail(word.segmentText)
        is PhoneSegment -> ClickablePhone(word.segmentText)
        is BechSegment -> BechLink(word.segmentText, true, quotesLeft, backgroundColor, accountViewModel, nav)
        is HashTagSegment -> HashTag(word, nav)
        is HashIndexUserSegment -> TagLink(word, accountViewModel, nav)
        is HashIndexEventSegment -> TagLink(word, true, quotesLeft, backgroundColor, accountViewModel, nav)
        is SchemelessUrlSegment -> NoProtocolUrlRenderer(word)
        is RegularTextSegment -> Text(word.segmentText)
    }
}

@Composable
private fun ZoomableContentView(
    word: String,
    state: RichTextViewerState,
    accountViewModel: AccountViewModel,
) {
    state.imagesForPager[word]?.let {
        Box(modifier = HalfVertPadding) {
            ZoomableContentView(it, state.imageList, roundedCorner = true, accountViewModel)
        }
    }
}

@Composable
private fun NoProtocolUrlRenderer(segment: SchemelessUrlSegment) {
    ClickableUrl(segment.url, "https://${segment.url}")
    segment.extras?.let { it1 -> Text(it1) }
}

@Composable
fun RenderCustomEmoji(
    word: String,
    state: RichTextViewerState,
) {
    CreateTextWithEmoji(
        text = word,
        emojis = state.customEmoji,
    )
}

@Composable
fun BechLink(
    word: String,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
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
    nav: (String) -> Unit,
) {
    NoteCompose(
        baseNote = note,
        accountViewModel = accountViewModel,
        modifier = MaterialTheme.colorScheme.innerPostModifier,
        parentBackgroundColor = backgroundColor,
        quotesLeft = quotesLeft - 1,
        isQuotedNote = true,
        nav = nav,
    )

    extraChars?.let {
        Text(
            it,
        )
    }
}

@Composable
fun HashTag(
    segment: HashTagSegment,
    nav: (String) -> Unit,
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
        modifier = remember { Modifier.clickable { nav("Hashtag/${segment.hashtag}") } },
        inlineContent =
            if (hashtagIcon != null) {
                mapOf("inlineContent" to InlineIcon(hashtagIcon))
            } else {
                emptyMap()
            },
    )
}

@Composable
private fun InlineIcon(hashtagIcon: HashtagIcon) =
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
    nav: (String) -> Unit,
) {
    LoadUser(baseUserHex = word.hex, accountViewModel) {
        if (it == null) {
            Text(text = word.segmentText)
        } else {
            Row {
                DisplayUserFromTag(it, nav)
                word.extras?.let {
                    Text(text = it)
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
        remember(baseNoteHex) { mutableStateOf<Note?>(accountViewModel.getNoteIfExists(baseNoteHex)) }

    if (note == null) {
        LaunchedEffect(key1 = baseNoteHex) {
            accountViewModel.checkGetOrCreateNote(baseNoteHex) { note = it }
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
    nav: (String) -> Unit,
) {
    LoadNote(baseNoteHex = word.hex, accountViewModel) {
        if (it == null) {
            Text(text = remember { word.segmentText.toShortenHex() })
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

@Composable
private fun DisplayNoteFromTag(
    baseNote: Note,
    addedChars: String?,
    canPreview: Boolean,
    quotesLeft: Int,
    accountViewModel: AccountViewModel,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
) {
    if (canPreview && quotesLeft > 0) {
        NoteCompose(
            baseNote = baseNote,
            accountViewModel = accountViewModel,
            modifier = MaterialTheme.colorScheme.innerPostModifier,
            parentBackgroundColor = backgroundColor,
            isQuotedNote = true,
            quotesLeft = quotesLeft - 1,
            nav = nav,
        )
    } else {
        ClickableNoteTag(baseNote, accountViewModel, nav)
    }

    addedChars?.ifBlank { null }?.let { Text(text = it) }
}

@Composable
private fun DisplayUserFromTag(
    baseUser: User,
    nav: (String) -> Unit,
) {
    val meta by baseUser.live().userMetadataInfo.observeAsState(baseUser.info)

    Crossfade(targetState = meta, label = "DisplayUserFromTag") {
        Row {
            CreateClickableTextWithEmoji(
                clickablePart = remember(meta) { it?.bestName() ?: baseUser.pubkeyDisplayHex() },
                maxLines = 1,
                route = "User/${baseUser.pubkeyHex}",
                nav = nav,
                tags = it?.tags,
            )
        }
    }
}
