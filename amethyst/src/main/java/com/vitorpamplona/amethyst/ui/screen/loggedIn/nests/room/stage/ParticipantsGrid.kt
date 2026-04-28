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

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.viewmodels.RoomMember
import com.vitorpamplona.amethyst.commons.viewmodels.RoomReaction
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

private val STAGE_CELL_MIN = 112.dp
private val STAGE_AVATAR = 100.dp
private val AUDIENCE_CELL_MIN = 112.dp
private val AUDIENCE_AVATAR = 100.dp
private val GRID_SPACING = 6.dp
private val AVATAR_RING_WIDTH = 3.dp

// Two rows visible by default. Each cell is roughly avatar + name +
// reaction headroom; with a 100dp avatar one row lifts to ~140dp, so
// two rows + label/padding lands near 320dp — tall enough to show a
// handful of speakers without scrolling but small enough to leave the
// chat / audience tab the majority of the viewport.
private val STAGE_MAX_HEIGHT = 320.dp

/**
 * Vertical adaptive grid for the on-stage section. Used as the
 * always-visible header strip in the room layout: speakers flow into
 * as many columns as the screen width allows, wrapping to a new row
 * once full. Capped at [STAGE_MAX_HEIGHT] so a 30-speaker room can
 * scroll inside the strip without pushing the tabs / chat below the
 * fold.
 *
 * Falls open: when [members] is empty, renders nothing.
 */
@Composable
internal fun StageGrid(
    members: List<RoomMember>,
    speakingNow: ImmutableSet<String>,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    reactionsByPubkey: Map<String, List<RoomReaction>> = emptyMap(),
    connectingSpeakers: ImmutableSet<String> = persistentSetOf(),
    onLongPressParticipant: ((String) -> Unit)? = null,
) {
    if (members.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = stringRes(R.string.nest_stage),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(STAGE_CELL_MIN),
            modifier = Modifier.fillMaxWidth().heightIn(max = STAGE_MAX_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
            verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
        ) {
            items(items = members, key = { it.pubkey }) { member ->
                MemberCell(
                    member = member,
                    avatarSize = STAGE_AVATAR,
                    isSpeaking = member.pubkey in speakingNow,
                    isConnecting = member.pubkey in connectingSpeakers,
                    showMicBadge = true,
                    reactions = reactionsByPubkey[member.pubkey].orEmpty(),
                    accountViewModel = accountViewModel,
                    onLongPressParticipant = onLongPressParticipant,
                )
            }
        }
    }
}

/**
 * Vertical adaptive grid for the audience tab. Sized to fill the
 * remaining vertical space its parent gives it (`Modifier.weight(1f)`)
 * so a room with 1,000 listeners scrolls efficiently — the lazy grid
 * only composes the rows currently visible.
 *
 * Audience cells deliberately omit the speaking ring, mic badge and
 * connecting spinner: only members with a live broadcast subscription
 * earn those, and audience by definition aren't broadcasting. Hand
 * badge still renders because the audience is the hand-raise queue.
 */
