package com.vitorpamplona.amethyst.ui.components

import android.content.res.Resources
import android.util.LruCache
import android.util.Patterns
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.navigation.NavController
import com.google.accompanist.flowlayout.FlowRow
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.vitorpamplona.amethyst.lnurl.LnInvoiceUtil
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.toByteArray
import com.vitorpamplona.amethyst.model.toNote
import com.vitorpamplona.amethyst.service.Nip19
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.Locale
import java.util.regex.Pattern
import nostr.postr.toNpub

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
fun RichTextViewer(content: String, canPreview: Boolean, tags: List<List<String>>?, navController: NavController) {
  val translatedTextState = remember {
    mutableStateOf(ResultOrError(content, null, null, null))
  }

  var showOriginal by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    LanguageTranslatorService.autoTranslate(content).addOnCompleteListener { task ->
      if (task.isSuccessful) {
        translatedTextState.value = task.result
      }
    }
  }

  val text = if (showOriginal) content else translatedTextState.value.result

  Column(modifier = Modifier.padding(top = 5.dp)) {
    // FlowRow doesn't work well with paragraphs. So we need to split them
    text?.split('\n')?.forEach { paragraph ->

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

    val target = translatedTextState.value.targetLang
    val source = translatedTextState.value.sourceLang

    if (source != null && target != null) {
      if (source != target) {
        Row() {
          Text(
            text = "Auto-translated from ",
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
          )
          ClickableText(
            text = AnnotatedString("${Locale(source).displayName}"),
            onClick = { showOriginal = true },
            style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary.copy(alpha = 0.52f))
          )
          Text(
            text = " to ",
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
          )
          ClickableText(
            text = AnnotatedString("${Locale(target).displayName}"),
            onClick = { showOriginal = false },
            style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary.copy(alpha = 0.52f))
          )
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


class ResultOrError(
  var result: String?,
  var sourceLang: String?,
  var targetLang: String?,
  var error: Exception?
)

object LanguageTranslatorService {
  private val languageIdentification = LanguageIdentification.getClient()

  private val languagesSpokenByTheUser = ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration()).toLanguageTags()
  private val usersPreferredLanguage = Locale.getDefault().language

  init {
    println("LanguagesAAA: ${languagesSpokenByTheUser}")
  }

  private val translators =
    object : LruCache<TranslatorOptions, Translator>(10) {
      override fun create(options: TranslatorOptions): Translator {
        return Translation.getClient(options)
      }

      override fun entryRemoved(
        evicted: Boolean,
        key: TranslatorOptions,
        oldValue: Translator,
        newValue: Translator?
      ) {
        oldValue.close()
      }
    }

  fun identifyLanguage(text: String): Task<String> {
    return languageIdentification.identifyLanguage(text)
  }

  fun translate(text: String, source: String, target: String): Task<ResultOrError> {
    val sourceLangCode = TranslateLanguage.fromLanguageTag(source)
    val targetLangCode = TranslateLanguage.fromLanguageTag(target)
    if (sourceLangCode == null || targetLangCode == null) {
      return Tasks.forCanceled()
    }

    val options = TranslatorOptions.Builder()
      .setSourceLanguage(sourceLangCode)
      .setTargetLanguage(targetLangCode)
      .build()

    val translator = translators[options]

    return translator.downloadModelIfNeeded().onSuccessTask {

      val tasks = mutableListOf<Task<String>>()
      for (paragraph in text.split("\n")) {
        tasks.add(translator.translate(paragraph))
      }

      Tasks.whenAll(tasks).continueWith {
        val results: MutableList<String> = ArrayList()
        for (task in tasks) {
          results.add(task.result)
        }
        ResultOrError(results.joinToString("\n"), source, target, null)
      }
    }
  }

  fun autoTranslate(text: String, target: String): Task<ResultOrError> {
    return identifyLanguage(text).onSuccessTask {
      if (it == target) {
        Tasks.forCanceled()
      } else if (it != "und" && !languagesSpokenByTheUser.contains(it)) {
        translate(text, it, target)
      } else {
        Tasks.forCanceled()
      }
    }
  }

  fun autoTranslate(text: String): Task<ResultOrError> {
    return autoTranslate(text, usersPreferredLanguage)
  }
}


