/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChatroomKeyable
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.SealedGossipEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoadRedirectScreen(
    eventId: String?,
    accountViewModel: AccountViewModel,
    navController: NavController,
) {
    if (eventId == null) return

    var noteBase by remember { mutableStateOf<Note?>(null) }
    val scope = rememberCoroutineScope()

    val nav =
        remember(navController) {
            { route: String ->
                scope.launch {
                    navController.navigate(route) { popUpTo(Route.Event.route) { inclusive = true } }
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
            nav = nav,
        )
    }
}

@Composable
fun LoadRedirectScreen(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteState by baseNote.live().metadata.observeAsState()

    LaunchedEffect(key1 = noteState) {
        val note = noteState?.note ?: return@LaunchedEffect
        val event = note.event

        if (event != null) {
            withContext(Dispatchers.IO) { redirect(event, note, accountViewModel, nav) }
        }
    }

    Column(
        Modifier.fillMaxHeight().fillMaxWidth().padding(horizontal = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringRes(R.string.looking_for_event, baseNote.idHex))
    }
}

fun redirect(
    event: EventInterface,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val channelHex = note.channelHex()

    if (event is GiftWrapEvent) {
        accountViewModel.unwrap(event) { redirect(it, note, accountViewModel, nav) }
    } else if (event is SealedGossipEvent) {
        accountViewModel.unseal(event) { redirect(it, note, accountViewModel, nav) }
    } else {
        if (event == null) {
            // stay here, loading
        } else if (event is ChannelCreateEvent) {
            nav("Channel/${note.idHex}")
        } else if (event is ChatroomKeyable) {
            note.author?.let {
                val withKey =
                    (event as ChatroomKeyable).chatroomKey(accountViewModel.userProfile().pubkeyHex)

                accountViewModel.userProfile().createChatroom(withKey)

                nav("Room/${withKey.hashCode()}")
            }
        } else if (channelHex != null) {
            nav("Channel/$channelHex")
        } else {
            nav("Note/${note.idHex}")
        }
    }
}
