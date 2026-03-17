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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.eventsync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

@Composable
fun EventSyncScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val syncViewModel = accountViewModel.eventSync

    val syncState by syncViewModel.syncState.collectAsStateWithLifecycle()
    val liveActivity by syncViewModel.liveActivity.collectAsStateWithLifecycle()
    val isMobileOrMetered by accountViewModel.settings.isMobileOrMeteredConnection.collectAsStateWithLifecycle()
    var showMobileDataDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopBarWithBackButton(
                caption = stringRes(R.string.event_sync_title),
                popBack = nav::popBack,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Progress / Status area ----
            when (val state = syncState) {
                is EventSync.SyncState.Idle -> {
                    // ---- Explanation card ----
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringRes(R.string.event_sync_what_happens_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringRes(R.string.event_sync_what_happens_body),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            StepRow(number = "1", text = stringRes(R.string.event_sync_step1))
                            Spacer(Modifier.height(4.dp))
                            StepRow(number = "2", text = stringRes(R.string.event_sync_step2))
                            Spacer(Modifier.height(4.dp))
                            StepRow(number = "3", text = stringRes(R.string.event_sync_step3))
                        }
                    }

                    // ---- WiFi warning ----
                    if (isMobileOrMetered) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                ),
                        ) {
                            Text(
                                text = stringRes(R.string.event_sync_wifi_warning),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                is EventSync.SyncState.Running -> {
                    SyncProgressCard(state = state)
                }

                is EventSync.SyncState.Paused -> {
                    PausedCard(state = state)
                }

                is EventSync.SyncState.Done -> {
                    DoneCard(state = state)
                }

                is EventSync.SyncState.Error -> {
                    ErrorCard(message = state.message)
                }
            }

            // ---- Live relay activity (shown during and after sync) ----
            if (liveActivity.outboxTargets.isNotEmpty() ||
                liveActivity.inboxTargets.isNotEmpty() ||
                liveActivity.dmTargets.isNotEmpty()
            ) {
                DestinationRelaysCard(activity = liveActivity)
            }

            if (liveActivity.recentCompletions.isNotEmpty()) {
                ActivityLogCard(completions = liveActivity.recentCompletions)
            }

            // ---- Action buttons ----
            when (val state = syncState) {
                is EventSync.SyncState.Idle,
                is EventSync.SyncState.Done,
                is EventSync.SyncState.Error,
                -> {
                    Button(
                        onClick = {
                            if (isMobileOrMetered) {
                                showMobileDataDialog = true
                            } else {
                                syncViewModel.start()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringRes(R.string.event_sync_start))
                    }
                }

                is EventSync.SyncState.Paused -> {
                    Button(
                        onClick = {
                            if (isMobileOrMetered) {
                                showMobileDataDialog = true
                            } else {
                                syncViewModel.resume()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringRes(R.string.event_sync_resume))
                    }
                    OutlinedButton(
                        onClick = { syncViewModel.start() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringRes(R.string.event_sync_start_over))
                    }
                }

                is EventSync.SyncState.Running -> {
                    OutlinedButton(
                        onClick = { syncViewModel.cancel() },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                    ) {
                        Text(stringRes(R.string.event_sync_pause))
                    }
                }
            }

            // ---- Mobile-data confirmation dialog ----
            if (showMobileDataDialog) {
                val isPaused = syncState is EventSync.SyncState.Paused
                AlertDialog(
                    onDismissRequest = { showMobileDataDialog = false },
                    title = { Text(stringRes(R.string.event_sync_mobile_data_dialog_title)) },
                    text = { Text(stringRes(R.string.event_sync_wifi_warning)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showMobileDataDialog = false
                                if (isPaused) syncViewModel.resume() else syncViewModel.start()
                            },
                        ) {
                            Text(stringRes(R.string.event_sync_start_anyway))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showMobileDataDialog = false }) {
                            Text(stringRes(R.string.event_sync_cancel))
                        }
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// -------------------------------------------------------------------------
// Progress / status cards
// -------------------------------------------------------------------------

@Composable
private fun SyncProgressCard(state: EventSync.SyncState.Running) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringRes(R.string.event_sync_relays_progress, state.relaysCompleted, state.totalRelays),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = {
                    if (state.totalRelays > 0) {
                        state.relaysCompleted.toFloat() / state.totalRelays
                    } else {
                        0f
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringRes(R.string.event_sync_events_sent, state.eventsSent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PausedCard(state: EventSync.SyncState.Paused) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringRes(R.string.event_sync_paused_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text =
                    stringRes(
                        R.string.event_sync_paused_body,
                        state.nextRelayIndex,
                        state.totalRelays,
                        state.eventsSent,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun DoneCard(state: EventSync.SyncState.Done) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringRes(R.string.event_sync_done_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringRes(R.string.event_sync_done_sent, state.totalEventsSent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringRes(R.string.event_sync_done_accepted, state.totalEventsAccepted),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringRes(R.string.event_sync_done_duration, (state.durationMs / 1000).toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringRes(R.string.event_sync_error_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

// -------------------------------------------------------------------------
// Live activity cards
// -------------------------------------------------------------------------

/**
 * Shows where events are being sent: outbox, inbox, and DM relay lists.
 */
@Composable
private fun DestinationRelaysCard(activity: EventSync.LiveSyncActivity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringRes(R.string.event_sync_sending_to),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            if (activity.outboxTargets.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                DestinationSection(
                    label = stringRes(R.string.event_sync_outbox_relays),
                    relays = activity.outboxTargets,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (activity.inboxTargets.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))
                DestinationSection(
                    label = stringRes(R.string.event_sync_inbox_relays),
                    relays = activity.inboxTargets,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            if (activity.dmTargets.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))
                DestinationSection(
                    label = stringRes(R.string.event_sync_dm_relays),
                    relays = activity.dmTargets,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun DestinationSection(
    label: String,
    relays: List<EventSync.LiveSyncActivity.DestinationRelayInfo>,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        relays.forEachIndexed { index, info ->
            if (index > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
            DestinationRelayRow(info = info, color = color)
        }
    }
}

@Composable
private fun DestinationRelayRow(
    info: EventSync.LiveSyncActivity.DestinationRelayInfo,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Text(
            text = info.relay.displayHost(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (info.eventsSent > 0) {
            Text(
                text = stringRes(R.string.event_sync_log_recv, formatCount(info.eventsSent)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringRes(R.string.event_sync_log_new, formatCount(info.eventsAccepted)),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (info.eventsAccepted > 0) FontWeight.SemiBold else FontWeight.Normal,
                color = if (info.eventsAccepted > 0) color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Scrollable log of recently completed relays, newest at the top.
 * Uses a fixed-height inner scroll area so it doesn't compete with the outer scroll.
 */
@Composable
private fun ActivityLogCard(completions: List<EventSync.LiveSyncActivity.CompletedRelayInfo>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringRes(R.string.event_sync_activity_log, completions.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    completions.forEachIndexed { index, info ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        ActivityLogRow(info = info)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityLogRow(info: EventSync.LiveSyncActivity.CompletedRelayInfo) {
    val hasEvents = info.eventsFound > 0
    val dotColor =
        if (hasEvents) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        }
    val textColor =
        if (hasEvents) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
        )
        Text(
            text = info.relay.displayHost(),
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (hasEvents) {
            Text(
                text = stringRes(R.string.event_sync_log_recv, formatCount(info.eventsFound)),
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringRes(R.string.event_sync_log_new, formatCount(info.eventsAccepted)),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (info.eventsAccepted > 0) FontWeight.SemiBold else FontWeight.Normal,
                color =
                    if (info.eventsAccepted > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        } else {
            Text(
                text = stringRes(R.string.event_sync_no_events),
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
            )
        }
    }
}

// -------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------

@Composable
private fun StepRow(
    number: String,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Strips the WebSocket scheme and trailing slash for compact display. */
private fun NormalizedRelayUrl.displayHost(): String =
    url
        .removePrefix("wss://")
        .removePrefix("ws://")
        .trimEnd('/')

/** Formats a count with K/M suffix for large numbers. */
private fun formatCount(n: Int): String =
    when {
        n >= 1_000_000 -> "${n / 1_000_000}M"
        n >= 1_000 -> "${n / 1_000}K"
        else -> n.toString()
    }

// -------------------------------------------------------------------------
// Preview data
// -------------------------------------------------------------------------

private val previewCompletions =
    listOf(
        EventSync.LiveSyncActivity.CompletedRelayInfo(NormalizedRelayUrl("wss://relay.damus.io"), 1247, 891),
        EventSync.LiveSyncActivity.CompletedRelayInfo(NormalizedRelayUrl("wss://nos.lol"), 892, 45),
        EventSync.LiveSyncActivity.CompletedRelayInfo(NormalizedRelayUrl("wss://relay.nostr.band"), 3500, 3498),
        EventSync.LiveSyncActivity.CompletedRelayInfo(NormalizedRelayUrl("wss://slow.relay.example.com"), 0, 0),
        EventSync.LiveSyncActivity.CompletedRelayInfo(NormalizedRelayUrl("wss://nostr.bitcoiner.social"), 15, 0),
        EventSync.LiveSyncActivity.CompletedRelayInfo(NormalizedRelayUrl("wss://unreachable.relay.xyz"), 0, 0),
    )

private val previewActivity =
    EventSync.LiveSyncActivity(
        recentCompletions = previewCompletions,
        outboxTargets =
            listOf(
                EventSync.LiveSyncActivity.DestinationRelayInfo(NormalizedRelayUrl("wss://outbox.nostr.com"), 1247, 891),
                EventSync.LiveSyncActivity.DestinationRelayInfo(NormalizedRelayUrl("wss://relay.damus.io"), 892, 45),
            ),
        inboxTargets =
            listOf(
                EventSync.LiveSyncActivity.DestinationRelayInfo(NormalizedRelayUrl("wss://inbox.nostr.com"), 500, 500),
                EventSync.LiveSyncActivity.DestinationRelayInfo(NormalizedRelayUrl("wss://nos.lol"), 0, 0),
            ),
        dmTargets =
            listOf(
                EventSync.LiveSyncActivity.DestinationRelayInfo(NormalizedRelayUrl("wss://dm.nostr.com"), 15, 10),
            ),
    )

// -------------------------------------------------------------------------
// Previews
// -------------------------------------------------------------------------

@Composable
@Preview
fun SyncProgressCardPreview() {
    ThemeComparisonColumn {
        SyncProgressCard(
            state =
                EventSync.SyncState.Running(
                    relaysCompleted = 312,
                    totalRelays = 1024,
                    eventsSent = 4821,
                ),
        )
    }
}

@Composable
@Preview
fun PausedCardPreview() {
    ThemeComparisonColumn {
        PausedCard(
            state =
                EventSync.SyncState.Paused(
                    nextRelayIndex = 260,
                    totalRelays = 1024,
                    eventsSent = 3200,
                ),
        )
    }
}

@Composable
@Preview
fun DoneCardPreview() {
    ThemeComparisonColumn {
        DoneCard(
            state =
                EventSync.SyncState.Done(
                    totalEventsSent = 18_432,
                    totalEventsAccepted = 14_891,
                    durationMs = 187_000,
                ),
        )
    }
}

@Composable
@Preview
fun ErrorCardPreview() {
    ThemeComparisonColumn {
        ErrorCard(message = "No outbox, inbox, or DM relays configured.")
    }
}

@Composable
@Preview
fun DestinationRelaysCardPreview() {
    ThemeComparisonColumn {
        DestinationRelaysCard(activity = previewActivity)
    }
}

@Composable
@Preview
fun ActivityLogCardPreview() {
    ThemeComparisonColumn {
        ActivityLogCard(completions = previewCompletions)
    }
}
