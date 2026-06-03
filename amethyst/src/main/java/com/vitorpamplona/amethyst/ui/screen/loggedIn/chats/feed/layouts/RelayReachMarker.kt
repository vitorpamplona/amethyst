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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.HalfPadding

/** How far one relay has paged into the conversation, for an in-stream progress marker. */
enum class RelayReachState {
    // Still paging older — its marker slides down (older) as it advances.
    REACHING,

    // Accepted but not answering right now (auth CLOSE / unreachable / slow); kept open, still trying.
    STALLED,

    // Hit an empty page: nothing older on this relay, it has reached the bottom of its window.
    DONE,
}

/** One relay's marker entry within a gap. */
data class RelayReach(
    val name: String,
    val state: RelayReachState,
)

/**
 * A thin divider drawn between two messages marking the point one or more relays have paged down to.
 * As a relay loads older history its reached cursor drops, so the caller places this marker further
 * down (older) in the stream — relays that race ahead leave their marker deep while slower relays'
 * markers trail higher up, converging as they catch up.
 *
 * A leading "Relay sync:" label gives the glyphs context; then each state renders one compact label:
 * a relay's host name when it is the only one of its state there (the usual converged case, where each
 * relay sits at its own depth), or just a count when several pile up at the same depth (e.g. all nine
 * clustered at the live-tail floor on first open) so the line can't grow into an unreadable comma list.
 * Reads e.g. "Relay sync: ✓ 8 · ↓ 1" or "Relay sync: ↓ nostr.wine".
 */
@Composable
fun RelayReachMarker(entries: List<RelayReach>) {
    if (entries.isEmpty()) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = HalfPadding,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = DividerThickness)
        Text(
            text = stringResource(R.string.chats_history_relay_sync),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        entries
            .groupBy { it.state }
            .toSortedMap(compareBy { it.ordinal })
            .entries
            .forEachIndexed { index, (state, list) ->
                if (index > 0) {
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                Text(
                    text = glyph(state) + " " + if (list.size == 1) list.first().name else list.size.toString(),
                    color = color(state),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = DividerThickness)
    }
}

private fun glyph(state: RelayReachState) =
    when (state) {
        RelayReachState.REACHING -> "↓"
        RelayReachState.STALLED -> "…"
        RelayReachState.DONE -> "✓"
    }

@Composable
private fun color(state: RelayReachState): Color =
    when (state) {
        RelayReachState.REACHING -> MaterialTheme.colorScheme.onSurfaceVariant
        RelayReachState.STALLED -> MaterialTheme.colorScheme.error
        RelayReachState.DONE -> MaterialTheme.colorScheme.primary
    }
