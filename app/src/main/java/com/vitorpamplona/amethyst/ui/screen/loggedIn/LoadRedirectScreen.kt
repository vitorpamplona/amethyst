package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun LoadRedirectScreen(eventId: String?, navController: NavController) {
    if (eventId == null) return

    var noteBase by remember { mutableStateOf<Note?>(null) }

    val nav = remember(navController) {
        { route: String ->
            navController.backQueue.removeLast()
            navController.navigate(route)
        }
    }

    LaunchedEffect(eventId) {
        launch(Dispatchers.IO) {
            val newNoteBase = LocalCache.checkGetOrCreateNote(eventId)
            if (newNoteBase != noteBase) {
                noteBase = newNoteBase
            }
        }
    }

    noteBase?.let {
        LoadRedirectScreen(
            baseNote = it,
            nav = nav
        )
    }
}

@Composable
fun LoadRedirectScreen(baseNote: Note, nav: (String) -> Unit) {
    val noteState by baseNote.live().metadata.observeAsState()

    val scope = rememberCoroutineScope()

    LaunchedEffect(key1 = noteState) {
        scope.launch {
            val note = noteState?.note
            val event = note?.event
            val channelHex = note?.channelHex()

            if (event == null) {
                // stay here, loading
            } else if (event is ChannelCreateEvent) {
                nav("Channel/${note.idHex}")
            } else if (event is PrivateDmEvent) {
                nav("Room/${note.author?.pubkeyHex}")
            } else if (channelHex != null) {
                nav("Channel/$channelHex")
            } else {
                nav("Note/${note.idHex}")
            }
        }
    }

    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .padding(horizontal = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.looking_for_event, baseNote.idHex))
    }
}
