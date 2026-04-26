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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.viewmodels.ParticipantGrid
import com.vitorpamplona.amethyst.commons.viewmodels.RoomMember
import com.vitorpamplona.amethyst.commons.viewmodels.RoomReaction
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size40dp
import kotlinx.collections.immutable.ImmutableSet

/**
 * Material3 grid for the room participants — replaces the
 * horizontal LazyRow layout for rooms with many speakers /
 * audience members. On-stage and audience render as separate
 * fixed-height horizontal grids (one row per group, scrolls
 * horizontally) so the chat panel below stays at a predictable
 * vertical position even when the room fills up.
 *
 * Falls open: when [grid] is empty (room with no presence yet),
 * neither section renders.
 *
 * Absent members (kind-30312 `p`-tag with no kind-10312 presence)
 * render at 50% alpha — matches nostrnests' grey-out for
 * "promoted but never joined".
 */
@Composable
internal fun ParticipantsGrid(
    grid: ParticipantGrid,
    speakingNow: ImmutableSet<String>,
    accountViewModel: AccountViewModel,
    onStageLabel: String,
    audienceLabel: String,
    modifier: Modifier = Modifier,
    reactionsByPubkey: Map<String, List<RoomReaction>> = emptyMap(),
    onLongPressParticipant: ((String) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (grid.onStage.isNotEmpty()) {
            ParticipantsSection(
                title = onStageLabel,
                members = grid.onStage,
                avatarSize = Size40dp,
                speakingNow = speakingNow,
                accountViewModel = accountViewModel,
                reactionsByPubkey = reactionsByPubkey,
                onLongPressParticipant = onLongPressParticipant,
            )
        }
        if (grid.audience.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ParticipantsSection(
                title = audienceLabel,
                members = grid.audience,
                avatarSize = Size35dp,
                // Audience rows don't get the speaking-ring — only
                // members with a live broadcast track do. Pass an
                // empty set rather than thread a per-section bool.
                speakingNow = kotlinx.collections.immutable.persistentSetOf(),
                accountViewModel = accountViewModel,
                reactionsByPubkey = emptyMap(),
                onLongPressParticipant = onLongPressParticipant,
            )
        }
    }
}

@Composable
private fun ParticipantsSection(
    title: String,
    members: List<RoomMember>,
    avatarSize: androidx.compose.ui.unit.Dp,
    speakingNow: ImmutableSet<String>,
    accountViewModel: AccountViewModel,
    reactionsByPubkey: Map<String, List<RoomReaction>>,
    onLongPressParticipant: ((String) -> Unit)?,
) {
    val ringColor = MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Single-row horizontal grid — visually identical to the
        // existing LazyRow layout but uses LazyHorizontalGrid so
        // adding a second row (e.g. names below avatars) is a
        // one-line change later. Cell size matches avatarSize +
        // padding for the reaction overlay below.
        LazyHorizontalGrid(
            rows = GridCells.Fixed(1),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(avatarSize + 32.dp)
                    .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items = members, key = { it.pubkey }) { member ->
                val isSpeaking = member.pubkey in speakingNow
                val avatarModifier =
                    Modifier
                        .let { if (isSpeaking) it.border(2.dp, ringColor, CircleShape) else it }
                        .let { if (member.absent) it.alpha(0.5f) else it }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ClickableUserPicture(
                        baseUserHex = member.pubkey,
                        size = avatarSize,
                        accountViewModel = accountViewModel,
                        modifier = avatarModifier,
                        onLongClick = onLongPressParticipant?.let { cb -> { hex -> cb(hex) } },
                    )
                    val reactions = reactionsByPubkey[member.pubkey].orEmpty()
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
