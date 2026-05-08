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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.stage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.RoomPresence
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.participants.RoomParticipantActions
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE
import kotlinx.coroutines.launch

/**
 * Host-only queue of audience members whose latest kind-10312
 * presence has `hand=1` AND who aren't already a host / moderator /
 * speaker (they wouldn't have raised their hand if they could
 * already speak). Each row has an "Approve" button that promotes
 * the user to SPEAKER via [RoomParticipantActions.setRole].
 *
 * Hidden when the queue is empty so it doesn't take up vertical
 * space on a quiet room.
 *
 * Visibility gating to host-only is the caller's responsibility.
 */
@Composable
internal fun HandRaiseQueueSection(
    event: MeetingSpaceEvent,
    viewModel: NestViewModel,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val presences by viewModel.presences.collectAsState()
    // Memoize the on-stage gate so a heartbeat that doesn't change
    // the kind-30312 doesn't re-build the set every recompose. The
    // event reference is stable across recompositions until the host
    // republishes; presence updates run through the second remember.
    val onStageKeys =
        remember(event) {
            event
                .participants()
                .filter { it.canSpeak() }
                .map { it.pubKey }
                .toSet()
        }
    val hands =
        remember(presences, onStageKeys) {
            presences.values
                .filter { it.handRaised && it.pubkey !in onStageKeys }
                .sortedBy { it.updatedAtSec }
        }

    if (hands.isEmpty()) return

    val scope = rememberCoroutineScope()
    Column(modifier = modifier.fillMaxSize().padding(top = 12.dp)) {
        Text(
            text = stringRes(R.string.nest_hand_raise_queue_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // LazyColumn so a flood of raised hands doesn't render every
        // row every recompose. Stable key by pubkey lets Compose
        // animate row entry/exit instead of teardown-rebuild.
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {
            items(items = hands, key = { it.pubkey }) { hand ->
                HandRaiseRow(
                    hand = hand,
                    accountViewModel = accountViewModel,
                    onApprove = {
                        scope.launch {
                            val template = RoomParticipantActions.setRole(event, hand.pubkey, ROLE.SPEAKER)
                            template?.let { runCatching { accountViewModel.account.signAndComputeBroadcast(it) } }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HandRaiseRow(
    hand: RoomPresence,
    accountViewModel: AccountViewModel,
    onApprove: () -> Unit,
) {
    val user = remember(hand.pubkey) { LocalCache.getOrCreateUser(hand.pubkey) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClickableUserPicture(
            baseUserHex = hand.pubkey,
            size = Size35dp,
            accountViewModel = accountViewModel,
        )
        UsernameDisplay(
            baseUser = user,
            weight = Modifier.weight(1f),
            accountViewModel = accountViewModel,
        )
        Spacer(Modifier.width(4.dp))
        Button(onClick = onApprove) {
            Text(stringRes(R.string.nest_hand_raise_approve))
        }
    }
}
