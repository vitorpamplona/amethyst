package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.text.AnnotatedString
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.toNote
import com.vitorpamplona.amethyst.ui.note.toShortenHex

@Composable
fun ClickableNoteTag(
  note: Note,
  navController: NavController
) {
  val innerNoteState by note.live.observeAsState()
  ClickableText(
    text = AnnotatedString("@${innerNoteState?.note?.id?.toNote()?.toShortenHex()} "),
    onClick = { navController.navigate("Note/${innerNoteState?.note?.idHex}") },
    style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary)
  )
}