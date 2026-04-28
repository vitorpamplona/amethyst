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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.nestsclient.BroadcastHandle
import com.vitorpamplona.nestsclient.NestsClient
import com.vitorpamplona.nestsclient.NestsListener
import com.vitorpamplona.nestsclient.NestsListenerState
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.NestsSpeaker
import com.vitorpamplona.nestsclient.NestsSpeakerState
import com.vitorpamplona.nestsclient.audio.AudioCapture
import com.vitorpamplona.nestsclient.audio.AudioPlayer
import com.vitorpamplona.nestsclient.audio.NestPlayer
import com.vitorpamplona.nestsclient.audio.OpusDecoder
import com.vitorpamplona.nestsclient.audio.OpusEncoder
import com.vitorpamplona.nestsclient.connectNestsSpeaker
import com.vitorpamplona.nestsclient.connectReconnectingNestsListener
import com.vitorpamplona.nestsclient.moq.SubscribeHandle
import com.vitorpamplona.nestsclient.transport.WebTransportFactory
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Per-screen state holder for a NIP-53 audio-room.
 *
 * Owns one [NestsListener] for the lifetime of the screen and one
 * [NestPlayer] per active speaker subscription. The screen tells the
 * VM which speakers it cares about via [updateSpeakers]; subscribe / play
 * happen automatically once the listener is [NestsListenerState.Connected].
 *
 * Lifecycle:
 *   - Construction: idle. Nothing is on the wire until [connect] is called.
 *   - [connect]: launches the HTTP→WebTransport→MoQ handshake. Idempotent
 *     while connecting/connected.
 *   - [setMuted]: routes through to every player so the mute toggle is
 *     instant; the network keeps running so unmute has no extra latency.
 *   - [disconnect] / [onCleared]: cancels subscriptions, stops players,
 *     closes the listener. Idempotent.
 *
 * Audio-pipeline construction is injected via [decoderFactory] /
 * [playerFactory] so commonMain doesn't have to know which platform's
 * MediaCodec / AudioTrack is in play. M1 wires Android-only — desktop
 * passes nothing here yet.
 *
 * **Threading contract:** all public methods (`connect`, `disconnect`,
 * `updateSpeakers`, `setMuted`, `setMicMuted`, `startBroadcast`,
 * `stopBroadcast`) MUST be called on the main thread. Internal mutation
 * of `activeSubscriptions` / `speakingExpiryJobs` / `requestedSpeakers`
 * is only thread-safe under that assumption — they're plain HashMaps
 * confined to `viewModelScope`'s dispatcher (Dispatchers.Main.immediate
 * on Android, which is the same dispatcher the MoQ flow's `onEach`
 * callback runs on because the player launch lives in viewModelScope).
 * If a future caller needs to invoke from a background thread, marshal
 * via `viewModelScope.launch(Dispatchers.Main.immediate) { ... }` first.
 */
