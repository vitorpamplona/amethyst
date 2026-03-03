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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Shared banner showing chess broadcast status - broadcast progress, sync status, etc.
 * Works on both Android and Desktop via Compose Multiplatform.
 */
@Composable
fun ChessBroadcastBanner(
    status: ChessBroadcastStatus,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVisible = status !is ChessBroadcastStatus.Idle

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(200)),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(150)),
        modifier = modifier,
    ) {
        Surface(
            color = getStatusBackgroundColor(status),
            tonalElevation = 2.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTap),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .animateContentSize(),
            ) {
                Icon(
                    imageVector = getStatusIcon(status),
                    contentDescription = null,
                    tint = getStatusIconColor(status),
                    modifier = Modifier.size(18.dp),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = getStatusText(status),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        Spacer(Modifier.width(8.dp))

                        Text(
                            text = getStatusDetail(status),
                            style = MaterialTheme.typography.labelMedium,
                            color = getStatusDetailColor(status),
                        )
                    }

                    // Show progress bar for broadcasting/syncing
                    val progress = getStatusProgress(status)
                    if (progress != null) {
                        Spacer(Modifier.height(4.dp))

                        val animatedProgress by animateFloatAsState(
                            targetValue = progress,
                            animationSpec = tween(300),
                            label = "progress",
                        )

                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = getStatusProgressColor(status),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun getStatusBackgroundColor(status: ChessBroadcastStatus): Color =
    when (status) {
        is ChessBroadcastStatus.Failed, is ChessBroadcastStatus.Desynced -> {
            MaterialTheme.colorScheme.errorContainer
        }

        is ChessBroadcastStatus.Success -> {
            MaterialTheme.colorScheme.primaryContainer
        }

        else -> {
            MaterialTheme.colorScheme.surfaceContainer
        }
    }

private fun getStatusIcon(status: ChessBroadcastStatus): ImageVector =
    when (status) {
        is ChessBroadcastStatus.Broadcasting -> Icons.Default.Sync
        is ChessBroadcastStatus.Success -> Icons.Default.CheckCircle
        is ChessBroadcastStatus.Failed -> Icons.Default.Error
        is ChessBroadcastStatus.WaitingForOpponent -> Icons.Default.HourglassBottom
        is ChessBroadcastStatus.Syncing -> Icons.Default.CloudSync
        is ChessBroadcastStatus.Desynced -> Icons.Default.Error
        is ChessBroadcastStatus.Idle -> Icons.Default.CheckCircle
    }

@Composable
private fun getStatusIconColor(status: ChessBroadcastStatus): Color =
    when (status) {
        is ChessBroadcastStatus.Failed, is ChessBroadcastStatus.Desynced -> {
            MaterialTheme.colorScheme.error
        }

        is ChessBroadcastStatus.Success -> {
            MaterialTheme.colorScheme.primary
        }

        is ChessBroadcastStatus.WaitingForOpponent -> {
            MaterialTheme.colorScheme.secondary
        }

        else -> {
            MaterialTheme.colorScheme.primary
        }
    }

private fun getStatusText(status: ChessBroadcastStatus): String =
    when (status) {
        is ChessBroadcastStatus.Broadcasting -> "Broadcasting: ${status.san}"
        is ChessBroadcastStatus.Success -> "Sent: ${status.san}"
        is ChessBroadcastStatus.Failed -> "Failed: ${status.san}"
        is ChessBroadcastStatus.WaitingForOpponent -> "Waiting for opponent's move..."
        is ChessBroadcastStatus.Syncing -> "Syncing game state..."
        is ChessBroadcastStatus.Desynced -> "Game desynced: ${status.message}"
        is ChessBroadcastStatus.Idle -> ""
    }

private fun getStatusDetail(status: ChessBroadcastStatus): String =
    when (status) {
        is ChessBroadcastStatus.Broadcasting -> "[${status.successCount}/${status.totalRelays}]"
        is ChessBroadcastStatus.Success -> "${status.relayCount} relays"
        is ChessBroadcastStatus.Failed -> "Tap to retry"
        is ChessBroadcastStatus.Syncing -> "${(status.progress * 100).toInt()}%"
        is ChessBroadcastStatus.Desynced -> "Tap to resync"
        else -> ""
    }

@Composable
private fun getStatusDetailColor(status: ChessBroadcastStatus): Color =
    when (status) {
        is ChessBroadcastStatus.Failed, is ChessBroadcastStatus.Desynced -> {
            MaterialTheme.colorScheme.error
        }

        else -> {
            MaterialTheme.colorScheme.primary
        }
    }

private fun getStatusProgress(status: ChessBroadcastStatus): Float? =
    when (status) {
        is ChessBroadcastStatus.Broadcasting -> status.progress
        is ChessBroadcastStatus.Syncing -> status.progress
        else -> null
    }

@Composable
private fun getStatusProgressColor(status: ChessBroadcastStatus): Color =
    when (status) {
        is ChessBroadcastStatus.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
