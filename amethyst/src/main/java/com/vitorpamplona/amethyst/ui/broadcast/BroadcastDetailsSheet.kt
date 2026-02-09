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
package com.vitorpamplona.amethyst.ui.broadcast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.service.broadcast.BroadcastEvent
import com.vitorpamplona.amethyst.service.broadcast.BroadcastStatus
import com.vitorpamplona.amethyst.service.broadcast.RelayResult
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl

private const val MAX_EXPANDED_SECTIONS = 2

/**
 * Modal bottom sheet showing detailed relay results for broadcasts.
 * Shows up to 2 expanded sections, with a summary for additional broadcasts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastDetailsSheet(
    broadcast: BroadcastEvent,
    onDismiss: () -> Unit,
    onRetryRelay: (NormalizedRelayUrl) -> Unit,
    onRetryAllFailed: () -> Unit,
    sheetState: SheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
) {
    MultiBroadcastDetailsSheet(
        broadcasts = listOf(broadcast),
        onDismiss = onDismiss,
        onRetryRelay = { _, relay -> onRetryRelay(relay) },
        onRetryAllFailed = { onRetryAllFailed() },
        sheetState = sheetState,
    )
}

/**
 * Modal bottom sheet showing detailed relay results for multiple broadcasts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiBroadcastDetailsSheet(
    broadcasts: List<BroadcastEvent>,
    onDismiss: () -> Unit,
    onRetryRelay: (BroadcastEvent, NormalizedRelayUrl) -> Unit,
    onRetryAllFailed: (BroadcastEvent) -> Unit,
    sheetState: SheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
) {
    // Synced rotation animation for all pending/retrying icons
    val infiniteTransition = rememberInfiniteTransition(label = "pendingRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "rotation",
    )

    // Track which sections are expanded (by broadcast ID)
    var expandedSections by remember { mutableStateOf(setOf<String>()) }

    // Sort: in-progress first, then by start time
    val sortedBroadcasts =
        broadcasts.sortedWith(
            compareBy<BroadcastEvent> { it.status != BroadcastStatus.IN_PROGRESS }
                .thenByDescending { it.startedAt },
        )

    // First 2 get shown expanded by default (unless completed)
    val visibleBroadcasts = sortedBroadcasts.take(MAX_EXPANDED_SECTIONS)
    val overflowBroadcasts = sortedBroadcasts.drop(MAX_EXPANDED_SECTIONS)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            // Header
            Text(
                text = if (broadcasts.size == 1) "Broadcast Results" else "Broadcasts (${broadcasts.size})",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(16.dp))

            // Visible broadcast sections
            visibleBroadcasts.forEachIndexed { index, broadcast ->
                val isExpanded =
                    broadcast.id in expandedSections ||
                        (broadcast.status == BroadcastStatus.IN_PROGRESS && broadcast.id !in expandedSections)

                // Auto-expand in-progress, auto-collapse completed (unless manually expanded)
                val showExpanded =
                    if (broadcast.status == BroadcastStatus.IN_PROGRESS) {
                        true
                    } else {
                        broadcast.id in expandedSections
                    }

                BroadcastSection(
                    broadcast = broadcast,
                    isExpanded = showExpanded,
                    onToggleExpand = {
                        expandedSections =
                            if (broadcast.id in expandedSections) {
                                expandedSections - broadcast.id
                            } else {
                                expandedSections + broadcast.id
                            }
                    },
                    onRetryRelay = { relay -> onRetryRelay(broadcast, relay) },
                    onRetryAllFailed = { onRetryAllFailed(broadcast) },
                    rotationAngle = rotationAngle,
                )

                if (index < visibleBroadcasts.size - 1 || overflowBroadcasts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Overflow summary
            if (overflowBroadcasts.isNotEmpty()) {
                OverflowSummary(overflowBroadcasts)
            }

            Spacer(Modifier.height(16.dp))

            // Dismiss button
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun BroadcastSection(
    broadcast: BroadcastEvent,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRetryRelay: (NormalizedRelayUrl) -> Unit,
    onRetryAllFailed: () -> Unit,
    rotationAngle: Float,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Section header (clickable to expand/collapse)
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
            color = Color.Transparent,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                StatusIcon(broadcast.status, rotationAngle)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = broadcast.eventName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "kind ${broadcast.kind}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = "${broadcast.successCount}/${broadcast.totalRelays}",
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor(broadcast.status),
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // Expandable relay list
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                broadcast.targetRelays.forEach { relay ->
                    val result = broadcast.results[relay] ?: RelayResult.Pending
                    RelayResultRow(
                        relay = relay,
                        result = result,
                        onRetry = { onRetryRelay(relay) },
                        rotationAngle = rotationAngle,
                    )
                }

                // Retry all button
                if (broadcast.failedRelays.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onRetryAllFailed,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Retry Failed (${broadcast.failedRelays.size})")
                    }
                }
            }
        }
    }
}

@Composable
private fun OverflowSummary(broadcasts: List<BroadcastEvent>) {
    val totalSuccess = broadcasts.sumOf { it.successCount }
    val totalRelays = broadcasts.sumOf { it.totalRelays }
    val inProgressCount = broadcasts.count { it.status == BroadcastStatus.IN_PROGRESS }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.HourglassEmpty,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "+${broadcasts.size} more broadcasts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        buildString {
                            append("$totalSuccess/$totalRelays relays")
                            if (inProgressCount > 0) {
                                append(" ($inProgressCount in progress)")
                            }
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun statusColor(status: BroadcastStatus): Color =
    when (status) {
        BroadcastStatus.SUCCESS -> Color(0xFF22C55E)
        BroadcastStatus.PARTIAL -> Color(0xFFF59E0B)
        BroadcastStatus.FAILED -> MaterialTheme.colorScheme.error
        BroadcastStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
    }

@Composable
private fun StatusIcon(
    status: BroadcastStatus,
    rotationAngle: Float = 0f,
) {
    val (icon, tint, shouldRotate) =
        when (status) {
            BroadcastStatus.SUCCESS -> Triple(Icons.Default.CheckCircle, Color(0xFF22C55E), false)
            BroadcastStatus.PARTIAL -> Triple(Icons.Default.Error, Color(0xFFF59E0B), false)
            BroadcastStatus.FAILED -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, false)
            BroadcastStatus.IN_PROGRESS -> Triple(Icons.Default.HourglassEmpty, MaterialTheme.colorScheme.primary, true)
        }

    Icon(
        imageVector = icon,
        contentDescription = status.name,
        tint = tint,
        modifier =
            Modifier
                .size(24.dp)
                .then(
                    if (shouldRotate) {
                        Modifier.graphicsLayer { rotationZ = rotationAngle }
                    } else {
                        Modifier
                    },
                ),
    )
}

@Composable
private fun RelayResultRow(
    relay: NormalizedRelayUrl,
    result: RelayResult,
    onRetry: () -> Unit,
    rotationAngle: Float = 0f,
) {
    val successColor = Color(0xFF22C55E)
    val errorColor = MaterialTheme.colorScheme.error
    val warningColor = Color(0xFFF59E0B)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status icon
        val (icon, tint, shouldRotate) =
            when (result) {
                is RelayResult.Success -> Triple(Icons.Default.CheckCircle, successColor, false)
                is RelayResult.Error -> Triple(Icons.Default.Error, errorColor, false)
                is RelayResult.Timeout -> Triple(Icons.Default.HourglassEmpty, warningColor, false)
                is RelayResult.Pending -> Triple(Icons.Default.HourglassEmpty, MaterialTheme.colorScheme.onSurfaceVariant, true)
                is RelayResult.Retrying -> Triple(Icons.Default.HourglassEmpty, MaterialTheme.colorScheme.primary, true)
            }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier =
                Modifier
                    .size(20.dp)
                    .then(
                        if (shouldRotate) {
                            Modifier.graphicsLayer { rotationZ = rotationAngle }
                        } else {
                            Modifier
                        },
                    ),
        )

        Spacer(Modifier.width(12.dp))

        // Relay URL
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = relay.displayUrl(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Error message if present
            when (result) {
                is RelayResult.Error -> {
                    Text(
                        text = "[${result.code}]${result.message?.let { " $it" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = errorColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                is RelayResult.Timeout -> {
                    Text(
                        text = "Timeout",
                        style = MaterialTheme.typography.bodySmall,
                        color = warningColor,
                    )
                }

                is RelayResult.Retrying -> {
                    Text(
                        text = "Retrying...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                else -> {}
            }
        }

        // Retry button for failed relays (not when already retrying)
        if ((result is RelayResult.Error || result is RelayResult.Timeout) && result !is RelayResult.Retrying) {
            IconButton(
                onClick = onRetry,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
