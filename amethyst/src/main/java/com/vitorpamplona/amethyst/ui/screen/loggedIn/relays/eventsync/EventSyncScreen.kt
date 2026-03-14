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
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.eventsync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun EventSyncScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val syncViewModel = accountViewModel.eventSyncViewModel

    val syncState by syncViewModel.syncState.collectAsStateWithLifecycle()
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
            Spacer(Modifier.height(8.dp))

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

            // ---- Progress / Status area ----
            when (val state = syncState) {
                is EventSyncViewModel.SyncState.Idle -> Unit

                is EventSyncViewModel.SyncState.Running -> {
                    SyncProgressCard(state = state)
                }

                is EventSyncViewModel.SyncState.Paused -> {
                    PausedCard(state = state)
                }

                is EventSyncViewModel.SyncState.Done -> {
                    DoneCard(state = state)
                }

                is EventSyncViewModel.SyncState.Error -> {
                    ErrorCard(message = state.message)
                }
            }

            // ---- Action buttons ----
            when (val state = syncState) {
                is EventSyncViewModel.SyncState.Idle,
                is EventSyncViewModel.SyncState.Done,
                is EventSyncViewModel.SyncState.Error,
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

                is EventSyncViewModel.SyncState.Paused -> {
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

                is EventSyncViewModel.SyncState.Running -> {
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
                val isPaused = syncState is EventSyncViewModel.SyncState.Paused
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

@Composable
private fun SyncProgressCard(state: EventSyncViewModel.SyncState.Running) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringRes(R.string.event_sync_batch_of, state.chunkIndex, state.totalChunks),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.chunkIndex.toFloat() / state.totalChunks },
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
private fun PausedCard(state: EventSyncViewModel.SyncState.Paused) {
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
                        state.nextChunkIndex,
                        state.totalChunks,
                        state.eventsSent,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun DoneCard(state: EventSyncViewModel.SyncState.Done) {
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
            Spacer(Modifier.height(4.dp))
            Text(
                text =
                    stringRes(
                        R.string.event_sync_done_body,
                        state.totalEventsSent,
                        (state.durationMs / 1000).toInt(),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
