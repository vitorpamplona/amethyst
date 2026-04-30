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
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
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
 * Sticky bottom action bar for the room screen.
 *
 * Layout: `[ start cluster ] · · · [ end cluster ]` with an optional
 * red status strip above for connection / broadcast / mute failures.
 *
 * Start cluster — driven by connection × broadcast × on-stage state:
 *
 * | State                                       | Start cluster contents                  |
 * |---------------------------------------------|-----------------------------------------|
 * | Idle / Closed / Failed connection           | `[Connect]`                             |
 * | Connecting / Reconnecting                   | status chip                             |
 * | Connected, audience                         | empty (system volume keys are enough)   |
 * | Connected, on stage, !canBroadcast          | `[Leave the Stage]`                     |
 * | Connected, on stage, mic idle               | `[Talk] [Leave the Stage]` (+ pill)     |
 * | Connected, on stage, going live             | status chip + `[Leave the Stage]`       |
 * | Connected, on stage, broadcasting           | `[MicMute] [Stop] [Leave the Stage]`    |
 * | Connected, on stage, broadcast failed       | `[Retry] [Leave the Stage]`             |
 *
 * End cluster: hand-raise (audience + connected only), react, leave room.
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
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ActionBarStatusStrip(ui = ui)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
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
                    isConnected = ui.connection is ConnectionUiState.Connected,
                    handRaised = handRaised,
                    onHandRaisedChange = onHandRaisedChange,
                    onShowReactionPicker = onShowReactionPicker,
                    onLeave = onLeave,
                )
            }
        }
    }
}

/** Single-line red error strip. Surfaces the most relevant failure. */
@Composable
private fun ActionBarStatusStrip(ui: NestUiState) {
    val text = ui.statusStripText() ?: return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun NestUiState.statusStripText(): String? {
    val connection = connection
    if (connection is ConnectionUiState.Failed) {
        return stringRes(R.string.nest_audio_failed, connection.reason)
    }
    return when (val b = broadcast) {
        is BroadcastUiState.Failed -> stringRes(R.string.nest_broadcast_failed, b.reason)
        is BroadcastUiState.Broadcasting -> b.muteError?.let { stringRes(R.string.nest_mute_failed, it) }
        else -> null
    }
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
            is ConnectionUiState.Idle,
            is ConnectionUiState.Closed,
            is ConnectionUiState.Failed,
            -> {
                ConnectButton(onClick = viewModel::connect)
            }

            is ConnectionUiState.Connecting -> {
                StatusChip(label = connectingLabel(connection))
            }

            is ConnectionUiState.Reconnecting -> {
                StatusChip(label = stringRes(R.string.nest_reconnecting))
            }

            is ConnectionUiState.Connected -> {
                when {
                    canBroadcast && isOnStage -> {
                        OnStageControls(
                            viewModel = viewModel,
                            broadcast = ui.broadcast,
                            speakerPubkeyHex = speakerPubkeyHex,
                        )
                    }

                    // On stage but no signing/permission — only thing we can do is step down.
                    isOnStage -> {
                        LeaveStageButton(onClick = { viewModel.setOnStage(false) })
                    }

                    // Audience: nothing here. Phone volume keys cover local volume.
                    else -> {
                        Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun OnStageControls(
    viewModel: NestViewModel,
    broadcast: BroadcastUiState,
    speakerPubkeyHex: String,
) {
    val leaveStage = {
        viewModel.stopBroadcast()
        viewModel.setOnStage(false)
    }
    when (broadcast) {
        BroadcastUiState.Idle -> {
            OnStageIdleControls(
                viewModel = viewModel,
                speakerPubkeyHex = speakerPubkeyHex,
            )
        }

        BroadcastUiState.Connecting -> {
            StatusChip(label = stringRes(R.string.nest_broadcast_connecting))
            // stopBroadcast() cancels the in-flight speakerConnectJob,
            // so leaving mid-handshake is safe.
            LeaveStageButton(onClick = leaveStage)
        }

        is BroadcastUiState.Broadcasting -> {
            MicMuteToggle(isMuted = broadcast.isMuted, onToggle = viewModel::setMicMuted)
            StopBroadcastButton(onClick = viewModel::stopBroadcast)
            LeaveStageButton(onClick = leaveStage)
        }

        is BroadcastUiState.Failed -> {
            // Reason is shown in the status strip; this button retries.
            TalkButton(
                onClick = { viewModel.startBroadcast(speakerPubkeyHex) },
                contentDescription = stringRes(R.string.nest_talk),
            )
            LeaveStageButton(onClick = { viewModel.setOnStage(false) })
        }
    }
}

@Composable
private fun OnStageIdleControls(
    viewModel: NestViewModel,
    speakerPubkeyHex: String,
) {
    val context = LocalContext.current
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionDenied = !granted
            if (granted) viewModel.startBroadcast(speakerPubkeyHex)
        }
    // The launcher callback never fires for Settings-deep-link grants, so
    // re-check on every recomposition to auto-clear the warning.
    val showDenialWarning =
        permissionDenied && !context.hasMicPermission()

    TalkButton(
        onClick = {
            if (context.hasMicPermission()) {
                viewModel.startBroadcast(speakerPubkeyHex)
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        contentDescription = stringRes(R.string.nest_talk),
    )
    LeaveStageButton(onClick = { viewModel.setOnStage(false) })
    if (showDenialWarning) {
        // After "Don't ask again" the launcher silently returns false;
        // expose a Settings deep-link.
        OutlinedButton(onClick = { context.openAppSettings() }) {
            Text(stringRes(R.string.nest_open_settings))
        }
    }
}

@Composable
private fun EndCluster(
    isOnStage: Boolean,
    isConnected: Boolean,
    handRaised: Boolean,
    onHandRaisedChange: (Boolean) -> Unit,
    onShowReactionPicker: () -> Unit,
    onLeave: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Hand-raise: only meaningful for connected audience. On stage
        // it's moot; disconnected it can't reach the room.
        if (!isOnStage && isConnected) {
            HandRaiseToggle(handRaised = handRaised, onToggle = onHandRaisedChange)
        }
        // React works in any state — even disconnected users can react
        // via the room note.
        FilledTonalIconButton(onClick = onShowReactionPicker) {
            Icon(
                symbol = MaterialSymbols.EmojiEmotions,
                contentDescription = stringRes(R.string.nest_reactions_button),
            )
        }
        Spacer(Modifier.width(4.dp))
        LeaveRoomButton(onClick = onLeave)
    }
}

// ── Reusable affordances ────────────────────────────────────────────────

@Composable
private fun ConnectButton(onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(stringRes(R.string.nest_connect))
    }
}

/** Disabled assist chip used as a status indicator (no click target). */
@Composable
private fun StatusChip(label: String) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
    )
}

@Composable
private fun LeaveStageButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(stringRes(R.string.nest_leave_stage))
    }
}

