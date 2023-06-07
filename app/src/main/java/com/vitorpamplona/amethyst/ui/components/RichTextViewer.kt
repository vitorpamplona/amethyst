package com.vitorpamplona.amethyst.ui.components

import android.util.Log
import android.util.LruCache
import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.Icon
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.markdown.MarkdownParseOptions
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material.MaterialRichText
import com.halilibo.richtext.ui.resolveDefaults
import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.checkForHashtagWithIcon
import com.vitorpamplona.amethyst.service.nip19.Nip19
import com.vitorpamplona.amethyst.ui.actions.ImmutableListOfLists
import com.vitorpamplona.amethyst.ui.actions.toImmutableListOfLists
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.uriToRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.regex.Pattern

val imageExtensions = listOf("png", "jpg", "gif", "bmp", "jpeg", "webp", "svg")
val videoExtensions = listOf("mp4", "avi", "wmv", "mpg", "amv", "webm", "mov", "mp3")

// Group 1 = url, group 4 additional chars
val noProtocolUrlValidator = Pattern.compile("(([\\w\\d-]+\\.)*[a-zA-Z][\\w-]+[\\.\\:]\\w+([\\/\\?\\=\\&\\#\\.]?[\\w-]+)*\\/?)(.*)")

val tagIndex = Pattern.compile("\\#\\[([0-9]+)\\](.*)")

val mentionsPattern: Pattern = Pattern.compile("@([A-Za-z0-9_\\-]+)")
val hashTagsPattern: Pattern = Pattern.compile("#([^\\s!@#\$%^&*()=+./,\\[{\\]};:'\"?><]+)(.*)", Pattern.CASE_INSENSITIVE)
val urlPattern: Pattern = Patterns.WEB_URL

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

val richTextDefaults = RichTextStyle().resolveDefaults()

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
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    Column(modifier = modifier) {
        if (remember(content) { isMarkdown(content) }) {
            RenderContentAsMarkdown(content, backgroundColor, tags, nav)
        } else {
            RenderRegular(content, tags, canPreview, backgroundColor, accountViewModel, nav)
        }
    }
}

val urlSetCache = LruCache<String, RichTextViewerState>(200)

