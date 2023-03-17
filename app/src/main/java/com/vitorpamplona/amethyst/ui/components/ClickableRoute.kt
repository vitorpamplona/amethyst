package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.nip19.Nip19

@Composable
fun ClickableRoute(
    nip19: Nip19.Return,
    navController: NavController
) {
    if (nip19.type == Nip19.Type.USER) {
        val userBase = LocalCache.getOrCreateUser(nip19.hex)

        val userState by userBase.live().metadata.observeAsState()
        val user = userState?.user ?: return

        CreateClickableText(user.toBestDisplayName(), nip19.additionalChars, "User/${nip19.hex}", navController)
    } else if (nip19.type == Nip19.Type.ADDRESS) {
        val noteBase = LocalCache.checkGetOrCreateAddressableNote(nip19.hex)

        if (noteBase == null) {
            Text(
                "@${nip19.hex}${nip19.additionalChars} "
            )
        } else {
            val noteState by noteBase.live().metadata.observeAsState()
            val note = noteState?.note ?: return

            CreateClickableText(note.idDisplayNote(), nip19.additionalChars, "Note/${nip19.hex}", navController)
        }
    } else if (nip19.type == Nip19.Type.NOTE) {
        val noteBase = LocalCache.getOrCreateNote(nip19.hex)
        val noteState by noteBase.live().metadata.observeAsState()
        val note = noteState?.note ?: return
        val channel = note.channel()

        if (note.event is ChannelCreateEvent) {
            CreateClickableText(note.idDisplayNote(), nip19.additionalChars, "Channel/${nip19.hex}", navController)
        } else if (channel != null) {
            CreateClickableText(channel.toBestDisplayName(), nip19.additionalChars, "Channel/${note.channel()?.idHex}", navController)
        } else {
            CreateClickableText(note.idDisplayNote(), nip19.additionalChars, "Note/${nip19.hex}", navController)
        }
    } else {
        Text(
            "@${nip19.hex}${nip19.additionalChars} "
        )
    }
}

@Composable
fun CreateClickableText(clickablePart: String, suffix: String, route: String, navController: NavController) {
    ClickableText(
        text = buildAnnotatedString {
            withStyle(
                LocalTextStyle.current.copy(color = MaterialTheme.colors.primary).toSpanStyle()
            ) {
                append("@$clickablePart")
            }
            withStyle(
                LocalTextStyle.current.copy(color = MaterialTheme.colors.onBackground).toSpanStyle()
            ) {
                append("$suffix ")
            }
        },
        onClick = { navController.navigate(route) }
    )
}