/**
 * Big primary 56dp mic button shown in the off-states (Idle, Failed)
 * to invite the user to start broadcasting. Larger than surrounding
 * 40dp icon buttons so the mic state is unmistakable.
 */
@Composable
private fun TalkButton(
    onClick: () -> Unit,
    contentDescription: String,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        Icon(
            symbol = MaterialSymbols.MicOff,
            contentDescription = contentDescription,
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * Big error-color 56dp mic button shown while broadcasting. Same
 * footprint as [TalkButton] so the on/off swap is impossible to miss.
 */
@Composable
private fun StopBroadcastButton(onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
    ) {
        Icon(
            symbol = MaterialSymbols.Mic,
            contentDescription = stringRes(R.string.nest_stop_talking),
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * Cheap broadcast-side mute toggle. Keeps the MoQ session open and
 * just stops sending audio frames; unmute is sample-accurate.
 */
@Composable
private fun MicMuteToggle(
    isMuted: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    FilledTonalIconToggleButton(
        checked = isMuted,
        onCheckedChange = onToggle,
        modifier = Modifier.size(width = 40.dp, height = ButtonDefaults.MinHeight),
    ) {
        Icon(
            symbol = if (isMuted) MaterialSymbols.AutoMirrored.VolumeOff else MaterialSymbols.AutoMirrored.VolumeUp,
            contentDescription = stringRes(if (isMuted) R.string.nest_mic_unmute else R.string.nest_mic_mute),
        )
    }
}

@Composable
private fun HandRaiseToggle(
    handRaised: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    FilledTonalIconToggleButton(
        checked = handRaised,
        onCheckedChange = onToggle,
    ) {
        Icon(
            symbol = MaterialSymbols.PanTool,
            contentDescription = stringRes(if (handRaised) R.string.nest_lower_hand else R.string.nest_raise_hand),
        )
    }
}

@Composable
private fun LeaveRoomButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
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

@Composable
private fun connectingLabel(connection: ConnectionUiState.Connecting): String =
    when (connection.step) {
        ConnectionUiState.Step.ResolvingRoom -> stringRes(R.string.nest_connecting_resolving)
        ConnectionUiState.Step.OpeningTransport -> stringRes(R.string.nest_connecting_transport)
        ConnectionUiState.Step.MoqHandshake -> stringRes(R.string.nest_connecting_handshake)
    }

// ── Permission helpers ──────────────────────────────────────────────────

private fun Context.hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    runCatching {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
    }
}