@Stable
class NestViewModel(
    private val httpClient: NestsClient,
    private val transport: WebTransportFactory,
    private val decoderFactory: () -> OpusDecoder,
    private val playerFactory: () -> AudioPlayer,
    private val signer: NostrSigner,
    private val room: NestsRoomConfig,
    // Speaker-side audio capture/encode actuals. Optional — desktop and
    // listener-only callers pass null and the speaker UI hides the talk
    // button. Android passes `{ AudioRecordCapture() }` /
    // `{ MediaCodecOpusEncoder() }`.
    private val captureFactory: (() -> AudioCapture)? = null,
    private val encoderFactory: (() -> OpusEncoder)? = null,
    // Seam for tests — production code uses the default which delegates to
    // the real `connectNestsListener`. Tests inject a fake that returns a
    // listener whose state they can drive directly.
    private val connector: NestsListenerConnector = DefaultNestsListenerConnector,
    private val speakerConnector: NestsSpeakerConnector = DefaultNestsSpeakerConnector,
    // Scope used for fire-and-forget MoQ cleanup (UNANNOUNCE,
    // SUBSCRIBE_DONE, MoQ session close) that needs to outlive the VM's
    // own scope. Production passes [GlobalScope] so onCleared can finish
    // sending control frames before the QUIC transport drops; tests pass
    // their `backgroundScope` so assertions can observe the close.
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private val cleanupScope: CoroutineScope = GlobalScope,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NestUiState())
    val uiState: StateFlow<NestUiState> = _uiState.asStateFlow()

    /**
     * Listener-side aggregation of every peer's most recent kind-10312
     * presence in this room. Populated by the platform layer
     * (amethyst observes [LocalCache] for `kinds=[10312], #a=[roomATag]`
     * and pipes events through [onPresenceEvent]) — keeping the data
     * source platform-specific lets commons stay free of
     * Android-only references.
     *
     * The participant grid (Tier 2 #1), hand-raise queue (T1 #5), and
     * listener counter (T1 #8) all subscribe to this StateFlow.
     */
    private val presenceAgg = RoomPresenceAggregator()
    private val _presences = MutableStateFlow<Map<String, RoomPresence>>(emptyMap())
    val presences: StateFlow<Map<String, RoomPresence>> = _presences.asStateFlow()

    /**
     * Chat ledger for the live-activities chat panel (#1) — every
     * kind-1311 ([com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent])
     * tagged with this room's `a`-pointer, ordered by `created_at`
     * ascending so the newest message is at the end (the panel
     * auto-scrolls to it).
     *
     * Dedupes by event id — a relay re-emit on reconnect can't
     * produce a duplicate row.
     */
    private val chatById = LinkedHashMap<String, com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent>()
    private val _chat =
        MutableStateFlow<List<com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent>>(emptyList())
    val chat: StateFlow<List<com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent>> = _chat.asStateFlow()

    /**
     * Recent kind-7 reactions for the floating speaker-avatar overlay
     * (#3). Keyed by target pubkey; room-wide reactions land under the
     * empty-string key. Sliding 30 s window driven by the platform
     * layer's tick (typically every 1 s).
     */
    private val reactionsAgg = RoomReactionsAggregator()
    private val _recentReactions = MutableStateFlow<Map<String, List<RoomReaction>>>(emptyMap())
    val recentReactions: StateFlow<Map<String, List<RoomReaction>>> = _recentReactions.asStateFlow()

    /**
     * moq-lite `catalog.json` payload per speaker pubkey. Populated
     * lazily as the per-speaker subscriptions land — listeners
     * without catalog support (IETF reference path) leave this map
     * empty. The participant context sheet reads this to surface
     * codec / sample-rate info; future commits could use it for
     * "speaker is broadcasting" indicators independent of the
     * actively-emitting `speakingNow` set.
     */
    private val _speakerCatalogs = MutableStateFlow<Map<String, RoomSpeakerCatalog>>(emptyMap())
    val speakerCatalogs: StateFlow<Map<String, RoomSpeakerCatalog>> = _speakerCatalogs.asStateFlow()

    /**
     * Pubkeys the moq-lite relay has announced as actively
     * broadcasting in this room. Populated by [observeAnnounces]
     * once the listener is Connected; an Active announce adds the
     * pubkey, an Ended announce removes it. Independent of the
     * kind-10312 `publishing` flag — announces come straight from
     * the relay's view of who has an open broadcast track, while
     * presence is the speaker's self-reported state. The two
     * usually agree but the announce flow is the more authoritative
     * "is this broadcast really live" signal.
     *
     * Empty when the listener doesn't expose announces (IETF
     * reference path); the UI falls back to `publishingNow` from
     * presence in that case.
     */
    private val _announcedSpeakers = MutableStateFlow<Set<String>>(emptySet())
    val announcedSpeakers: StateFlow<Set<String>> = _announcedSpeakers.asStateFlow()

    /**
     * Per-speaker peak audio amplitude for the most recent decoded
     * frame, normalized to `[0, 1]`. Drives the live "voice ring"
     * around speaker avatars: while a pubkey is in [NestUiState.speakingNow],
     * the UI reads this map to throb the green border in time with
     * the voice.
     *
     * Updated by [NestPlayer]'s `onLevel` callback at ~50 Hz per speaker
     * (one frame per 20 ms Opus packet); the VM coalesces those raw
     * updates into a single StateFlow emission every [LEVEL_TICK_MS]
     * via [levelEmitterJob]. Empty when no speaker is being decoded;
     * an entry drops when its subscription closes.
     */
    private val rawAudioLevels = mutableMapOf<String, Float>()
    private val _audioLevels = MutableStateFlow<Map<String, Float>>(emptyMap())
    val audioLevels: StateFlow<Map<String, Float>> = _audioLevels.asStateFlow()
    private var levelEmitterJob: Job? = null

    /**
     * `true` once the local user has been kicked (#5) — the platform
     * layer flips this on a valid kind-4312 from a host/moderator and
     * the UI can show a toast + finish the activity. Set-once; never
     * unset for the lifetime of the VM (a kick survives reconnect
     * attempts; the user must rejoin the room manually).
     */
    private val _wasKicked = MutableStateFlow(false)
    val wasKicked: StateFlow<Boolean> = _wasKicked.asStateFlow()

    /**
     * Mark the local user as kicked and disconnect the listener +
     * speaker pumps. Idempotent. Caller (amethyst layer) is
     * responsible for verifying the inbound kind-4312 was signed by
     * a host or moderator on the active kind-30312 before invoking
     * this — the relay does not enforce that.
     */
    fun onKick() {
        if (closed) return
        if (_wasKicked.value) return
        _wasKicked.value = true
        disconnect()
    }

    private var listener: NestsListener? = null
    private var connectJob: Job? = null
    private var stateObserverJob: Job? = null
    private var announcesJob: Job? = null

    // Last in-flight `listener.close()` launched by teardown(). A subsequent
    // connect() awaits this before opening a fresh transport so two QUIC
    // sessions (the closing old one and the opening new one) don't briefly
    // coexist. Some servers dedupe by client pubkey and reject the new one.
    private var pendingCloseJob: Job? = null

    private val activeSubscriptions = mutableMapOf<String, ActiveSubscription>()
    private val speakingExpiryJobs = mutableMapOf<String, Job>()

    /**
     * Per-speaker catalog-fetch coroutines. Each entry is the
     * background `subscribeCatalog` collector launched in
     * [fetchSpeakerCatalog]; cancelled in [closeSubscription] so
     * removing a speaker doesn't leave a zombie subscription that
     * keeps re-adding the catalog entry on every wrapper-side
     * session swap.
     */
    private val catalogJobs = mutableMapOf<String, Job>()
    private var requestedSpeakers: Set<String> = emptySet()
    private var closed = false

    // Speaker / publisher path
    private var speaker: NestsSpeaker? = null
    private var broadcastHandle: BroadcastHandle? = null
    private var speakerStateJob: Job? = null
    private var speakerConnectJob: Job? = null

    /**
     * Push the latest known speaker set from the room event. The user's
     * own pubkey (when broadcasting) is filtered out so we don't subscribe
     * to our own forwarded audio — that would echo through the local
     * playback device whenever our broadcast track loops back from the relay.
     */
    fun updateSpeakers(speakerPubkeys: Set<String>) {
        if (closed) return
        val selfHex = signer.pubKey
        requestedSpeakers = if (selfHex.isEmpty()) speakerPubkeys else speakerPubkeys - selfHex
        if (_uiState.value.connection is ConnectionUiState.Connected) {
            reconcileSubscriptions()
        }
    }

    /**
     * Kick off the HTTP → WebTransport → MoQ handshake. No-op if a connect
     * attempt is already in flight or already connected.
     */
    fun connect() {
        if (closed) return
        val current = _uiState.value.connection
        if (current is ConnectionUiState.Connecting || current is ConnectionUiState.Connected) return

        // If a stale listener is still set (e.g. arrived in Failed and the
        // user is manually retrying before the auto-retry fires, or
        // entering from a server-Closed-but-not-yet-disconnected state),
        // tear it down so the new connect doesn't leave the old MoQ
        // session open and unowned (audit round-2 VM #3). teardown()
        // also cancels stateObserverJob so its delayed emissions can't
        // clobber the fresh Connecting UI.
        if (listener != null || stateObserverJob != null) {
            teardown(targetState = ConnectionUiState.Idle, finalCleanup = false)
        }

        launchConnect()
    }

    fun setMuted(muted: Boolean) {
        if (closed) return
        _uiState.update { it.copy(isMuted = muted) }
        activeSubscriptions.values.forEach { it.player?.setMutedSafe(muted) }
    }

    /**
     * Toggle whether we hold a speaker slot. Tier-2's "leave the stage"
     * tap flips this to `false`; the next kind-10312 heartbeat picks up
     * the change via [NestUiState.onStageNow]. Does NOT stop a
     * running broadcast — UI / caller is expected to call
     * [BroadcastHandle.close] separately when leaving the stage.
     */
    fun setOnStage(onStage: Boolean) {
        if (closed) return
        _uiState.update { it.copy(onStageNow = onStage) }
    }

    /**
     * Apply one kind-10312 presence event to the in-memory aggregator.
     * Caller owns the LocalCache → VM plumbing (the platform layer
     * filters events by the current room's `a`-tag before invoking
     * this). Out-of-order arrivals can't downgrade fresher state —
     * see [RoomPresenceAggregator.apply].
     */
    fun onPresenceEvent(event: com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent) {
        if (closed) return
        _presences.value = presenceAgg.apply(event)
    }

    /**
     * Drop peers whose last heartbeat is older than [olderThanSec].
     * The platform layer drives this on a periodic tick (typically
     * every 60 s with `now - 6 * 60` so a peer that's missed one
     * heartbeat plus a 5-min "still here" window gets evicted).
     */
    fun evictStalePresences(olderThanSec: Long) {
        if (closed) return
        _presences.value = presenceAgg.evictOlderThan(olderThanSec)
    }

    /**
     * Apply one kind-1311 chat event to the room ledger. The platform
     * layer is the source — it filters by `a`-tag matching the current
     * room before invoking this. Same-id re-emits are deduped; the
     * resulting list is sorted by `created_at` ascending.
     */
    fun onChatEvent(event: com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent) {
        if (closed) return
        if (chatById.containsKey(event.id)) return
        chatById[event.id] = event
        _chat.value = chatById.values.sortedBy { it.createdAt }
    }

    /**
     * Apply one kind-7 reaction event to the sliding-window aggregator.
     * Caller passes [nowSec] (so tests can be deterministic) and the
     * fixed 30-s window. Mirror of [evictReactions] for the per-tick
     * cleanup.
     */
    fun onReactionEvent(
        event: com.vitorpamplona.quartz.nip25Reactions.ReactionEvent,
        nowSec: Long,
        windowSec: Long = REACTION_WINDOW_SEC,
    ) {
        if (closed) return
        _recentReactions.value = reactionsAgg.apply(event, nowSec, windowSec)
    }

    /**
     * Drop reactions older than the staleness threshold. Platform
     * layer drives this on a 1-s tick to update the floating overlay
     * without per-component animation timers.
     */
    fun evictReactions(olderThanSec: Long) {
        if (closed) return
        _recentReactions.value = reactionsAgg.evictAndSnapshot(olderThanSec)
    }

    /**
     * Whether this VM was constructed with capture + encoder factories. The
     * UI uses this to decide whether to render the talk button at all —
     * desktop and listener-only screens leave it false.
     */
    val canBroadcast: Boolean = captureFactory != null && encoderFactory != null

    /**
     * Begin broadcasting our own pubkey's audio. Caller must have already
     * obtained `RECORD_AUDIO`. Idempotent: returns immediately if a
     * broadcast is already in flight.
     *
     * If the listener path isn't [ConnectionUiState.Connected] yet, the
     * call is a no-op (the UI shouldn't expose the talk button before
     * Connected anyway).
     *
     * Note that the listener and speaker paths each own their own MoQ
     * session over a separate WebTransport — nests' protocol uses one
     * session per direction.
     */
    fun startBroadcast(speakerPubkeyHex: String) {
        if (closed || !canBroadcast) return
        if (_uiState.value.connection !is ConnectionUiState.Connected) return
        if (_uiState.value.broadcast is BroadcastUiState.Broadcasting ||
            _uiState.value.broadcast is BroadcastUiState.Connecting
        ) {
            return
        }
        _uiState.update {
            it.copy(broadcast = BroadcastUiState.Connecting)
        }
        speakerConnectJob =
            viewModelScope.launch {
                try {
                    val s =
                        speakerConnector.connect(
                            httpClient = httpClient,
                            transport = transport,
                            scope = viewModelScope,
                            room = room,
                            signer = signer,
                            speakerPubkeyHex = speakerPubkeyHex,
                            captureFactory = captureFactory!!,
                            encoderFactory = encoderFactory!!,
                        )
                    if (closed) {
                        runCatching { s.close() }
                        return@launch
                    }
                    speaker = s
                    observeSpeakerState(s)
                    val handle = s.startBroadcasting()
                    broadcastHandle = handle
                    _uiState.update { it.copy(broadcast = BroadcastUiState.Broadcasting(isMuted = false)) }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    _uiState.update {
                        it.copy(
                            broadcast =
                                BroadcastUiState.Failed(
                                    t.message ?: t::class.simpleName ?: "broadcast failed",
                                ),
                        )
                    }
                }
            }
    }

    /**
     * Toggle the speaker-side mic mute. Cheap; the network keeps running.
     *
     * UI flips AFTER the suspending `broadcastHandle.setMuted(...)` returns
     * so the indicator never claims muted while audio is still flowing
     * (audit VM #7). We accept a small UI latency in exchange for an
     * accurate state machine.
     */
    fun setMicMuted(muted: Boolean) {
        if (closed) return
        val handle = broadcastHandle ?: return
        viewModelScope.launch {
            val result = handle.runCatching { setMuted(muted) }
            if (closed) return@launch
            _uiState.update {
                val current = it.broadcast
                if (current !is BroadcastUiState.Broadcasting) return@update it
                if (result.isSuccess) {
                    it.copy(broadcast = current.copy(isMuted = muted, muteError = null))
                } else {
                    // Surface the failure so the UI can show a toast / inline
                    // message instead of silently swallowing (audit round-2
                    // VM #8b). We keep the broadcast running with the prior
                    // mute state — only the mute toggle failed.
                    val why = result.exceptionOrNull()?.message ?: "mute failed"
                    it.copy(broadcast = current.copy(muteError = why))
                }
            }
        }
    }

    /** Stop broadcasting. UI keeps the listener path connected. Idempotent. */
    fun stopBroadcast() {
        if (closed) return
        teardownBroadcast(BroadcastUiState.Idle)
    }

    /** Tear down without finalizing the VM (e.g. user pressed Disconnect). */
    fun disconnect() {
        if (closed) return
        // Forget the requested speaker set so a fresh connect() to a
        // different room (or same room after a long pause) doesn't reuse
        // a stale snapshot that may no longer be on stage.
        requestedSpeakers = emptySet()
        teardownBroadcast(BroadcastUiState.Idle, finalCleanup = false)
        teardown(targetState = ConnectionUiState.Idle, finalCleanup = false)
    }

    override fun onCleared() {
        closed = true
        teardownBroadcast(BroadcastUiState.Idle, finalCleanup = true)
        teardown(targetState = ConnectionUiState.Closed, finalCleanup = true)
        super.onCleared()
    }

    private fun observeSpeakerState(s: NestsSpeaker) {
        speakerStateJob?.cancel()
        speakerStateJob =
            viewModelScope.launch {
                s.state.collect { state ->
                    when (state) {
                        is NestsSpeakerState.Failed -> {
                            _uiState.update {
                                it.copy(broadcast = BroadcastUiState.Failed(state.reason))
                            }
                        }

                        NestsSpeakerState.Closed -> {
                            _uiState.update { it.copy(broadcast = BroadcastUiState.Idle) }
                        }

                        else -> { /* startBroadcast already set Broadcasting; no extra action */ }
                    }
                }
            }
    }

    private fun teardownBroadcast(
        targetState: BroadcastUiState,
        finalCleanup: Boolean = false,
    ) {
        speakerConnectJob?.cancel()
        speakerConnectJob = null
        speakerStateJob?.cancel()
        speakerStateJob = null
        val handle = broadcastHandle
        val s = speaker
        broadcastHandle = null
        speaker = null
        if (handle != null || s != null) {
            // User-driven disconnect can use viewModelScope (still alive);
            // onCleared must use cleanupScope because viewModelScope is
            // already cancelled and a launch on it would no-op without
            // emitting the UNANNOUNCE / SUBSCRIBE_DONE wire frames.
            val scope = if (finalCleanup) cleanupScope else viewModelScope
            scope.launch {
                handle?.runCatching { close() }
                s?.runCatching { close() }
            }
        }
        _uiState.update { it.copy(broadcast = targetState) }
    }

    /**
     * Drain the moq-lite ANNOUNCE flow into [announcedSpeakers].
     * Each Active emission adds the speaker pubkey; an inactive
     * emission removes it. Best-effort — listeners that don't
     * support announces (IETF reference path) throw on the first
     * `collect` and we silently leave the set empty (the UI falls
     * back to presence's `publishingNow` flag).
     */
    private fun observeAnnounces(l: NestsListener) {
        announcesJob?.cancel()
        announcesJob =
            viewModelScope.launch {
                runCatching {
                    l.announces().collect { ann ->
                        if (closed) return@collect
                        if (ann.active) {
                            _announcedSpeakers.update { it + ann.pubkey }
                        } else {
                            _announcedSpeakers.update { it - ann.pubkey }
                        }
                    }
                }
            }
    }

    private fun observeListenerState(l: NestsListener) {
        stateObserverJob?.cancel()
        stateObserverJob =
            viewModelScope.launch {
                l.state.collect { state ->
                    _uiState.update { ui ->
                        ui.copy(connection = state.toUiState(ui.connection))
                    }
                    when (state) {
                        is NestsListenerState.Connected -> {
                            // Re-issue the requested speaker set against
                            // whatever session is now live. With the
                            // reconnecting wrapper in place, on a session
                            // swap the existing handles auto-survive via
                            // the wrapper's MutableSharedFlow pump, so
                            // this is typically a no-op in toAdd / toRemove
                            // — runs anyway for the first-Connected path.
                            reconcileSubscriptions()
                        }

                        is NestsListenerState.Failed -> {
                            // Wrapper has already exhausted its retry
                            // policy; surface Failed to the user and
                            // wait for a manual reconnect tap.
                        }

                        // Server-initiated Closed: tear down stale
                        // local state so any later user-driven reconnect
                        // starts fresh.
                        NestsListenerState.Closed -> {
                            teardown(targetState = ConnectionUiState.Closed)
                        }

                        else -> { /* no extra side effect */ }
                    }
                }
            }
    }

    /**
     * Shared connect-launch body for [connect]. Awaits any in-flight
     * `listener.close()` from a previous teardown so two QUIC sessions
     * don't briefly coexist (audit round-2 VM #10), then runs the
     * connector and observes the resulting listener.
     *
     * Retry-on-failure is handled inside the connector (the production
     * default wraps each session in [connectReconnectingNestsListener]),
     * so this path no longer schedules its own retries.
     */
    private fun launchConnect() {
        _uiState.update { it.copy(connection = ConnectionUiState.Connecting(ConnectionUiState.Step.ResolvingRoom)) }

        val priorClose = pendingCloseJob
        pendingCloseJob = null

        connectJob =
            viewModelScope.launch {
                try {
                    // Drain the previous listener's close before opening a
                    // new transport. This is fast (< 100 ms typical: send
                    // UNSUBSCRIBE / UNANNOUNCE / WT_CLOSE_SESSION + drain).
                    if (priorClose?.isActive == true) {
                        runCatching { priorClose.join() }
                    }
                    val l =
                        connector.connect(
                            httpClient = httpClient,
                            transport = transport,
                            scope = viewModelScope,
                            room = room,
                            signer = signer,
                        )
                    if (closed) {
                        runCatching { l.close() }
                        return@launch
                    }
                    listener = l
                    observeListenerState(l)
                    observeAnnounces(l)
                    startLevelEmitter()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    _uiState.update {
                        it.copy(connection = ConnectionUiState.Failed(t.message ?: t::class.simpleName ?: "connect failed"))
                    }
                }
            }
    }

    private fun reconcileSubscriptions() {
        val l = listener ?: return
        if (_uiState.value.connection !is ConnectionUiState.Connected) return

        val toAdd = requestedSpeakers - activeSubscriptions.keys
        val toRemove = activeSubscriptions.keys - requestedSpeakers

        toRemove.forEach { pubkey ->
            activeSubscriptions.remove(pubkey)?.let { closeSubscription(it) }
        }

        toAdd.forEach { pubkey ->
            // Mark as pending immediately so concurrent reconciles don't
            // double-subscribe; flip to active once the SUBSCRIBE_OK arrives.
            val pending = ActiveSubscription.pending(pubkey)
            activeSubscriptions[pubkey] = pending
            viewModelScope.launch {
                openSubscription(l, pubkey, pending)
            }
        }

        publishActiveSpeakers()
    }

    /**
     * Stop the per-speaker player + fire-and-forget UNSUBSCRIBE on the VM
     * scope. Both `NestPlayer.stop()` and `SubscribeHandle.unsubscribe()`
     * are suspend, so we route them through one coroutine instead of two.
     */
    private fun closeSubscription(slot: ActiveSubscription) {
        val (roomPlayer, handle) = slot.detach()
        speakingExpiryJobs.remove(slot.pubkey)?.cancel()
        // Cancel + drop the catalog collector BEFORE removing the
        // map entry. Otherwise the still-running collector could
        // re-add the entry on the next emission (the wrapper's
        // re-issuance pump keeps the underlying subscription alive
        // across session swaps).
        catalogJobs.remove(slot.pubkey)?.cancel()
        if (_uiState.value.speakingNow.contains(slot.pubkey)) {
            _uiState.update { it.copy(speakingNow = (it.speakingNow - slot.pubkey).toPersistentSet()) }
        }
        if (_uiState.value.connectingSpeakers.contains(slot.pubkey)) {
            _uiState.update { it.copy(connectingSpeakers = (it.connectingSpeakers - slot.pubkey).toPersistentSet()) }
        }
        if (_speakerCatalogs.value.containsKey(slot.pubkey)) {
            _speakerCatalogs.update { it - slot.pubkey }
        }
        if (rawAudioLevels.remove(slot.pubkey) != null) {
            _audioLevels.value = rawAudioLevels.toMap()
        }
        if (roomPlayer != null || handle != null) {
            viewModelScope.launch {
                roomPlayer?.runCatching { stop() }
                handle?.runCatching { unsubscribe() }
            }
        }
    }

    /**
     * Open the speaker's `catalog.json` track in the background, parse
     * the first frame, and stash it in [speakerCatalogs]. Best-effort —
     * any failure (IETF listener throws UnsupportedOperationException,
     * the publisher doesn't publish a catalog, the JSON is malformed)
     * is silent. The catalog channel uses the same re-issuing pump
     * the audio path does, so it survives reconnects.
     */
    private fun fetchSpeakerCatalog(
        l: NestsListener,
        pubkey: String,
    ) {
        // Cancel any in-flight catalog collector for this pubkey
        // before launching a fresh one — guards against a re-add
        // racing with closeSubscription's cancel.
        catalogJobs.remove(pubkey)?.cancel()
        catalogJobs[pubkey] =
            viewModelScope.launch {
                val handle = runCatching { l.subscribeCatalog(pubkey) }.getOrNull() ?: return@launch
                try {
                    handle.objects.collect { obj ->
                        if (closed) return@collect
                        val parsed = RoomSpeakerCatalog.parseOrNull(obj.payload) ?: return@collect
                        _speakerCatalogs.update { it + (pubkey to parsed) }
                    }
                } finally {
                    runCatching { handle.unsubscribe() }
                }
            }
    }

    private suspend fun openSubscription(
        l: NestsListener,
        pubkey: String,
        slot: ActiveSubscription,
    ) {
        if (closed || activeSubscriptions[pubkey] !== slot) return
        try {
            val handle = l.subscribeSpeaker(pubkey)
            // Re-check after the suspending subscribeSpeaker — the user
            // may have removed this speaker via updateSpeakers / disconnected
            // while the SUBSCRIBE was in flight. If so, abandon the handle
            // cleanly (fire-and-forget UNSUBSCRIBE) instead of attaching it
            // to a slot the reconcile loop has already discarded.
            if (closed || activeSubscriptions[pubkey] !== slot) {
                viewModelScope.launch { runCatching { handle.unsubscribe() } }
                return
            }
            // Allocate native resources (MediaCodec decoder + AudioTrack
            // player on Android). Both are heavy and leaky if dropped on
            // the floor — wrap them in a nested try so any cancellation
            // or throw between here and slot.attach releases them
            // (audit round-2 VM #7).
            val decoder = decoderFactory()
            val player =
                try {
                    playerFactory()
                } catch (t: Throwable) {
                    runCatching { decoder.release() }
                    throw t
                }
            try {
                val isMuted = _uiState.value.isMuted
                val roomPlayer = NestPlayer(decoder, player, viewModelScope)
                // Apply current mute state before play() opens the device so the
                // first frame respects it.
                player.setMutedSafe(isMuted)
                // Tap the object flow to drive the speaking-now indicator before
                // the decoder consumes it.
                val instrumented = handle.objects.onEach { onSpeakerActivity(pubkey) }
                roomPlayer.play(
                    instrumented,
                    onError = { /* swallow per-packet decoder errors */ },
                    onLevel = { onAudioLevel(pubkey, it) },
                )
                slot.attach(handle, roomPlayer, player)
                publishActiveSpeakers()
                // Enter the buffering window — UI renders a spinner
                // overlay until the first audio frame triggers
                // `onSpeakerActivity` and clears the entry.
                _uiState.update {
                    it.copy(connectingSpeakers = (it.connectingSpeakers + pubkey).toPersistentSet())
                }
                // Parallel catalog fetch — best-effort, doesn't gate
                // audio playback. Tracked in catalogJobs; cancelled
                // by closeSubscription so a removed speaker doesn't
                // leave the catalog collector running on the
                // wrapper's still-live re-issuing handle.
                fetchSpeakerCatalog(l, pubkey)
            } catch (t: Throwable) {
                // Either CancellationException (scope cancelled mid-construction)
                // or an unexpected throw — release the half-built pipeline and
                // re-throw so the outer catch handles slot rollback.
                runCatching { player.stop() }
                runCatching { decoder.release() }
                throw t
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            // Roll back the slot we reserved so a future reconcile can retry.
            if (activeSubscriptions[pubkey] === slot) {
                activeSubscriptions.remove(pubkey)
                publishActiveSpeakers()
            }
        }
    }

    private fun teardown(
        targetState: ConnectionUiState,
        finalCleanup: Boolean = false,
    ) {
        connectJob?.cancel()
        connectJob = null
        stateObserverJob?.cancel()
        stateObserverJob = null
        announcesJob?.cancel()
        announcesJob = null
        levelEmitterJob?.cancel()
        levelEmitterJob = null
        rawAudioLevels.clear()
        if (_audioLevels.value.isNotEmpty()) {
            _audioLevels.value = emptyMap()
        }
        if (_announcedSpeakers.value.isNotEmpty()) {
            _announcedSpeakers.value = emptySet()
        }
        // Cancel every per-speaker catalog collector — these are
        // launched on viewModelScope but track the OLD listener's
        // re-issuing handle. Without explicit cancel they'd survive
        // until onCleared and pile up across reconnect / room
        // swaps. Drop the catalog map at the same time.
        catalogJobs.values.forEach { it.cancel() }
        catalogJobs.clear()
        if (_speakerCatalogs.value.isNotEmpty()) {
            _speakerCatalogs.value = emptyMap()
        }
        // Detach + suspend-stop each player on the cleanup scope. The
        // listener.close() below tears down the MoQ session and drops
        // every active subscription, so we don't need to call
        // unsubscribe() per-handle here — but we DO need to await each
        // NestPlayer.stop() so the native MediaCodec / AudioTrack
        // is released after its decode loop has unwound.
        val scope = if (finalCleanup) cleanupScope else viewModelScope
        val players = activeSubscriptions.values.map { it.detach().first }
        activeSubscriptions.clear()
        speakingExpiryJobs.values.forEach { it.cancel() }
        speakingExpiryJobs.clear()
        if (players.isNotEmpty()) {
            scope.launch {
                players.forEach { p -> p?.runCatching { stop() } }
            }
        }
        val l = listener
        listener = null
        if (l != null) {
            // Listener.close() sends MoQ control frames (UNSUBSCRIBE etc.)
            // before the QUIC transport drops. Same scope rule as the
            // player stop above: viewModelScope when alive, cleanupScope
            // for onCleared so the wire teardown survives.
            // Track the close so the next connect() can await it (audit
            // round-2 VM #10). If `finalCleanup` is true we don't bother
            // — the VM is going away and no future connect() will run.
            val closeLaunch = scope.launch { runCatching { l.close() } }
            if (!finalCleanup) pendingCloseJob = closeLaunch
        }
        _uiState.update {
            it.copy(
                connection = targetState,
                activeSpeakers = persistentSetOf(),
                speakingNow = persistentSetOf(),
                connectingSpeakers = persistentSetOf(),
            )
        }
    }

    private fun publishActiveSpeakers() {
        val active =
            activeSubscriptions
                .filterValues { it.isPlaying }
                .keys
                .toPersistentSet()
        _uiState.update { it.copy(activeSpeakers = active) }
    }

    /**
     * Mark [pubkey] as currently speaking and (re)arm a [SPEAKING_TIMEOUT_MS]
     * coroutine that clears it once they go quiet. Called once per
     * MoQ object received on the speaker's track.
     */
    private fun onSpeakerActivity(pubkey: String) {
        if (closed) return
        speakingExpiryJobs[pubkey]?.cancel()
        // First frame for this subscription — clear the buffering
        // overlay. Subsequent frames are no-ops here.
        if (_uiState.value.connectingSpeakers.contains(pubkey)) {
            _uiState.update { it.copy(connectingSpeakers = (it.connectingSpeakers - pubkey).toPersistentSet()) }
        }
        if (!_uiState.value.speakingNow.contains(pubkey)) {
            _uiState.update { it.copy(speakingNow = (it.speakingNow + pubkey).toPersistentSet()) }
        }
        speakingExpiryJobs[pubkey] =
            viewModelScope.launch {
                delay(SPEAKING_TIMEOUT_MS)
                clearSpeaking(pubkey)
            }
    }

    private fun clearSpeaking(pubkey: String) {
        speakingExpiryJobs.remove(pubkey)
        if (_uiState.value.speakingNow.contains(pubkey)) {
            _uiState.update { it.copy(speakingNow = (it.speakingNow - pubkey).toPersistentSet()) }
        }
        // Drop the latest level too — when the speaker goes quiet the
        // ring should fall back to the static "in speakingNow" colour
        // rather than freezing at the last loud peak.
        if (rawAudioLevels.remove(pubkey) != null) {
            _audioLevels.value = rawAudioLevels.toMap()
        }
    }

    /**
     * Record the latest decoded peak amplitude for [pubkey]. Called
     * from the [NestPlayer] decode loop on the same dispatcher as the
     * VM, so plain map mutation is safe. The actual StateFlow emission
     * is coalesced by [startLevelEmitter] so a 50 Hz packet rate
     * doesn't translate into 50 Hz recompositions.
     */
    private fun onAudioLevel(
        pubkey: String,
        level: Float,
    ) {
        if (closed) return
        rawAudioLevels[pubkey] = level
    }

    /**
     * Tick every [LEVEL_TICK_MS] and publish the current map of
     * per-speaker levels. Coalesces the high-frequency raw updates
     * into ~10 Hz UI state so the speaking-ring animation has a
     * smooth, lightweight signal to follow.
     */
    private fun startLevelEmitter() {
        if (levelEmitterJob?.isActive == true) return
        levelEmitterJob =
            viewModelScope.launch {
                while (true) {
                    delay(LEVEL_TICK_MS)
                    if (closed) return@launch
                    val snapshot = if (rawAudioLevels.isEmpty()) emptyMap() else rawAudioLevels.toMap()
                    if (snapshot != _audioLevels.value) {
                        _audioLevels.value = snapshot
                    }
                }
            }
    }

    private class ActiveSubscription private constructor(
        val pubkey: String,
    ) {
        private var handle: SubscribeHandle? = null
        private var roomPlayer: NestPlayer? = null
        var player: AudioPlayer? = null
            private set
        var isPlaying: Boolean = false
            private set

        fun attach(
            handle: SubscribeHandle,
            roomPlayer: NestPlayer,
            player: AudioPlayer,
        ) {
            this.handle = handle
            this.roomPlayer = roomPlayer
            this.player = player
            this.isPlaying = true
        }

        /**
         * Hand the player + handle back to the caller's coroutine scope —
         * `NestPlayer.stop()` and `SubscribeHandle.unsubscribe()` are
         * both suspend, and the caller has the right scope to await them
         * (so native MediaCodec/AudioTrack release runs after the decode
         * loop has unwound, per audit MoQ #11/#12).
         */
        fun detach(): Pair<NestPlayer?, SubscribeHandle?> {
            isPlaying = false
            val p = roomPlayer
            val h = handle
            roomPlayer = null
            handle = null
            player = null
            return p to h
        }

        companion object {
            fun pending(pubkey: String) = ActiveSubscription(pubkey)
        }
    }

    // Platform-specific Factory lives in `amethyst/.../nests/room/`,
    // not commonMain — the lifecycle KMP `ViewModelProvider.Factory`
    // signature has been migrating between releases; a thin Android-side
    // factory keeps that churn out of shared code.
}

