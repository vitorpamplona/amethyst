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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.service.broadcast.BroadcastEvent
import com.vitorpamplona.amethyst.service.broadcast.BroadcastStatus
import kotlinx.coroutines.flow.SharedFlow

/**
 * Custom snackbar visuals for broadcast results.
 */
data class BroadcastSnackbarVisuals(
    val broadcast: BroadcastEvent,
    override val actionLabel: String? = "View",
    override val duration: SnackbarDuration = SnackbarDuration.Short,
    override val withDismissAction: Boolean = true,
) : SnackbarVisuals {
    override val message: String
        get() =
            when (broadcast.status) {
                BroadcastStatus.SUCCESS -> "${broadcast.eventName} sent to ${broadcast.successCount}/${broadcast.totalRelays} relays"
                BroadcastStatus.PARTIAL -> "${broadcast.eventName} sent to ${broadcast.successCount}/${broadcast.totalRelays} relays"
                BroadcastStatus.FAILED -> "${broadcast.eventName} failed - 0/${broadcast.totalRelays} relays"
                BroadcastStatus.IN_PROGRESS -> "Broadcasting ${broadcast.eventName}..."
            }
}

/**
 * Snackbar host that listens to completed broadcasts and shows result notifications.
 */
@Composable
fun BroadcastSnackbarHost(
    completedBroadcast: SharedFlow<BroadcastEvent>,
    onViewDetails: (BroadcastEvent) -> Unit,
    onRetry: (BroadcastEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(completedBroadcast) {
        completedBroadcast.collect { broadcast ->
            val visuals = BroadcastSnackbarVisuals(broadcast)
            val result = snackbarHostState.showSnackbar(visuals)

            when (result) {
                SnackbarResult.ActionPerformed -> {
                    if (broadcast.status == BroadcastStatus.FAILED) {
                        onRetry(broadcast)
                    } else {
                        onViewDetails(broadcast)
                    }
                }

                SnackbarResult.Dismissed -> { /* No action */ }
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier.padding(bottom = 8.dp),
    ) { snackbarData ->
        BroadcastSnackbarContent(snackbarData)
    }
}

@Composable
private fun BroadcastSnackbarContent(snackbarData: SnackbarData) {
    val visuals = snackbarData.visuals
    val broadcastVisuals = visuals as? BroadcastSnackbarVisuals

    val containerColor =
        when (broadcastVisuals?.broadcast?.status) {
            BroadcastStatus.FAILED -> Color(0xFF7F1D1D)

            // Dark red
            BroadcastStatus.PARTIAL -> Color(0xFF78350F)

            // Dark amber
            else -> Color(0xFF1E3A5F) // Dark blue (default)
        }

    val actionLabel =
        when (broadcastVisuals?.broadcast?.status) {
            BroadcastStatus.FAILED -> "Retry"
            else -> "View"
        }

    Snackbar(
        action = {
            snackbarData.visuals.actionLabel?.let {
                TextButton(onClick = { snackbarData.performAction() }) {
                    Text(actionLabel)
                }
            }
        },
        dismissAction =
            if (visuals.withDismissAction) {
                {
                    TextButton(onClick = { snackbarData.dismiss() }) {
                        Text("Dismiss")
                    }
                }
            } else {
                null
            },
        containerColor = containerColor,
    ) {
        Text(visuals.message)
    }
}
