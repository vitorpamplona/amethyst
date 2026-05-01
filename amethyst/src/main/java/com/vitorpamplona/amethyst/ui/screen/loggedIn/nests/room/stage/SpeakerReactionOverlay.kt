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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.viewmodels.REACTION_WINDOW_SEC
import com.vitorpamplona.amethyst.commons.viewmodels.RoomReaction
import kotlinx.coroutines.delay

/**
 * Floating-emoji overlay drawn under a speaker's avatar. Each chip
 * is keyed by emoji content; bursts of the same emoji collapse into
 * a single `🔥 ×3` chip whose count tracks live as new reactions land.
 *
 * Reactions are about what the speaker is saying RIGHT NOW, so each
 * chip lives for [REACTION_WINDOW_SEC] seconds: the moment its
 * youngest reaction arrives the chip fades+scales in, and over that
 * window it slowly drifts upward and fades out before the eviction
 * tick removes it from the underlying list.
 */
@Composable
internal fun SpeakerReactionOverlay(
    reactions: List<RoomReaction>,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = reactions.isNotEmpty(),
        enter = fadeIn() + scaleIn(initialScale = 0.6f),
        exit = fadeOut() + scaleOut(targetScale = 0.6f),
        modifier = modifier,
    ) {
        // Group by content so duplicate emojis collapse into a single
        // "🔥 ×N" chip. Order by most recent so a fresh reaction lands
        // at the front of the row.
        val byContent = reactions.groupBy { it.content }
        val ordered =
            byContent.toList().sortedByDescending { (_, list) ->
                list.maxOf { it.createdAtSec }
            }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ordered.forEach { (content, list) ->
                // Newest createdAt drives the chip's lifecycle —
                // a fresh reaction restarts the upward-drift+fade.
                val youngestSec = list.maxOf { it.createdAtSec }
                ReactionChip(
                    content = content,
                    count = list.size,
                    youngestSec = youngestSec,
                )
            }
        }
    }
}

@Composable
private fun ReactionChip(
    content: String,
    count: Int,
    youngestSec: Long,
) {
    // Tick a [progress] state from 0f → 1f over the eviction window
    // so the chip can drift + fade in lockstep with how close it is
    // to falling out of the aggregator. Reset whenever a fresher
    // reaction lands by re-keying on [youngestSec].
    var progress by remember(youngestSec) { mutableStateOf(0f) }
    LaunchedEffect(youngestSec) {
        // Re-sync against wall-clock so a chip whose window started
        // before this composable mounted (e.g. user rotated the
        // device mid-burst) still ages correctly.
        val ageMs = (System.currentTimeMillis() / 1000L - youngestSec).coerceAtLeast(0L) * 1000L
        val remaining = (REACTION_WINDOW_MS - ageMs).coerceAtLeast(0L)
        progress = (ageMs.toFloat() / REACTION_WINDOW_MS).coerceIn(0f, 1f)
        if (remaining <= 0L) return@LaunchedEffect
        // 100 ms ticks keep the drift smooth without burning a frame
        // budget — the avatar is small and the chip's motion is
        // sub-pixel between ticks anyway.
        val steps = (remaining / 100L).coerceAtLeast(1L)
        repeat(steps.toInt()) {
            delay(100L)
            progress =
                ((System.currentTimeMillis() / 1000L - youngestSec).toFloat() * 1000f / REACTION_WINDOW_MS)
                    .coerceIn(0f, 1f)
        }
        progress = 1f
    }

    // Drift up by ~16 dp over the window so the chip reads as
    // "rising and dissipating", and fade out over the second half
    // so the first half stays solidly readable.
    val driftDp = (-16f * progress).dp
    val animatedAlpha by animateFloatAsState(
        targetValue = (1f - ((progress - 0.5f).coerceAtLeast(0f) * 2f)).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 100, easing = LinearEasing),
        label = "reaction-chip-alpha",
    )

    Surface(
        modifier =
            Modifier
                .offset(y = driftDp)
                .alpha(animatedAlpha),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        val label = if (count > 1) "$content ×$count" else content
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private const val REACTION_WINDOW_MS = REACTION_WINDOW_SEC * 1000L
