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
package com.vitorpamplona.amethyst.desktop.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.chess.RelaySyncState
import com.vitorpamplona.amethyst.commons.chess.RelaySyncStatus
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import kotlinx.collections.immutable.ImmutableList

@Composable
fun SearchSyncBanner(
    relayStates: ImmutableList<RelaySyncState>,
    isSearching: Boolean,
    modifier: Modifier = Modifier,
) {
    val isVisible = isSearching || relayStates.isNotEmpty()
    var isExpanded by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        modifier = modifier,
    ) {
        Column {
            // Collapsed summary row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
            ) {
                val responded = relayStates.count { it.status == RelaySyncStatus.EOSE_RECEIVED }
                val total = relayStates.size
                val totalEvents = relayStates.sumOf { it.eventsReceived }

                Text(
                    text =
                        if (total > 0) {
                            "$responded/$total relays responded \u00B7 $totalEvents events"
                        } else {
                            "Connecting to relays..."
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Icon(
                    symbol = if (isExpanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Expanded per-relay details
            AnimatedVisibility(
                visible = isExpanded && relayStates.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp,
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    ) {
                        relayStates.forEach { relay ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    symbol = relayStatusIcon(relay.status),
                                    contentDescription = null,
                                    tint = relayStatusColor(relay.status),
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = relay.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${relay.eventsReceived} events",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun relayStatusIcon(status: RelaySyncStatus): MaterialSymbol =
    when (status) {
        RelaySyncStatus.CONNECTING -> MaterialSymbols.HourglassEmpty
        RelaySyncStatus.WAITING -> MaterialSymbols.HourglassEmpty
        RelaySyncStatus.RECEIVING -> MaterialSymbols.CloudDownload
        RelaySyncStatus.EOSE_RECEIVED -> MaterialSymbols.CheckCircle
        RelaySyncStatus.FAILED -> MaterialSymbols.Error
    }

@Composable
private fun relayStatusColor(status: RelaySyncStatus): Color =
    when (status) {
        RelaySyncStatus.CONNECTING -> MaterialTheme.colorScheme.secondary
        RelaySyncStatus.WAITING -> MaterialTheme.colorScheme.secondary
        RelaySyncStatus.RECEIVING -> MaterialTheme.colorScheme.primary
        RelaySyncStatus.EOSE_RECEIVED -> MaterialTheme.colorScheme.primary
        RelaySyncStatus.FAILED -> MaterialTheme.colorScheme.error
    }
