/*
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent

/**
 * Lobby card rendered in [com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.ChannelView]
 * for NIP-53 kind 30312 meeting-space events. Shows the room title +
 * summary + a "Join audio room" button that launches [NestActivity].
 *
 * The lobby is intentionally thin: no audio session, no presence event, no
 * hand-raise. All of that lives behind the Join button so the on-the-wire
 * traffic only fires when the user actually wants to be in the room.
 *
 * Hidden when the event has no `service` tag — those rooms are hosted on
 * non-nests servers we can't connect to.
 */
@Composable
fun NestJoinCard(
    baseChannel: LiveActivitiesChannel,
    accountViewModel: AccountViewModel,
) {
    LoadAddressableNote(baseChannel.address, accountViewModel) { addressableNote ->
        addressableNote ?: return@LoadAddressableNote
        val event = addressableNote.event as? MeetingSpaceEvent ?: return@LoadAddressableNote
        NestJoinCardContent(event, accountViewModel)
    }
}

@Composable
private fun NestJoinCardContent(
    event: MeetingSpaceEvent,
    accountViewModel: AccountViewModel,
) {
    val serviceBase = event.service()
    val endpoint = event.endpoint()
    val roomId = event.address().dTag
    // Need the auth base, MoQ endpoint, and the room creator's pubkey to
    // mint a JWT scoped to this namespace. Skip rendering Join when any
    // are missing — those rooms aren't joinable on the audio plane.
    if (serviceBase.isNullOrBlank() || endpoint.isNullOrBlank() || roomId.isBlank()) return

    val context = LocalContext.current
    val addressValue = remember(event) { event.address().toValue() }
    val hostPubkey = event.pubKey
    val kind = event.kind

    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            event.room()?.let {
                Text(text = it, style = MaterialTheme.typography.titleMedium)
            }
            event.summary()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JoinNestButton(event = event, accountViewModel = accountViewModel)
            }
        }
    }
}

/**
 * Standalone "Join audio room" button. Reusable from any composable
 * that has a [MeetingSpaceEvent] in hand — the lobby card, the
 * in-feed note renderer (so a `nostr:naddr1...` deep-link to a
 * kind-30312 lands one tap away from the room), and any future
 * room-list surface. Renders nothing for events without a
 * service / endpoint / d-tag — those rooms can't be joined on
 * the audio plane.
 */
@Composable
fun JoinNestButton(
    event: MeetingSpaceEvent,
    accountViewModel: AccountViewModel,
) {
    val serviceBase = event.service()
    val endpoint = event.endpoint()
    val roomId = event.address().dTag
    if (serviceBase.isNullOrBlank() || endpoint.isNullOrBlank() || roomId.isBlank()) return

    val context = LocalContext.current
    val addressValue = remember(event) { event.address().toValue() }
    val hostPubkey = event.pubKey
    val kind = event.kind

    Button(onClick = {
        NestBridge.set(accountViewModel)
        NestActivity.launch(
            context = context,
            addressValue = addressValue,
            authBaseUrl = serviceBase,
            endpoint = endpoint,
            hostPubkey = hostPubkey,
            roomId = roomId,
            kind = kind,
        )
    }) {
        Text(stringRes(R.string.nest_join))
    }
}
