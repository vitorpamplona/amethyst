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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.viewmodels.ParticipantGrid
import com.vitorpamplona.amethyst.commons.viewmodels.RoomMember
import com.vitorpamplona.amethyst.commons.viewmodels.RoomReaction
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
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
    connectingSpeakers: ImmutableSet<String> = kotlinx.collections.immutable.persistentSetOf(),
    onLongPressParticipant: ((String) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (grid.onStage.isNotEmpty()) {
            ParticipantsSection(
                title = onStageLabel,
                members = grid.onStage,
                avatarSize = Size40dp,
                speakingNow = speakingNow,
                connectingSpeakers = connectingSpeakers,
                showMicBadge = true,
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
                // Audience rows don't get the speaking-ring, mic-state
                // pill, or buffering overlay — only members with a
                // live broadcast subscription do. Hand-raise badge
                // still renders (audience is the queue).
                speakingNow = kotlinx.collections.immutable.persistentSetOf(),
                connectingSpeakers = kotlinx.collections.immutable.persistentSetOf(),
                showMicBadge = false,
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
    avatarSize: Dp,
    speakingNow: ImmutableSet<String>,
    connectingSpeakers: ImmutableSet<String>,
    showMicBadge: Boolean,
    accountViewModel: AccountViewModel,
    reactionsByPubkey: Map<String, List<RoomReaction>>,
    onLongPressParticipant: ((String) -> Unit)?,
) {
    val ringColor = MaterialTheme.colorScheme.primary
    // Hoist the size/spacing modifiers — same Dp values for every
    // cell, so allocating them once per section beats allocating
    // per-cell-recompose with N speakers.
    val gridModifier =
        remember(avatarSize) {
            Modifier
                .fillMaxWidth()
                .height(avatarSize + 48.dp)
                .padding(top = 4.dp)
        }
    val cellWidthModifier = remember(avatarSize) { Modifier.width(avatarSize + 16.dp) }
    val absentAlphaModifier = remember { Modifier.alpha(0.5f) }
    val speakingBorderModifier = remember(ringColor) { Modifier.border(2.dp, ringColor, CircleShape) }
    val spinnerModifier = remember(avatarSize) { Modifier.size(avatarSize - 8.dp) }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Single-row horizontal grid. Cell height = avatar +
        // reaction overlay headroom + a username line below; the
        // grid auto-fits the avatar + name + (optional) reaction
        // bubble vertically without clipping.
        LazyHorizontalGrid(
            rows = GridCells.Fixed(1),
            modifier = gridModifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items = members, key = { it.pubkey }) { member ->
                val isSpeaking = member.pubkey in speakingNow
                val avatarModifier =
                    when {
                        isSpeaking && member.absent -> speakingBorderModifier.then(absentAlphaModifier)
                        isSpeaking -> speakingBorderModifier
                        member.absent -> absentAlphaModifier
                        else -> Modifier
                    }
                val user =
                    remember(member.pubkey) {
                        com.vitorpamplona.amethyst.model.LocalCache
                            .getOrCreateUser(member.pubkey)
                    }
                // Cache the long-click adapter per (pubkey, callback)
                // tuple — `onLongPressParticipant?.let { cb -> { hex -> cb(hex) } }`
                // would otherwise allocate a fresh lambda on every
                // recompose, which adds up across N members during a
                // connectingSpeakers / speakingNow flip.
                val onLongClick =
                    remember(member.pubkey, onLongPressParticipant) {
                        onLongPressParticipant?.let { cb -> { hex: String -> cb(hex) } }
                    }
                val isConnecting = member.pubkey in connectingSpeakers
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = cellWidthModifier,
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
                            // Pre-roll buffering overlay — visible
                            // between SUBSCRIBE_OK and the first
                            // decoded frame (typically 0.5-2 s on a
                            // fresh subscription). Sized smaller than
                            // the avatar so the user picture stays
                            // recognisable underneath.
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = spinnerModifier,
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
                    com.vitorpamplona.amethyst.ui.note.UsernameDisplay(
                        baseUser = user,
                        weight = Modifier.fillMaxWidth(),
                        accountViewModel = accountViewModel,
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
