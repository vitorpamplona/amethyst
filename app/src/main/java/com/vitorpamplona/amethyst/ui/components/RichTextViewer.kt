package com.vitorpamplona.amethyst.ui.components

import android.util.Log
import android.util.Patterns
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.markdown.MarkdownParseOptions
import com.halilibo.richtext.ui.material.MaterialRichText
import com.vitorpamplona.amethyst.model.HashtagIcon
import com.vitorpamplona.amethyst.model.LocalCache
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
import com.vitorpamplona.amethyst.service.nip19.Nip19
import com.vitorpamplona.amethyst.ui.actions.ImmutableListOfLists
import com.vitorpamplona.amethyst.ui.note.LoadUser
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Font17SP
import com.vitorpamplona.amethyst.ui.theme.MarkdownTextStyle
import com.vitorpamplona.amethyst.ui.theme.innerPostModifier
import com.vitorpamplona.amethyst.ui.theme.markdownStyle
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.amethyst.ui.uriToRoute
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
    showImage: MutableState<Boolean>,
    automaticallyStartPlayback: MutableState<Boolean>,
    automaticallyShowUrlPreview: MutableState<Boolean>,
    nav: (String) -> Unit
) {
    Column(modifier = modifier) {
        if (remember(content) { isMarkdown(content) }) {
            RenderContentAsMarkdown(content, tags, nav)
        } else {
            RenderRegular(content, tags, canPreview, backgroundColor, accountViewModel, showImage, automaticallyStartPlayback, automaticallyShowUrlPreview, nav)
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
    showImage: MutableState<Boolean>,
    automaticallyStartPlayback: MutableState<Boolean>,
    automaticallyShowUrlPreview: MutableState<Boolean>,
    nav: (String) -> Unit
) {
    val state by remember(content) {
        mutableStateOf(CachedRichTextParser.parseText(content, tags))
    }

    val currentTextStyle = LocalTextStyle.current

    val textStyle = currentTextStyle.copy(
        textDirection = TextDirection.Content,
        lineHeight = 1.4.em,
        color = currentTextStyle.color.takeOrElse {
            LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
        }
    )

    MeasureSpaceWidth() { spaceWidth ->
        Column {
            if (canPreview) {
                // FlowRow doesn't work well with paragraphs. So we need to split them
                state.paragraphs.forEach { paragraph ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spaceWidth)) {
                        paragraph.forEach { word ->
                            RenderWordWithPreview(
                                word,
                                state,
                                backgroundColor,
                                textStyle,
                                accountViewModel,
                                automaticallyStartPlayback,
                                automaticallyShowUrlPreview,
                                nav
                            )
                        }
                    }
                }
            } else {
                // FlowRow doesn't work well with paragraphs. So we need to split them
                state.paragraphs.forEach { paragraph ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spaceWidth)) {
                        paragraph.forEach { word ->
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
        is HashIndexUserSegment -> TagLink(word, nav)
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
    automaticallyStartPlayback: MutableState<Boolean>,
    automaticallyShowUrlPreview: MutableState<Boolean>,
    nav: (String) -> Unit
) {
    when (word) {
        is ImageSegment -> ZoomableContentView(word.segmentText, state, accountViewModel, automaticallyStartPlayback)
        is LinkSegment -> UrlPreview(word.segmentText, word.segmentText, automaticallyShowUrlPreview)
        is EmojiSegment -> RenderCustomEmoji(word.segmentText, state)
        is InvoiceSegment -> MayBeInvoicePreview(word.segmentText)
        is WithdrawSegment -> MayBeWithdrawal(word.segmentText)
        is CashuSegment -> CashuPreview(word.segmentText, accountViewModel)
        is EmailSegment -> ClickableEmail(word.segmentText)
        is PhoneSegment -> ClickablePhone(word.segmentText)
        is BechSegment -> BechLink(word.segmentText, true, backgroundColor, accountViewModel, nav)
        is HashTagSegment -> HashTag(word, nav)
        is HashIndexUserSegment -> TagLink(word, nav)
        is HashIndexEventSegment -> TagLink(word, true, backgroundColor, accountViewModel, nav)
        is SchemelessUrlSegment -> NoProtocolUrlRenderer(word)
        is RegularTextSegment -> NormalWord(word.segmentText, style)
    }
}

@Composable
private fun ZoomableContentView(
    word: String,
    state: RichTextViewerState,
    accountViewModel: AccountViewModel,
    automaticallyStartPlayback: MutableState<Boolean>
) {
    state.imagesForPager[word]?.let {
        ZoomableContentView(it, state.imageList, accountViewModel, automaticallyStartPlayback)
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
    Row() {
        ClickableUrl(segment.url, "https://${segment.url}")
        segment.extras?.let { it1 -> Text(it1) }
    }
}

@Composable
fun RenderCustomEmoji(word: String, state: RichTextViewerState) {
    CreateTextWithEmoji(
        text = word,
        emojis = state.customEmoji
    )
}

@Composable
private fun RenderContentAsMarkdown(content: String, tags: ImmutableListOfLists<String>?, nav: (String) -> Unit) {
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
        MaterialRichText(
            style = MaterialTheme.colors.markdownStyle
        ) {
            RefreshableContent(content, tags) {
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
private fun RefreshableContent(content: String, tags: ImmutableListOfLists<String>?, onCompose: @Composable (String) -> Unit) {
    var markdownWithSpecialContent by remember(content) { mutableStateOf<String?>(content) }

    ObserverAllNIP19References(content, tags) {
        val newMarkdownWithSpecialContent = returnMarkdownWithSpecialContent(content, tags)
        if (markdownWithSpecialContent != newMarkdownWithSpecialContent) {
            markdownWithSpecialContent = newMarkdownWithSpecialContent
        }
    }

    markdownWithSpecialContent?.let {
        onCompose(it)
    }
}

@Composable
fun ObserverAllNIP19References(content: String, tags: ImmutableListOfLists<String>?, onRefresh: () -> Unit) {
    var nip19References by remember(content) { mutableStateOf<List<Nip19.Return>>(emptyList()) }

    LaunchedEffect(key1 = content) {
        launch(Dispatchers.IO) {
            nip19References = returnNIP19References(content, tags)
            onRefresh()
        }
    }

    nip19References.forEach {
        ObserveNIP19(it, onRefresh)
    }
}

@Composable
fun ObserveNIP19(
    it: Nip19.Return,
    onRefresh: () -> Unit
) {
    if (it.type == Nip19.Type.NOTE || it.type == Nip19.Type.EVENT || it.type == Nip19.Type.ADDRESS) {
        ObserveNIP19Event(it, onRefresh)
    } else if (it.type == Nip19.Type.USER) {
        ObserveNIP19User(it, onRefresh)
    }
}

@Composable
private fun ObserveNIP19Event(
    it: Nip19.Return,
    onRefresh: () -> Unit
) {
    var baseNote by remember(it) { mutableStateOf<Note?>(LocalCache.getNoteIfExists(it.hex)) }

    if (baseNote == null) {
        LaunchedEffect(key1 = it.hex) {
            launch(Dispatchers.IO) {
                if (it.type == Nip19.Type.NOTE || it.type == Nip19.Type.EVENT || it.type == Nip19.Type.ADDRESS) {
                    LocalCache.checkGetOrCreateNote(it.hex)?.let { note ->
                        launch(Dispatchers.Main) { baseNote = note }
                    }
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
    val loadedNoteId by note.live().metadata.map {
        it.note.event?.id()
    }.distinctUntilChanged().observeAsState(note.event?.id())

    LaunchedEffect(key1 = loadedNoteId) {
        if (loadedNoteId != null) {
            launch(Dispatchers.IO) {
                onRefresh()
            }
        }
    }
}

@Composable
private fun ObserveNIP19User(
    it: Nip19.Return,
    onRefresh: () -> Unit
) {
    var baseUser by remember(it) { mutableStateOf<User?>(LocalCache.getUserIfExists(it.hex)) }

    if (baseUser == null) {
        LaunchedEffect(key1 = it.hex) {
            launch(Dispatchers.IO) {
                if (it.type == Nip19.Type.USER) {
                    LocalCache.checkGetOrCreateUser(it.hex)?.let { user ->
                        launch(Dispatchers.Main) { baseUser = user }
                    }
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
    val loadedUserMetaId by user.live().metadata.map {
        it.user.info?.latestMetadata?.id
    }.distinctUntilChanged().observeAsState(user.info?.latestMetadata?.id)

    LaunchedEffect(key1 = loadedUserMetaId) {
        if (loadedUserMetaId != null) {
            launch(Dispatchers.IO) {
                onRefresh()
            }
        }
    }
}

private fun getDisplayNameAndNIP19FromTag(tag: String, tags: ImmutableListOfLists<String>): Pair<String, String>? {
    val matcher = tagIndex.matcher(tag)
    val (index, suffix) = try {
        matcher.find()
        Pair(matcher.group(1)?.toInt(), matcher.group(2) ?: "")
    } catch (e: Exception) {
        Log.w("Tag Parser", "Couldn't link tag $tag", e)
        Pair(null, null)
    }

    if (index != null && index >= 0 && index < tags.lists.size) {
        val tag = tags.lists[index]

        if (tag.size > 1) {
            if (tag[0] == "p") {
                LocalCache.checkGetOrCreateUser(tag[1])?.let {
                    return Pair(it.toBestDisplayName(), it.pubkeyNpub())
                }
            } else if (tag[0] == "e" || tag[0] == "a") {
                LocalCache.checkGetOrCreateNote(tag[1])?.let {
                    return Pair(it.idDisplayNote(), it.toNEvent())
                }
            }
        }
    }

    return null
}

private fun getDisplayNameFromNip19(nip19: Nip19.Return): Pair<String, String>? {
    if (nip19.type == Nip19.Type.USER) {
        LocalCache.users[nip19.hex]?.let {
            return Pair(it.toBestDisplayName(), it.pubkeyNpub())
        }
    } else if (nip19.type == Nip19.Type.NOTE) {
        LocalCache.notes[nip19.hex]?.let {
            return Pair(it.idDisplayNote(), it.toNEvent())
        }
    } else if (nip19.type == Nip19.Type.ADDRESS) {
        LocalCache.addressables[nip19.hex]?.let {
            return Pair(it.idDisplayNote(), it.toNEvent())
        }
    } else if (nip19.type == Nip19.Type.EVENT) {
        LocalCache.notes[nip19.hex]?.let {
            return Pair(it.idDisplayNote(), it.toNEvent())
        }
    }

    return null
}

private fun returnNIP19References(content: String, tags: ImmutableListOfLists<String>?): List<Nip19.Return> {
    val listOfReferences = mutableListOf<Nip19.Return>()
    content.split('\n').forEach { paragraph ->
        paragraph.split(' ').forEach { word: String ->
            if (startsWithNIP19Scheme(word)) {
                val parsedNip19 = Nip19.uriToRoute(word)
                parsedNip19?.let {
                    listOfReferences.add(it)
                }
            }
        }
    }

    tags?.lists?.forEach {
        if (it[0] == "p" && it.size > 1) {
            listOfReferences.add(Nip19.Return(Nip19.Type.USER, it[1], null, null, null, ""))
        } else if (it[0] == "e" && it.size > 1) {
            listOfReferences.add(Nip19.Return(Nip19.Type.NOTE, it[1], null, null, null, ""))
        } else if (it[0] == "a" && it.size > 1) {
            listOfReferences.add(Nip19.Return(Nip19.Type.ADDRESS, it[1], null, null, null, ""))
        }
    }

    return listOfReferences
}

private fun returnMarkdownWithSpecialContent(content: String, tags: ImmutableListOfLists<String>?): String {
    var returnContent = ""
    content.split('\n').forEach { paragraph ->
        paragraph.split(' ').forEach { word: String ->
            if (isValidURL(word)) {
                val removedParamsFromUrl = word.split("?")[0].lowercase()
                if (imageExtensions.any { removedParamsFromUrl.endsWith(it) }) {
                    returnContent += "![]($word) "
                } else {
                    returnContent += "[$word]($word) "
                }
            } else if (Patterns.EMAIL_ADDRESS.matcher(word).matches()) {
                returnContent += "[$word](mailto:$word) "
            } else if (Patterns.PHONE.matcher(word).matches() && word.length > 6) {
                returnContent += "[$word](tel:$word) "
            } else if (startsWithNIP19Scheme(word)) {
                val parsedNip19 = Nip19.uriToRoute(word)
                returnContent += if (parsedNip19 !== null) {
                    val pair = getDisplayNameFromNip19(parsedNip19)
                    if (pair != null) {
                        val (displayName, nip19) = pair
                        "[$displayName](nostr:$nip19) "
                    } else {
                        "$word "
                    }
                } else {
                    "$word "
                }
            } else if (word.startsWith("#")) {
                if (tagIndex.matcher(word).matches() && tags != null) {
                    val pair = getDisplayNameAndNIP19FromTag(word, tags)
                    if (pair != null) {
                        returnContent += "[${pair.first}](nostr:${pair.second}) "
                    } else {
                        returnContent += "$word "
                    }
                } else if (hashTagsPattern.matcher(word).matches()) {
                    val hashtagMatcher = hashTagsPattern.matcher(word)

                    val (myTag, mySuffix) = try {
                        hashtagMatcher.find()
                        Pair(hashtagMatcher.group(1), hashtagMatcher.group(2))
                    } catch (e: Exception) {
                        Log.e("Hashtag Parser", "Couldn't link hashtag $word", e)
                        Pair(null, null)
                    }

                    if (myTag != null) {
                        returnContent += "[#$myTag](nostr:Hashtag?id=$myTag)$mySuffix "
                    } else {
                        returnContent += "$word "
                    }
                } else {
                    returnContent += "$word "
                }
            } else {
                returnContent += "$word "
            }
        }
        returnContent += "\n"
    }
    return returnContent
}

fun startsWithNIP19Scheme(word: String): Boolean {
    val cleaned = word.lowercase().removePrefix("@").removePrefix("nostr:").removePrefix("@")

    return listOf("npub1", "naddr1", "note1", "nprofile1", "nevent1").any { cleaned.startsWith(it) }
}

@Immutable
data class LoadedBechLink(val baseNote: Note?, val nip19: Nip19.Return)

@Composable
fun BechLink(word: String, canPreview: Boolean, backgroundColor: MutableState<Color>, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    var loadedLink by remember { mutableStateOf<LoadedBechLink?>(null) }

    if (loadedLink == null) {
        LaunchedEffect(key1 = word) {
            launch(Dispatchers.IO) {
                Nip19.uriToRoute(word)?.let {
                    var returningNote: Note? = null
                    if (it.type == Nip19.Type.NOTE || it.type == Nip19.Type.EVENT || it.type == Nip19.Type.ADDRESS) {
                        LocalCache.checkGetOrCreateNote(it.hex)?.let { note ->
                            returningNote = note
                        }
                    }

                    val newLink = LoadedBechLink(returningNote, it)

                    launch(Dispatchers.Main) {
                        loadedLink = newLink
                    }
                }
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
            ClickableRoute(loadedLink?.nip19!!, nav)
        }
    } else {
        val text = remember {
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
        modifier = MaterialTheme.colors.replyModifier,
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
    Row() {
        RenderHashtag(word, nav)
    }
}

@Composable
private fun RenderHashtag(
    segment: HashTagSegment,
    nav: (String) -> Unit
) {
    val primary = MaterialTheme.colors.primary
    val hashtagIcon = remember(segment.hashtag) { checkForHashtagWithIcon(segment.hashtag, primary) }
    ClickableText(
        text = buildAnnotatedString {
            withStyle(
                LocalTextStyle.current.copy(color = MaterialTheme.colors.primary).toSpanStyle()
            ) {
                append("#${segment.hashtag}")
            }
        },
        onClick = { nav("Hashtag/${segment.hashtag}") }
    )

    if (hashtagIcon != null) {
        val myId = "inlineContent"
        val emptytext = buildAnnotatedString {
            withStyle(
                LocalTextStyle.current.copy(color = MaterialTheme.colors.primary).toSpanStyle()
            ) {
                append("")
                appendInlineContent(myId, "[icon]")
            }
        }
        // Empty Text for Size of Icon
        Text(
            text = emptytext,
            inlineContent = mapOf(
                myId to InlineIcon(hashtagIcon)
            )
        )
    }
    segment.extras?.ifBlank { "" }?.let {
        Text(text = it)
    }
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
fun TagLink(word: HashIndexUserSegment, nav: (String) -> Unit) {
    LoadUser(baseUserHex = word.hex) {
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
fun LoadNote(baseNoteHex: String, content: @Composable (Note?) -> Unit) {
    var note by remember(baseNoteHex) {
        mutableStateOf<Note?>(LocalCache.getNoteIfExists(baseNoteHex))
    }

    if (note == null) {
        LaunchedEffect(key1 = baseNoteHex) {
            launch(Dispatchers.IO) {
                note = LocalCache.checkGetOrCreateNote(baseNoteHex)
            }
        }
    }

    content(note)
}

@Composable
fun TagLink(word: HashIndexEventSegment, canPreview: Boolean, backgroundColor: MutableState<Color>, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    LoadNote(baseNoteHex = word.hex) {
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
            modifier = MaterialTheme.colors.innerPostModifier,
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

    val meta by baseUser.live().metadata.map {
        it.user.info
    }.distinctUntilChanged().observeAsState(baseUser.info)

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
