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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.viewmodels.BroadcastUiState
import com.vitorpamplona.amethyst.commons.viewmodels.ConnectionUiState
import com.vitorpamplona.amethyst.commons.viewmodels.NestUiState
import com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel
import com.vitorpamplona.amethyst.ui.stringRes

/**
 * Sticky bottom action bar for the room screen. Replaces the three
 * separate rows (`ConnectionRow`, `TalkRow`, hand/react/leave) that
 * used to scroll away with the rest of the metadata.
 *
 * Layout adapts to connection / broadcast / on-stage state:
 *   - Disconnected → `[Connect]` (start) · `[Leave]` (end)
 *   - Connecting / Reconnecting → state chip · `[Leave]`
 *   - Connected, audience → `[Listen mute toggle]` · `[Hand] [React] [Leave]`
 *   - Connected, on-stage idle → `[Talk] [Leave stage]` · `[React] [Leave]`
 *   - Connected, on-stage live → `[Mic mute] [Stop] [Leave stage]` · `[React] [Leave]`
 *   - Failed (connection or broadcast) → status strip above the bar
 *     plus a retry button in the start cluster.
 *
 * The Hand button hides when the user is already on stage — the
 * action it represents ("I want to speak") doesn't apply once you can.
 */
@Composable
internal fun NestActionBar(
    viewModel: NestViewModel,
    ui: NestUiState,
    isOnStage: Boolean,
    canBroadcast: Boolean,
    speakerPubkeyHex: String,
    handRaised: Boolean,
    onHandRaisedChange: (Boolean) -> Unit,
    onShowReactionPicker: () -> Unit,
    onLeave: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(),
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ActionBarStatusStrip(ui = ui)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f, fill = true)) {
                    StartCluster(
                        viewModel = viewModel,
                        ui = ui,
                        isOnStage = isOnStage,
                        canBroadcast = canBroadcast,
                        speakerPubkeyHex = speakerPubkeyHex,
                    )
                }
                EndCluster(
                    isOnStage = isOnStage,
                    handRaised = handRaised,
                    onHandRaisedChange = onHandRaisedChange,
                    onShowReactionPicker = onShowReactionPicker,
                    onLeave = onLeave,
                )
            }
        }
    }
}

@Composable
private fun ActionBarStatusStrip(ui: NestUiState) {
    val text =
        when (val connection = ui.connection) {
            is ConnectionUiState.Failed -> {
                stringRes(R.string.nest_audio_failed, connection.reason)
            }

            else -> {
                when (val broadcast = ui.broadcast) {
                    is BroadcastUiState.Failed -> {
                        stringRes(R.string.nest_broadcast_failed, broadcast.reason)
                    }

                    else -> {
                        null
                    }
                }
            }
        }
    if (text == null) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun StartCluster(
    viewModel: NestViewModel,
    ui: NestUiState,
    isOnStage: Boolean,
    canBroadcast: Boolean,
    speakerPubkeyHex: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (val connection = ui.connection) {
            is ConnectionUiState.Idle, is ConnectionUiState.Closed -> {
                Button(onClick = { viewModel.connect() }) {
                    Text(stringRes(R.string.nest_connect))
                }
            }

            is ConnectionUiState.Connecting -> {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(connectingLabel(connection)) },
                )
            }

            is ConnectionUiState.Reconnecting -> {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(stringRes(R.string.nest_reconnecting)) },
                )
            }

            is ConnectionUiState.Connected -> {
                if (canBroadcast && isOnStage) {
                    OnStageControls(
                        viewModel = viewModel,
                        ui = ui,
                        speakerPubkeyHex = speakerPubkeyHex,
                    )
                } else {
                    // Audience: a single mute toggle for the inbound
                    // listener stream. Mirrors the old ConnectionRow.
                    FilledTonalIconToggleButton(
                        checked = ui.isMuted,
                        onCheckedChange = { viewModel.setMuted(it) },
                    ) {
                        Icon(
                            symbol =
                                if (ui.isMuted) {
                                    MaterialSymbols.AutoMirrored.VolumeOff
                                } else {
                                    MaterialSymbols.AutoMirrored.VolumeUp
                                },
                            contentDescription =
                                stringRes(
                                    if (ui.isMuted) R.string.nest_unmute else R.string.nest_mute,
                                ),
                        )
                    }
                }
            }

            is ConnectionUiState.Failed -> {
                Button(onClick = { viewModel.connect() }) {
                    Text(stringRes(R.string.nest_connect))
                }
            }
        }
    }
}

