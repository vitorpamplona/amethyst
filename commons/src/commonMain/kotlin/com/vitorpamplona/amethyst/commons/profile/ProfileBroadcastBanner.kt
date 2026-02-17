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
package com.vitorpamplona.amethyst.commons.profile

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
import androidx.compose.material.icons.filled.Error
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
 * Shared banner showing profile metadata broadcast progress.
 * Works on both Android and Desktop via Compose Multiplatform.
 */
@Composable
fun ProfileBroadcastBanner(
    status: ProfileBroadcastStatus,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVisible = status !is ProfileBroadcastStatus.Idle

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
                        .padding(horizontal = 16.dp, vertical = 10.dp)
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
                        verticalAlignment = Alignment.CenterVertically,
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

                    // Progress bar for broadcasting
                    if (status is ProfileBroadcastStatus.Broadcasting) {
                        Spacer(Modifier.height(4.dp))

                        val animatedProgress by animateFloatAsState(
                            targetValue = status.progress,
                            animationSpec = tween(300),
                            label = "broadcastProgress",
                        )

                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun getStatusBackgroundColor(status: ProfileBroadcastStatus): Color =
    when (status) {
        is ProfileBroadcastStatus.Failed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
        is ProfileBroadcastStatus.Success -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

private fun getStatusIcon(status: ProfileBroadcastStatus): ImageVector =
    when (status) {
        is ProfileBroadcastStatus.Broadcasting -> Icons.Default.Sync
        is ProfileBroadcastStatus.Success -> Icons.Default.CheckCircle
        is ProfileBroadcastStatus.Failed -> Icons.Default.Error
        is ProfileBroadcastStatus.Idle -> Icons.Default.CheckCircle
    }

@Composable
private fun getStatusIconColor(status: ProfileBroadcastStatus): Color =
    when (status) {
        is ProfileBroadcastStatus.Failed -> MaterialTheme.colorScheme.error
        is ProfileBroadcastStatus.Success -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }

private fun getStatusText(status: ProfileBroadcastStatus): String =
    when (status) {
        is ProfileBroadcastStatus.Broadcasting -> "Updating ${status.fieldName}..."
        is ProfileBroadcastStatus.Success -> "Updated ${status.fieldName}"
        is ProfileBroadcastStatus.Failed -> "Failed to update ${status.fieldName}"
        is ProfileBroadcastStatus.Idle -> ""
    }

private fun getStatusDetail(status: ProfileBroadcastStatus): String =
    when (status) {
        is ProfileBroadcastStatus.Broadcasting -> "[${status.successCount}/${status.totalRelays}]"
        is ProfileBroadcastStatus.Success -> "${status.relayCount} relays"
        is ProfileBroadcastStatus.Failed -> "Tap to retry"
        is ProfileBroadcastStatus.Idle -> ""
    }

@Composable
private fun getStatusDetailColor(status: ProfileBroadcastStatus): Color =
    when (status) {
        is ProfileBroadcastStatus.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
