package com.vitorpamplona.amethyst.ui.components

import android.util.Log
import android.util.Patterns
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Icon
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
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
import com.vitorpamplona.amethyst.model.checkForHashtagWithIcon
import com.vitorpamplona.amethyst.service.lnurl.LnInvoiceUtil
import com.vitorpamplona.amethyst.service.nip19.Nip19
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.regex.Pattern

val imageExtension = Pattern.compile("(.*/)*.+\\.(png|jpg|gif|bmp|jpeg|webp|svg)$", Pattern.CASE_INSENSITIVE)
val videoExtension = Pattern.compile("(.*/)*.+\\.(mp4|avi|wmv|mpg|amv|webm|mov)$", Pattern.CASE_INSENSITIVE)

// Group 1 = url, group 4 additional chars
val noProtocolUrlValidator = Pattern.compile("(([\\w\\d-]+\\.)*[a-zA-Z][\\w-]+[\\.\\:]\\w+([\\/\\?\\=\\&\\#\\.]?[\\w-]+)*\\/?)(.*)")

val tagIndex = Pattern.compile("\\#\\[([0-9]+)\\](.*)")

val mentionsPattern: Pattern = Pattern.compile("@([A-Za-z0-9_\\-]+)")
val hashTagsPattern: Pattern = Pattern.compile("#([a-z0-9_\\-]+)(.*)", Pattern.CASE_INSENSITIVE)
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
                                val matcher = noProtocolUrlValidator.matcher(word)
                                matcher.find()
                                val url = matcher.group(1) // url
                                val additionalChars = matcher.group(4) ?: "" // additional chars

                                ClickableUrl(url, "https://$url")
                                Text("$additionalChars ")
                            } else if (tagIndex.matcher(word).matches() && tags != null) {
                                TagLink(word, tags, canPreview, backgroundColor, accountViewModel, navController)
                            } else if (hashTagsPattern.matcher(word).matches()) {
                                HashTag(word, accountViewModel, navController)
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
                                val matcher = noProtocolUrlValidator.matcher(word)
                                matcher.find()
                                val url = matcher.group(1) // url
                                val additionalChars = matcher.group(4) ?: "" // additional chars

                                ClickableUrl(url, "https://$url")
                                Text("$additionalChars ")
                            } else if (tagIndex.matcher(word).matches() && tags != null) {
                                TagLink(word, tags, canPreview, backgroundColor, accountViewModel, navController)
                            } else if (hashTagsPattern.matcher(word).matches()) {
                                HashTag(word, accountViewModel, navController)
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
    val cleaned = word.removePrefix("@").removePrefix("nostr:").removePrefix("@")

    return listOf("npub1", "naddr1", "note1", "nprofile1", "nevent1").any { cleaned.startsWith(it, true) }
}

@Composable
fun BechLink(word: String, navController: NavController) {
    val nip19Route = Nip19.uriToRoute(word)

    if (nip19Route == null) {
        Text(text = "$word ")
    } else {
        ClickableRoute(nip19Route, navController)
    }
}

@Composable
fun HashTag(word: String, accountViewModel: AccountViewModel, navController: NavController) {
    val hashtagMatcher = hashTagsPattern.matcher(word)

    val (tag, suffix) = try {
        hashtagMatcher.find()
        Pair(hashtagMatcher.group(1), hashtagMatcher.group(2))
    } catch (e: Exception) {
        Log.e("Hashtag Parser", "Couldn't link hashtag $word", e)
        Pair(null, null)
    }

    if (tag != null) {
        val hashtagIcon = checkForHashtagWithIcon(tag)

        ClickableText(
            text = buildAnnotatedString {
                withStyle(
                    LocalTextStyle.current.copy(color = MaterialTheme.colors.primary).toSpanStyle()
                ) {
                    append("#$tag")
                }
            },
            onClick = { navController.navigate("Hashtag/$tag") }
        )

        if (hashtagIcon != null) {
            Icon(
                painter = painterResource(hashtagIcon.icon),
                contentDescription = hashtagIcon.description,
                tint = hashtagIcon.color,
                modifier = Modifier.size(20.dp).padding(0.dp, 5.dp, 0.dp, 0.dp)
            )
        }

        Text(text = "$suffix ")
    } else {
        Text(text = "$word ")
    }
}

@Composable
fun TagLink(word: String, tags: List<List<String>>, canPreview: Boolean, backgroundColor: Color, accountViewModel: AccountViewModel, navController: NavController) {
    val matcher = tagIndex.matcher(word)

    val (index, extraCharacters) = try {
        matcher.find()
        Pair(matcher.group(1)?.toInt(), matcher.group(2) ?: "")
    } catch (e: Exception) {
        Log.w("Tag Parser", "Couldn't link tag $word", e)
        Pair(null, null)
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
                    Text(text = "$extraCharacters ")
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
                    Text(text = "$extraCharacters ")
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
