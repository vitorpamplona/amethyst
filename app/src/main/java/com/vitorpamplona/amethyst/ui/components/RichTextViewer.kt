package com.vitorpamplona.amethyst.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
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
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.markdown.MarkdownParseOptions
import com.halilibo.richtext.ui.material3.Material3RichText
import com.vitorpamplona.amethyst.model.HashtagIcon
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.checkForHashtagWithIcon
import com.vitorpamplona.amethyst.service.BechSegment
import com.vitorpamplona.amethyst.service.CachedRichTextParser
import com.vitorpamplona.amethyst.service.CashuSegment
import com.vitorpamplona.amethyst.service.EmailSegment
import com.vitorpamplona.amethyst.service.EmojiSegment
import com.vitorpamplona.amethyst.service.HashIndexEventSegment
import com.vitorpamplona.amethyst.service.HashIndexUserSegment
import com.vitorpamplona.amethyst.service.HashTagSegment
import com.vitorpamplona.amethyst.service.ImageSegment
import com.vitorpamplona.amethyst.service.InvoiceSegment
import com.vitorpamplona.amethyst.service.LinkSegment
import com.vitorpamplona.amethyst.service.PhoneSegment
import com.vitorpamplona.amethyst.service.RegularTextSegment
import com.vitorpamplona.amethyst.service.RichTextViewerState
import com.vitorpamplona.amethyst.service.SchemelessUrlSegment
import com.vitorpamplona.amethyst.service.Segment
import com.vitorpamplona.amethyst.service.WithdrawSegment
import com.vitorpamplona.amethyst.ui.note.LoadUser
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.LoadedBechLink
import com.vitorpamplona.amethyst.ui.theme.Font17SP
import com.vitorpamplona.amethyst.ui.theme.HalfVertPadding
import com.vitorpamplona.amethyst.ui.theme.MarkdownTextStyle
import com.vitorpamplona.amethyst.ui.theme.innerPostModifier
import com.vitorpamplona.amethyst.ui.theme.markdownStyle
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.amethyst.ui.uriToRoute
import com.vitorpamplona.quartz.encoders.Nip19
import com.vitorpamplona.quartz.events.ImmutableListOfLists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.regex.Pattern

val imageExtensions = listOf("png", "jpg", "gif", "bmp", "jpeg", "webp", "svg")
val videoExtensions = listOf("mp4", "avi", "wmv", "mpg", "amv", "webm", "mov", "mp3", "m3u8")

val tagIndex = Pattern.compile("\\#\\[([0-9]+)\\](.*)")
val hashTagsPattern: Pattern = Pattern.compile("#([^\\s!@#\$%^&*()=+./,\\[{\\]};:'\"?><]+)(.*)", Pattern.CASE_INSENSITIVE)

fun isValidURL(url: String?): Boolean {
    return try {
        URL(url).toURI()
        true
    } catch (e: MalformedURLException) {
        false
    } catch (e: URISyntaxException) {
        false
    }
}

fun isMarkdown(content: String): Boolean {
    return content.startsWith("> ") ||
        content.startsWith("# ") ||
        content.contains("##") ||
        content.contains("__") ||
        content.contains("```") ||
        content.contains("](")
}

