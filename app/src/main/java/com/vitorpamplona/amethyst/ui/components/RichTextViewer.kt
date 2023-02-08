package com.vitorpamplona.amethyst.ui.components

import android.util.Patterns
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.navigation.NavController
import com.google.accompanist.flowlayout.FlowRow
import com.vitorpamplona.amethyst.lnurl.LnInvoiceUtil
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.toByteArray
import com.vitorpamplona.amethyst.model.toNote
import com.vitorpamplona.amethyst.service.Nip19
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import nostr.postr.toNpub
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