@Immutable
data class RichTextViewerState(
    val urlSet: ImmutableSet<String>,
    val imagesForPager: ImmutableMap<String, ZoomableUrlContent>,
    val imageList: ImmutableList<ZoomableUrlContent>,
    val customEmoji: ImmutableMap<String, String>
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderRegular(
    content: String,
    tags: ImmutableListOfLists<String>,
    canPreview: Boolean,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val state by remember(content) {
        if (urlSetCache[content] != null) {
            mutableStateOf(urlSetCache[content])
        } else {
            val newUrls = parseUrls(content, tags)
            urlSetCache.put(content, newUrls)
            mutableStateOf(newUrls)
        }
    }

    val paragraphs = remember(content) {
        content.split('\n').toImmutableList()
    }

    // FlowRow doesn't work well with paragraphs. So we need to split them
    paragraphs.forEach { paragraph ->
        RenderParagraph(paragraph, state, canPreview, backgroundColor, accountViewModel, nav, tags)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RenderParagraph(
    paragraph: String,
    state: RichTextViewerState,
    canPreview: Boolean,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    tags: ImmutableListOfLists<String>
) {
    val s = remember(paragraph) {
        if (isArabic(paragraph)) {
            paragraph.trim().split(' ').reversed().toImmutableList()
        } else {
            paragraph.trim().split(' ').toImmutableList()
        }
    }

    FlowRow() {
        s.forEach { word: String ->
            RenderWord(
                word,
                state,
                canPreview,
                backgroundColor,
                accountViewModel,
                nav,
                tags
            )
        }
    }
}

private fun parseUrls(
    content: String,
    tags: ImmutableListOfLists<String>
): RichTextViewerState {
    val urls = UrlDetector(content, UrlDetectorOptions.Default).detect()
    val urlSet = urls.mapTo(LinkedHashSet(urls.size)) { it.originalUrl }
    val imagesForPager = urlSet.mapNotNull { fullUrl ->
        val removedParamsFromUrl = fullUrl.split("?")[0].lowercase()
        if (imageExtensions.any { removedParamsFromUrl.endsWith(it) }) {
            ZoomableUrlImage(fullUrl)
        } else if (videoExtensions.any { removedParamsFromUrl.endsWith(it) }) {
            ZoomableUrlVideo(fullUrl)
        } else {
            null
        }
    }.associateBy { it.url }
    val imageList = imagesForPager.values.toList()

    val emojiMap =
        tags.lists.filter { it.size > 2 && it[0] == "emoji" }.associate { ":${it[1]}:" to it[2] }

    return if (urlSet.isNotEmpty() || emojiMap.isNotEmpty()) {
        RichTextViewerState(
            urlSet.toImmutableSet(),
            imagesForPager.toImmutableMap(),
            imageList.toImmutableList(),
            emojiMap.toImmutableMap()
        )
    } else {
        RichTextViewerState(
            persistentSetOf(),
            persistentMapOf(),
            persistentListOf(),
            persistentMapOf()
        )
    }
}

enum class WordType {
    IMAGE, LINK, EMOJI, INVOICE, WITHDRAW, EMAIL, PHONE, BECH, HASH_INDEX, HASHTAG, SCHEMELESS_URL, OTHER
}

@Composable
private fun RenderWord(
    word: String,
    state: RichTextViewerState,
    canPreview: Boolean,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    tags: ImmutableListOfLists<String>
) {
    val type = remember(word) {
        if (state.imagesForPager[word] != null) {
            WordType.IMAGE
        } else if (state.urlSet.contains(word)) {
            WordType.LINK
        } else if (state.customEmoji.any { word.contains(it.key) }) {
            WordType.EMOJI
        } else if (word.startsWith("lnbc", true)) {
            WordType.INVOICE
        } else if (word.startsWith("lnurl", true)) {
            WordType.WITHDRAW
        } else if (Patterns.EMAIL_ADDRESS.matcher(word).matches()) {
            WordType.EMAIL
        } else if (word.length > 6 && Patterns.PHONE.matcher(word).matches()) {
            WordType.PHONE
        } else if (startsWithNIP19Scheme(word)) {
            WordType.BECH
        } else if (word.startsWith("#")) {
            if (tagIndex.matcher(word).matches()) {
                if (tags.lists.isNotEmpty()) {
                    WordType.HASH_INDEX
                } else {
                    WordType.OTHER
                }
            } else if (hashTagsPattern.matcher(word).matches()) {
                WordType.HASHTAG
            } else {
                WordType.OTHER
            }
        } else if (noProtocolUrlValidator.matcher(word).matches()) {
            WordType.SCHEMELESS_URL
        } else {
            WordType.OTHER
        }
    }

    if (canPreview) {
        RenderWordWithPreview(type, word, state, tags, backgroundColor, accountViewModel, nav)
    } else {
        RenderWordWithoutPreview(type, word, state, tags, backgroundColor, accountViewModel, nav)
    }
}

@Composable
private fun RenderWordWithoutPreview(
    type: WordType,
    word: String,
    state: RichTextViewerState,
    tags: ImmutableListOfLists<String>,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val wordSpace = remember(word) {
        "$word "
    }

    when (type) {
        // Don't preview Images
        WordType.IMAGE -> UrlPreview(word, wordSpace)
        WordType.LINK -> UrlPreview(word, wordSpace)
        WordType.EMOJI -> RenderCustomEmoji(word, state)
        // Don't offer to pay invoices
        WordType.INVOICE -> NormalWord(wordSpace)
        // Don't offer to withdraw
        WordType.WITHDRAW -> NormalWord(wordSpace)
        WordType.EMAIL -> ClickableEmail(word)
        WordType.PHONE -> ClickablePhone(word)
        WordType.BECH -> BechLink(word, false, backgroundColor, accountViewModel, nav)
        WordType.HASHTAG -> HashTag(word, nav)
        WordType.HASH_INDEX -> TagLink(word, tags, false, backgroundColor, accountViewModel, nav)
        WordType.SCHEMELESS_URL -> NoProtocolUrlRenderer(word)
        WordType.OTHER -> NormalWord(wordSpace)
    }
}

@Composable
private fun RenderWordWithPreview(
    type: WordType,
    word: String,
    state: RichTextViewerState,
    tags: ImmutableListOfLists<String>,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val wordSpace = remember(word) {
        "$word "
    }

    when (type) {
        WordType.IMAGE -> ZoomableContentView(word, state)
        WordType.LINK -> UrlPreview(word, wordSpace)
        WordType.EMOJI -> RenderCustomEmoji(word, state)
        WordType.INVOICE -> MayBeInvoicePreview(word)
        WordType.WITHDRAW -> MayBeWithdrawal(word)
        WordType.EMAIL -> ClickableEmail(word)
        WordType.PHONE -> ClickablePhone(word)
        WordType.BECH -> BechLink(word, true, backgroundColor, accountViewModel, nav)
        WordType.HASHTAG -> HashTag(word, nav)
        WordType.HASH_INDEX -> TagLink(word, tags, true, backgroundColor, accountViewModel, nav)
        WordType.SCHEMELESS_URL -> NoProtocolUrlRenderer(word)
        WordType.OTHER -> NormalWord(wordSpace)
    }
}

@Composable
private fun ZoomableContentView(word: String, state: RichTextViewerState) {
    state.imagesForPager[word]?.let {
        ZoomableContentView(it, state.imageList)
    }
}

@Composable
private fun NormalWord(word: String) {
    Text(
        text = word,
        style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
    )
}

@Composable
private fun NoProtocolUrlRenderer(word: String) {
    val wordSpace = remember(word) {
        "$word "
    }

    var linkedUrl by remember(word) {
        mutableStateOf<Pair<String, String>?>(null)
    }

    LaunchedEffect(key1 = word) {
        launch(Dispatchers.Default) {
            val matcher = noProtocolUrlValidator.matcher(word)
            if (matcher.find()) {
                val url = matcher.group(1) // url
                val additionalChars = matcher.group(4) ?: "" // additional chars

                linkedUrl = Pair(url, additionalChars)
            }
        }
    }

    if (linkedUrl != null) {
        ClickableUrl(linkedUrl!!.first, "https://${linkedUrl!!.first}")
        Text("${linkedUrl!!.second} ")
    } else {
        Text(
            text = wordSpace,
            style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
        )
    }
}

@Composable
fun RenderCustomEmoji(word: String, state: RichTextViewerState) {
    CreateTextWithEmoji(
        text = remember { "$word " },
        emojis = state.customEmoji
    )
}

@Composable
private fun RenderContentAsMarkdown(content: String, backgroundColor: Color, tags: ImmutableListOfLists<String>?, nav: (String) -> Unit) {
    val myMarkDownStyle = richTextDefaults.copy(
        codeBlockStyle = richTextDefaults.codeBlockStyle?.copy(
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            ),
            modifier = Modifier
                .padding(0.dp)
                .fillMaxWidth()
                .clip(shape = RoundedCornerShape(15.dp))
                .border(
                    1.dp,
                    MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                    RoundedCornerShape(15.dp)
                )
                .background(
                    MaterialTheme.colors.onSurface
                        .copy(alpha = 0.05f)
                        .compositeOver(backgroundColor)
                )
        ),
        stringStyle = richTextDefaults.stringStyle?.copy(
            linkStyle = SpanStyle(
                color = MaterialTheme.colors.primary
            ),
            codeStyle = SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                background = MaterialTheme.colors.onSurface.copy(alpha = 0.22f)
                    .compositeOver(backgroundColor)
            )
        )
    )

    val uri = LocalUriHandler.current
    val onClick = { link: String ->
        val route = uriToRoute(link)
        if (route != null) {
            nav(route)
        } else {
            runCatching { uri.openUri(link) }
        }
        Unit
    }

    MaterialRichText(
        style = myMarkDownStyle
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
    var baseNote by remember(it) { mutableStateOf<Note?>(null) }

    LaunchedEffect(key1 = it.hex) {
        if (baseNote == null) {
            launch(Dispatchers.IO) {
                if (it.type == Nip19.Type.NOTE || it.type == Nip19.Type.EVENT || it.type == Nip19.Type.ADDRESS) {
                    LocalCache.checkGetOrCreateNote(it.hex)?.let { note ->
                        baseNote = note
                    }
                }
            }
        }
    }

    baseNote?.let { note ->
        val noteState by note.live().metadata.observeAsState()

        LaunchedEffect(key1 = noteState) {
            if (noteState?.note?.event != null) {
                launch(Dispatchers.IO) {
                    onRefresh()
                }
            }
        }
    }
}

@Composable
private fun ObserveNIP19User(
    it: Nip19.Return,
    onRefresh: () -> Unit
) {
    var baseUser by remember(it) { mutableStateOf<User?>(null) }

    LaunchedEffect(key1 = it.hex) {
        if (baseUser == null) {
            launch(Dispatchers.IO) {
                if (it.type == Nip19.Type.USER) {
                    LocalCache.checkGetOrCreateUser(it.hex)?.let { user ->
                        baseUser = user
                    }
                }
            }
        }
    }

    baseUser?.let { user ->
        val userState by user.live().metadata.observeAsState()

        LaunchedEffect(key1 = userState) {
            if (userState?.user?.info != null) {
                launch(Dispatchers.IO) {
                    onRefresh()
                }
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
                    returnContent += "$word "
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

private fun isArabic(text: String): Boolean {
    return text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }
}

fun startsWithNIP19Scheme(word: String): Boolean {
    val cleaned = word.lowercase().removePrefix("@").removePrefix("nostr:").removePrefix("@")

    return listOf("npub1", "naddr1", "note1", "nprofile1", "nevent1").any { cleaned.startsWith(it) }
}

@Immutable
data class LoadedBechLink(val baseNote: Note?, val nip19: Nip19.Return)

@Composable
fun BechLink(word: String, canPreview: Boolean, backgroundColor: Color, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    var loadedLink by remember { mutableStateOf<LoadedBechLink?>(null) }

    LaunchedEffect(key1 = word) {
        launch(Dispatchers.IO) {
            Nip19.uriToRoute(word)?.let {
                var returningNote: Note? = null
                if (it.type == Nip19.Type.NOTE || it.type == Nip19.Type.EVENT || it.type == Nip19.Type.ADDRESS) {
                    LocalCache.checkGetOrCreateNote(it.hex)?.let { note ->
                        returningNote = note
                    }
                }

                loadedLink = LoadedBechLink(returningNote, it)
            }
        }
    }

    if (canPreview) {
        loadedLink?.let { loadedLink ->
            loadedLink.baseNote?.let {
                DisplayFullNote(it, accountViewModel, backgroundColor, nav, loadedLink)
            } ?: run {
                ClickableRoute(loadedLink.nip19, nav)
            }
        } ?: run {
            Text(text = remember { "$word " })
        }
    } else {
        loadedLink?.let {
            ClickableRoute(it.nip19, nav)
        } ?: run {
            Text(text = remember { "$word " })
        }
    }
}

@Composable
private fun DisplayFullNote(
    it: Note,
    accountViewModel: AccountViewModel,
    backgroundColor: Color,
    nav: (String) -> Unit,
    loadedLink: LoadedBechLink
) {
    val borderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)

    val modifier = remember {
        Modifier
            .padding(top = 2.dp, bottom = 0.dp, start = 0.dp, end = 0.dp)
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(15.dp))
            .border(
                1.dp,
                borderColor,
                RoundedCornerShape(15.dp)
            )
    }

    NoteCompose(
        baseNote = it,
        accountViewModel = accountViewModel,
        modifier = modifier,
        parentBackgroundColor = backgroundColor,
        isQuotedNote = true,
        nav = nav
    )

    val extraChars = remember(loadedLink) {
        if (loadedLink.nip19.additionalChars.isNotBlank()) {
            "${loadedLink.nip19.additionalChars} "
        } else {
            null
        }
    }

    extraChars?.let {
        Text(
            it
        )
    }
}

@Composable
fun HashTag(word: String, nav: (String) -> Unit) {
    var tagSuffixPair by remember { mutableStateOf<Pair<String, String?>?>(null) }

    LaunchedEffect(key1 = word) {
        launch(Dispatchers.IO) {
            val hashtagMatcher = hashTagsPattern.matcher(word)

            val (myTag, mySuffix) = try {
                hashtagMatcher.find()
                Pair(hashtagMatcher.group(1), hashtagMatcher.group(2))
            } catch (e: Exception) {
                Log.e("Hashtag Parser", "Couldn't link hashtag $word", e)
                Pair(null, null)
            }

            if (myTag != null) {
                tagSuffixPair = Pair(myTag, mySuffix)
            }
        }
    }

    tagSuffixPair?.let { tagPair ->
        val hashtagIcon = remember(tagPair.first) { checkForHashtagWithIcon(tagPair.first) }
        ClickableText(
            text = buildAnnotatedString {
                withStyle(
                    LocalTextStyle.current.copy(color = MaterialTheme.colors.primary).toSpanStyle()
                ) {
                    append("#${tagPair.first}")
                }
            },
            onClick = { nav("Hashtag/${tagPair.first}") }
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
            val inlineContent = mapOf(
                Pair(
                    myId,
                    InlineTextContent(
                        Placeholder(
                            width = 17.sp,
                            height = 17.sp,
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
                )
            )

            // Empty Text for Size of Icon
            Text(
                text = emptytext,
                inlineContent = inlineContent
            )
        }
        tagPair.second?.ifBlank { "" }?.let {
            Text(text = "$it ")
        }
    } ?: Text(text = "$word ")
}

data class LoadedTag(val user: User?, val note: Note?, val addedChars: String)

@Composable
fun TagLink(word: String, tags: ImmutableListOfLists<String>, canPreview: Boolean, backgroundColor: Color, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    var loadedTag by remember { mutableStateOf<LoadedTag?>(null) }

    LaunchedEffect(key1 = word) {
        if (loadedTag == null) {
            launch(Dispatchers.IO) {
                val matcher = tagIndex.matcher(word)
                val (index, suffix) = try {
                    matcher.find()
                    Pair(matcher.group(1)?.toInt(), matcher.group(2) ?: "")
                } catch (e: Exception) {
                    Log.w("Tag Parser", "Couldn't link tag $word", e)
                    Pair(null, "")
                }

                if (index != null && index >= 0 && index < tags.lists.size) {
                    val tag = tags.lists[index]

                    if (tag.size > 1) {
                        if (tag[0] == "p") {
                            LocalCache.checkGetOrCreateUser(tag[1])?.let {
                                loadedTag = LoadedTag(it, null, suffix)
                            }
                        } else if (tag[0] == "e" || tag[0] == "a") {
                            LocalCache.checkGetOrCreateNote(tag[1])?.let {
                                loadedTag = LoadedTag(null, it, suffix)
                            }
                        }
                    }
                }
            }
        }
    }

    if (loadedTag == null) {
        Text(
            text = remember {
                "$word "
            }
        )
    } else {
        loadedTag?.user?.let {
            DisplayUserFromTag(it, loadedTag?.addedChars ?: "", nav)
        }

        loadedTag?.note?.let {
            DisplayNoteFromTag(it, loadedTag?.addedChars ?: "", canPreview, accountViewModel, backgroundColor, nav)
        }
    }
}

@Composable
private fun DisplayNoteFromTag(
    baseNote: Note,
    addedChars: String,
    canPreview: Boolean,
    accountViewModel: AccountViewModel,
    backgroundColor: Color,
    nav: (String) -> Unit
) {
    if (canPreview) {
        NoteCompose(
            baseNote = baseNote,
            accountViewModel = accountViewModel,
            modifier = Modifier
                .padding(top = 2.dp, bottom = 0.dp, start = 0.dp, end = 0.dp)
                .fillMaxWidth()
                .clip(shape = RoundedCornerShape(15.dp))
                .border(
                    1.dp,
                    MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                    RoundedCornerShape(15.dp)
                ),
            parentBackgroundColor = backgroundColor,
            isQuotedNote = true,
            nav = nav
        )
        addedChars.ifBlank { null }?.let {
            Text(text = remember { "$it " })
        }
    } else {
        ClickableNoteTag(baseNote, nav)
        Text(text = remember { "$addedChars " })
    }
}

@Composable
private fun DisplayUserFromTag(
    baseUser: User,
    addedChars: String,
    nav: (String) -> Unit
) {
    val innerUserState by baseUser.live().metadata.observeAsState()
    val displayName = remember(innerUserState) {
        innerUserState?.user?.toBestDisplayName() ?: ""
    }
    val route = remember(innerUserState) {
        "User/${baseUser.pubkeyHex}"
    }
    val userTags = remember(innerUserState) {
        innerUserState?.user?.info?.latestMetadata?.tags?.toImmutableListOfLists()
    }

    CreateClickableTextWithEmoji(
        clickablePart = displayName,
        suffix = remember { "$addedChars " },
        tags = userTags,
        route = route,
        nav = nav
    )
}
