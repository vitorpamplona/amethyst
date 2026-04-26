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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.viewmodels.AudioRoomViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.ConnectionUiState
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size40dp
import com.vitorpamplona.nestsclient.OkHttpNestsClient
import com.vitorpamplona.nestsclient.audio.AudioTrackPlayer
import com.vitorpamplona.nestsclient.audio.MediaCodecOpusDecoder
import com.vitorpamplona.nestsclient.transport.QuicWebTransportFactory
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Clubhouse-style audio-room "stage" rendered in place of the video player when
 * the underlying activity is a NIP-53 kind 30312 [MeetingSpaceEvent].
 *
 * Responsibilities (M1 = listener-only):
 *   - Displays host / speaker / audience avatars parsed from the 30312 `p` tags.
 *   - Publishes kind 10312 presence on enter and every 30 s while composed.
 *   - Hand-raise toggle flips the `["hand","1"|"0"]` tag on that presence event
 *     so a host on any NIP-53 client can see the request and promote.
 *   - **Connect button** opens the listener-side audio pipeline (HTTP →
 *     WebTransport over QUIC → MoQ → Opus decode → AudioTrack). On Connected,
 *     auto-subscribes to every host's speaker track.
 *   - **Mute toggle** silences the local audio device without halting the
 *     network pipeline so unmute is instant.
 *
 * Speaker-side (mic capture + publish) is M5+ in the audio-rooms completion
 * plan — `nestsClient/plans/2026-04-26-audio-rooms-completion.md`. Until then
 * the presence event omits the `muted` tag (we have no mic to mute).
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

    var handRaised by rememberSaveable(event.address().toValue()) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val account = accountViewModel.account

    // Publish initial presence on enter and refresh every PRESENCE_REFRESH_MS while composed.
    LaunchedEffect(event.address().toValue(), handRaised) {
        publishPresence(account, event, handRaised)
        while (isActive) {
            delay(PRESENCE_REFRESH_MS)
            publishPresence(account, event, handRaised)
        }
    }

    // Best-effort "leave" — re-publish a lowered-hand presence so peers see us
    // drop sooner than the 30 s heartbeat would otherwise allow.
    DisposableEffect(event.address().toValue()) {
        onDispose {
            scope.launch(Dispatchers.IO) {
                runCatching { publishPresence(account, event, handRaised = false) }
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

            if (hosts.isNotEmpty() || speakers.isNotEmpty()) {
                StagePeopleRow(
                    label = stringRes(R.string.audio_room_stage),
                    people = hosts + speakers,
                    avatarSize = Size40dp,
                    accountViewModel = accountViewModel,
                )
            }

            if (audience.isNotEmpty()) {
                StagePeopleRow(
                    label = stringRes(R.string.audio_room_audience),
                    people = audience,
                    avatarSize = Size35dp,
                    accountViewModel = accountViewModel,
                )
            }

            AudioConnectionRow(
                event = event,
                hosts = hosts,
                accountViewModel = accountViewModel,
            )

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
    accountViewModel: AccountViewModel,
) {
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
            items(items = people, key = { it.pubKey }) {
                ClickableUserPicture(
                    baseUserHex = it.pubKey,
                    size = avatarSize,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}

/**
 * Connect / state-chip / mute row. Wires [AudioRoomViewModel] for the listener
 * audio pipeline. Hidden when the room event has no `service` tag (legacy
 * recordings or rooms hosted on non-nests servers — nothing to connect to).
 */
@Composable
private fun AudioConnectionRow(
    event: MeetingSpaceEvent,
    hosts: List<ParticipantTag>,
    accountViewModel: AccountViewModel,
) {
    val serviceBase = event.service()
    val roomId = event.address().dTag

    if (serviceBase.isNullOrBlank() || roomId.isBlank()) {
        Text(
            text = stringRes(R.string.audio_room_audio_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        return
    }

    val signer = accountViewModel.account.signer
    val viewModelKey = remember(serviceBase, roomId) { "$serviceBase|$roomId" }

    val viewModel: AudioRoomViewModel =
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

    val hostKeys = remember(hosts) { hosts.map { it.pubKey }.toSet() }
    LaunchedEffect(viewModel, hostKeys) {
        viewModel.updateSpeakers(hostKeys)
    }

    val ui by viewModel.uiState.collectAsState()

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
 * Android-side Factory for [AudioRoomViewModel]. The ViewModel itself lives
 * in `commons/` so a future desktop port can reuse the orchestration once
 * Compose Desktop has WebTransport; this factory binds it to the Android
 * actuals (OkHttp HTTP, pure-Kotlin QUIC, MediaCodec Opus, AudioTrack).
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
        ) as T
}

private const val PRESENCE_REFRESH_MS = 30_000L

private suspend fun publishPresence(
    account: com.vitorpamplona.amethyst.model.Account,
    event: MeetingSpaceEvent,
    handRaised: Boolean,
) {
    runCatching {
        account.signAndComputeBroadcast(
            MeetingRoomPresenceEvent.build(
                root = event,
                handRaised = handRaised,
                // muted tag intentionally omitted — listener-only mute (M1)
                // silences our speakers, not a mic we don't yet broadcast.
                // Re-evaluate when M5 (publisher path) lands.
                muted = null,
            ),
        )
    }
}
