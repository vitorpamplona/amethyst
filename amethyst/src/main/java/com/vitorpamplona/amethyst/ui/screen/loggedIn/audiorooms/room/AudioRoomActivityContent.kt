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
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.audiorooms.AudioRoomForegroundService
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.datasource.RoomAdminCommandsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.datasource.RoomChatFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.datasource.RoomPresenceFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.datasource.RoomReactionsFilterAssemblerSubscription
import com.vitorpamplona.quartz.experimental.audiorooms.admin.AdminCommandEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
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
    room: com.vitorpamplona.nestsclient.NestsRoomConfig,
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
            roomNote = addressableNote,
            room = room,
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
    roomNote: com.vitorpamplona.amethyst.model.AddressableNote,
    room: com.vitorpamplona.nestsclient.NestsRoomConfig,
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
    // The grid renderer derives audience from `event.participants()` +
    // the kind-10312 presence aggregator (see ParticipantsGrid /
    // buildParticipantGrid). This composable only needs the on-stage
    // subset for the talk-row gate + PIP renderer.
    val onStage = remember(hosts, speakers) { hosts + speakers }
    val onStageKeys = remember(onStage) { onStage.map { it.pubKey }.toSet() }

    val account = accountViewModel.account
    val signer = account.signer

    val viewModel: AudioRoomViewModel =
        viewModel(
            key = "${room.authBaseUrl}|${room.roomId}",
            factory =
                remember(room, signer) {
                    AudioRoomViewModelFactory(
                        signer = signer,
                        room = room,
                    )
                },
        )

    // Auto-connect on activity entry. The VM guards against duplicate
    // connect() calls if the screen recomposes.
    LaunchedEffect(viewModel) { viewModel.connect() }

    LaunchedEffect(viewModel, onStageKeys) { viewModel.updateSpeakers(onStageKeys) }

    // Per-room kind-10312 presence: open the wire sub for the duration
    // of this Composable, observe matching events from LocalCache, feed
    // them into the VM's aggregator. Drives the listener counter +
    // (Tier 2) participant grid + (T1 #5) hand-raise queue.
    val roomATag = remember(event) { event.address().toValue() }
    RoomPresenceFilterAssemblerSubscription(roomATag, accountViewModel)
    LaunchedEffect(viewModel, roomATag) {
        val filter =
            Filter(
                kinds = listOf(MeetingRoomPresenceEvent.KIND),
                tags = mapOf("a" to listOf(roomATag)),
            )
        LocalCache.observeEvents<MeetingRoomPresenceEvent>(filter).collect { events ->
            events.forEach { viewModel.onPresenceEvent(it) }
        }
    }
    // Eviction tick — drop peers silent for >6 min (one missed
    // 30-s heartbeat plus a 5-min "still here" tolerance window so
    // a transient relay hiccup doesn't drop everyone).
    LaunchedEffect(viewModel) {
        while (isActive) {
            delay(PRESENCE_EVICT_INTERVAL_MS)
            viewModel.evictStalePresences(System.currentTimeMillis() / 1000 - PRESENCE_STALE_THRESHOLD_SEC)
        }
    }

    // Per-room kind-1311 live chat: open the wire sub for the duration
    // of this Composable, observe matching events from LocalCache, feed
    // them into the VM's chat ledger.
    RoomChatFilterAssemblerSubscription(roomATag, accountViewModel)
    LaunchedEffect(viewModel, roomATag) {
        val filter =
            Filter(
                kinds = listOf(LiveActivitiesChatMessageEvent.KIND),
                tags = mapOf("a" to listOf(roomATag)),
            )
        LocalCache.observeEvents<LiveActivitiesChatMessageEvent>(filter).collect { events ->
            events.forEach { viewModel.onChatEvent(it) }
        }
    }

    // Per-room kind-7 reactions: same shape as chat. The VM's
    // sliding-window aggregator drops entries older than 30 s; the
    // 1-s tick below drives the eviction so the floating-up overlay
    // animation timing is one-place rather than per-component.
    RoomReactionsFilterAssemblerSubscription(roomATag, accountViewModel)
    LaunchedEffect(viewModel, roomATag) {
        val filter =
            Filter(
                kinds = listOf(ReactionEvent.KIND),
                tags = mapOf("a" to listOf(roomATag)),
            )
        LocalCache.observeEvents<ReactionEvent>(filter).collect { events ->
            val nowSec = System.currentTimeMillis() / 1000
            events.forEach { viewModel.onReactionEvent(it, nowSec) }
        }
    }
    LaunchedEffect(viewModel) {
        while (isActive) {
            delay(REACTIONS_TICK_MS)
            viewModel.evictReactions(System.currentTimeMillis() / 1000 - REACTION_WINDOW_SEC_LOCAL)
        }
    }

    // Per-room kind-4312 admin commands targeting THIS user. The
    // signer-must-be-host-or-moderator authority check happens here
    // (not in the relay) — only honour kicks where the signer's
    // ParticipantTag.canSpeak() returns true on the active
    // kind-30312. nostrnests' UI does the same gating.
    val localPubkey = accountViewModel.account.signer.pubKey
    RoomAdminCommandsFilterAssemblerSubscription(roomATag, localPubkey, accountViewModel)
    LaunchedEffect(viewModel, roomATag, localPubkey) {
        val filter =
            Filter(
                kinds = listOf(AdminCommandEvent.KIND),
                tags = mapOf("a" to listOf(roomATag), "p" to listOf(localPubkey)),
            )
        LocalCache.observeNewEvents<AdminCommandEvent>(filter).collect { cmd ->
            if (cmd.action() != AdminCommandEvent.Action.KICK) return@collect
            if (cmd.targetPubkey() != localPubkey) return@collect
            // Authority: the signer must currently hold a
            // host/moderator role on the kind-30312 we joined.
            // Falling back to "is the signer the room's pubkey"
            // (the original host) covers the case where the host
            // sends from the same key that wrote the room event.
            val signerIsAuthorised =
                cmd.pubKey == event.pubKey ||
                    event.participants().any { it.pubKey == cmd.pubKey && (it.isHost() || it.isModerator()) }
            if (!signerIsAuthorised) return@collect
            viewModel.onKick()
        }
    }
    val wasKicked by viewModel.wasKicked.collectAsState()
    LaunchedEffect(wasKicked) { if (wasKicked) onLeave() }

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
    val publishingTag: Boolean = ui.publishingNow
    val onstageTag: Boolean = ui.onStageNow
    // Heartbeat loop — publishes once on enter / hand-raise / onstage
    // change, then every 30 s. micMutedTag and publishingTag are
    // intentionally NOT keys here: every mute toggle would otherwise
    // trigger a presence publish + relay round trip (audit Android #11).
    // The next heartbeat picks up the latest mic / publishing state up
    // to 30 s later, which is well within the user's "did the peer see
    // my mute" tolerance.
    LaunchedEffect(event.address().toValue(), handRaised, onstageTag) {
        publishPresence(account, event, handRaised, micMutedTag, publishingTag, onstageTag)
        while (isActive) {
            delay(PRESENCE_REFRESH_MS)
            publishPresence(account, event, handRaised, micMutedTag, publishingTag, onstageTag)
        }
    }
    // Debounced state-change publisher: after a mute toggle, wait
    // PRESENCE_DEBOUNCE_MS for further changes before sending a fresh
    // presence event. The LaunchedEffect's auto-cancel on key change
    // serves as the debounce mechanism.
    //
    // Skip when `micMutedTag` is null (we're not broadcasting) — otherwise
    // the FIRST composition would publish twice within 500 ms (heartbeat
    // immediately + debounce-publisher 500 ms later, both with muted=null).
    // Audit round-2 Android #10.
    if (micMutedTag != null) {
        LaunchedEffect(micMutedTag) {
            delay(PRESENCE_DEBOUNCE_MS)
            publishPresence(account, event, handRaised, micMutedTag, publishingTag, onstageTag)
        }
    }
    DisposableEffect(event.address().toValue()) {
        onDispose {
            // Final "leaving" presence runs on a non-cancellable scope so
            // it survives the composable's scope being cancelled mid-
            // network. Without this the leave event almost never reaches
            // the relay (audit Android #12). publishing=false / onstage=false
            // tells aggregating peers to drop us from the participant grid
            // immediately rather than wait out the staleness window.
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                runCatching {
                    publishPresence(
                        account = account,
                        event = event,
                        handRaised = false,
                        micMuted = null,
                        publishing = false,
                        onstage = false,
                    )
                }
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
            roomNote = roomNote,
            onStage = onStage,
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
private const val PRESENCE_EVICT_INTERVAL_MS = 60_000L
private const val PRESENCE_STALE_THRESHOLD_SEC = 6L * 60L
private const val REACTIONS_TICK_MS = 1_000L

// Mirror of [com.vitorpamplona.amethyst.commons.viewmodels.REACTION_WINDOW_SEC]
// — the platform layer doesn't import from commons here, and a
// duplicate constant is cheaper than another import. If these
// drift, the 1-s tick still self-heals within one window length.
private const val REACTION_WINDOW_SEC_LOCAL = 30L

private suspend fun publishPresence(
    account: com.vitorpamplona.amethyst.model.Account,
    event: MeetingSpaceEvent,
    handRaised: Boolean,
    micMuted: Boolean?,
    publishing: Boolean? = null,
    onstage: Boolean? = null,
) {
    runCatching {
        account.signAndComputeBroadcast(
            MeetingRoomPresenceEvent.build(
                root = event,
                handRaised = handRaised,
                muted = micMuted,
                publishing = publishing,
                onstage = onstage,
            ),
        )
    }
}
