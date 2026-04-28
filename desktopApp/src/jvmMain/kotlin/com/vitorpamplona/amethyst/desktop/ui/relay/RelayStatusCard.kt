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
package com.vitorpamplona.amethyst.desktop.ui.relay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.tor.TorServiceStatus
import com.vitorpamplona.amethyst.desktop.network.RelayStatus
import com.vitorpamplona.amethyst.desktop.ui.tor.LocalTorState
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isOnion

/**
 * Card displaying the status of a Nostr relay connection.
 * Shows connection status, ping time, compression, and errors.
 */
@Composable
fun RelayStatusCard(
    status: RelayStatus,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                val statusColor =
                    when {
                        status.connected -> Color.Green
                        status.error != null -> Color.Red
                        else -> Color.Gray
                    }

                if (status.connected) {
                    Icon(
                        MaterialSymbols.Check,
                        contentDescription = "Connected",
                        tint = statusColor,
                        modifier = Modifier.size(20.dp),
                    )
                } else if (status.error != null) {
                    Icon(
                        MaterialSymbols.Close,
                        contentDescription = "Error",
                        tint = statusColor,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            status.url.url,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        // .onion badge: show "Requires Tor" when Tor is off
                        if (status.url.isOnion()) {
                            val torState = LocalTorState.current
                            val badgeColor =
                                if (torState.status is TorServiceStatus.Off) {
                                    Color(0xFFF44336) // Red — Tor required but off
                                } else {
                                    Color(0xFF4CAF50) // Green — routed via Tor
                                }
                            val badgeText =
                                if (torState.status is TorServiceStatus.Off) "Requires Tor" else "via Tor"
                            Text(
                                badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                            )
                        }
                    }
                    if (status.connected && status.pingMs != null) {
                        Text(
                            "${status.pingMs}ms${if (status.compressed) " • compressed" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    status.error?.let { error ->
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Red.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            IconButton(onClick = onRemove) {
                Icon(
                    MaterialSymbols.Close,
                    contentDescription = "Remove relay",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
