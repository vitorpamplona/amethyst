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
package com.vitorpamplona.amethyst.commons.chess

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Shared banner showing chess sync/subscription status with expandable relay details.
 * Shows incoming event progress from relays during refresh.
 */
@Composable
fun ChessSyncBanner(
    status: ChessSyncStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVisible = status !is ChessSyncStatus.Idle
    var isExpanded by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(200)),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(150)),
        modifier = modifier,
    ) {
        Surface(
            color = getSyncStatusBackgroundColor(status),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier =
                    Modifier
                        .animateContentSize()
                        .clickable {
                            if (status is ChessSyncStatus.PartialSync) {
                                onRetry()
                            } else {
                                isExpanded = !isExpanded
                            }
                        },
            ) {
                // Main status row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = getSyncStatusIcon(status),
                        contentDescription = null,
                        tint = getSyncStatusIconColor(status),
                        modifier = Modifier.size(18.dp),
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = getSyncStatusText(status),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )

                            Spacer(Modifier.width(8.dp))

                            Text(
                                text = getSyncStatusDetail(status),
                                style = MaterialTheme.typography.labelMedium,
                                color = getSyncStatusDetailColor(status),
                            )
                        }

                        // Progress bar for syncing
                        if (status is ChessSyncStatus.Syncing) {
                            Spacer(Modifier.height(4.dp))
                            val progress = status.eoseCount.toFloat() / status.totalCount.coerceAtLeast(1)
                            val animatedProgress by animateFloatAsState(
                                targetValue = progress,
                                animationSpec = tween(300),
                                label = "syncProgress",
                            )
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }

                    // Expand/collapse icon
                    if (status !is ChessSyncStatus.Idle && getRelayStates(status).isNotEmpty()) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Expandable relay details
                AnimatedVisibility(
                    visible = isExpanded && getRelayStates(status).isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                        )
                        RelayStatusList(
                            relayStates = getRelayStates(status),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RelayStatusList(
    relayStates: List<RelaySyncState>,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        relayStates.forEach { relay ->
            RelayStatusRow(relay)
        }
    }
}

@Composable
private fun RelayStatusRow(relay: RelaySyncState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = getRelayStatusIcon(relay.status),
            contentDescription = null,
            tint = getRelayStatusColor(relay.status),
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

private fun getRelayStates(status: ChessSyncStatus): List<RelaySyncState> =
    when (status) {
        is ChessSyncStatus.Syncing -> status.relayStates
        is ChessSyncStatus.Synced -> status.relayStates
        is ChessSyncStatus.PartialSync -> status.relayStates
        is ChessSyncStatus.Idle -> emptyList()
    }

private fun getRelayStatusIcon(status: RelaySyncStatus): ImageVector =
    when (status) {
        RelaySyncStatus.CONNECTING -> Icons.Default.HourglassEmpty
        RelaySyncStatus.WAITING -> Icons.Default.HourglassEmpty
        RelaySyncStatus.RECEIVING -> Icons.Default.CloudDownload
        RelaySyncStatus.EOSE_RECEIVED -> Icons.Default.CheckCircle
        RelaySyncStatus.FAILED -> Icons.Default.Error
    }

@Composable
private fun getRelayStatusColor(status: RelaySyncStatus): Color =
    when (status) {
        RelaySyncStatus.CONNECTING -> MaterialTheme.colorScheme.secondary
        RelaySyncStatus.WAITING -> MaterialTheme.colorScheme.secondary
        RelaySyncStatus.RECEIVING -> MaterialTheme.colorScheme.primary
        RelaySyncStatus.EOSE_RECEIVED -> MaterialTheme.colorScheme.primary
        RelaySyncStatus.FAILED -> MaterialTheme.colorScheme.error
    }

@Composable
private fun getSyncStatusBackgroundColor(status: ChessSyncStatus): Color =
    when (status) {
        is ChessSyncStatus.PartialSync -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        is ChessSyncStatus.Synced -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

private fun getSyncStatusIcon(status: ChessSyncStatus): ImageVector =
    when (status) {
        is ChessSyncStatus.Syncing -> Icons.Default.CloudDownload
        is ChessSyncStatus.Synced -> Icons.Default.CheckCircle
        is ChessSyncStatus.PartialSync -> Icons.Default.Warning
        is ChessSyncStatus.Idle -> Icons.Default.CheckCircle
    }

@Composable
private fun getSyncStatusIconColor(status: ChessSyncStatus): Color =
    when (status) {
        is ChessSyncStatus.PartialSync -> MaterialTheme.colorScheme.error
        is ChessSyncStatus.Synced -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }

private fun getSyncStatusText(status: ChessSyncStatus): String =
    when (status) {
        is ChessSyncStatus.Syncing -> "Syncing ${status.phase}..."
        is ChessSyncStatus.Synced -> "Synced"
        is ChessSyncStatus.PartialSync -> status.message
        is ChessSyncStatus.Idle -> ""
    }

private fun getSyncStatusDetail(status: ChessSyncStatus): String =
    when (status) {
        is ChessSyncStatus.Syncing -> "${status.eoseCount}/${status.totalCount} relays • ${status.totalEventsReceived} events"
        is ChessSyncStatus.Synced -> "${status.successCount}/${status.totalCount} relays • ${status.challengeCount} challenges • ${status.gameCount} games"
        is ChessSyncStatus.PartialSync -> "Tap to retry"
        is ChessSyncStatus.Idle -> ""
    }

@Composable
private fun getSyncStatusDetailColor(status: ChessSyncStatus): Color =
    when (status) {
        is ChessSyncStatus.PartialSync -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