/**
 * Map [NestsListenerState] (from the transport library) onto the small set of
 * UI-level states the screen actually needs to render. We collapse the three
 * `Connecting` substeps into one enum so the chip can show a single
 * progress message; advanced UI can branch on [ConnectionUiState.Step]
 * later if it cares.
 */
private fun NestsListenerState.toUiState(previous: ConnectionUiState): ConnectionUiState =
    when (this) {
        // The transport library starts in Idle, but the VM has already
        // shown "Connecting → ResolvingRoom" by the time observation starts;
        // don't regress the UI back to Idle on the very first emission.
        NestsListenerState.Idle -> {
            if (previous is ConnectionUiState.Connecting) previous else ConnectionUiState.Idle
        }

        is NestsListenerState.Connecting -> {
            ConnectionUiState.Connecting(
                step =
                    when (step) {
                        NestsListenerState.Connecting.ConnectStep.ResolvingRoom -> ConnectionUiState.Step.ResolvingRoom
                        NestsListenerState.Connecting.ConnectStep.OpeningTransport -> ConnectionUiState.Step.OpeningTransport
                        NestsListenerState.Connecting.ConnectStep.MoqHandshake -> ConnectionUiState.Step.MoqHandshake
                    },
            )
        }

        is NestsListenerState.Connected -> {
            ConnectionUiState.Connected
        }

        is NestsListenerState.Reconnecting -> {
            ConnectionUiState.Reconnecting(attempt = attempt, delayMs = delayMs)
        }

        is NestsListenerState.Failed -> {
            ConnectionUiState.Failed(reason)
        }

        NestsListenerState.Closed -> {
            ConnectionUiState.Closed
        }
    }

