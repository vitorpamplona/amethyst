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
import androidx.compose.animation.core.animateDpAsState
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
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

private val STAGE_CELL_MIN = 96.dp
private val STAGE_AVATAR = 100.dp
private val AUDIENCE_CELL_MIN = 96.dp
private val AUDIENCE_AVATAR = 100.dp
private val GRID_SPACING = 6.dp
private val AVATAR_RING_WIDTH = 3.dp

// Upper bound for the live "voice" halo. Speaking with a peak audio
// level of 1.0 grows the ring from AVATAR_RING_WIDTH to this value;
// quieter voices land somewhere in between.
private val MAX_RING_WIDTH = 7.dp

// Cap badge sizes so they stay legible without dominating the avatar
// at 100.dp. The 0.42 ratio was tuned for ~48.dp avatars (giving
// ~20.dp badges); without a cap, scaling to 100.dp produces 42.dp
// badges that compete with the face for attention.
private val MAX_BADGE_SIZE = 28.dp

// Cell min sits 4.dp under the avatar diameter so 4 columns fit on a
// 411.dp phone (Pixel 6/7/8). The 100.dp avatar overflows ~2.dp into
// the 6.dp horizontal arrangement on each side, leaving a visible
// ~2.dp gap between neighbors. Names below ellipsize to the cell
// width, which is fine since they're short display handles.

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
    audioLevels: Map<String, Float> = emptyMap(),
    reactionsByPubkey: Map<String, List<RoomReaction>> = emptyMap(),
    connectingSpeakers: ImmutableSet<String> = persistentSetOf(),
    onLongPressParticipant: ((String) -> Unit)? = null,
    myPubkey: String? = null,
    onTapSelf: (() -> Unit)? = null,
) {
    // Float currently-speaking members to the top so the listener can
    // see who they're hearing without scrolling. sortedBy is stable in
    // Kotlin — speakers retain relative order among themselves, as do
    // non-speakers. Memoized on (members, speakingNow) so a 250ms
    // speaking flip doesn't reallocate when neither input changed.
    val sortedMembers =
        remember(members, speakingNow) {
            members.sortedBy { if (it.pubkey in speakingNow) 0 else 1 }
        }
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = stringRes(R.string.nest_stage),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        if (members.isEmpty()) {
            // Keep the strip visible so the room doesn't look broken
            // when nobody is on stage yet — the title plus a quiet
            // hint signals "waiting state" without taking the chat /
            // audience tab's space below.
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringRes(R.string.nest_stage_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(STAGE_CELL_MIN),
            modifier = Modifier.fillMaxWidth().heightIn(max = STAGE_MAX_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
            verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
        ) {
            items(items = sortedMembers, key = { it.pubkey }) { member ->
                val isSelf = myPubkey != null && member.pubkey == myPubkey
                MemberCell(
                    member = member,
                    avatarSize = STAGE_AVATAR,
                    isSpeaking = member.pubkey in speakingNow,
                    audioLevel = audioLevels[member.pubkey] ?: 0f,
                    isConnecting = member.pubkey in connectingSpeakers,
                    showMicBadge = true,
                    reactions = reactionsByPubkey[member.pubkey].orEmpty(),
                    accountViewModel = accountViewModel,
                    onLongPressParticipant = onLongPressParticipant,
                    isSelf = isSelf,
                    onTapSelf = if (isSelf) onTapSelf else null,
                    modifier = Modifier.animateItem(),
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
    myPubkey: String? = null,
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
    // Pull raised hands to the top of the audience so a moderator
    // approving the queue isn't scrolling. Stable sort keeps everyone
    // else in their incoming order.
    val sortedMembers =
        remember(members) {
            members.sortedBy { if (it.handRaised) 0 else 1 }
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
        items(items = sortedMembers, key = { it.pubkey }) { member ->
            MemberCell(
                member = member,
                avatarSize = AUDIENCE_AVATAR,
                isSpeaking = false,
                audioLevel = 0f,
                isConnecting = false,
                showMicBadge = false,
                reactions = emptyList(),
                accountViewModel = accountViewModel,
                onLongPressParticipant = onLongPressParticipant,
                isSelf = myPubkey != null && member.pubkey == myPubkey,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun MemberCell(
    member: RoomMember,
    avatarSize: Dp,
    isSpeaking: Boolean,
    audioLevel: Float,
    isConnecting: Boolean,
    showMicBadge: Boolean,
    reactions: List<RoomReaction>,
    accountViewModel: AccountViewModel,
    onLongPressParticipant: ((String) -> Unit)?,
    isSelf: Boolean = false,
    onTapSelf: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
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
    // Throb the ring width with the live peak amplitude — quiet voices
    // sit at the base width, loud ones widen the halo. animateDpAsState
    // smooths the 10 Hz raw signal into a continuous animation. The
    // muted ring stays at base width because no audio is being decoded.
    val targetRingWidth =
        when {
            isSpeaking -> AVATAR_RING_WIDTH + (audioLevel.coerceIn(0f, 1f) * (MAX_RING_WIDTH - AVATAR_RING_WIDTH).value).dp
            ringColor != null -> AVATAR_RING_WIDTH
            else -> 0.dp
        }
    val animatedRingWidth by animateDpAsState(targetValue = targetRingWidth, label = "speaker-ring-width")
    val avatarModifier =
        Modifier
            .let { if (ringColor != null) it.border(animatedRingWidth, ringColor, CircleShape) else it }
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
    // Self-cell tap shortcut: tap your own avatar to toggle mic-mute
    // when broadcasting. For other cells we leave tap unhandled so
    // the existing nav-to-profile path stays consistent (and there's
    // nothing visually different from a non-clickable avatar).
    val onClick =
        if (isSelf && onTapSelf != null) {
            remember(onTapSelf) { { _: String -> onTapSelf() } }
        } else {
            null
        }
    val selfTint = if (isSelf) MaterialTheme.colorScheme.primary else Color.Unspecified
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            ClickableUserPicture(
                baseUserHex = member.pubkey,
                size = avatarSize,
                accountViewModel = accountViewModel,
                modifier = avatarModifier,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(avatarSize - 8.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val role = member.role
            if (role == ROLE.HOST || role == ROLE.MODERATOR) {
                RoleBadge(
                    role = role,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
            if (member.handRaised) {
                HandRaiseBadge(
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
            if (showMicBadge && member.publishing) {
                MicStateBadge(
                    isSpeaking = isSpeaking,
                    isMuted = member.muted == true,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            // Reactions float over the avatar's bottom-right corner so
            // a 👏 burst no longer pushes the username down and reflows
            // neighbouring cells. The mic badge sits at BottomCenter,
            // so BottomEnd + a small outward offset keeps them clear.
            if (reactions.isNotEmpty()) {
                SpeakerReactionOverlay(
                    reactions = reactions,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 6.dp, y = 6.dp),
                )
            }
        }
        UsernameDisplay(
            baseUser = user,
            weight = Modifier.fillMaxWidth().padding(top = 4.dp),
            textColor = selfTint,
            textAlign = TextAlign.Center,
            accountViewModel = accountViewModel,
        )
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
private fun HandRaiseBadge(modifier: Modifier = Modifier) {
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
    Box(
        modifier =
            modifier
                .offset(x = 2.dp, y = (-2).dp + offsetY.dp)
                .size(MAX_BADGE_SIZE)
                .background(NEST_HAND_RAISE_COLOR, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = MaterialSymbols.PanTool,
            contentDescription = stringRes(R.string.nest_raise_hand),
            tint = Color.White,
            modifier = Modifier.size(MAX_BADGE_SIZE - 4.dp),
        )
    }
}

/**
 * Role indicator overlaid on the top-left of the avatar — only
 * rendered for HOST and MODERATOR. Mirrors how nostrnests
 * surfaces room ownership/staff at a glance.
 */
@Composable
private fun RoleBadge(
    role: ROLE,
    modifier: Modifier = Modifier,
) {
    val (color, symbol, label) =
        when (role) {
            ROLE.HOST -> Triple(NEST_HOST_COLOR, MaterialSymbols.MilitaryTech, stringRes(R.string.nest_role_host))
            ROLE.MODERATOR -> Triple(NEST_MODERATOR_COLOR, MaterialSymbols.Shield, stringRes(R.string.nest_role_moderator))
            else -> return
        }
    Box(
        modifier =
            modifier
                .offset(x = (-2).dp, y = (-2).dp)
                .size(MAX_BADGE_SIZE)
                .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = symbol,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(MAX_BADGE_SIZE - 4.dp),
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
    modifier: Modifier = Modifier,
) {
    val color =
        when {
            isSpeaking -> NEST_SPEAKING_COLOR
            isMuted -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }
    val description =
        when {
            isMuted -> stringRes(R.string.nest_state_muted)
            isSpeaking -> stringRes(R.string.nest_state_speaking)
            else -> stringRes(R.string.nest_state_mic_open)
        }
    Box(
        modifier =
            modifier
                .offset(y = 2.dp)
                .size(MAX_BADGE_SIZE)
                .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = if (isMuted) MaterialSymbols.MicOff else MaterialSymbols.Mic,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(MAX_BADGE_SIZE - 4.dp),
        )
    }
}

private val NEST_HAND_RAISE_COLOR = Color(0xFFEAB308) // tailwind yellow-500
private val NEST_SPEAKING_COLOR = Color(0xFF22C55E) // tailwind green-500
private val NEST_HOST_COLOR = Color(0xFFA855F7) // tailwind purple-500
private val NEST_MODERATOR_COLOR = Color(0xFF0EA5E9) // tailwind sky-500
