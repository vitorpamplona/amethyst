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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.viewmodels.AudioRoomViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.BroadcastUiState
import com.vitorpamplona.amethyst.commons.viewmodels.ConnectionUiState
import com.vitorpamplona.amethyst.service.audiorooms.AudioRoomForegroundService
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size40dp
import com.vitorpamplona.nestsclient.OkHttpNestsClient
import com.vitorpamplona.nestsclient.audio.AudioRecordCapture
import com.vitorpamplona.nestsclient.audio.AudioTrackPlayer
import com.vitorpamplona.nestsclient.audio.MediaCodecOpusDecoder
import com.vitorpamplona.nestsclient.audio.MediaCodecOpusEncoder
import com.vitorpamplona.nestsclient.transport.QuicWebTransportFactory
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Clubhouse-style audio-room "stage" rendered in place of the video player when
 * the underlying activity is a NIP-53 kind 30312 [MeetingSpaceEvent].
 *
 * Responsibilities:
 *   - Displays host / speaker / audience avatars parsed from the 30312 `p` tags.
 *     Avatars get a primary-color ring while their MoQ track is delivering audio.
 *   - Publishes kind 10312 presence on enter and every 30 s while composed,
 *     reflecting the hand-raise + mic-mute state.
 *   - **Listener** path: Connect button → HTTP → WebTransport over QUIC → MoQ
 *     listener session → Opus decode → AudioTrack. Auto-subscribes to every
 *     host + speaker track. Mute toggle silences the local device without
 *     halting the network so unmute is instant.
 *   - **Speaker** path (only for users in the room's `p` tags as host or
 *     speaker): Talk button → RECORD_AUDIO permission → second MoQ session
 *     in publisher mode → AudioRecord → MediaCodec Opus encoder → MoQ
 *     OBJECT_DATAGRAM emission. Live indicator + mic-mute toggle.
 */
@Composable
fun AudioRoomStage(
    baseChannel: LiveActivitiesChannel,
    accountViewModel: AccountViewModel,
) {
    LoadAddressableNote(baseChannel.address, accountViewModel) { addressableNote ->
        addressableNote ?: return@LoadAddressableNote
        val event = addressableNote.event as? MeetingSpaceEvent ?: return@LoadAddressableNote
        AudioRoomStageContent(event, accountViewModel)
    }
}

