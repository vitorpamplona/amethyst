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
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChatroomKeyable
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.SealedGossipEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoadRedirectScreen(eventId: String?, accountViewModel: AccountViewModel, navController: NavController) {
    if (eventId == null) return

    var noteBase by remember { mutableStateOf<Note?>(null) }
    val scope = rememberCoroutineScope()

    val nav = remember(navController) {
        { route: String ->
            scope.launch {
                navController.navigate(route) {
                    popUpTo(Route.Event.route) {
                        inclusive = true
                    }
                }
            }
            Unit
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
            accountViewModel = accountViewModel,
            nav = nav
        )
    }
}

@Composable
fun LoadRedirectScreen(baseNote: Note, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val noteState by baseNote.live().metadata.observeAsState()

    LaunchedEffect(key1 = noteState) {
        val note = noteState?.note ?: return@LaunchedEffect
        var event = note.event
        val channelHex = note.channelHex()

        if (event is GiftWrapEvent) {
            event = accountViewModel.unwrap(event)
        }

        if (event is SealedGossipEvent) {
            event = accountViewModel.unseal(event)
        }

        if (event == null) {
            // stay here, loading
        } else if (event is ChannelCreateEvent) {
            nav("Channel/${note.idHex}")
        } else if (event is ChatroomKeyable) {
            note.author?.let {
                val withKey = (event as ChatroomKeyable)
                    .chatroomKey(accountViewModel.userProfile().pubkeyHex)

                withContext(Dispatchers.IO) {
                    accountViewModel.userProfile().createChatroom(withKey)
                }

                nav("Room/${withKey.hashCode()}")
            }
        } else if (channelHex != null) {
            nav("Channel/$channelHex")
        } else {
            nav("Note/${note.idHex}")
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
