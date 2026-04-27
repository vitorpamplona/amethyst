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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.viewmodels.ConnectionUiState
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import kotlinx.collections.immutable.ImmutableSet

/**
 * Avatar row for hosts / speakers / audience. Active speakers get a
 * primary-color ring while their MoQ track is delivering audio.
 */
@Composable
internal fun StagePeopleRow(
    label: String,
    people: List<ParticipantTag>,
    avatarSize: Dp,
    speakingNow: ImmutableSet<String>,
    accountViewModel: AccountViewModel,
    reactionsByPubkey: Map<String, List<com.vitorpamplona.amethyst.commons.viewmodels.RoomReaction>> = emptyMap(),
    onLongPressParticipant: ((String) -> Unit)? = null,
) {
    val ringColor = MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items = people, key = { it.pubKey }) { participant ->
                val isSpeaking = participant.pubKey in speakingNow
                val avatarModifier =
                    if (isSpeaking) {
                        Modifier.border(2.dp, ringColor, CircleShape)
                    } else {
                        Modifier
                    }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    ClickableUserPicture(
                        baseUserHex = participant.pubKey,
                        size = avatarSize,
                        accountViewModel = accountViewModel,
                        modifier = avatarModifier,
                        onLongClick = onLongPressParticipant?.let { cb -> { hex -> cb(hex) } },
                    )
                    val reactions = reactionsByPubkey[participant.pubKey].orEmpty()
                    if (reactions.isNotEmpty()) {
                        SpeakerReactionOverlay(
                            reactions = reactions,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun connectingLabel(connection: ConnectionUiState.Connecting): String =
    when (connection.step) {
        ConnectionUiState.Step.ResolvingRoom -> stringRes(R.string.nest_connecting_resolving)
        ConnectionUiState.Step.OpeningTransport -> stringRes(R.string.nest_connecting_transport)
        ConnectionUiState.Step.MoqHandshake -> stringRes(R.string.nest_connecting_handshake)
    }
