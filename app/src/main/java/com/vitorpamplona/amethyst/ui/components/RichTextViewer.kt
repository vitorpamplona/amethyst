package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.accompanist.flowlayout.FlowRow
import com.vitorpamplona.amethyst.model.LocalCache
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.regex.Pattern

val imageExtension = Pattern.compile("(.*/)*.+\\.(png|jpg|gif|bmp|jpeg|webp)$")
val noProtocolUrlValidator = Pattern.compile("^[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b(?:[-a-zA-Z0-9()@:%_\\+.~#?&//=]*)$")
val tagIndex = Pattern.compile("\\#\\[([0-9]*)\\]")

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
fun RichTextViewer(content: String, tags: List<List<String>>?) {
  Column(modifier = Modifier.padding(top = 5.dp)) {
    // FlowRow doesn't work well with paragraphs. So we need to split them
    content.split('\n').forEach { paragraph ->

      FlowRow() {
        paragraph.split(' ').forEach { word: String ->
          // Explicit URL
          if (isValidURL(word)) {
            val removedParamsFromUrl = word.split("?")[0].toLowerCase()
            if (imageExtension.matcher(removedParamsFromUrl).matches()) {
              AsyncImage(
                model = word,
                contentDescription = word,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                  .padding(top = 4.dp)
                  .fillMaxWidth()
                  .clip(shape = RoundedCornerShape(15.dp))
                  .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f), RoundedCornerShape(15.dp))
              )
            } else {
              UrlPreview(word, word)
            }
          } else if (noProtocolUrlValidator.matcher(word).matches()) {
            UrlPreview("https://$word", word)
          } else if (tagIndex.matcher(word).matches() && tags != null) {
            TagLink(word, tags)
          } else {
            Text(text = "$word ")
          }
        }
      }

    }
  }
}

@Composable
fun TagLink(word: String, tags: List<List<String>>) {
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

  if (index > 0 && index < tags.size) {
    if (tags[index][0] == "p") {
      val user = LocalCache.users[tags[index][1]]
      if (user != null) {
        val innerUserState by user.live.observeAsState()
        Text(
          "${innerUserState?.user?.toBestDisplayName()}"
        )
      }
    } else if (tags[index][0] == "e") {
      val note = LocalCache.notes[tags[index][1]]
      if (note != null) {
        val innerNoteState by note.live.observeAsState()
        Text(
          "${innerNoteState?.note?.idDisplayHex}"
        )
      }
    } else
      Text(text = "$word ")
  }
}