@Composable
internal fun AudienceGrid(
    members: List<RoomMember>,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    onLongPressParticipant: ((String) -> Unit)? = null,
) {
    if (members.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringRes(R.string.nest_audience_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(AUDIENCE_CELL_MIN),
        modifier = modifier.fillMaxSize().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
        verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(vertical = 8.dp),
    ) {
        items(items = members, key = { it.pubkey }) { member ->
            MemberCell(
                member = member,
                avatarSize = AUDIENCE_AVATAR,
                isSpeaking = false,
                isConnecting = false,
                showMicBadge = false,
                reactions = emptyList(),
                accountViewModel = accountViewModel,
                onLongPressParticipant = onLongPressParticipant,
            )
        }
    }
}

@Composable
private fun MemberCell(
    member: RoomMember,
    avatarSize: Dp,
    isSpeaking: Boolean,
    isConnecting: Boolean,
    showMicBadge: Boolean,
    reactions: List<RoomReaction>,
    accountViewModel: AccountViewModel,
    onLongPressParticipant: ((String) -> Unit)?,
) {
    val mutedRingColor = MaterialTheme.colorScheme.error
    // Tri-state ring around the avatar so a glance distinguishes:
    //   - green ring: actively speaking right now
    //   - red ring:   on stage and broadcasting but mic flagged muted
    //   - no ring:    idle, audience, or unknown mute state
    val ringColor =
        when {
            isSpeaking -> NEST_SPEAKING_COLOR
            showMicBadge && member.publishing && member.muted == true -> mutedRingColor
            else -> null
        }
    val avatarModifier =
        Modifier
            .let { if (ringColor != null) it.border(AVATAR_RING_WIDTH, ringColor, CircleShape) else it }
            .let { if (member.absent) it.alpha(0.5f) else it }
    val user =
        remember(member.pubkey) {
            com.vitorpamplona.amethyst.model.LocalCache
                .getOrCreateUser(member.pubkey)
        }
    // Cache the long-click adapter per (pubkey, callback) tuple so a
    // recompose during a connectingSpeakers / speakingNow flip doesn't
    // allocate a fresh lambda for every cell.
    val onLongClick =
        remember(member.pubkey, onLongPressParticipant) {
            onLongPressParticipant?.let { cb -> { hex: String -> cb(hex) } }
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            ClickableUserPicture(
                baseUserHex = member.pubkey,
                size = avatarSize,
                accountViewModel = accountViewModel,
                modifier = avatarModifier,
                onLongClick = onLongClick,
            )
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(avatarSize - 8.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (member.handRaised) {
                HandRaiseBadge(
                    avatarSize = avatarSize,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
            if (showMicBadge && member.publishing) {
                MicStateBadge(
                    isSpeaking = isSpeaking,
                    isMuted = member.muted == true,
                    avatarSize = avatarSize,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        UsernameDisplay(
            baseUser = user,
            weight = Modifier.fillMaxWidth().padding(top = 4.dp),
            textAlign = TextAlign.Center,
            accountViewModel = accountViewModel,
        )
        if (reactions.isNotEmpty()) {
            SpeakerReactionOverlay(
                reactions = reactions,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Hand-raise indicator overlaid on the avatar — yellow circle with
 * a hand glyph at the top-right, animated in a subtle vertical
 * bounce so the eye catches it across a busy room. Mirrors
 * nostrnests' `animate-bounce` Tailwind utility on
 * `ParticipantAvatar`.
 */
@Composable
private fun HandRaiseBadge(
    avatarSize: Dp,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "hand-raise-bounce")
    val offsetY by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = -3f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "hand-raise-bounce-y",
        )
    val badgeSize = (avatarSize.value * 0.42f).dp
    Box(
        modifier =
            modifier
                .offset(x = 2.dp, y = (-2).dp + offsetY.dp)
                .size(badgeSize)
                .background(NEST_HAND_RAISE_COLOR, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = MaterialSymbols.PanTool,
            contentDescription = stringRes(R.string.nest_raise_hand),
            tint = Color.White,
            modifier = Modifier.size(badgeSize - 4.dp),
        )
    }
}

/**
 * Mic-state pill anchored to the bottom-center of an on-stage
 * speaker's avatar. Color encodes whether the mic is currently
 * carrying audio:
 *   - green   — speaker is speaking right now (in `speakingNow`)
 *   - red     — speaker is publishing but their `muted` flag is set
 *   - primary — speaker is publishing but silent (mic open, no audio)
 *
 * Audience members never get this badge (the parent gates render
 * with `showMicBadge`); a speaker who isn't `publishing` doesn't
 * get one either — they're on stage but not on air.
 */
@Composable
private fun MicStateBadge(
    isSpeaking: Boolean,
    isMuted: Boolean,
    avatarSize: Dp,
    modifier: Modifier = Modifier,
) {
    val color =
        when {
            isSpeaking -> NEST_SPEAKING_COLOR
            isMuted -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }
    val badgeSize = (avatarSize.value * 0.42f).dp
    Box(
        modifier =
            modifier
                .offset(y = 2.dp)
                .size(badgeSize)
                .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = if (isMuted) MaterialSymbols.MicOff else MaterialSymbols.Mic,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(badgeSize - 4.dp),
        )
    }
}

private val NEST_HAND_RAISE_COLOR = Color(0xFFEAB308) // tailwind yellow-500
private val NEST_SPEAKING_COLOR = Color(0xFF22C55E) // tailwind green-500
