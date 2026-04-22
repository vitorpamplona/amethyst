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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.nestsclient.NestsListener
import com.vitorpamplona.nestsclient.NestsListenerState
import com.vitorpamplona.nestsclient.OkHttpNestsClient
import com.vitorpamplona.nestsclient.audio.AudioRoomPlayer
import com.vitorpamplona.nestsclient.audio.AudioTrackPlayer
import com.vitorpamplona.nestsclient.audio.MediaCodecOpusDecoder
import com.vitorpamplona.nestsclient.connectNestsListener
import com.vitorpamplona.nestsclient.transport.KwikWebTransportFactory
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Audio-pipeline owner for one open audio-room screen. Bridges the Compose
 * stage UI to the [NestsListener] facade in `nestsClient`.
 *
 * The ViewModel is intentionally Android-only (lives in `amethyst/`) — it
 * needs `viewModelScope`, the `MediaCodecOpusDecoder`, and the
 * `AudioTrackPlayer`, none of which exist in commons. It is a thin shell
 * that owns lifetime; all the real work happens in the `nestsClient` module.
 *
 * Lifecycle:
 *   - `connect(event, signer)` resolves the room HTTP-side, opens the
 *     WebTransport (currently throws NotImplemented from the Kwik stub —
 *     Phase 3b-2), runs the MoQ handshake, then subscribes to every speaker
 *     listed in the 30312 event and pipes their Opus frames through one
 *     [AudioRoomPlayer] each.
 *   - `disconnect()` tears down all per-speaker players and closes the
 *     listener. Idempotent, also called from `onCleared()`.
 */
class AudioRoomConnectionViewModel : ViewModel() {
    private val _state = MutableStateFlow<NestsListenerState>(NestsListenerState.Idle)
    val state: StateFlow<NestsListenerState> = _state.asStateFlow()

    private var listener: NestsListener? = null
    private val playersBySpeaker = LinkedHashMap<String, AudioRoomPlayer>()
    private var connectJob: Job? = null

    /**
     * Resolve the room URL and start playing every host/speaker track. Re-entrant
     * calls cancel any in-flight connect and start fresh — typically only useful
     * after a [disconnect] / failure.
     */
    fun connect(
        event: MeetingSpaceEvent,
        signer: NostrSigner,
    ) {
        connectJob?.cancel()
        connectJob =
            viewModelScope.launch(Dispatchers.IO) {
                disconnectInternal()
                _state.value = NestsListenerState.Connecting(NestsListenerState.Connecting.ConnectStep.ResolvingRoom)

                val service = event.service()
                if (service.isNullOrBlank()) {
                    _state.value =
                        NestsListenerState.Failed(
                            "Room has no `service` URL — cannot resolve a nests endpoint",
                        )
                    return@launch
                }

                val l =
                    com.vitorpamplona.nestsclient
                        .connectNestsListener(
                            httpClient = OkHttpNestsClient(),
                            transport = KwikWebTransportFactory(),
                            scope = viewModelScope,
                            serviceBase = service,
                            roomId = event.dTag(),
                            signer = signer,
                        )
                listener = l

                // Mirror the underlying listener's state so consumers only need
                // to observe one StateFlow.
                viewModelScope.launch { l.state.collect { _state.value = it } }

                if (l.state.value !is NestsListenerState.Connected) {
                    return@launch
                }

                // Subscribe to every host + speaker. Audience members don't
                // publish audio; ignore them.
                val speakerKeys =
                    event
                        .participants()
                        .filter { it.role.equals(ROLE.HOST.code, true) || it.role.equals(ROLE.SPEAKER.code, true) }
                        .map { it.pubKey }
                        .distinct()

                for (pubkey in speakerKeys) {
                    runCatching {
                        val handle = l.subscribeSpeaker(pubkey)
                        val player =
                            AudioRoomPlayer(
                                decoder = MediaCodecOpusDecoder(),
                                player = AudioTrackPlayer(),
                                scope = viewModelScope,
                            )
                        player.play(handle.objects) { /* per-speaker decode errors swallowed */ }
                        playersBySpeaker[pubkey] = player
                    }
                }
            }
    }

    /** Stop playback and tear down the listener. Idempotent. */
    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        viewModelScope.launch(Dispatchers.IO) { disconnectInternal() }
    }

    private suspend fun disconnectInternal() {
        for ((_, p) in playersBySpeaker) runCatching { p.stop() }
        playersBySpeaker.clear()
        runCatching { listener?.close() }
        listener = null
        // Leave _state alone if it's already Closed; otherwise reset to Idle so
        // the UI's "tap to retry" path is available.
        if (_state.value !is NestsListenerState.Closed) {
            _state.value = NestsListenerState.Idle
        }
    }

    override fun onCleared() {
        // Best-effort sync teardown — viewModelScope is being cancelled around us,
        // so any suspending listener.close() in disconnectInternal() may not run.
        runCatching {
            for ((_, p) in playersBySpeaker) p.stop()
            playersBySpeaker.clear()
        }
        super.onCleared()
    }
}
