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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.Nav
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip17Dm.ChatroomKeyable
import com.vitorpamplona.quartz.nip28PublicChat.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.ChannelMessageEvent
import com.vitorpamplona.quartz.nip28PublicChat.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip53LiveActivities.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.GiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.SealedGossipEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoadRedirectScreen(
    eventId: String?,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    if (eventId == null) return

    var noteBase by remember { mutableStateOf<Note?>(null) }

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
    nav: INav,
) {
    val noteState by baseNote.live().metadata.observeAsState()

    LaunchedEffect(key1 = noteState) {
        val note = noteState?.note ?: return@LaunchedEffect
        val event = note.event

        if (event != null) {
            withContext(Dispatchers.IO) { redirect(event, accountViewModel, nav) }
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
    eventId: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LocalCache.getNoteIfExists(eventId)?.event?.let {
        redirect(it, accountViewModel, nav)
    }
}

fun redirect(
    event: Event,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val channelHex =
        if (
            event is ChannelMessageEvent ||
            event is ChannelMetadataEvent ||
            event is ChannelCreateEvent ||
            event is LiveActivitiesChatMessageEvent ||
            event is LiveActivitiesEvent
        ) {
            (event as? ChannelMessageEvent)?.channel()
                ?: (event as? ChannelMetadataEvent)?.channel()
                ?: (event as? ChannelCreateEvent)?.id
                ?: (event as? LiveActivitiesChatMessageEvent)?.activity()?.toTag()
                ?: (event as? LiveActivitiesEvent)?.address()?.toTag()
        } else {
            null
        }

    if (event is GiftWrapEvent) {
        event.innerEventId?.let {
            redirect(it, accountViewModel, nav)
        } ?: run {
            accountViewModel.unwrap(event) { redirect(it, accountViewModel, nav) }
        }
    } else if (event is SealedGossipEvent) {
        event.innerEventId?.let {
            redirect(it, accountViewModel, nav)
        } ?: run {
            accountViewModel.unseal(event) { redirect(it, accountViewModel, nav) }
        }
    } else {
        if (event is ChannelCreateEvent) {
            nav.popUpTo("Channel/${event.id}", Route.Event.route)
        } else if (event is ChatroomKeyable) {
            val withKey = event.chatroomKey(accountViewModel.userProfile().pubkeyHex)
            accountViewModel.userProfile().createChatroom(withKey)
            nav.popUpTo("Room/${withKey.hashCode()}", Route.Event.route)
        } else if (channelHex != null) {
            nav.popUpTo("Channel/$channelHex", Route.Event.route)
        } else {
            nav.popUpTo("Note/${event.id}", Route.Event.route)
        }
    }
}
