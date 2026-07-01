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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.relays.health.LatencyMetric
import com.vitorpamplona.amethyst.commons.relays.health.RelayLatencySnapshot
import com.vitorpamplona.amethyst.commons.relays.health.SlowReason
import com.vitorpamplona.amethyst.desktop.network.Nip11Fetcher
import com.vitorpamplona.amethyst.desktop.network.RelayMetrics
import com.vitorpamplona.amethyst.desktop.network.RelayStatus
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isOnion
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation

@Composable
fun RelayMetricCard(
    status: RelayStatus,
    metrics: RelayMetrics?,
    latency: RelayLatencySnapshot?,
    slowReason: SlowReason?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    nip11Fetcher: Nip11Fetcher,
    modifier: Modifier = Modifier,
) {
    // NIP-11 fetched per-card to avoid parent recomposition
    val nip11 by produceState<Nip11RelayInformation?>(null, status.url) {
        value = nip11Fetcher.fetch(status.url)
    }

    Card(onClick = onToggleExpand, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status indicator
                    Icon(
                        MaterialSymbols.Circle,
                        contentDescription = if (status.connected) "Connected" else "Disconnected",
                        modifier = Modifier.size(10.dp),
                        tint =
                            if (status.connected) {
                                MaterialTheme.colorScheme.primary
                            } else if (status.error != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                    )

                    Spacer(Modifier.width(8.dp))

                    Column {
                        Text(
                            nip11?.name ?: status.url.displayUrl(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (nip11?.name != null) {
                            Text(
                                status.url.displayUrl(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Ping + Tor badge
                    if (status.pingMs != null) {
                        Text(
                            "${status.pingMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (status.url.isOnion()) {
                        Text(
                            ".onion",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }

                    // Per-metric p50s (rolling last 50 samples). Three compact columns. Missing
                    // metrics simply omit their cell — common during the first ~60 s after start
                    // before the tracker's first snapshot lands.
                    LatencyCell("OK", latency?.p50Of(LatencyMetric.OK_ACK))
                    LatencyCell("EOSE", latency?.p50Of(LatencyMetric.EOSE))
                    LatencyCell("FR", latency?.p50Of(LatencyMetric.FIRST_RESULT))

                    if (slowReason != null) {
                        SlowRelayChip(slowReason)
                    }

                    // Event count
                    if (metrics != null && metrics.eventCount > 0) {
                        Text(
                            "${metrics.eventCount} events",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Last event relative time
                    if (metrics?.lastEventAt != null) {
                        val ago = formatRelativeTime(metrics.lastEventAt)
                        Text(
                            ago,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Error message
            if (status.error != null) {
                Text(
                    status.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        AnimatedVisibility(isExpanded) {
            RelayDetailPanel(nip11, latency = latency, slowReason = slowReason)
        }
    }
}

/** Compact single-column latency display: "label / p50ms" stacked. Hidden when no sample. */
@Composable
private fun LatencyCell(
    label: String,
    p50: Int?,
) {
    if (p50 == null) return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${p50}ms",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** "Slow: OK 2.4×" outlined chip. Static (no ripple) — matches UnhealthyRelayRow's tag style. */
@Composable
private fun SlowRelayChip(reason: SlowReason) {
    val label =
        when (reason.metric) {
            LatencyMetric.OK_ACK -> "OK"
            LatencyMetric.EOSE -> "EOSE"
            LatencyMetric.FIRST_RESULT -> "FR"
            LatencyMetric.PING -> "Ping"
        }
    val multiplierText = String.format("%.1f", reason.multiplier)
    androidx.compose.material3.AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(
                "Slow: $label $multiplierText×",
                style = MaterialTheme.typography.labelSmall,
            )
        },
        colors =
            androidx.compose.material3.AssistChipDefaults.assistChipColors(
                disabledLabelColor = MaterialTheme.colorScheme.error,
                disabledContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            ),
        border = null,
    )
}

fun formatRelativeTime(epochMs: Long): String {
    val diffMs = System.currentTimeMillis() - epochMs
    val seconds = diffMs / 1000
    return when {
        seconds < 5 -> "just now"
        seconds < 60 -> "${seconds}s ago"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        else -> "${seconds / 86400}d ago"
    }
}
