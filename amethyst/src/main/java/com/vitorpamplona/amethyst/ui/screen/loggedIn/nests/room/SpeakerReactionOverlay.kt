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
    if (reactions.isEmpty()) return
    // Group by content so duplicate emojis collapse into a single
    // "🔥 ×N" chip. Order by most recent so a fresh reaction lands
    // at the front of the row.
    val byContent = reactions.groupBy { it.content }
    val ordered = byContent.toList().sortedByDescending { (_, list) -> list.maxOf { it.createdAtSec } }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ordered.forEach { (content, list) ->
            ReactionChip(content = content, count = list.size)
        }
    }
}

@Composable
private fun ReactionChip(
    content: String,
    count: Int,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        val label = if (count > 1) "$content ×$count" else content
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