private fun AudioPlayer.setMutedSafe(muted: Boolean) {
    runCatching { setMuted(muted) }
}

@Immutable
data class NestUiState(
    val connection: ConnectionUiState = ConnectionUiState.Idle,
    val isMuted: Boolean = false,
    /** Pubkeys we have an open MoQ subscription for. */
    val activeSpeakers: ImmutableSet<String> = persistentSetOf(),
    /** Pubkeys whose audio track delivered an object in the last [SPEAKING_TIMEOUT_MS]. */
    val speakingNow: ImmutableSet<String> = persistentSetOf(),
    /**
     * Pubkeys we have an open subscription for but no audio frame has
     * arrived yet — the pre-roll window between SUBSCRIBE_OK and the
     * first decoded packet. Typically 0.5-2 s on a fresh join. The UI
     * shows a buffering overlay here so the user knows audio is on
     * its way (vs the speaker sitting silent on stage).
     *
     * Membership is set-once-per-subscription: once a frame arrives
     * the pubkey moves out of this set and stays out, even if the
     * speaker subsequently goes quiet. Cleared on speaker removal.
     */
    val connectingSpeakers: ImmutableSet<String> = persistentSetOf(),
    /** Speaker / publisher state — only relevant when [NestViewModel.canBroadcast]. */
    val broadcast: BroadcastUiState = BroadcastUiState.Idle,
    /**
     * `true` when we hold a speaker slot vs being pure audience. Defaults
     * to `true` for users with [NestViewModel.canBroadcast]; the
     * "leave the stage" tap (Tier 2) flips it to `false`. Drives the
     * `["onstage", "0|1"]` tag on emitted kind-10312 presence events.
     */
    val onStageNow: Boolean = true,
) {
    /**
     * Derived: are we currently pushing audio packets to the relay?
     * `true` only when the broadcast handle is live AND we're not muted.
     * Drives the `["publishing", "0|1"]` tag on emitted kind-10312
     * presence events.
     */
    val publishingNow: Boolean
        get() = broadcast is BroadcastUiState.Broadcasting && !broadcast.isMuted
}

