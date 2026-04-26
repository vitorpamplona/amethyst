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
import com.vitorpamplona.nestsclient.NestsClient
import com.vitorpamplona.nestsclient.NestsListener
import com.vitorpamplona.nestsclient.NestsListenerState
import com.vitorpamplona.nestsclient.audio.AudioPlayer
import com.vitorpamplona.nestsclient.audio.AudioRoomPlayer
import com.vitorpamplona.nestsclient.audio.OpusDecoder
import com.vitorpamplona.nestsclient.connectNestsListener
import com.vitorpamplona.nestsclient.moq.SubscribeHandle
import com.vitorpamplona.nestsclient.transport.WebTransportFactory
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineScope
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
 * [AudioRoomPlayer] per active speaker subscription. The screen tells the
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
 */
@Stable
class AudioRoomViewModel(
    private val httpClient: NestsClient,
    private val transport: WebTransportFactory,
    private val decoderFactory: () -> OpusDecoder,
    private val playerFactory: () -> AudioPlayer,
    private val signer: NostrSigner,
    private val serviceBase: String,
    private val roomId: String,
    // Seam for tests — production code uses the default which delegates to
    // the real `connectNestsListener`. Tests inject a fake that returns a
    // listener whose state they can drive directly.
    private val connector: NestsListenerConnector = DefaultNestsListenerConnector,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AudioRoomUiState())
    val uiState: StateFlow<AudioRoomUiState> = _uiState.asStateFlow()

    private var listener: NestsListener? = null
    private var connectJob: Job? = null
    private var stateObserverJob: Job? = null

    private val activeSubscriptions = mutableMapOf<String, ActiveSubscription>()
    private val speakingExpiryJobs = mutableMapOf<String, Job>()
    private var requestedSpeakers: Set<String> = emptySet()
    private var closed = false

    /** Push the latest known speaker set from the room event. */
    fun updateSpeakers(speakerPubkeys: Set<String>) {
        if (closed) return
        requestedSpeakers = speakerPubkeys
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

        _uiState.update { it.copy(connection = ConnectionUiState.Connecting(ConnectionUiState.Step.ResolvingRoom)) }

        connectJob =
            viewModelScope.launch {
                try {
                    val l =
                        connector.connect(
                            httpClient = httpClient,
                            transport = transport,
                            scope = viewModelScope,
                            serviceBase = serviceBase,
                            roomId = roomId,
                            signer = signer,
                        )
                    if (closed) {
                        runCatching { l.close() }
                        return@launch
                    }
                    listener = l
                    observeListenerState(l)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    _uiState.update {
                        it.copy(connection = ConnectionUiState.Failed(t.message ?: t::class.simpleName ?: "connect failed"))
                    }
                }
            }
    }

    fun setMuted(muted: Boolean) {
        if (closed) return
        _uiState.update { it.copy(isMuted = muted) }
        activeSubscriptions.values.forEach { it.player?.setMutedSafe(muted) }
    }

    /** Tear down without finalizing the VM (e.g. user pressed Disconnect). */
    fun disconnect() {
        if (closed) return
        teardown(targetState = ConnectionUiState.Idle)
    }

    override fun onCleared() {
        closed = true
        teardown(targetState = ConnectionUiState.Closed)
        super.onCleared()
    }

    private fun observeListenerState(l: NestsListener) {
        stateObserverJob?.cancel()
        stateObserverJob =
            viewModelScope.launch {
                l.state.collect { state ->
                    _uiState.update { ui ->
                        ui.copy(connection = state.toUiState(ui.connection))
                    }
                    if (state is NestsListenerState.Connected) {
                        reconcileSubscriptions()
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
     * Stop the per-speaker player synchronously (releases the audio device
     * immediately) and fire-and-forget the MoQ UNSUBSCRIBE on the VM scope.
     */
    private fun closeSubscription(slot: ActiveSubscription) {
        val handle = slot.detach()
        speakingExpiryJobs.remove(slot.pubkey)?.cancel()
        if (_uiState.value.speakingNow.contains(slot.pubkey)) {
            _uiState.update { it.copy(speakingNow = (it.speakingNow - slot.pubkey).toPersistentSet()) }
        }
        if (handle != null) {
            viewModelScope.launch { runCatching { handle.unsubscribe() } }
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
            val decoder = decoderFactory()
            val player = playerFactory()
            val isMuted = _uiState.value.isMuted
            val roomPlayer = AudioRoomPlayer(decoder, player, viewModelScope)
            // Apply current mute state before play() opens the device so the
            // first frame respects it.
            player.setMutedSafe(isMuted)
            // Tap the object flow to drive the speaking-now indicator before
            // the decoder consumes it.
            val instrumented = handle.objects.onEach { onSpeakerActivity(pubkey) }
            roomPlayer.play(instrumented, onError = { /* swallow per-packet decoder errors */ })
            slot.attach(handle, roomPlayer, player)
            publishActiveSpeakers()
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

    private fun teardown(targetState: ConnectionUiState) {
        connectJob?.cancel()
        connectJob = null
        stateObserverJob?.cancel()
        stateObserverJob = null
        // Stop players synchronously — unsubscribe happens implicitly when
        // the listener.close() below tears down the MoQ session.
        activeSubscriptions.values.forEach { it.detach() }
        activeSubscriptions.clear()
        speakingExpiryJobs.values.forEach { it.cancel() }
        speakingExpiryJobs.clear()
        val l = listener
        listener = null
        if (l != null) {
            // Closing the listener is suspending; fire-and-forget on the VM
            // scope is fine — even if the scope is cancelled (onCleared), the
            // underlying transport's own cleanup runs.
            viewModelScope.launch { runCatching { l.close() } }
        }
        _uiState.update {
            it.copy(
                connection = targetState,
                activeSpeakers = persistentSetOf(),
                speakingNow = persistentSetOf(),
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
    }

    private class ActiveSubscription private constructor(
        val pubkey: String,
    ) {
        private var handle: SubscribeHandle? = null
        private var roomPlayer: AudioRoomPlayer? = null
        var player: AudioPlayer? = null
            private set
        var isPlaying: Boolean = false
            private set

        fun attach(
            handle: SubscribeHandle,
            roomPlayer: AudioRoomPlayer,
            player: AudioPlayer,
        ) {
            this.handle = handle
            this.roomPlayer = roomPlayer
            this.player = player
            this.isPlaying = true
        }

        /**
         * Stop the player + decoder synchronously and return the
         * [SubscribeHandle] (if any) so the caller can fire-and-forget
         * UNSUBSCRIBE on its own coroutine scope.
         */
        fun detach(): SubscribeHandle? {
            isPlaying = false
            roomPlayer?.let { runCatching { it.stop() } }
            val h = handle
            roomPlayer = null
            handle = null
            player = null
            return h
        }

        companion object {
            fun pending(pubkey: String) = ActiveSubscription(pubkey)
        }
    }

    // Platform-specific Factory lives in `amethyst/.../audiorooms/room/`,
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
data class AudioRoomUiState(
    val connection: ConnectionUiState = ConnectionUiState.Idle,
    val isMuted: Boolean = false,
    /** Pubkeys we have an open MoQ subscription for. */
    val activeSpeakers: ImmutableSet<String> = persistentSetOf(),
    /** Pubkeys whose audio track delivered an object in the last [SPEAKING_TIMEOUT_MS]. */
    val speakingNow: ImmutableSet<String> = persistentSetOf(),
)

/**
 * How long a speaker stays "speaking" after their last received MoQ object.
 * Roughly 12 × the 20 ms Opus frame so brief packet jitter doesn't make the
 * indicator flicker.
 */
const val SPEAKING_TIMEOUT_MS: Long = 250L

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
        serviceBase: String,
        roomId: String,
        signer: NostrSigner,
    ): NestsListener
}

private val DefaultNestsListenerConnector =
    NestsListenerConnector { httpClient, transport, scope, serviceBase, roomId, signer ->
        connectNestsListener(
            httpClient = httpClient,
            transport = transport,
            scope = scope,
            serviceBase = serviceBase,
            roomId = roomId,
            signer = signer,
        )
    }

@Immutable
sealed class ConnectionUiState {
    data object Idle : ConnectionUiState()

    data class Connecting(
        val step: Step,
    ) : ConnectionUiState()

    data object Connected : ConnectionUiState()

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
