package com.vitorpamplona.amethyst.ui.components

import android.util.Log
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
import com.google.accompanist.flowlayout.FlowRow
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
import com.vitorpamplona.amethyst.service.lnurl.LnWithdrawalUtil
import com.vitorpamplona.amethyst.service.nip19.Nip19
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
    modifier: Modifier = Modifier,
    tags: List<List<String>>?,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val isMarkdown = remember(content) { isMarkdown(content) }

    Column(modifier = modifier) {
        if (isMarkdown) {
            RenderContentAsMarkdown(content, backgroundColor, tags, nav)
        } else {
            RenderRegular(content, tags, canPreview, backgroundColor, accountViewModel, nav)
        }
    }
}

@Stable
class RichTextViewerState(
    val content: String,
    val urlSet: ImmutableSet<String>,
    val imagesForPager: ImmutableMap<String, ZoomableUrlContent>,
    val imageList: ImmutableList<ZoomableUrlContent>,
    val customEmoji: ImmutableMap<String, String>
)

@Composable
private fun RenderRegular(
    content: String,
    tags: List<List<String>>?,
    canPreview: Boolean,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    var state by remember(content) {
        mutableStateOf(
            RichTextViewerState(
                content,
                persistentSetOf(),
                persistentMapOf(),
                persistentListOf(),
                persistentMapOf()
            )
        )
    }

    LaunchedEffect(key1 = content) {
        launch(Dispatchers.IO) {
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

            val emojiMap = tags?.filter { it.size > 2 && it[0] == "emoji" }?.associate { ":${it[1]}:" to it[2] } ?: emptyMap()

            if (urlSet.isNotEmpty() || emojiMap.isNotEmpty()) {
                state = RichTextViewerState(
                    content,
                    urlSet.toImmutableSet(),
                    imagesForPager.toImmutableMap(),
                    imageList.toImmutableList(),
                    emojiMap.toImmutableMap()
                )
            }
        }
    }

    // FlowRow doesn't work well with paragraphs. So we need to split them
    content.split('\n').forEach { paragraph ->
        FlowRow() {
            val s = remember(paragraph) {
                if (isArabic(paragraph)) {
                    paragraph.trim().split(' ').reversed()
                } else {
                    paragraph.trim().split(' ')
                }
            }
            s.forEach { word: String ->
                RenderWord(word, state, canPreview, backgroundColor, accountViewModel, nav, tags)
            }
        }
    }
}

@Composable
private fun RenderWord(
    word: String,
    state: RichTextViewerState,
    canPreview: Boolean,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    tags: List<List<String>>?
) {
    val wordSpace = remember(word) {
        "$word "
    }

    if (canPreview) {
        // Explicit URL
        val img = state.imagesForPager[word]
        if (img != null) {
            ZoomableContentView(img, state.imageList)
        } else if (state.urlSet.contains(word)) {
            UrlPreview(word, wordSpace)
        } else if (state.customEmoji.any { word.contains(it.key) }) {
            RenderCustomEmoji(word, state.customEmoji)
        } else if (word.startsWith("lnbc", true)) {
            MayBeInvoicePreview(word)
        } else if (word.startsWith("lnurl", true)) {
            MayBeWithdrawal(word)
        } else if (Patterns.EMAIL_ADDRESS.matcher(word).matches()) {
            ClickableEmail(word)
        } else if (word.length > 6 && Patterns.PHONE.matcher(word).matches()) {
            ClickablePhone(word)
        } else if (isBechLink(word)) {
            BechLink(
                word,
                canPreview,
                backgroundColor,
                accountViewModel,
                nav
            )
        } else if (word.startsWith("#")) {
            if (tagIndex.matcher(word).matches() && tags != null) {
                TagLink(
                    word,
                    tags,
                    canPreview,
                    backgroundColor,
                    accountViewModel,
                    nav
                )
            } else if (hashTagsPattern.matcher(word).matches()) {
                HashTag(word, nav)
            } else {
                Text(
                    text = wordSpace,
                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                )
            }
        } else if (noProtocolUrlValidator.matcher(word).matches()) {
            val matcher = noProtocolUrlValidator.matcher(word)
            matcher.find()
            val url = matcher.group(1) // url
            val additionalChars = matcher.group(4) ?: "" // additional chars

            if (url != null) {
                ClickableUrl(url, "https://$url")
                Text("$additionalChars ")
            } else {
                Text(
                    text = wordSpace,
                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                )
            }
        } else {
            Text(
                text = wordSpace,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
            )
        }
    } else {
        if (state.urlSet.contains(word)) {
            ClickableUrl(wordSpace, word)
        } else if (word.startsWith("lnurl", true)) {
            val lnWithdrawal = LnWithdrawalUtil.findWithdrawal(word)
            if (lnWithdrawal != null) {
                ClickableWithdrawal(withdrawalString = lnWithdrawal)
            } else {
                Text(
                    text = wordSpace,
                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                )
            }
        } else if (state.customEmoji.any { word.contains(it.key) }) {
            RenderCustomEmoji(word, state.customEmoji)
        } else if (Patterns.EMAIL_ADDRESS.matcher(word).matches()) {
            ClickableEmail(word)
        } else if (Patterns.PHONE.matcher(word).matches() && word.length > 6) {
            ClickablePhone(word)
        } else if (isBechLink(word)) {
            BechLink(
                word,
                canPreview,
                backgroundColor,
                accountViewModel,
                nav
            )
        } else if (word.startsWith("#")) {
            if (tagIndex.matcher(word).matches() && tags != null) {
                TagLink(
                    word,
                    tags,
                    canPreview,
                    backgroundColor,
                    accountViewModel,
                    nav
                )
            } else if (hashTagsPattern.matcher(word).matches()) {
                HashTag(word, nav)
            } else {
                Text(
                    text = wordSpace,
                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                )
            }
        } else if (noProtocolUrlValidator.matcher(word).matches()) {
            val matcher = noProtocolUrlValidator.matcher(word)
            matcher.find()
            val url = matcher.group(1) // url
            val additionalChars = matcher.group(4) ?: "" // additional chars

            if (url != null) {
                ClickableUrl(url, "https://$url")
                Text("$additionalChars ")
            } else {
                Text(
                    text = wordSpace,
                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                )
            }
        } else {
            Text(
                text = wordSpace,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
            )
        }
    }
}

