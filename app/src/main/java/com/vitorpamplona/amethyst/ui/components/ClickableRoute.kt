package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.nip19.Nip19
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ClickableRoute(
    nip19: Nip19.Return,
    navController: NavController
) {
    if (nip19.type == Nip19.Type.USER) {
        DisplayUser(nip19, navController)
    } else if (nip19.type == Nip19.Type.ADDRESS) {
        DisplayAddress(nip19, navController)
    } else if (nip19.type == Nip19.Type.NOTE) {
        DisplayNote(nip19, navController)
    } else if (nip19.type == Nip19.Type.EVENT) {
        DisplayEvent(nip19, navController)
    } else {
        Text(
            "@${nip19.hex}${nip19.additionalChars} "
        )
    }
}

@Composable
private fun DisplayEvent(
    nip19: Nip19.Return,
    navController: NavController
) {
    var noteBase by remember { mutableStateOf<Note?>(null) }

    LaunchedEffect(key1 = nip19.hex) {
        withContext(Dispatchers.IO) {
            noteBase = LocalCache.checkGetOrCreateNote(nip19.hex)
        }
    }

    noteBase?.let {
        val noteState by it.live().metadata.observeAsState()
        val note = noteState?.note ?: return
        val channel = note.channel()

        if (note.event is ChannelCreateEvent) {
            CreateClickableText(
                note.idDisplayNote(),
                nip19.additionalChars,
                "Channel/${nip19.hex}",
                navController
            )
        } else if (note.event is PrivateDmEvent) {
            CreateClickableText(
                note.idDisplayNote(),
                nip19.additionalChars,
                "Room/${note.author?.pubkeyHex}",
                navController
            )
        } else if (channel != null) {
            CreateClickableText(
                channel.toBestDisplayName(),
                nip19.additionalChars,
                "Channel/${note.channel()?.idHex}",
                navController
            )
        } else {
            CreateClickableText(
                note.idDisplayNote(),
                nip19.additionalChars,
                "Event/${nip19.hex}",
                navController
            )
        }
    }

    if (noteBase == null) {
        Text(
            "@${nip19.hex}${nip19.additionalChars} "
        )
    }
}

@Composable
private fun DisplayNote(
    nip19: Nip19.Return,
    navController: NavController
) {
    var noteBase by remember { mutableStateOf<Note?>(null) }

    LaunchedEffect(key1 = nip19.hex) {
        withContext(Dispatchers.IO) {
            noteBase = LocalCache.checkGetOrCreateNote(nip19.hex)
        }
    }

    noteBase?.let {
        val noteState by it.live().metadata.observeAsState()
        val note = noteState?.note ?: return
        val channel = note.channel()

        if (note.event is ChannelCreateEvent) {
            CreateClickableText(
                note.idDisplayNote(),
                nip19.additionalChars,
                "Channel/${nip19.hex}",
                navController
            )
        } else if (note.event is PrivateDmEvent) {
            CreateClickableText(
                note.idDisplayNote(),
                nip19.additionalChars,
                "Room/${note.author?.pubkeyHex}",
                navController
            )
        } else if (channel != null) {
            CreateClickableText(
                channel.toBestDisplayName(),
                nip19.additionalChars,
                "Channel/${note.channel()?.idHex}",
                navController
            )
        } else {
            CreateClickableText(
                note.idDisplayNote(),
                nip19.additionalChars,
                "Note/${nip19.hex}",
                navController
            )
        }
    }

    if (noteBase == null) {
        Text(
            "@${nip19.hex}${nip19.additionalChars} "
        )
    }
}

@Composable
private fun DisplayAddress(
    nip19: Nip19.Return,
    navController: NavController
) {
    var noteBase by remember { mutableStateOf<Note?>(null) }

    LaunchedEffect(key1 = nip19.hex) {
        withContext(Dispatchers.IO) {
            noteBase = LocalCache.checkGetOrCreateAddressableNote(nip19.hex)
        }
    }

    noteBase?.let {
        val noteState by it.live().metadata.observeAsState()
        val note = noteState?.note ?: return

        CreateClickableText(
            note.idDisplayNote(),
            nip19.additionalChars,
            "Note/${nip19.hex}",
            navController
        )
    }

    if (noteBase == null) {
        Text(
            "@${nip19.hex}${nip19.additionalChars} "
        )
    }
}

@Composable
private fun DisplayUser(
    nip19: Nip19.Return,
    navController: NavController
) {
    var userBase by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(key1 = nip19.hex) {
        withContext(Dispatchers.IO) {
            userBase = LocalCache.checkGetOrCreateUser(nip19.hex)
        }
    }

    userBase?.let {
        val userState by it.live().metadata.observeAsState()
        val user = userState?.user ?: return

        CreateClickableText(
            user.toBestDisplayName(),
            nip19.additionalChars,
            "User/${nip19.hex}",
            navController
        )
    }

    if (userBase == null) {
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
