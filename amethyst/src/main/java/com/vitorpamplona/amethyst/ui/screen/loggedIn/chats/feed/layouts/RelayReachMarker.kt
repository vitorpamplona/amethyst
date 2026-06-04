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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
 * One relay's window-limit, used to both place a marker and act as the load sentinel for that relay.
 * The marker sits at [reachedUntil] (the oldest point the relay has paged to). [advance] pulls that
 * relay's next, older page; the renderer fires it while the marker is on screen (see
 * [RelayWindowLimitMarkers]).
 *
 * @param key stable identity (protocol tag + relay url) so the sentinel survives list reorders.
 */
data class RelayWindowLimit(
    val key: String,
    val name: String,
    val reachedUntil: Long,
    val state: RelayReachState,
    val advance: () -> Unit,
)

/**
 * Renders the window-limit markers for the relays whose limit falls in the gap between a newer message
 * (at [newerCreatedAt]) and its next-older neighbour (at [olderCreatedAt], null at the oldest end), and
 * — this is the load driver — makes each one a **sentinel**: while this gap is composed (i.e. on/near
 * screen), it pulls that relay's next page, and keeps pulling as each page lands ([LaunchedEffect] keyed
 * on the relay's reached cursor) for as long as the marker stays visible. When a page fills enough to
 * push the marker off screen, or the user scrolls away, the gap is disposed and paging stops on its own.
 * A done relay just shows its ✓ and drives nothing.
 */
@Composable
fun RelayWindowLimitMarkers(
    limits: List<RelayWindowLimit>,
    newerCreatedAt: Long?,
    olderCreatedAt: Long?,
) {
    val here =
        remember(limits, newerCreatedAt, olderCreatedAt) {
            limits.filter { lim ->
                newerCreatedAt != null &&
                    newerCreatedAt > lim.reachedUntil &&
                    (olderCreatedAt == null || olderCreatedAt <= lim.reachedUntil)
            }
        }
    if (here.isEmpty()) return

    here.forEach { lim ->
        if (lim.state != RelayReachState.DONE) {
            // Keyed identity so the effect isn't torn down on reorder; keyed on the reached cursor so each
            // returned page re-fires it (continue while visible). A stalled relay re-fires only on
            // re-composition (scroll back into view) — a single retry, not a busy loop.
            key(lim.key) {
                LaunchedEffect(lim.reachedUntil, lim.state) { lim.advance() }
            }
        }
    }

    RelayReachMarker(here.map { RelayReach(it.name, it.state) })
}

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