@Composable
fun RenderCustomEmoji(word: String, customEmoji: Map<String, String>) {
    CreateTextWithEmoji(
        text = "$word ",
        emojis = customEmoji
    )
}

@Composable
private fun RenderContentAsMarkdown(content: String, backgroundColor: Color, tags: List<List<String>>?, nav: (String) -> Unit) {
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

    var markdownWithSpecialContent by remember(content) { mutableStateOf<String?>(null) }
    var nip19References by remember(content) { mutableStateOf<List<Nip19.Return>>(emptyList()) }
    var refresh by remember(content) { mutableStateOf(0) }

    LaunchedEffect(key1 = content) {
        launch(Dispatchers.IO) {
            nip19References = returnNIP19References(content, tags)
            markdownWithSpecialContent = returnMarkdownWithSpecialContent(content, tags)
        }
    }

    LaunchedEffect(key1 = refresh) {
        launch(Dispatchers.IO) {
            val newMarkdownWithSpecialContent = returnMarkdownWithSpecialContent(content, tags)
            if (markdownWithSpecialContent != newMarkdownWithSpecialContent) {
                markdownWithSpecialContent = newMarkdownWithSpecialContent
            }
        }
    }

    nip19References.forEach {
        ObserveNIP19(it) {
            refresh++
        }
    }

    val uri = LocalUriHandler.current

    markdownWithSpecialContent?.let {
        MaterialRichText(
            style = myMarkDownStyle
        ) {
            Markdown(
                content = it,
                markdownParseOptions = MarkdownParseOptions.Default
            ) { link ->
                val route = uriToRoute(link)
                if (route != null) {
                    nav(route)
                } else {
                    runCatching { uri.openUri(link) }
                }
            }
        }
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

private fun getDisplayNameAndNIP19FromTag(tag: String, tags: List<List<String>>): Pair<String, String>? {
    val matcher = tagIndex.matcher(tag)
    val (index, suffix) = try {
        matcher.find()
        Pair(matcher.group(1)?.toInt(), matcher.group(2) ?: "")
    } catch (e: Exception) {
        Log.w("Tag Parser", "Couldn't link tag $tag", e)
        Pair(null, null)
    }

    if (index != null && index >= 0 && index < tags.size) {
        val tag = tags[index]

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

private fun returnNIP19References(content: String, tags: List<List<String>>?): List<Nip19.Return> {
    val listOfReferences = mutableListOf<Nip19.Return>()
    content.split('\n').forEach { paragraph ->
        paragraph.split(' ').forEach { word: String ->
            if (isBechLink(word)) {
                val parsedNip19 = Nip19.uriToRoute(word)
                parsedNip19?.let {
                    listOfReferences.add(it)
                }
            }
        }
    }

    tags?.forEach {
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

private fun returnMarkdownWithSpecialContent(content: String, tags: List<List<String>>?): String {
    var returnContent = ""
    content.split('\n').forEach { paragraph ->
        paragraph.split(' ').forEach { word: String ->
            if (isValidURL(word)) {
                returnContent += "[$word]($word) "
            } else if (Patterns.EMAIL_ADDRESS.matcher(word).matches()) {
                returnContent += "[$word](mailto:$word) "
            } else if (Patterns.PHONE.matcher(word).matches() && word.length > 6) {
                returnContent += "[$word](tel:$word) "
            } else if (isBechLink(word)) {
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

fun isBechLink(word: String): Boolean {
    val cleaned = word.lowercase().removePrefix("@").removePrefix("nostr:").removePrefix("@")

    return listOf("npub1", "naddr1", "note1", "nprofile1", "nevent1").any { cleaned.startsWith(it) }
}

@Composable
fun BechLink(word: String, canPreview: Boolean, backgroundColor: Color, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    var nip19Route by remember { mutableStateOf<Nip19.Return?>(null) }
    var baseNotePair by remember { mutableStateOf<Pair<Note, String?>?>(null) }

    LaunchedEffect(key1 = word) {
        launch(Dispatchers.IO) {
            Nip19.uriToRoute(word)?.let {
                if (it.type == Nip19.Type.NOTE || it.type == Nip19.Type.EVENT || it.type == Nip19.Type.ADDRESS) {
                    LocalCache.checkGetOrCreateNote(it.hex)?.let { note ->
                        baseNotePair = Pair(note, it.additionalChars)
                    }
                }

                nip19Route = it
            }
        }
    }

    if (canPreview) {
        baseNotePair?.let {
            NoteCompose(
                baseNote = it.first,
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
            if (!it.second.isNullOrEmpty()) {
                Text(
                    "${it.second} "
                )
            }
        } ?: nip19Route?.let {
            ClickableRoute(it, nav)
        } ?: Text(text = "$word ")
    } else {
        nip19Route?.let {
            ClickableRoute(it, nav)
        } ?: Text(text = "$word ")
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

@Composable
fun TagLink(word: String, tags: List<List<String>>, canPreview: Boolean, backgroundColor: Color, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    var baseUserPair by remember { mutableStateOf<Pair<User, String?>?>(null) }
    var baseNotePair by remember { mutableStateOf<Pair<Note, String?>?>(null) }

    LaunchedEffect(key1 = word) {
        if (baseUserPair == null && baseNotePair == null) {
            launch(Dispatchers.IO) {
                val matcher = tagIndex.matcher(word)
                val (index, suffix) = try {
                    matcher.find()
                    Pair(matcher.group(1)?.toInt(), matcher.group(2) ?: "")
                } catch (e: Exception) {
                    Log.w("Tag Parser", "Couldn't link tag $word", e)
                    Pair(null, null)
                }

                if (index != null && index >= 0 && index < tags.size) {
                    val tag = tags[index]

                    if (tag.size > 1) {
                        if (tag[0] == "p") {
                            LocalCache.checkGetOrCreateUser(tag[1])?.let {
                                baseUserPair = Pair(it, suffix)
                            }
                        } else if (tag[0] == "e" || tag[0] == "a") {
                            LocalCache.checkGetOrCreateNote(tag[1])?.let {
                                baseNotePair = Pair(it, suffix)
                            }
                        }
                    }
                }
            }
        }
    }

    baseUserPair?.let {
        val innerUserState by it.first.live().metadata.observeAsState()
        val displayName = remember(innerUserState) {
            innerUserState?.user?.toBestDisplayName() ?: ""
        }
        val route = remember(innerUserState) {
            "User/${it.first.pubkeyHex}"
        }
        val userTags = remember(innerUserState) {
            innerUserState?.user?.info?.latestMetadata?.tags
        }

        CreateClickableTextWithEmoji(
            clickablePart = displayName,
            suffix = "${it.second} ",
            tags = userTags,
            route = route,
            nav = nav
        )
    }

    baseNotePair?.let {
        if (canPreview) {
            NoteCompose(
                baseNote = it.first,
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
            it.second?.ifBlank { null }?.let {
                Text(text = "$it ")
            }
        } else {
            ClickableNoteTag(it.first, nav)
            Text(text = "${it.second} ")
        }
    }

    if (baseNotePair == null && baseUserPair == null) {
        Text(text = "$word ")
    }
}