@Immutable
sealed class BroadcastUiState {
    data object Idle : BroadcastUiState()

    data object Connecting : BroadcastUiState()

    data class Broadcasting(
        val isMuted: Boolean,
        /**
         * Non-null when the most recent mute toggle failed (e.g. handle
         * setMuted threw). UI surfaces this as an inline message; the
         * broadcast itself stays running with the previous mute state.
         * Cleared on the next successful mute toggle.
         */
        val muteError: String? = null,
    ) : BroadcastUiState()

    data class Failed(
        val reason: String,
    ) : BroadcastUiState()
}

/**
 * How long a speaker stays "speaking" after their last received MoQ object.
 * Roughly 12 × the 20 ms Opus frame so brief packet jitter doesn't make the
 * indicator flicker.
 */
const val SPEAKING_TIMEOUT_MS: Long = 250L

/**
 * Coalescing interval for [NestViewModel.audioLevels]. The decode loop
 * pushes a fresh peak every ~20 ms (one per Opus frame); we publish to
 * the StateFlow at this cadence instead so the UI ring animates ~10 Hz
 * instead of recomposing every frame. 100 ms is fast enough that the
 * eye still reads the throb as live, slow enough that the cost across
 * a busy stage stays trivial.
 */
const val LEVEL_TICK_MS: Long = 100L

