package com.vitorpamplona.amethyst.ui.components

import android.util.Patterns
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.accompanist.flowlayout.FlowRow
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.markdown.MarkdownParseOptions
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material.MaterialRichText
import com.halilibo.richtext.ui.resolveDefaults
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.lnurl.LnInvoiceUtil
import com.vitorpamplona.amethyst.service.nip19.Nip19
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.regex.Pattern

val imageExtension = Pattern.compile("(.*/)*.+\\.(png|jpg|gif|bmp|jpeg|webp|svg)$")
val videoExtension = Pattern.compile("(.*/)*.+\\.(mp4|avi|wmv|mpg|amv|webm)$")
val noProtocolUrlValidator = Pattern.compile("^[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b(?:[-a-zA-Z0-9()@:%_\\+.~#?&//=]*)$")
val tagIndex = Pattern.compile(".*\\#\\[([0-9]+)\\].*")

val mentionsPattern: Pattern = Pattern.compile("@([A-Za-z0-9_-]+)")
val hashTagsPattern: Pattern = Pattern.compile("#([A-Za-z0-9_-]+)")
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

@Composable
fun RichTextViewer(
    content: String,
    canPreview: Boolean,
    modifier: Modifier = Modifier,
    tags: List<List<String>>?,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val myMarkDownStyle = RichTextStyle().resolveDefaults().copy(
        codeBlockStyle = RichTextStyle().resolveDefaults().codeBlockStyle?.copy(
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
                .background(MaterialTheme.colors.onSurface.copy(alpha = 0.05f).compositeOver(backgroundColor))
        ),
        stringStyle = RichTextStyle().resolveDefaults().stringStyle?.copy(
            linkStyle = SpanStyle(
                textDecoration = TextDecoration.Underline,
                color = MaterialTheme.colors.primary
            ),
            codeStyle = SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                background = MaterialTheme.colors.onSurface.copy(alpha = 0.22f).compositeOver(backgroundColor)
            )
        )
    )

    Column(modifier = modifier.animateContentSize()) {
        if (content.startsWith("# ") ||
            content.contains("##") ||
            content.contains("**") ||
            content.contains("__") ||
            content.contains("```")
        ) {
            MaterialRichText(
                style = myMarkDownStyle
            ) {
                Markdown(
                    content = content,
                    markdownParseOptions = MarkdownParseOptions.Default
                )
            }
        } else {
            // FlowRow doesn't work well with paragraphs. So we need to split them
            content.split('\n').forEach { paragraph ->
                FlowRow() {
                    val s = if (isArabic(paragraph)) paragraph.split(' ').reversed() else paragraph.split(' ')
                    s.forEach { word: String ->
                        if (canPreview) {
                            // Explicit URL
                            val lnInvoice = LnInvoiceUtil.findInvoice(word)
                            if (lnInvoice != null) {
                                InvoicePreview(lnInvoice)
                            } else if (isValidURL(word)) {
                                val removedParamsFromUrl = word.split("?")[0].lowercase()
                                if (imageExtension.matcher(removedParamsFromUrl).matches()) {
                                    ZoomableImageView(word)
                                } else if (videoExtension.matcher(removedParamsFromUrl).matches()) {
                                    VideoView(word)
                                } else {
                                    UrlPreview(word, word)
                                }
                            } else if (Patterns.EMAIL_ADDRESS.matcher(word).matches()) {
                                ClickableEmail(word)
                            } else if (Patterns.PHONE.matcher(word).matches() && word.length > 6) {
                                ClickablePhone(word)
                            } else if (noProtocolUrlValidator.matcher(word).matches()) {
                                UrlPreview("https://$word", word)
                            } else if (tagIndex.matcher(word).matches() && tags != null) {
                                TagLink(word, tags, canPreview, backgroundColor, accountViewModel, navController)
                            } else if (isBechLink(word)) {
                                BechLink(word, navController)
                            } else {
                                Text(
                                    text = "$word ",
                                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                                )
                            }
                        } else {
                            if (isValidURL(word)) {
                                ClickableUrl("$word ", word)
                            } else if (Patterns.EMAIL_ADDRESS.matcher(word).matches()) {
                                ClickableEmail(word)
                            } else if (Patterns.PHONE.matcher(word).matches() && word.length > 6) {
                                ClickablePhone(word)
                            } else if (noProtocolUrlValidator.matcher(word).matches()) {
                                ClickableUrl(word, "https://$word")
                            } else if (tagIndex.matcher(word).matches() && tags != null) {
                                TagLink(word, tags, canPreview, backgroundColor, accountViewModel, navController)
                            } else if (isBechLink(word)) {
                                BechLink(word, navController)
                            } else {
                                Text(
                                    text = "$word ",
                                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isArabic(text: String): Boolean {
    return text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }
}

fun isBechLink(word: String): Boolean {
    return word.startsWith("nostr:", true) ||
        word.startsWith("npub1", true) ||
        word.startsWith("naddr1", true) ||
        word.startsWith("note1", true) ||
        word.startsWith("nprofile1", true) ||
        word.startsWith("nevent1", true) ||
        word.startsWith("@npub1", true) ||
        word.startsWith("@note1", true) ||
        word.startsWith("@addr1", true) ||
        word.startsWith("@nprofile1", true) ||
        word.startsWith("@nevent1", true)
}

@Composable
fun BechLink(word: String, navController: NavController) {
    val uri = if (word.startsWith("nostr", true)) {
        word
    } else if (word.startsWith("@")) {
        word.replaceFirst("@", "nostr:")
    } else {
        "nostr:$word"
    }

    val nip19Route = try {
        Nip19.uriToRoute(uri)
    } catch (e: Exception) {
        null
    }

    if (nip19Route == null) {
        Text(text = "$word ")
    } else {
        ClickableRoute(nip19Route, navController)
    }
}

@Composable
fun TagLink(word: String, tags: List<List<String>>, canPreview: Boolean, backgroundColor: Color, accountViewModel: AccountViewModel, navController: NavController) {
    val matcher = tagIndex.matcher(word)

    val index = try {
        matcher.find()
        matcher.group(1).toInt()
    } catch (e: Exception) {
        println("Couldn't link tag $word")
        null
    }

    if (index == null) {
        return Text(text = "$word ")
    }

    if (index >= 0 && index < tags.size) {
        if (tags[index][0] == "p") {
            val baseUser = LocalCache.checkGetOrCreateUser(tags[index][1])
            if (baseUser != null) {
                val userState = baseUser.live().metadata.observeAsState()
                val user = userState.value?.user
                if (user != null) {
                    ClickableUserTag(user, navController)
                } else {
                    Text(text = "$word ")
                }
            } else {
                // if here the tag is not a valid Nostr Hex
                Text(text = "$word ")
            }
        } else if (tags[index][0] == "e") {
            val note = LocalCache.checkGetOrCreateNote(tags[index][1])
            if (note != null) {
                if (canPreview) {
                    NoteCompose(
                        baseNote = note,
                        accountViewModel = accountViewModel,
                        modifier = Modifier
                            .padding(0.dp)
                            .fillMaxWidth()
                            .clip(shape = RoundedCornerShape(15.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                                RoundedCornerShape(15.dp)
                            ),
                        parentBackgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.05f)
                            .compositeOver(backgroundColor),
                        isQuotedNote = true,
                        navController = navController
                    )
                } else {
                    ClickableNoteTag(note, navController)
                }
            } else {
                // if here the tag is not a valid Nostr Hex
                Text(text = "$word ")
            }
        } else {
            Text(text = "$word ")
        }
    }
}
