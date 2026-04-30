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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.activity

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.viewmodels.ConnectionUiState
import com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.ui.call.KeepScreenOn
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.datasource.NestRoomFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.lifecycle.AutoConnectAndTrackSpeakers
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.lifecycle.LeaveOnKick
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.lifecycle.LeaveOnRoomClosed
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.lifecycle.NestForegroundServiceLifecycle
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.lifecycle.NestPresencePublisher
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.lifecycle.NestRoomEventCollectors
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.lifecycle.PipBridge
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.lifecycle.rememberNestViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.screen.NestFullScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.screen.NestPipScreen
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE
import kotlinx.coroutines.flow.SharedFlow

/**
 * Top-level composable for [NestActivity]. Resolves the kind-30312
 * note from the address extra, then hands control to
 * [NestActivityBody].
 */
@Composable
internal fun NestActivityContent(
    addressValue: String,
    accountViewModel: AccountViewModel,
    isInPipMode: Boolean,
    isPipSupported: Boolean,
    onMuteState: (Boolean) -> Unit,
    onConnectedChange: (Boolean) -> Unit,
    pipMuteSignal: SharedFlow<Unit>,
    onLeave: () -> Unit,
    onMinimize: () -> Unit,
) {
    val parsedAddress = remember(addressValue) { Address.parse(addressValue) }
    if (parsedAddress == null) {
        // Malformed address — bail. The Activity will receive a Closed
        // signal through the VM teardown when the user finishes; until
        // then we just render nothing.
        return
    }

    LoadAddressableNote(parsedAddress, accountViewModel) { addressableNote ->
        addressableNote ?: return@LoadAddressableNote
        val event by observeNoteEvent<MeetingSpaceEvent>(addressableNote, accountViewModel)

        event?.let {
            val service = it.service()
            val endPoint = it.endpoint()
            if (service != null && endPoint != null) {
                val viewModel =
                    rememberNestViewModel(
                        NestsRoomConfig(
                            authBaseUrl = service,
                            endpoint = endPoint,
                            hostPubkey = it.pubKey,
                            roomId = it.dTag(),
                            kind = it.kind,
                        ),
                        accountViewModel.account.signer,
                    )

                NestActivityBody(
                    event = it,
                    roomNote = addressableNote,
                    viewModel = viewModel,
                    accountViewModel = accountViewModel,
                    isInPipMode = isInPipMode,
                    isPipSupported = isPipSupported,
                    onMuteState = onMuteState,
                    onConnectedChange = onConnectedChange,
                    pipMuteSignal = pipMuteSignal,
                    onLeave = onLeave,
                    onMinimize = onMinimize,
                )
            }
        }
    }
}

/**
 * Per-room orchestration. Wires the [NestViewModel] to:
 *   - the relay subscription that feeds LocalCache
 *   - LocalCache → VM event collectors (presence, chat, reactions, kicks)
 *   - the system bridges (PIP, foreground service)
 *   - the kind-10312 presence publisher
 *
 * Each concern is one named composable from a sibling file. This
 * function stays a list of well-labelled wiring calls so the
 * room's lifecycle is readable end-to-end at a glance.
 */
@Composable
private fun NestActivityBody(
    event: MeetingSpaceEvent,
    roomNote: AddressableNote,
    viewModel: NestViewModel,
    accountViewModel: AccountViewModel,
    isInPipMode: Boolean,
    isPipSupported: Boolean,
    onMuteState: (Boolean) -> Unit,
    onConnectedChange: (Boolean) -> Unit,
    pipMuteSignal: SharedFlow<Unit>,
    onLeave: () -> Unit,
    onMinimize: () -> Unit,
) {
    val account = accountViewModel.account
    val localPubkey = account.signer.pubKey
    val roomATag = roomNote.idHex

    // Static room layout: hosts + speakers from the kind-30312 `p`-tags.
    // The grid renderer derives audience from the kind-10312 presence
    // aggregator (see ParticipantsGrid / buildParticipantGrid); this
    // composable only needs the on-stage subset for the talk-row gate
    // and the PIP renderer.
    val onStage =
        remember(event) {
            event.participants().filter {
                it.role.equals(ROLE.HOST.code, true) || it.role.equals(ROLE.SPEAKER.code, true)
            }
        }
    val onStageKeys = remember(onStage) { onStage.map { it.pubKey }.toSet() }

    AutoConnectAndTrackSpeakers(viewModel, onStageKeys)

    // Single REQ per relay covering chat, presence, reactions, admin
    // commands. See NestRoomFilterAssembler for the filter shape.
    NestRoomFilterAssemblerSubscription(roomATag, accountViewModel)

    // Read side: LocalCache → VM. Each kind has its own collector
    // (different VM hook per kind) plus the two eviction tickers.
    NestRoomEventCollectors(viewModel, event, roomATag, localPubkey)

    // Kick → leave the activity.
    LeaveOnKick(viewModel, onLeave)

    // Host ended the room (status: CLOSED) → leave the activity.
    // Same teardown path as kick — VM.onCleared() releases the
    // listener + speaker when the activity finishes.
    LeaveOnRoomClosed(event, onLeave)

    val ui by viewModel.uiState.collectAsState()

    // Hold a screen-on lock while the user is actively in the room so the
    // device doesn't lock and interrupt the audio session UI.
    if (ui.connection is ConnectionUiState.Connected) {
        KeepScreenOn()
    }

    // System bridges: PIP overlay actions + foreground service.
    PipBridge(ui, pipMuteSignal, viewModel, onMuteState, onConnectedChange)
    NestForegroundServiceLifecycle(ui)

    // Hand-raise is screen-local UI state; presence-publish picks it
    // up via the heartbeat and emits onstage / muted / publishing
    // alongside.
    var handRaised by rememberSaveable(roomATag) { mutableStateOf(false) }
    NestPresencePublisher(
        account = account,
        event = event,
        ui = ui,
        handRaised = handRaised,
    )

    // Back gesture while connected = minimize to PIP rather than tearing
    // down the audio session. When disconnected (lobby / Connecting /
    // Failed) or PIP isn't supported, we let back fall through to the
    // default Activity.finish() so the user can cancel out cleanly.
    val canMinimize = isPipSupported && !isInPipMode && ui.connection is ConnectionUiState.Connected
    BackHandler(enabled = canMinimize) { onMinimize() }

    if (isInPipMode) {
        NestPipScreen(
            title = event.room(),
            onStage = onStage,
            ui = ui,
            viewModel = viewModel,
            handRaised = handRaised,
            accountViewModel = accountViewModel,
        )
    } else {
        NestFullScreen(
            event = event,
            roomNote = roomNote,
            onStage = onStage,
            viewModel = viewModel,
            ui = ui,
            accountViewModel = accountViewModel,
            handRaised = handRaised,
            onHandRaisedChange = { handRaised = it },
            onLeave = onLeave,
            onMinimize = onMinimize,
            canMinimize = isPipSupported,
        )
    }
}