@Composable
private fun AudioRoomStageContent(
    event: MeetingSpaceEvent,
    accountViewModel: AccountViewModel,
) {
    val participants = remember(event) { event.participants() }
    val hosts = remember(participants) { participants.filter { it.role.equals(ROLE.HOST.code, true) } }
    val speakers = remember(participants) { participants.filter { it.role.equals(ROLE.SPEAKER.code, true) } }
    val audience =
        remember(participants) {
            participants.filter {
                !it.role.equals(ROLE.HOST.code, true) &&
                    !it.role.equals(ROLE.SPEAKER.code, true)
            }
        }
    val onStage = remember(hosts, speakers) { hosts + speakers }
    val onStageKeys = remember(onStage) { onStage.map { it.pubKey }.toSet() }

    var handRaised by rememberSaveable(event.address().toValue()) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val account = accountViewModel.account

    val serviceBase = event.service()
    val roomId = event.address().dTag
    val audioAvailable = !serviceBase.isNullOrBlank() && roomId.isNotBlank()

    val viewModel: AudioRoomViewModel? =
        if (audioAvailable) {
            val signer = account.signer
            val viewModelKey = remember(serviceBase, roomId) { "$serviceBase|$roomId" }
            viewModel(
                key = viewModelKey,
                factory =
                    remember(viewModelKey, signer) {
                        AudioRoomViewModelFactory(
                            signer = signer,
                            serviceBase = serviceBase,
                            roomId = roomId,
                        )
                    },
            )
        } else {
            null
        }

    LaunchedEffect(viewModel, onStageKeys) {
        viewModel?.updateSpeakers(onStageKeys)
    }

    val ui = viewModel?.uiState?.collectAsState()?.value
    val speakingNow = ui?.speakingNow ?: persistentSetOf()

    // Foreground-service lifecycle. The service is a process-anchor — it
    // doesn't own the audio resources (those live in the VM) but its
    // foreground notification + wake-lock keep audio playing with the
    // screen off. Promotes to mediaPlayback+microphone type while the user
    // broadcasts (Android 14+ split foreground-type permission model).
    val context = LocalContext.current
    val isConnected = ui?.connection is ConnectionUiState.Connected
    val isBroadcasting = ui?.broadcast is BroadcastUiState.Broadcasting
    LaunchedEffect(isConnected, isBroadcasting) {
        when {
            isConnected && isBroadcasting -> AudioRoomForegroundService.promoteToMicrophone(context)
            isConnected -> AudioRoomForegroundService.startListening(context)
            else -> AudioRoomForegroundService.stop(context)
        }
    }
    DisposableEffect(Unit) {
        onDispose { AudioRoomForegroundService.stop(context) }
    }
    // Mic state for the presence event: null when not broadcasting (we have
    // no mic stream to be muted on), explicit true/false while live so other
    // clients can render the mute indicator on our avatar.
    val micMutedTag: Boolean? =
        when (val b = ui?.broadcast) {
            is BroadcastUiState.Broadcasting -> b.isMuted
            else -> null
        }

    // Publish initial presence on enter and refresh every PRESENCE_REFRESH_MS while composed.
    LaunchedEffect(event.address().toValue(), handRaised, micMutedTag) {
        publishPresence(account, event, handRaised, micMutedTag)
        while (isActive) {
            delay(PRESENCE_REFRESH_MS)
            publishPresence(account, event, handRaised, micMutedTag)
        }
    }

    // Best-effort "leave" — re-publish a lowered-hand presence so peers see us
    // drop sooner than the 30 s heartbeat would otherwise allow.
    DisposableEffect(event.address().toValue()) {
        onDispose {
            scope.launch(Dispatchers.IO) {
                runCatching { publishPresence(account, event, handRaised = false, micMuted = null) }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            event.room()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            event.summary()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (onStage.isNotEmpty()) {
                StagePeopleRow(
                    label = stringRes(R.string.audio_room_stage),
                    people = onStage,
                    avatarSize = Size40dp,
                    speakingNow = speakingNow,
                    accountViewModel = accountViewModel,
                )
            }

            if (audience.isNotEmpty()) {
                StagePeopleRow(
                    label = stringRes(R.string.audio_room_audience),
                    people = audience,
                    avatarSize = Size35dp,
                    speakingNow = persistentSetOf(),
                    accountViewModel = accountViewModel,
                )
            }

            if (viewModel != null && ui != null) {
                AudioConnectionRow(viewModel = viewModel, ui = ui)
                val myPubkey = account.signer.pubKey
                val canTalkRoleWise = onStage.any { it.pubKey == myPubkey }
                if (viewModel.canBroadcast && canTalkRoleWise) {
                    AudioTalkRow(viewModel = viewModel, ui = ui, speakerPubkeyHex = myPubkey)
                }
            } else {
                Text(
                    text = stringRes(R.string.audio_room_audio_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconToggleButton(
                    checked = handRaised,
                    onCheckedChange = { handRaised = it },
                ) {
                    Icon(
                        symbol = MaterialSymbols.PanTool,
                        contentDescription =
                            stringRes(
                                if (handRaised) R.string.audio_room_lower_hand else R.string.audio_room_raise_hand,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun StagePeopleRow(
    label: String,
    people: List<ParticipantTag>,
    avatarSize: androidx.compose.ui.unit.Dp,
    speakingNow: ImmutableSet<String>,
    accountViewModel: AccountViewModel,
) {
    val ringColor = MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items = people, key = { it.pubKey }) { participant ->
                val isSpeaking = participant.pubKey in speakingNow
                val avatarModifier =
                    if (isSpeaking) {
                        Modifier.border(2.dp, ringColor, CircleShape)
                    } else {
                        Modifier
                    }
                ClickableUserPicture(
                    baseUserHex = participant.pubKey,
                    size = avatarSize,
                    accountViewModel = accountViewModel,
                    modifier = avatarModifier,
                )
            }
        }
    }
}

/**
 * Connect / state-chip / mute row. Pure-render — the [AudioRoomViewModel] is
 * owned by the parent so the speaker rows can read `speakingNow` from the
 * same UI state.
 */
@Composable
private fun AudioConnectionRow(
    viewModel: AudioRoomViewModel,
    ui: com.vitorpamplona.amethyst.commons.viewmodels.AudioRoomUiState,
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
                OutlinedButton(onClick = { viewModel.disconnect() }) {
                    Text(stringRes(R.string.audio_room_disconnect))
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
private fun connectingLabel(connection: ConnectionUiState.Connecting): String =
    when (connection.step) {
        ConnectionUiState.Step.ResolvingRoom -> stringRes(R.string.audio_room_connecting_resolving)
        ConnectionUiState.Step.OpeningTransport -> stringRes(R.string.audio_room_connecting_transport)
        ConnectionUiState.Step.MoqHandshake -> stringRes(R.string.audio_room_connecting_handshake)
    }

/**
 * Talk button + mic-mute toggle + Live indicator. Hidden unless the user is
 * in the room's `p` tags as host or speaker AND the listener path is
 * Connected (this composable's caller already checks `canBroadcast` +
 * role).
 */
@Composable
private fun AudioTalkRow(
    viewModel: AudioRoomViewModel,
    ui: com.vitorpamplona.amethyst.commons.viewmodels.AudioRoomUiState,
    speakerPubkeyHex: String,
) {
    if (ui.connection !is ConnectionUiState.Connected) return

    val context = LocalContext.current
    var permissionDeniedShown by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.startBroadcast(speakerPubkeyHex)
            } else {
                permissionDeniedShown = true
            }
        }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (val broadcast = ui.broadcast) {
            BroadcastUiState.Idle -> {
                Button(
                    onClick = {
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
                    },
                ) {
                    Text(stringRes(R.string.audio_room_talk))
                }
                if (permissionDeniedShown) {
                    Text(
                        text = stringRes(R.string.audio_room_record_permission_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f, fill = false),
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

/**
 * Android-side Factory for [AudioRoomViewModel]. The ViewModel itself lives
 * in `commons/` so a future desktop port can reuse the orchestration once
 * Compose Desktop has WebTransport; this factory binds it to the Android
 * actuals (OkHttp HTTP, pure-Kotlin QUIC, MediaCodec Opus, AudioTrack +
 * AudioRecord on the speaker side).
 */
private class AudioRoomViewModelFactory(
    private val signer: com.vitorpamplona.quartz.nip01Core.signers.NostrSigner,
    private val serviceBase: String,
    private val roomId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AudioRoomViewModel(
            httpClient = OkHttpNestsClient(),
            transport = QuicWebTransportFactory(),
            decoderFactory = { MediaCodecOpusDecoder() },
            playerFactory = { AudioTrackPlayer() },
            signer = signer,
            serviceBase = serviceBase,
            roomId = roomId,
            captureFactory = { AudioRecordCapture() },
            encoderFactory = { MediaCodecOpusEncoder() },
        ) as T
}

private const val PRESENCE_REFRESH_MS = 30_000L

private suspend fun publishPresence(
    account: com.vitorpamplona.amethyst.model.Account,
    event: MeetingSpaceEvent,
    handRaised: Boolean,
    micMuted: Boolean?,
) {
    runCatching {
        account.signAndComputeBroadcast(
            MeetingRoomPresenceEvent.build(
                root = event,
                handRaised = handRaised,
                // null = not broadcasting (no mic stream); true/false reflect
                // the speaker's current mic state so other clients can show a
                // mute indicator on the avatar.
                muted = micMuted,
            ),
        )
    }
}
