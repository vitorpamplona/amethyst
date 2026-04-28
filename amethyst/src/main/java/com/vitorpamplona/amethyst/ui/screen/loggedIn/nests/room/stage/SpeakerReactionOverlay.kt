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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.viewmodels.RoomReaction

/**
 * Floating-emoji overlay drawn under a speaker's avatar. Aggregates
 * the same emoji into one chip with a count badge so a burst of
 * "🔥🔥🔥" reads as `🔥 ×3` rather than three stacked chips.
 *
 * Hides itself when there are no reactions in the window — the
 * 30-s sliding-window aggregator in
 * [com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel.recentReactions]
 * drops stale entries on the 1-s tick.
 */
@Composable
internal fun SpeakerReactionOverlay(
    reactions: List<RoomReaction>,
    modifier: Modifier = Modifier,
) {
    // Wrap the row in AnimatedVisibility so the burst scales-and-fades
    // in instead of snapping; a fading-out tail also smooths the
    // 30 s eviction sweep instead of having chips just disappear.
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
        val ordered = byContent.toList().sortedByDescending { (_, list) -> list.maxOf { it.createdAtSec } }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ordered.forEach { (content, list) ->
                ReactionChip(content = content, count = list.size)
            }
        }
    }
}

@Composable
private fun ReactionChip(
    content: String,
    count: Int,
) {
    // Tonal Surface picks up the right elevation tint in both light
    // and dark themes — softer than the flat secondaryContainer fill
    // and keeps a tiny shadow so the chip reads as floating.
    Surface(
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
