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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.audiorooms.admin.AdminCommandEvent
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE
import kotlinx.coroutines.launch

/**
 * Host-only management sheet for one room participant. Surfaces:
 *   * Promote to speaker (idempotent — re-promoting a current speaker
 *     just re-publishes the same role; no harm, no warning needed).
 *   * Demote to listener (no-op for the host themselves; the underlying
 *     [RoomParticipantActions.demoteToListener] returns null in that
 *     case and the action is a silent skip).
 *   * Kick (sends an AdminCommandEvent.kick — relay forwards;
 *     recipients gate on signer being host/moderator).
 *
 * The kick path doesn't ALSO demote the target — that's nostrnests'
 * own behaviour: the kicked user's session ends and any future
 * presence events from them get dropped by the recipient's
 * server-side filter. A future commit can chain the two actions
 * if we want a "kick AND remove from participants" flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ParticipantHostActionsSheet(
    target: String,
    event: MeetingSpaceEvent,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val roomATag =
        ATag(
            kind = event.kind,
            pubKeyHex = event.pubKey,
            dTag = event.dTag(),
            relay = null,
        )

    fun broadcast(template: com.vitorpamplona.quartz.nip01Core.signers.EventTemplate<out com.vitorpamplona.quartz.nip01Core.core.Event>?) {
        template ?: return
        scope.launch { runCatching { accountViewModel.account.signAndComputeBroadcast(template) } }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = target.take(8) + "…",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            ActionRow(stringRes(R.string.audio_room_promote_speaker)) {
                broadcast(RoomParticipantActions.setRole(event, target, ROLE.SPEAKER))
                onDismiss()
            }
            ActionRow(stringRes(R.string.audio_room_demote_listener)) {
                broadcast(RoomParticipantActions.demoteToListener(event, target))
                onDismiss()
            }
            ActionRow(
                text = stringRes(R.string.audio_room_kick_action),
                color = MaterialTheme.colorScheme.error,
            ) {
                broadcast(AdminCommandEvent.kick(roomATag, target))
                onDismiss()
            }
        }
    }
}

@Composable
private fun ActionRow(
    text: String,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyLarge,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
    )
}
