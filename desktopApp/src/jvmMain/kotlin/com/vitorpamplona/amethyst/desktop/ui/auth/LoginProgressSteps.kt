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
package com.vitorpamplona.amethyst.desktop.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.desktop.account.LoginProgress
import com.vitorpamplona.amethyst.desktop.account.RelayLoginStatus

private enum class StepState { DONE, ACTIVE, PENDING }

private data class StepInfo(
    val label: String,
    val state: StepState,
)

@Composable
fun LoginProgressSteps(
    progress: LoginProgress,
    modifier: Modifier = Modifier,
) {
    val steps = buildStepList(progress)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        steps.forEach { step ->
            StepRow(step)

            // Show relay rows under the active step
            if (step.state == StepState.ACTIVE && progress.relayStatuses.isNotEmpty()) {
                progress.relayStatuses.forEach { (relay, status) ->
                    RelayRow(relay.url, status)
                }
            }
        }
    }
}

private fun buildStepList(progress: LoginProgress): List<StepInfo> {
    val orderedSteps =
        listOf(
            "Connecting to relays",
            "Waiting for signer",
            "Sending acknowledgment",
        )

    val activeIndex =
        when (progress) {
            is LoginProgress.ConnectingToRelays -> 0
            is LoginProgress.WaitingForSigner -> 1
            is LoginProgress.SendingAck -> 2
        }

    return orderedSteps.mapIndexed { index, label ->
        StepInfo(
            label = label,
            state =
                when {
                    index < activeIndex -> StepState.DONE
                    index == activeIndex -> StepState.ACTIVE
                    else -> StepState.PENDING
                },
        )
    }
}

@Composable
private fun StepRow(step: StepInfo) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        when (step.state) {
            StepState.DONE -> {
                Icon(
                    MaterialSymbols.Check,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(16.dp),
                )
            }

            StepState.ACTIVE -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            }

            StepState.PENDING -> {
                Spacer(Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            step.label,
            style = MaterialTheme.typography.bodySmall,
            color =
                when (step.state) {
                    StepState.DONE -> Color(0xFF4CAF50)
                    StepState.ACTIVE -> MaterialTheme.colorScheme.onSurface
                    StepState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
        )
    }
}

@Composable
private fun RelayRow(
    url: String,
    status: RelayLoginStatus,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp),
    ) {
        when (status) {
            RelayLoginStatus.EVENT_SENT -> {
                Icon(
                    MaterialSymbols.Check,
                    contentDescription = null,
                    tint = Color(0xFF2196F3),
                    modifier = Modifier.size(12.dp),
                )
            }

            RelayLoginStatus.CONNECTED -> {
                Icon(
                    MaterialSymbols.Check,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(12.dp),
                )
            }

            RelayLoginStatus.FAILED,
            RelayLoginStatus.SEND_FAILED,
            -> {
                Icon(
                    MaterialSymbols.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(12.dp),
                )
            }

            RelayLoginStatus.CONNECTING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        val label = url.removePrefix("wss://").removeSuffix("/")
        val statusLabel =
            when (status) {
                RelayLoginStatus.EVENT_SENT -> "$label (sent)"
                RelayLoginStatus.SEND_FAILED -> "$label (send failed)"
                RelayLoginStatus.FAILED -> "$label (failed)"
                else -> label
            }
        Text(
            statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color =
                when (status) {
                    RelayLoginStatus.FAILED, RelayLoginStatus.SEND_FAILED -> {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    }

                    RelayLoginStatus.EVENT_SENT -> {
                        Color(0xFF2196F3)
                    }

                    else -> {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                },
        )
    }
}
