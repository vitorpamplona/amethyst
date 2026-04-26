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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.viewmodels.AudioRoomViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.BroadcastUiState
import com.vitorpamplona.amethyst.commons.viewmodels.ConnectionUiState
import com.vitorpamplona.amethyst.service.audiorooms.AudioRoomForegroundService
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Top-level composable for [AudioRoomActivity]. Observes the room event +
 * VM, auto-connects on entry, runs the kind-10312 presence loop, drives
 * the foreground service, and flips between full-screen and PIP layouts.
 *
 * The [AudioRoomActivity] supplies [isInPipMode] (a Compose state observed
 * via the activity's `mutableStateOf`) and [onLeave] which finishes the
 * activity. Everything else is local to this composable's scope.
 */
@Composable
internal fun AudioRoomActivityContent(
    addressValue: String,
    serviceBase: String,
    roomId: String,
    accountViewModel: AccountViewModel,
    isInPipMode: Boolean,
    onMuteState: (Boolean) -> Unit,
    onConnectedChange: (Boolean) -> Unit,
    pipMuteSignal: SharedFlow<Unit>,
    onLeave: () -> Unit,
) {
    val parsedAddress = remember(addressValue) { Address.parse(addressValue) }
    if (parsedAddress == null) {
        // Malformed address — bail. The Activity will receive a Closed signal
        // through the VM teardown when the user finishes; until then we just
        // render nothing.
        return
    }

    LoadAddressableNote(parsedAddress, accountViewModel) { addressableNote ->
        addressableNote ?: return@LoadAddressableNote
        val event = addressableNote.event as? MeetingSpaceEvent ?: return@LoadAddressableNote
        AudioRoomActivityBody(
            event = event,
            serviceBase = serviceBase,
            roomId = roomId,
            accountViewModel = accountViewModel,
            isInPipMode = isInPipMode,
            onMuteState = onMuteState,
            onConnectedChange = onConnectedChange,
            pipMuteSignal = pipMuteSignal,
            onLeave = onLeave,
        )
    }
}

@Composable
private fun AudioRoomActivityBody(
    event: MeetingSpaceEvent,
    serviceBase: String,
    roomId: String,
    accountViewModel: AccountViewModel,
    isInPipMode: Boolean,
    onMuteState: (Boolean) -> Unit,
    onConnectedChange: (Boolean) -> Unit,
    pipMuteSignal: SharedFlow<Unit>,
    onLeave: () -> Unit,
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

    val account = accountViewModel.account
    val signer = account.signer

    val viewModel: AudioRoomViewModel =
        viewModel(
            key = "$serviceBase|$roomId",
            factory =
                remember(serviceBase, roomId, signer) {
                    AudioRoomViewModelFactory(
                        signer = signer,
                        serviceBase = serviceBase,
                        roomId = roomId,
                    )
                },
        )

    // Auto-connect on activity entry. The VM guards against duplicate
    // connect() calls if the screen recomposes.
    LaunchedEffect(viewModel) { viewModel.connect() }

    LaunchedEffect(viewModel, onStageKeys) { viewModel.updateSpeakers(onStageKeys) }

    val ui by viewModel.uiState.collectAsState()

    // Push mute + connected state up so the Activity can rebuild PIP
    // RemoteActions and gate `enterPictureInPictureMode` on Connected.
    LaunchedEffect(ui.isMuted) { onMuteState(ui.isMuted) }
    val isConnectedNow = ui.connection is ConnectionUiState.Connected
    LaunchedEffect(isConnectedNow) { onConnectedChange(isConnectedNow) }

    // Forward PIP-overlay mute taps into the VM. Per-Activity SharedFlow
    // so a stale emission from a previous Activity instance can't leak
    // into a new one.
    LaunchedEffect(viewModel) {
        pipMuteSignal.collect {
            viewModel.setMuted(!viewModel.uiState.value.isMuted)
        }
    }

    var handRaised by rememberSaveable(event.address().toValue()) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Presence: kind 10312 every 30 s while the activity is composed.
    val micMutedTag: Boolean? =
        when (val b = ui.broadcast) {
            is BroadcastUiState.Broadcasting -> b.isMuted
            else -> null
        }
    // Heartbeat loop — publishes once on enter / hand-raise change, then
    // every 30 s. micMutedTag is intentionally NOT a key here: every mute
    // toggle would otherwise trigger a presence publish + relay round trip
    // (audit Android #11). The next heartbeat picks up the latest mic
    // state up to 30 s later, which is well within the user's "did the
    // peer see my mute" tolerance.
    LaunchedEffect(event.address().toValue(), handRaised) {
        publishPresence(account, event, handRaised, micMutedTag)
        while (isActive) {
            delay(PRESENCE_REFRESH_MS)
            publishPresence(account, event, handRaised, micMutedTag)
        }
    }
    // Debounced state-change publisher: after a mute toggle, wait
    // PRESENCE_DEBOUNCE_MS for further changes before sending a fresh
    // presence event. The LaunchedEffect's auto-cancel on key change
    // serves as the debounce mechanism.
    LaunchedEffect(micMutedTag) {
        delay(PRESENCE_DEBOUNCE_MS)
        publishPresence(account, event, handRaised, micMutedTag)
    }
    DisposableEffect(event.address().toValue()) {
        onDispose {
            // Final "leaving" presence runs on a non-cancellable scope so
            // it survives the composable's scope being cancelled mid-
            // network. Without this the leave event almost never reaches
            // the relay (audit Android #12).
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                runCatching { publishPresence(account, event, handRaised = false, micMuted = null) }
            }
        }
    }

    // Foreground service lifecycle — the Activity is the lifecycle anchor;
    // the service exists for screen-locked / activity-stopped audio.
    val context = LocalContext.current
    val isConnected = ui.connection is ConnectionUiState.Connected
    val isBroadcasting = ui.broadcast is BroadcastUiState.Broadcasting
    LaunchedEffect(isConnected, isBroadcasting) {
        when {
            isConnected && isBroadcasting -> AudioRoomForegroundService.promoteToMicrophone(context)
            isConnected -> AudioRoomForegroundService.startListening(context)
            else -> AudioRoomForegroundService.stop(context)
        }
    }
    DisposableEffect(Unit) { onDispose { AudioRoomForegroundService.stop(context) } }

    if (isInPipMode) {
        AudioRoomPipScreen(
            title = event.room(),
            onStage = onStage,
            ui = ui,
            accountViewModel = accountViewModel,
        )
    } else {
        AudioRoomFullScreen(
            event = event,
            onStage = onStage,
            audience = audience,
            viewModel = viewModel,
            ui = ui,
            accountViewModel = accountViewModel,
            handRaised = handRaised,
            onHandRaisedChange = { handRaised = it },
            onLeave = onLeave,
        )
    }
}

private const val PRESENCE_REFRESH_MS = 30_000L
private const val PRESENCE_DEBOUNCE_MS = 500L

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
                muted = micMuted,
            ),
        )
    }
}
