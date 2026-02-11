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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.broadcast.BroadcastEvent
import com.vitorpamplona.amethyst.service.broadcast.BroadcastStatus
import com.vitorpamplona.amethyst.service.broadcast.BroadcastTracker
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Displays broadcast progress UI components:
 * - BroadcastBanner: Shows active broadcasts with progress
 * - CompletedBroadcastIndicator: Shows completed broadcast for tap-to-view (auto-dismisses after 10s)
 * - BroadcastDetailsSheet: Shows detailed relay status on tap
 *
 * Only shown when FeatureSetType.COMPLETE is enabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayBroadcastProgress(accountViewModel: AccountViewModel) {
    // Only show in COMPLETE UI mode
    if (!accountViewModel.settings.isCompleteUIMode()) return

    val activeBroadcasts by accountViewModel.broadcastTracker.activeBroadcasts.collectAsStateWithLifecycle()

    // State for completed broadcast (with auto-dismiss)
    var completedBroadcast by remember { mutableStateOf<BroadcastEvent?>(null) }

    // State for details sheet
    var selectedBroadcast by remember { mutableStateOf<BroadcastEvent?>(null) }

    // Collect completed broadcasts and set auto-dismiss timer
    LaunchedEffect(Unit) {
        accountViewModel.broadcastTracker.completedBroadcast.collect { broadcast ->
            completedBroadcast = broadcast

            // Auto-dismiss after LONG duration
            delay(BroadcastTracker.COMPLETED_DISPLAY_DURATION_MS)

            // Only dismiss if still showing same broadcast
            if (completedBroadcast?.id == broadcast.id) {
                completedBroadcast = null
                accountViewModel.broadcastTracker.expireBroadcast(broadcast.id)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Banner for active broadcasts (above bottom navigation)
        BroadcastBanner(
            broadcasts = activeBroadcasts,
            onTap = {
                activeBroadcasts.firstOrNull()?.let { selectedBroadcast = it }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 50.dp),
        )

        // Completed broadcast indicator (when no active broadcasts)
        if (activeBroadcasts.isEmpty()) {
            val scope = rememberCoroutineScope()
            CompletedBroadcastIndicator(
                broadcast = completedBroadcast,
                onTap = { broadcast ->
                    selectedBroadcast = broadcast
                },
                onRetry = { b ->
                    scope.launch {
                        accountViewModel.broadcastTracker.retry(
                            broadcast = b,
                            client = accountViewModel.account.client,
                        )
                    }
                },
                onDismiss = { broadcast ->
                    completedBroadcast = null
                    accountViewModel.broadcastTracker.expireBroadcast(broadcast.id)
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 50.dp),
            )
        }
    }

    // Details sheet - show all broadcasts when opened
    if (selectedBroadcast != null) {
        val scope = rememberCoroutineScope()
        // Combine active broadcasts with the selected/completed one
        val allBroadcasts =
            (activeBroadcasts + listOfNotNull(completedBroadcast))
                .distinctBy { it.id }

        MultiBroadcastDetailsSheet(
            broadcasts = allBroadcasts,
            onDismiss = { selectedBroadcast = null },
            onRetryRelay = { b: BroadcastEvent, relay: NormalizedRelayUrl ->
                scope.launch {
                    accountViewModel.broadcastTracker.retry(
                        broadcast = b,
                        client = accountViewModel.account.client,
                        specificRelay = relay,
                    )
                }
            },
            onRetryAllFailed = { b: BroadcastEvent ->
                scope.launch {
                    accountViewModel.broadcastTracker.retry(
                        broadcast = b,
                        client = accountViewModel.account.client,
                    )
                }
            },
        )
    }
}

/**
 * Compact indicator for completed broadcasts.
 * Styled consistently with BroadcastBanner.
 * Tappable to show details sheet, with retry button for failures.
 */
@Composable
private fun CompletedBroadcastIndicator(
    broadcast: BroadcastEvent?,
    onTap: (BroadcastEvent) -> Unit,
    onRetry: (BroadcastEvent) -> Unit,
    onDismiss: (BroadcastEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Status colors (for icon tint only)
    val successColor = Color(0xFF22C55E)
    val warningColor = Color(0xFFF59E0B)

    AnimatedVisibility(
        visible = broadcast != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(150)),
        modifier = modifier,
    ) {
        broadcast?.let { b ->
            val (statusIcon, iconTint) =
                when (b.status) {
                    BroadcastStatus.SUCCESS -> Icons.Default.CheckCircle to successColor
                    BroadcastStatus.PARTIAL -> Icons.Default.Error to warningColor
                    BroadcastStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
                    BroadcastStatus.IN_PROGRESS -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
                }

            // Same styling as BroadcastBanner
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onTap(b) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    // Small status icon (like BroadcastBanner)
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = b.status.name,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp),
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringRes(R.string.event_sent, b.eventName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )

                            Text(
                                text = stringRes(R.string.share_of, b.successCount, b.totalRelays),
                                style = MaterialTheme.typography.labelMedium,
                                color = iconTint,
                            )
                        }

                        if (b.failedRelays.isNotEmpty()) {
                            Text(
                                text = stringRes(R.string.tap_to_view_details),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Retry button for failures
                    if (b.failedRelays.isNotEmpty()) {
                        IconButton(
                            onClick = { onRetry(b) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringRes(R.string.retry_failed),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    // Dismiss X
                    Text(
                        text = "×",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .clickable { onDismiss(b) }
                                .padding(4.dp),
                    )
                }
            }
        }
    }
}
