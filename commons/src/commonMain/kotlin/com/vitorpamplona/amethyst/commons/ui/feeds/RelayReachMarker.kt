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
package com.vitorpamplona.amethyst.commons.ui.feeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chats_history_relay_sync
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource

// A relay-reach divider is hair-thin; inlined here so the shared component carries no app-theme dep.
private val DividerThickness = 0.25.dp

/**
 * True when [reachedUntil] falls in the gap between a newer message (at [newerCreatedAt]) and its
 * next-older neighbour (at [olderCreatedAt], null past the oldest end): the newer side is strictly
 * newer than the cursor and the older side is at or below it (or absent). This single predicate both
 * places the marker ([RelayWindowLimitMarkers]) and decides when its paging sentinel is on screen
 * ([RelayWindowLimitSentinels]), so the two can never disagree about which gap a cursor lives in.
 */
internal fun reachedFallsInGap(
    reachedUntil: Long,
    newerCreatedAt: Long?,
    olderCreatedAt: Long?,
): Boolean =
    newerCreatedAt != null &&
        newerCreatedAt > reachedUntil &&
        (olderCreatedAt == null || olderCreatedAt <= reachedUntil)

/** How far one relay has paged into a feed's history, for an in-stream progress marker. */
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
 * One relay's window-limit: places a marker and carries the [advance] that pulls that relay's next,
 * older page. The marker sits at [reachedUntil] (the oldest point the relay has paged to);
 * [RelayWindowLimitMarkers] draws it and [RelayWindowLimitSentinels] fires [advance] while it is on
 * screen.
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
 * Drives demand-driven paging for every limit, **hoisted above the list** so its identity does not ride
 * on which row currently hosts the marker. Each non-done limit gets one stable effect (keyed by
 * [RelayWindowLimit.key]) that watches the [listState] and pulls that relay's next page when its marker
 * is on screen.
 *
 * Why hoisted: the marker for a limit lives in exactly one gap (between the two rows straddling its
 * reached cursor). Placing the sentinel *inside* that row made its effect's identity ride the hosting
 * row — so any feed reorder (a live message, or a slow relay dribbling a history page) moved the gap to a
 * different row, tore the effect down and recreated it, and re-fired `advance()` on a static screen.
 * That re-armed stalled/auth relays into a silence-watchdog storm and could walk a delivering relay
 * back a window with no scroll. Hoisting the effect and driving it off **viewport visibility** instead
 * of composition presence removes that coupling.
 *
 * Fires `advance()` when (and only when) the marker's gap is among the currently visible rows AND either
 * it just scrolled into view OR its reached cursor moved (a page landed — keep paging while visible).
 * A reorder that keeps the marker on the same side of the fold changes neither, so it no longer re-fires.
 * A done relay drives nothing.
 *
 * @param createdAtAt createdAt of the list item at an index (null past the ends / for non-message rows),
 *   so the visible-gap test mirrors [RelayWindowLimitMarkers]'s placement against only the on-screen rows.
 */
@Composable
fun RelayWindowLimitSentinels(
    limits: List<RelayWindowLimit>,
    listState: LazyListState,
    createdAtAt: (index: Int) -> Long?,
) {
    limits.forEach { lim ->
        if (lim.state == RelayReachState.DONE) return@forEach
        key(lim.key) {
            val reached = rememberUpdatedState(lim.reachedUntil)
            val advance = rememberUpdatedState(lim.advance)
            val getAt = rememberUpdatedState(createdAtAt)
            LaunchedEffect(Unit) {
                snapshotFlow {
                    val r = reached.value
                    val at = getAt.value
                    // Visible if any on-screen row is the "newer" side of the gap holding this cursor —
                    // the same predicate RelayWindowLimitMarkers uses to place the marker, but over the
                    // visible rows only.
                    val onScreen =
                        listState.layoutInfo.visibleItemsInfo.any { info ->
                            reachedFallsInGap(r, at(info.index), at(info.index + 1))
                        }
                    // Pair so distinctUntilChanged also lets a landed page (r moved) re-fire while visible,
                    // not just the off→on-screen transition.
                    onScreen to r
                }.distinctUntilChanged()
                    .collect { (onScreen, r) ->
                        if (onScreen) {
                            // One line per sentinel fire — a re-fire LOOP would show the same key firing
                            // over and over (and whether its reached cursor is drifting).
                            Log.d("DMPagination") { "marker fire ${lim.key} reachedUntil=$r" }
                            advance.value()
                        }
                    }
            }
        }
    }
}

/**
 * Renders the window-limit markers for the relays whose limit falls in the gap between a newer message
 * (at [newerCreatedAt]) and its next-older neighbour (at [olderCreatedAt], null at the oldest end). Pure
 * UI: the load driving lives in [RelayWindowLimitSentinels], so this can be (re)placed freely per row on
 * every feed reorder without triggering any paging.
 */
@Composable
fun RelayWindowLimitMarkers(
    limits: List<RelayWindowLimit>,
    newerCreatedAt: Long?,
    olderCreatedAt: Long?,
) {
    val here =
        remember(limits, newerCreatedAt, olderCreatedAt) {
            limits.filter { reachedFallsInGap(it.reachedUntil, newerCreatedAt, olderCreatedAt) }
        }
    if (here.isEmpty()) return

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
        modifier = Modifier.padding(5.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = DividerThickness)
        Text(
            text = stringResource(Res.string.chats_history_relay_sync),
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