/**
 * How long a kind-7 reaction stays in
 * [NestViewModel.recentReactions] before the eviction sweep
 * drops it. Matches the duration of the floating-up animation in the
 * SpeakerReactionOverlay.
 */
const val REACTION_WINDOW_SEC: Long = 30L

/**
 * Indirection over the top-level `connectNestsListener` so tests can drive
 * a fake [NestsListener] directly without standing up an HTTP fake +
 * WebTransport fake.
 */
fun interface NestsListenerConnector {
    suspend fun connect(
        httpClient: NestsClient,
        transport: WebTransportFactory,
        scope: CoroutineScope,
        room: NestsRoomConfig,
        signer: NostrSigner,
    ): NestsListener
}

/**
 * Production listener factory — wraps each session in
 * [connectReconnectingNestsListener] so transport drops auto-retry
 * with exponential backoff and `SubscribeHandle`s survive session
 * swaps via the wrapper's MutableSharedFlow re-issuance pump.
 *
 * The VM's own scheduleAutoRetry path was retired in favour of
 * this — a single retry policy lives in the transport layer rather
 * than racing two of them.
 */
private val DefaultNestsListenerConnector =
    NestsListenerConnector { httpClient, transport, scope, room, signer ->
        connectReconnectingNestsListener(
            httpClient = httpClient,
            transport = transport,
            scope = scope,
            room = room,
            signer = signer,
        )
    }