@Composable
fun RichTextViewer(
    content: String,
    canPreview: Boolean,
    modifier: Modifier,
    tags: ImmutableListOfLists<String>,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    Column(modifier = modifier) {
        if (remember(content) { isMarkdown(content) }) {
            RenderContentAsMarkdown(content, tags, accountViewModel, nav)
        } else {
            RenderRegular(content, tags, canPreview, backgroundColor, accountViewModel, nav)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderRegular(
    content: String,
    tags: ImmutableListOfLists<String>,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val state by remember(content) {
        mutableStateOf(CachedRichTextParser.parseText(content, tags))
    }

    val currentTextStyle = LocalTextStyle.current
    val currentTextColor = LocalContentColor.current

    val textStyle = remember(currentTextStyle, currentTextColor) {
        currentTextStyle.copy(
            lineHeight = 1.4.em,
            color = currentTextStyle.color.takeOrElse {
                currentTextColor
            }
        )
    }

    MeasureSpaceWidth() { spaceWidth ->
        Column() {
            if (canPreview) {
                // FlowRow doesn't work well with paragraphs. So we need to split them
                state.paragraphs.forEach { paragraph ->
                    val direction = if (paragraph.isRTL) {
                        LayoutDirection.Rtl
                    } else {
                        LayoutDirection.Ltr
                    }

                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        FlowRow(
                            modifier = Modifier.align(if (paragraph.isRTL) Alignment.End else Alignment.Start),
                            horizontalArrangement = Arrangement.spacedBy(spaceWidth)
                        ) {
                            paragraph.words.forEach { word ->
                                RenderWordWithPreview(
                                    word,
                                    state,
                                    backgroundColor,
                                    textStyle,
                                    accountViewModel,
                                    nav
                                )
                            }
                        }
                    }
                }
            } else {
                // FlowRow doesn't work well with paragraphs. So we need to split them
                state.paragraphs.forEach { paragraph ->
                    val direction = if (paragraph.isRTL) {
                        LayoutDirection.Rtl
                    } else {
                        LayoutDirection.Ltr
                    }

                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spaceWidth),
                            modifier = Modifier.align(if (paragraph.isRTL) Alignment.End else Alignment.Start)
                        ) {
                            paragraph.words.forEach { word ->
                                RenderWordWithoutPreview(
                                    word,
                                    state,
                                    backgroundColor,
                                    textStyle,
                                    accountViewModel,
                                    nav
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MeasureSpaceWidth(
    content: @Composable (measuredWidth: Dp) -> Unit
) {
    SubcomposeLayout { constraints ->
        val measuredWidth = subcompose("viewToMeasure", { Text(" ") })[0].measure(Constraints()).width.toDp()

        val contentPlaceable = subcompose("content") {
            content(measuredWidth)
        }[0].measure(constraints)
        layout(contentPlaceable.width, contentPlaceable.height) {
            contentPlaceable.place(0, 0)
        }
    }
}

@Composable
private fun RenderWordWithoutPreview(
    word: Segment,
    state: RichTextViewerState,
    backgroundColor: MutableState<Color>,
    style: TextStyle,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    when (word) {
        // Don't preview Images
        is ImageSegment -> ClickableUrl(word.segmentText, word.segmentText)
        is LinkSegment -> ClickableUrl(word.segmentText, word.segmentText)
        is EmojiSegment -> RenderCustomEmoji(word.segmentText, state)
        // Don't offer to pay invoices
        is InvoiceSegment -> NormalWord(word.segmentText, style)
        // Don't offer to withdraw
        is WithdrawSegment -> NormalWord(word.segmentText, style)
        is CashuSegment -> NormalWord(word.segmentText, style)
        is EmailSegment -> ClickableEmail(word.segmentText)
        is PhoneSegment -> ClickablePhone(word.segmentText)
        is BechSegment -> BechLink(word.segmentText, false, backgroundColor, accountViewModel, nav)
        is HashTagSegment -> HashTag(word, nav)
        is HashIndexUserSegment -> TagLink(word, accountViewModel, nav)
        is HashIndexEventSegment -> TagLink(word, false, backgroundColor, accountViewModel, nav)
        is SchemelessUrlSegment -> NoProtocolUrlRenderer(word)
        is RegularTextSegment -> NormalWord(word.segmentText, style)
    }
}

@Composable
private fun RenderWordWithPreview(
    word: Segment,
    state: RichTextViewerState,
    backgroundColor: MutableState<Color>,
    style: TextStyle,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    when (word) {
        is ImageSegment -> ZoomableContentView(word.segmentText, state, accountViewModel)
        is LinkSegment -> UrlPreview(word.segmentText, word.segmentText, accountViewModel)
        is EmojiSegment -> RenderCustomEmoji(word.segmentText, state)
        is InvoiceSegment -> MayBeInvoicePreview(word.segmentText)
        is WithdrawSegment -> MayBeWithdrawal(word.segmentText)
        is CashuSegment -> CashuPreview(word.segmentText, accountViewModel)
        is EmailSegment -> ClickableEmail(word.segmentText)
        is PhoneSegment -> ClickablePhone(word.segmentText)
        is BechSegment -> BechLink(word.segmentText, true, backgroundColor, accountViewModel, nav)
        is HashTagSegment -> HashTag(word, nav)
        is HashIndexUserSegment -> TagLink(word, accountViewModel, nav)
        is HashIndexEventSegment -> TagLink(word, true, backgroundColor, accountViewModel, nav)
        is SchemelessUrlSegment -> NoProtocolUrlRenderer(word)
        is RegularTextSegment -> NormalWord(word.segmentText, style)
    }
}

@Composable
private fun ZoomableContentView(
    word: String,
    state: RichTextViewerState,
    accountViewModel: AccountViewModel
) {
    state.imagesForPager[word]?.let {
        Box(modifier = HalfVertPadding) {
            ZoomableContentView(it, state.imageList, roundedCorner = true, accountViewModel)
        }
    }
}

@Composable
private fun NormalWord(word: String, style: TextStyle) {
    BasicText(
        text = word,
        style = style
    )
}

@Composable
private fun NoProtocolUrlRenderer(word: SchemelessUrlSegment) {
    RenderUrl(word)
}

@Composable
private fun RenderUrl(segment: SchemelessUrlSegment) {
    ClickableUrl(segment.url, "https://${segment.url}")
    segment.extras?.let { it1 -> Text(it1) }
}

@Composable
fun RenderCustomEmoji(word: String, state: RichTextViewerState) {
    CreateTextWithEmoji(
        text = word,
        emojis = state.customEmoji
    )
}

@Composable
private fun RenderContentAsMarkdown(content: String, tags: ImmutableListOfLists<String>?, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val uri = LocalUriHandler.current
    val onClick = remember {
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
        Material3RichText(
            style = MaterialTheme.colorScheme.markdownStyle
        ) {
            RefreshableContent(content, tags, accountViewModel) {
                Markdown(
                    content = it,
                    markdownParseOptions = MarkdownParseOptions.Default,
                    onLinkClicked = onClick
                )
            }
        }
    }
}

@Composable
private fun RefreshableContent(content: String, tags: ImmutableListOfLists<String>?, accountViewModel: AccountViewModel, onCompose: @Composable (String) -> Unit) {
    var markdownWithSpecialContent by remember(content) { mutableStateOf<String?>(content) }

    ObserverAllNIP19References(content, tags, accountViewModel) {
        accountViewModel.returnMarkdownWithSpecialContent(content, tags) { newMarkdownWithSpecialContent ->
            if (markdownWithSpecialContent != newMarkdownWithSpecialContent) {
                markdownWithSpecialContent = newMarkdownWithSpecialContent
            }
        }
    }

    markdownWithSpecialContent?.let {
        onCompose(it)
    }
}

@Composable
fun ObserverAllNIP19References(content: String, tags: ImmutableListOfLists<String>?, accountViewModel: AccountViewModel, onRefresh: () -> Unit) {
    var nip19References by remember(content) { mutableStateOf<List<Nip19.Return>>(emptyList()) }

    LaunchedEffect(key1 = content) {
        accountViewModel.returnNIP19References(content, tags) {
            nip19References = it
            onRefresh()
        }
    }

    nip19References.forEach {
        ObserveNIP19(it, accountViewModel, onRefresh)
    }
}

@Composable
fun ObserveNIP19(
    it: Nip19.Return,
    accountViewModel: AccountViewModel,
    onRefresh: () -> Unit
) {
    if (it.type == Nip19.Type.NOTE || it.type == Nip19.Type.EVENT || it.type == Nip19.Type.ADDRESS) {
        ObserveNIP19Event(it, accountViewModel, onRefresh)
    } else if (it.type == Nip19.Type.USER) {
        ObserveNIP19User(it, accountViewModel, onRefresh)
    }
}

@Composable
private fun ObserveNIP19Event(
    it: Nip19.Return,
    accountViewModel: AccountViewModel,
    onRefresh: () -> Unit
) {
    var baseNote by remember(it) { mutableStateOf<Note?>(accountViewModel.getNoteIfExists(it.hex)) }

    if (baseNote == null) {
        LaunchedEffect(key1 = it.hex) {
            if (it.type == Nip19.Type.NOTE || it.type == Nip19.Type.EVENT || it.type == Nip19.Type.ADDRESS) {
                accountViewModel.checkGetOrCreateNote(it.hex) { note ->
                    launch(Dispatchers.Main) { baseNote = note }
                }
            }
        }
    }

    baseNote?.let { note ->
        ObserveNote(note, onRefresh)
    }
}

@Composable
fun ObserveNote(note: Note, onRefresh: () -> Unit) {
    val loadedNoteId by note.live().metadata.observeAsState()

    LaunchedEffect(key1 = loadedNoteId) {
        if (loadedNoteId != null) {
            onRefresh()
        }
    }
}

@Composable
private fun ObserveNIP19User(
    it: Nip19.Return,
    accountViewModel: AccountViewModel,
    onRefresh: () -> Unit
) {
    var baseUser by remember(it) { mutableStateOf<User?>(accountViewModel.getUserIfExists(it.hex)) }

    if (baseUser == null) {
        LaunchedEffect(key1 = it.hex) {
            if (it.type == Nip19.Type.USER) {
                accountViewModel.checkGetOrCreateUser(it.hex)?.let { user ->
                    launch(Dispatchers.Main) { baseUser = user }
                }
            }
        }
    }

    baseUser?.let { user ->
        ObserveUser(user, onRefresh)
    }
}

@Composable
private fun ObserveUser(user: User, onRefresh: () -> Unit) {
    val loadedUserMetaId by user.live().metadata.observeAsState()

    LaunchedEffect(key1 = loadedUserMetaId) {
        if (loadedUserMetaId != null) {
            onRefresh()
        }
    }
}

@Composable
fun BechLink(word: String, canPreview: Boolean, backgroundColor: MutableState<Color>, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    var loadedLink by remember { mutableStateOf<LoadedBechLink?>(null) }

    if (loadedLink == null) {
        LaunchedEffect(key1 = word) {
            accountViewModel.parseNIP19(word) {
                loadedLink = it
            }
        }
    }

    if (canPreview && loadedLink?.baseNote != null) {
        Row() {
            DisplayFullNote(
                loadedLink?.baseNote!!,
                accountViewModel,
                backgroundColor,
                nav,
                loadedLink!!
            )
        }
    } else if (loadedLink?.nip19 != null) {
        Row() {
            ClickableRoute(loadedLink?.nip19!!, accountViewModel, nav)
        }
    } else {
        val text = remember(word) {
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
private fun DisplayFullNote(
    it: Note,
    accountViewModel: AccountViewModel,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
    loadedLink: LoadedBechLink
) {
    NoteCompose(
        baseNote = it,
        accountViewModel = accountViewModel,
        modifier = MaterialTheme.colorScheme.replyModifier,
        parentBackgroundColor = backgroundColor,
        isQuotedNote = true,
        nav = nav
    )

    val extraChars = remember(loadedLink) {
        loadedLink.nip19.additionalChars.ifBlank { null }
    }

    extraChars?.let {
        Text(
            it
        )
    }
}

@Composable
fun HashTag(word: HashTagSegment, nav: (String) -> Unit) {
    RenderHashtag(word, nav)
}

@Composable
private fun RenderHashtag(
    segment: HashTagSegment,
    nav: (String) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val hashtagIcon = remember(segment.hashtag) {
        checkForHashtagWithIcon(segment.hashtag, primary)
    }

    val regularText =
        SpanStyle(color = MaterialTheme.colorScheme.onBackground)

    val clickableTextStyle =
        SpanStyle(color = primary)

    val annotatedTermsString = buildAnnotatedString {
        withStyle(clickableTextStyle) {
            pushStringAnnotation("routeToHashtag", "")
            append("#${segment.hashtag}")
        }

        if (hashtagIcon != null) {
            withStyle(clickableTextStyle) {
                pushStringAnnotation("routeToHashtag", "")
                appendInlineContent("inlineContent", "[icon]")
            }
        }

        segment.extras?.ifBlank { "" }?.let {
            withStyle(regularText) {
                append(it)
            }
        }
    }

    val inlineContent = if (hashtagIcon != null) {
        mapOf("inlineContent" to InlineIcon(hashtagIcon))
    } else {
        emptyMap()
    }

    val pressIndicator = Modifier.clickable {
        nav("Hashtag/${segment.hashtag}")
    }

    Text(
        text = annotatedTermsString,
        modifier = pressIndicator,
        inlineContent = inlineContent
    )
}

@Composable
private fun InlineIcon(hashtagIcon: HashtagIcon) =
    InlineTextContent(
        Placeholder(
            width = Font17SP,
            height = Font17SP,
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
        )
    ) {
        Icon(
            painter = painterResource(hashtagIcon.icon),
            contentDescription = hashtagIcon.description,
            tint = hashtagIcon.color,
            modifier = hashtagIcon.modifier
        )
    }

@Composable
fun TagLink(word: HashIndexUserSegment, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    LoadUser(baseUserHex = word.hex, accountViewModel) {
        if (it == null) {
            Text(text = word.segmentText)
        } else {
            Row() {
                DisplayUserFromTag(it, word.extras, nav)
            }
        }
    }
}

@Composable
fun LoadNote(baseNoteHex: String, accountViewModel: AccountViewModel, content: @Composable (Note?) -> Unit) {
    var note by remember(baseNoteHex) {
        mutableStateOf<Note?>(accountViewModel.getNoteIfExists(baseNoteHex))
    }

    if (note == null) {
        LaunchedEffect(key1 = baseNoteHex) {
            accountViewModel.checkGetOrCreateNote(baseNoteHex) {
                note = it
            }
        }
    }

    content(note)
}

@Composable
fun TagLink(word: HashIndexEventSegment, canPreview: Boolean, backgroundColor: MutableState<Color>, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    LoadNote(baseNoteHex = word.hex, accountViewModel) {
        if (it == null) {
            Text(text = remember { word.segmentText.toShortenHex() })
        } else {
            Row() {
                DisplayNoteFromTag(
                    it,
                    word.extras,
                    canPreview,
                    accountViewModel,
                    backgroundColor,
                    nav
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
    accountViewModel: AccountViewModel,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit
) {
    if (canPreview) {
        NoteCompose(
            baseNote = baseNote,
            accountViewModel = accountViewModel,
            modifier = MaterialTheme.colorScheme.innerPostModifier,
            parentBackgroundColor = backgroundColor,
            isQuotedNote = true,
            nav = nav
        )
    } else {
        ClickableNoteTag(baseNote, nav)
    }

    addedChars?.ifBlank { null }?.let {
        Text(text = it)
    }
}

@Composable
private fun DisplayUserFromTag(
    baseUser: User,
    addedChars: String?,
    nav: (String) -> Unit
) {
    val route = remember { "User/${baseUser.pubkeyHex}" }
    val hex = remember { baseUser.pubkeyDisplayHex() }

    val meta by baseUser.live().userMetadataInfo.observeAsState(baseUser.info)

    Crossfade(targetState = meta) {
        Row() {
            val displayName = remember(it) {
                it?.bestDisplayName() ?: it?.bestUsername() ?: hex
            }
            CreateClickableTextWithEmoji(
                clickablePart = displayName,
                suffix = addedChars,
                maxLines = 1,
                route = route,
                nav = nav,
                tags = it?.tags
            )
        }
    }
}
