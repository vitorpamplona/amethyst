package com.vitorpamplona.amethyst.ui.components

import android.content.res.Resources
import android.util.Patterns
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.navigation.NavController
import com.google.accompanist.flowlayout.FlowRow
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.lnurl.LnInvoiceUtil
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.toByteArray
import com.vitorpamplona.amethyst.model.toNote
import com.vitorpamplona.amethyst.service.Nip19
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import com.vitorpamplona.amethyst.service.lang.ResultOrError
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import nostr.postr.toNpub
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.*
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
fun TranslateableRichTextViewer(
  content: String,
  canPreview: Boolean,
  tags: List<List<String>>?,
  accountViewModel: AccountViewModel,
  navController: NavController
) {
  val translatedTextState = remember {
    mutableStateOf(ResultOrError(content, null, null, null))
  }

  var showOriginal by remember { mutableStateOf(false) }
  var langSettingsPopupExpanded by remember { mutableStateOf(false) }

  val context = LocalContext.current

  val accountState by accountViewModel.accountLanguagesLiveData.observeAsState()
  val account = accountState?.account ?: return

  LaunchedEffect(accountState) {
    LanguageTranslatorService.autoTranslate(content, account.dontTranslateFrom, account.translateTo).addOnCompleteListener { task ->
      if (task.isSuccessful) {
        translatedTextState.value = task.result
      } else {
        translatedTextState.value = ResultOrError(content, null, null, null)
      }
    }
  }

  val toBeViewed = if (showOriginal) content else translatedTextState.value.result ?: content

  Column(modifier = Modifier.padding(top = 5.dp)) {
    ExpandableRichTextViewer(
      toBeViewed,
      canPreview,
      tags,
      navController
    )

    val target = translatedTextState.value.targetLang
    val source = translatedTextState.value.sourceLang

    if (source != null && target != null) {
      if (source != target) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) {
          val clickableTextStyle = SpanStyle(color = MaterialTheme.colors.primary.copy(alpha = 0.52f))

          val annotatedTranslationString= buildAnnotatedString {
            withStyle(clickableTextStyle) {
              pushStringAnnotation("langSettings", true.toString())
              append("Auto")
            }

            append("-translated from ")

            withStyle(clickableTextStyle) {
              pushStringAnnotation("showOriginal", true.toString())
              append(Locale(source).displayName)
            }

            append(" to ")

            withStyle(clickableTextStyle) {
              pushStringAnnotation("showOriginal", false.toString())
              append(Locale(target).displayName)
            }
          }

          ClickableText(
            text = annotatedTranslationString,
            style = LocalTextStyle.current.copy(color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)),
            overflow = TextOverflow.Visible,
            maxLines = 3
          ) { spanOffset -> annotatedTranslationString.getStringAnnotations(spanOffset, spanOffset)
            .firstOrNull()
            ?.also { span ->
              if (span.tag == "showOriginal")
                showOriginal = span.item.toBoolean()
              else
                langSettingsPopupExpanded = !langSettingsPopupExpanded
            }
          }

          DropdownMenu(
            expanded = langSettingsPopupExpanded,
            onDismissRequest = { langSettingsPopupExpanded = false }
          ) {
            DropdownMenuItem(onClick = {
              accountViewModel.dontTranslateFrom(source, context)
              langSettingsPopupExpanded = false
            }) {
              Text("Never translate from ${Locale(source).displayName}")
            }
            Divider()
            val languageList = ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration())
            for (i in 0 until languageList.size()) {
              languageList.get(i)?.let { lang ->
                DropdownMenuItem(onClick = {
                  accountViewModel.translateTo(lang, context)
                  langSettingsPopupExpanded = false
                }) {
                  Text("Always translate to ${lang.displayName}")
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun ExpandableRichTextViewer(
  content: String,
  canPreview: Boolean,
  tags: List<List<String>>?,
  navController: NavController
) {
  var showFullText by remember { mutableStateOf(false) }

  val text = if (showFullText) content else content.take(350)

  Box(contentAlignment = Alignment.BottomCenter) {
    RichTextViewer(text, canPreview, tags, navController)

    if (content.length > 350 && !showFullText) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
          .fillMaxWidth()
          .background(
            brush = Brush.verticalGradient(
              colors = listOf(
                MaterialTheme.colors.background.copy(alpha = 0f),
                MaterialTheme.colors.background
              )
            )
          )
      ) {
        Button(
          modifier = Modifier.padding(top = 10.dp),
          onClick = { showFullText = !showFullText },
          shape = RoundedCornerShape(20.dp),
          colors = ButtonDefaults
            .buttonColors(
              backgroundColor = MaterialTheme.colors.primary
            ),
          contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)
        ) {
          Text(text = "Show More", color = Color.White)
        }
      }
    }
  }
}

@Composable
fun RichTextViewer(
  content: String,
  canPreview: Boolean,
  tags: List<List<String>>?,
  navController: NavController
) {
  Column(
    Modifier
      .fillMaxWidth()
      .animateContentSize()) {
    // FlowRow doesn't work well with paragraphs. So we need to split them
    content.split('\n').forEach { paragraph ->

      FlowRow() {
        paragraph.split(' ').forEach { word: String ->

          if (canPreview) {
            // Explicit URL
            val lnInvoice = LnInvoiceUtil.findInvoice(word)
            if (lnInvoice != null) {
              InvoicePreview(lnInvoice)
            } else if (isValidURL(word)) {
              val removedParamsFromUrl = word.split("?")[0].toLowerCase()
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
              TagLink(word, tags, navController)
            } else if (isBechLink(word)) {
              BechLink(word, navController)
            } else {
              Text(
                text = "$word ",
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
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
              TagLink(word, tags, navController)
            } else if (isBechLink(word)) {
              BechLink(word, navController)
            } else {
              Text(
                text = "$word ",
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
              )
            }
          }
        }
      }
    }
  }
}


fun isBechLink(word: String): Boolean {
  return word.startsWith("nostr:", true)
    || word.startsWith("npub1", true)
    || word.startsWith("note1", true)
    || word.startsWith("nprofile1", true)
    || word.startsWith("nevent1", true)
    || word.startsWith("@npub1", true)
    || word.startsWith("@note1", true)
    || word.startsWith("@nprofile1", true)
    || word.startsWith("@nevent1", true)
}

@Composable
fun BechLink(word: String, navController: NavController) {
  val uri = if (word.startsWith("nostr", true)) {
    word
  } else if (word.startsWith("@")) {
    word.replaceFirst("@", "nostr:")
  } else {
    "nostr:${word}"
  }

  val nip19Route = try {
    Nip19().uriToRoute(uri)
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
fun TagLink(word: String, tags: List<List<String>>, navController: NavController) {
  val matcher = tagIndex.matcher(word)

  val index = try {
    matcher.find()
    matcher.group(1).toInt()
  } catch (e: Exception) {
    println("Couldn't link tag ${word}")
    null
  }

  if (index == null) {
    return Text(text = "$word ")
  }

  if (index >= 0 && index < tags.size) {
    if (tags[index][0] == "p") {
      val user = LocalCache.users[tags[index][1]]
      if (user != null) {
        ClickableUserTag(user, navController)
      } else {
        Text(text = "${tags[index][1].toByteArray().toNpub().toShortenHex()} ")
      }
    } else if (tags[index][0] == "e") {
      val note = LocalCache.notes[tags[index][1]]
      if (note != null) {
        ClickableNoteTag(note, navController)
      } else {
        Text(text = "${tags[index][1].toByteArray().toNote().toShortenHex()} ")
      }
    } else
      Text(text = "$word ")
  }
}