/** Speaker-side equivalent of [NestsListenerConnector]. */
fun interface NestsSpeakerConnector {
    suspend fun connect(
        httpClient: NestsClient,
        transport: WebTransportFactory,
        scope: CoroutineScope,
        room: NestsRoomConfig,
        signer: NostrSigner,
        speakerPubkeyHex: String,
        captureFactory: () -> AudioCapture,
        encoderFactory: () -> OpusEncoder,
    ): NestsSpeaker
}

private val DefaultNestsSpeakerConnector =
    NestsSpeakerConnector { httpClient, transport, scope, room, signer, pubkey, capF, encF ->
        connectNestsSpeaker(
            httpClient = httpClient,
            transport = transport,
            scope = scope,
            room = room,
            signer = signer,
            speakerPubkeyHex = pubkey,
            captureFactory = capF,
            encoderFactory = encF,
        )
    }

@Immutable
sealed class ConnectionUiState {
    data object Idle : ConnectionUiState()

    data class Connecting(
        val step: Step,
    ) : ConnectionUiState()

    data object Connected : ConnectionUiState()

    /**
     * The previous session dropped and the wrapper's retry loop is
     * waiting [delayMs] before its next attempt. [attempt] is
     * 1-indexed (1 = first retry after the original session
     * failed). UI shows "Reconnecting…" with a friendlier message
     * than a raw `Failed` would convey — the user typically
     * doesn't need to do anything; the orchestrator will flip back
     * to [Connected] on its own.
     */
    data class Reconnecting(
        val attempt: Int,
        val delayMs: Long,
    ) : ConnectionUiState()

    data class Failed(
        val reason: String,
    ) : ConnectionUiState()

    data object Closed : ConnectionUiState()

    enum class Step {
        ResolvingRoom,
        OpeningTransport,
        MoqHandshake,
    }
}
