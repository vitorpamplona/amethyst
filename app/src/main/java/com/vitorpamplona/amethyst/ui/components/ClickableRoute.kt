package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.text.AnnotatedString
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.toByteArray
import com.vitorpamplona.amethyst.model.toNote
import com.vitorpamplona.amethyst.service.Nip19
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import nostr.postr.toNpub

@Composable
fun ClickableRoute(
  nip19: Nip19.Return,
  navController: NavController
) {
  if (nip19.type == Nip19.Type.USER) {
    val userBase = LocalCache.getOrCreateUser(nip19.hex)
    val userState by userBase.live.observeAsState()
    val user = userState?.user ?: return

    val route = "User/${nip19.hex}"
    val text = user.toBestDisplayName()

    ClickableText(
      text = AnnotatedString("@${text} "),
      onClick = { navController.navigate(route) },
      style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary)
    )
  } else {
    val noteBase = LocalCache.getOrCreateNote(nip19.hex)
    val noteState by noteBase.live.observeAsState()
    val note = noteState?.note ?: return

    if (note.event is ChannelCreateEvent) {
      ClickableText(
        text = AnnotatedString("@${note.idDisplayNote} "),
        onClick = { navController.navigate("Channel/${nip19.hex}") },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary)
      )
    } else if (note.channel != null) {
      ClickableText(
        text = AnnotatedString("@${note.channel?.toBestDisplayName()} "),
        onClick = { navController.navigate("Channel/${note.channel?.idHex}") },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary)
      )
    } else {
      ClickableText(
        text = AnnotatedString("@${note.idDisplayNote} "),
        onClick = { navController.navigate("Note/${nip19.hex}") },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary)
      )
    }
  }
}