@Composable
private fun OnStageControls(
    viewModel: NestViewModel,
    ui: NestUiState,
    speakerPubkeyHex: String,
) {
    val context = LocalContext.current
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                permissionDenied = false
                viewModel.startBroadcast(speakerPubkeyHex)
            } else {
                permissionDenied = true
            }
        }
    // If the user grants RECORD_AUDIO via the system Settings deep-link
    // and returns to the activity, the launcher callback never fires —
    // recompute every time so the warning auto-clears.
    val showDenialWarning =
        permissionDenied &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED

    when (val broadcast = ui.broadcast) {
        BroadcastUiState.Idle -> {
            Button(onClick = {
                val granted =
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    viewModel.startBroadcast(speakerPubkeyHex)
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }) {
                Text(stringRes(R.string.nest_talk))
            }
            OutlinedButton(onClick = { viewModel.setOnStage(false) }) {
                Text(stringRes(R.string.nest_leave_stage))
            }
            if (showDenialWarning) {
                // After "Don't ask again" the permission launcher
                // silently returns false; surface a Settings deep-link.
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(
                            android.content
                                .Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", context.packageName, null),
                                ).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                },
                        )
                    }
                }) {
                    Text(stringRes(R.string.nest_open_settings))
                }
            }
        }

        BroadcastUiState.Connecting -> {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(stringRes(R.string.nest_broadcast_connecting)) },
            )
        }

        is BroadcastUiState.Broadcasting -> {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(stringRes(R.string.nest_broadcasting)) },
                colors =
                    AssistChipDefaults.assistChipColors(
                        disabledLabelColor = MaterialTheme.colorScheme.error,
                    ),
            )
            FilledTonalIconToggleButton(
                checked = broadcast.isMuted,
                onCheckedChange = { viewModel.setMicMuted(it) },
            ) {
                Icon(
                    symbol =
                        if (broadcast.isMuted) {
                            MaterialSymbols.AutoMirrored.VolumeOff
                        } else {
                            MaterialSymbols.AutoMirrored.VolumeUp
                        },
                    contentDescription =
                        stringRes(
                            if (broadcast.isMuted) R.string.nest_mic_unmute else R.string.nest_mic_mute,
                        ),
                )
            }
            OutlinedButton(onClick = { viewModel.stopBroadcast() }) {
                Text(stringRes(R.string.nest_stop_talking))
            }
            OutlinedButton(onClick = {
                viewModel.stopBroadcast()
                viewModel.setOnStage(false)
            }) {
                Text(stringRes(R.string.nest_leave_stage))
            }
        }

        is BroadcastUiState.Failed -> {
            Button(onClick = { viewModel.startBroadcast(speakerPubkeyHex) }) {
                Text(stringRes(R.string.nest_talk))
            }
        }
    }
}

@Composable
private fun EndCluster(
    isOnStage: Boolean,
    handRaised: Boolean,
    onHandRaisedChange: (Boolean) -> Unit,
    onShowReactionPicker: () -> Unit,
    onLeave: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Hand-raise only makes sense for audience: if you're already
        // on stage, you don't need to ask. Hidden when on-stage.
        if (!isOnStage) {
            FilledTonalIconToggleButton(
                checked = handRaised,
                onCheckedChange = onHandRaisedChange,
            ) {
                Icon(
                    symbol = MaterialSymbols.PanTool,
                    contentDescription =
                        stringRes(
                            if (handRaised) R.string.nest_lower_hand else R.string.nest_raise_hand,
                        ),
                )
            }
        }
        // React is always visible — even disconnected users can
        // (un)react via the reactions picker on the room note.
        FilledTonalIconButton(onClick = onShowReactionPicker) {
            Icon(
                symbol = MaterialSymbols.EmojiEmotions,
                contentDescription = stringRes(R.string.nest_reactions_button),
            )
        }
        Spacer(Modifier.width(4.dp))
        OutlinedButton(
            onClick = onLeave,
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
        ) {
            Icon(
                symbol = MaterialSymbols.AutoMirrored.Logout,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(6.dp))
            Text(stringRes(R.string.nest_leave))
        }
    }
}

@Composable
private fun connectingLabel(connection: ConnectionUiState.Connecting): String =
    when (connection.step) {
        ConnectionUiState.Step.ResolvingRoom -> stringRes(R.string.nest_connecting_resolving)
        ConnectionUiState.Step.OpeningTransport -> stringRes(R.string.nest_connecting_transport)
        ConnectionUiState.Step.MoqHandshake -> stringRes(R.string.nest_connecting_handshake)
    }
