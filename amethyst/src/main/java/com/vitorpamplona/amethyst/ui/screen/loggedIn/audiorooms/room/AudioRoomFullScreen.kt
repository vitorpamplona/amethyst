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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.room

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.vitorpamplona.amethyst.commons.viewmodels.AudioRoomUiState
import com.vitorpamplona.amethyst.commons.viewmodels.AudioRoomViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.BroadcastUiState
import com.vitorpamplona.amethyst.commons.viewmodels.ConnectionUiState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size40dp
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag

/**
 * Full-screen layout for [AudioRoomActivity]. Renders title + summary,
 * host/speaker/audience rows (with active-speaker rings), connection chip
 * + mute, talk row (when allowed), hand-raise, and a Leave button that
 * finishes the activity.
 *
 * The PIP variant lives in [AudioRoomPipScreen]; the activity flips
 * between them based on `isInPipMode`.
 */
@Composable
internal fun AudioRoomFullScreen(
    event: MeetingSpaceEvent,
    onStage: List<ParticipantTag>,
    audience: List<ParticipantTag>,
    viewModel: AudioRoomViewModel,
    ui: AudioRoomUiState,
    accountViewModel: AccountViewModel,
    handRaised: Boolean,
    onHandRaisedChange: (Boolean) -> Unit,
    onLeave: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        event.room()?.let {
            Text(text = it, style = MaterialTheme.typography.headlineSmall)
        }
        event.summary()?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (onStage.isNotEmpty()) {
            StagePeopleRow(
                label = stringRes(R.string.audio_room_stage),
                people = onStage,
                avatarSize = Size40dp,
                speakingNow = ui.speakingNow,
                accountViewModel = accountViewModel,
            )
        }
        if (audience.isNotEmpty()) {
            StagePeopleRow(
                label = stringRes(R.string.audio_room_audience),
                people = audience,
                avatarSize = Size35dp,
                speakingNow = kotlinx.collections.immutable.persistentSetOf(),
                accountViewModel = accountViewModel,
            )
        }

        ConnectionRow(viewModel = viewModel, ui = ui)

        val myPubkey = accountViewModel.account.signer.pubKey
        if (viewModel.canBroadcast && onStage.any { it.pubKey == myPubkey }) {
            TalkRow(viewModel = viewModel, ui = ui, speakerPubkeyHex = myPubkey)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconToggleButton(
                checked = handRaised,
                onCheckedChange = onHandRaisedChange,
            ) {
                Icon(
                    symbol = MaterialSymbols.PanTool,
                    contentDescription =
                        stringRes(
                            if (handRaised) R.string.audio_room_lower_hand else R.string.audio_room_raise_hand,
                        ),
                )
            }
            OutlinedButton(onClick = onLeave) {
                Text(stringRes(R.string.audio_room_leave))
            }
        }
    }
}

@Composable
private fun ConnectionRow(
    viewModel: AudioRoomViewModel,
    ui: AudioRoomUiState,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (val connection = ui.connection) {
            is ConnectionUiState.Idle, is ConnectionUiState.Closed -> {
                Button(onClick = { viewModel.connect() }) {
                    Text(stringRes(R.string.audio_room_connect))
                }
            }

            is ConnectionUiState.Connecting -> {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(connectingLabel(connection)) },
                )
            }

            is ConnectionUiState.Connected -> {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(stringRes(R.string.audio_room_connected)) },
                    colors =
                        AssistChipDefaults.assistChipColors(
                            disabledLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                )
                FilledTonalIconToggleButton(
                    checked = ui.isMuted,
                    onCheckedChange = { viewModel.setMuted(it) },
                ) {
                    Icon(
                        symbol = if (ui.isMuted) MaterialSymbols.AutoMirrored.VolumeOff else MaterialSymbols.AutoMirrored.VolumeUp,
                        contentDescription =
                            stringRes(
                                if (ui.isMuted) R.string.audio_room_unmute else R.string.audio_room_mute,
                            ),
                    )
                }
            }

            is ConnectionUiState.Failed -> {
                Text(
                    text = stringRes(R.string.audio_room_audio_failed, connection.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Button(onClick = { viewModel.connect() }) {
                    Text(stringRes(R.string.audio_room_connect))
                }
            }
        }
    }
}

@Composable
private fun TalkRow(
    viewModel: AudioRoomViewModel,
    ui: AudioRoomUiState,
    speakerPubkeyHex: String,
) {
    if (ui.connection !is ConnectionUiState.Connected) return
    val context = LocalContext.current
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.startBroadcast(speakerPubkeyHex) else permissionDenied = true
        }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
                    Text(stringRes(R.string.audio_room_talk))
                }
                if (permissionDenied) {
                    Text(
                        text = stringRes(R.string.audio_room_record_permission_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            BroadcastUiState.Connecting -> {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(stringRes(R.string.audio_room_broadcast_connecting)) },
                )
            }

            is BroadcastUiState.Broadcasting -> {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(stringRes(R.string.audio_room_broadcasting)) },
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
                        symbol = if (broadcast.isMuted) MaterialSymbols.AutoMirrored.VolumeOff else MaterialSymbols.AutoMirrored.VolumeUp,
                        contentDescription =
                            stringRes(
                                if (broadcast.isMuted) R.string.audio_room_mic_unmute else R.string.audio_room_mic_mute,
                            ),
                    )
                }
                OutlinedButton(onClick = { viewModel.stopBroadcast() }) {
                    Text(stringRes(R.string.audio_room_stop_talking))
                }
            }

            is BroadcastUiState.Failed -> {
                Text(
                    text = stringRes(R.string.audio_room_broadcast_failed, broadcast.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Button(onClick = { viewModel.startBroadcast(speakerPubkeyHex) }) {
                    Text(stringRes(R.string.audio_room_talk))
                }
            }
        }
    }
}
