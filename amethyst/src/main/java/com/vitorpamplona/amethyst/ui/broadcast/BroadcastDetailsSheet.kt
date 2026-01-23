/**
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.service.broadcast.BroadcastEvent
import com.vitorpamplona.amethyst.service.broadcast.BroadcastStatus
import com.vitorpamplona.amethyst.service.broadcast.RelayResult
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl

/**
 * Modal bottom sheet showing detailed relay results for a broadcast.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastDetailsSheet(
    broadcast: BroadcastEvent,
    onDismiss: () -> Unit,
    onRetryRelay: (NormalizedRelayUrl) -> Unit,
    onRetryAllFailed: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
        ) {
            // Header
            Text(
                text = "Broadcast Results",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(8.dp))

            // Summary
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusIcon(broadcast.status)

                Text(
                    text = "${broadcast.eventName} (kind ${broadcast.kind})",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.weight(1f))

                Text(
                    text = "${broadcast.successCount}/${broadcast.totalRelays} relays",
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        when (broadcast.status) {
                            BroadcastStatus.SUCCESS -> Color(0xFF22C55E)
                            BroadcastStatus.PARTIAL -> Color(0xFFF59E0B)
                            BroadcastStatus.FAILED -> Color(0xFFEF4444)
                            BroadcastStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
                        },
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Relay list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(broadcast.targetRelays) { relay ->
                    val result = broadcast.results[relay] ?: RelayResult.Pending
                    RelayResultRow(
                        relay = relay,
                        result = result,
                        onRetry = { onRetryRelay(relay) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Dismiss")
                }

                if (broadcast.failedRelays.isNotEmpty()) {
                    Button(
                        onClick = onRetryAllFailed,
                        modifier = Modifier.weight(1f),
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
private fun StatusIcon(status: BroadcastStatus) {
    val (icon, tint) =
        when (status) {
            BroadcastStatus.SUCCESS -> Icons.Default.CheckCircle to Color(0xFF22C55E)
            BroadcastStatus.PARTIAL -> Icons.Default.Error to Color(0xFFF59E0B)
            BroadcastStatus.FAILED -> Icons.Default.Error to Color(0xFFEF4444)
            BroadcastStatus.IN_PROGRESS -> Icons.Default.HourglassEmpty to MaterialTheme.colorScheme.primary
        }

    Icon(
        imageVector = icon,
        contentDescription = status.name,
        tint = tint,
        modifier = Modifier.size(24.dp),
    )
}

@Composable
private fun RelayResultRow(
    relay: NormalizedRelayUrl,
    result: RelayResult,
    onRetry: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status icon
        val (icon, tint) =
            when (result) {
                is RelayResult.Success -> Icons.Default.CheckCircle to Color(0xFF22C55E)
                is RelayResult.Error -> Icons.Default.Error to Color(0xFFEF4444)
                is RelayResult.Timeout -> Icons.Default.HourglassEmpty to Color(0xFFF59E0B)
                is RelayResult.Pending -> Icons.Default.HourglassEmpty to MaterialTheme.colorScheme.onSurfaceVariant
            }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
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
                        color = Color(0xFFEF4444),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                is RelayResult.Timeout -> {
                    Text(
                        text = "Timeout",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF59E0B),
                    )
                }
                else -> {}
            }
        }

        // Retry button for failed relays
        if (result is RelayResult.Error || result is RelayResult.Timeout) {
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
