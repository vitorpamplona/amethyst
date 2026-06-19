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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.relays.health.LatencyMetric
import com.vitorpamplona.amethyst.commons.relays.health.RelayLatencySnapshot
import com.vitorpamplona.amethyst.commons.relays.health.SlowReason
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation

@Composable
fun RelayDetailPanel(
    nip11: Nip11RelayInformation?,
    latency: RelayLatencySnapshot? = null,
    slowReason: SlowReason? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(12.dp),
    ) {
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

        if (nip11 == null && latency == null) {
            Text(
                "Relay info unavailable",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        nip11?.let { info ->
            // Description
            info.description?.let { desc ->
                Text(desc, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }

            // Software + version
            info.software?.let { sw ->
                val version = info.version?.let { " v$it" } ?: ""
                DetailRow("Software", "$sw$version")
            }

            // Supported NIPs
            info.supported_nips?.let { nips ->
                if (nips.isNotEmpty()) {
                    DetailRow("NIPs", nips.joinToString(", "))
                }
            }

            // Payment status
            val paymentRequired = info.limitation?.payment_required == true
            DetailRow("Payment", if (paymentRequired) "Paid" else "Free")
        }

        if (latency != null && latency.samples.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                "Latency (rolling last 50 samples)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            LatencyMetric.entries.forEach { metric ->
                val sample = latency.samples[metric] ?: return@forEach
                val label =
                    when (metric) {
                        LatencyMetric.OK_ACK -> "OK ACK (publish→OK)"
                        LatencyMetric.EOSE -> "EOSE (REQ→EOSE)"
                        LatencyMetric.FIRST_RESULT -> "First result (REQ→1st event)"
                        LatencyMetric.PING -> "Ping (connect)"
                    }
                val multiplier =
                    if (slowReason?.metric == metric) {
                        " — ${String.format("%.1f", slowReason.multiplier)}× cohort median"
                    } else {
                        ""
                    }
                DetailRow(label, "${sample.p50Ms} ms (${sample.count} samples)$multiplier")
            }
            if (slowReason?.metric == LatencyMetric.FIRST_RESULT) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "First-result depends on filter content (popular pubkey ≠ quiet pubkey) — " +
                        "treat as a coarse signal alongside OK ACK / EOSE.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Text(
        "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}
