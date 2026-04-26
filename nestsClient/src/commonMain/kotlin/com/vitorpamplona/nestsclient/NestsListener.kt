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
package com.vitorpamplona.nestsclient

import com.vitorpamplona.nestsclient.moq.MoqSession
import com.vitorpamplona.nestsclient.moq.SubscribeFilter
import com.vitorpamplona.nestsclient.moq.SubscribeHandle
import com.vitorpamplona.nestsclient.moq.TrackNamespace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High-level listener handle for an audio-room. Hides the layered HTTP +
 * WebTransport + MoQ wiring under one observable state machine so UI code
 * (and tests) can reason about the room as a single resource.
 *
 * Open one [NestsListener] per audio-room screen. To listen to multiple
 * speakers in the room, call [subscribeSpeaker] once per speaker pubkey.
 */
interface NestsListener {
    /** Live connection state — the UI typically shows a chip / spinner derived from this. */
    val state: StateFlow<NestsListenerState>

    /**
     * Subscribe to one speaker's audio track. nests publishes each speaker's
     * Opus stream under the namespace `["nests", <roomId>]` with the
     * speaker's pubkey hex as the track name.
     *
     * @throws com.vitorpamplona.nestsclient.moq.MoqProtocolException if the
     *   publisher rejects the subscription.
     * @throws IllegalStateException if the listener is not in [NestsListenerState.Connected].
     */
    suspend fun subscribeSpeaker(speakerPubkeyHex: String): SubscribeHandle

    /** Tear down the MoQ session + underlying transport. Idempotent. */
    suspend fun close()
}

/**
 * Lifecycle states of a [NestsListener].
 *
 * Resolved/connected information (room metadata, MoQ version) is carried on
 * [NestsListenerState.Connected] so the UI can render details without
 * threading them separately.
 */
sealed class NestsListenerState {
    /** No connection attempt has started yet. */
    data object Idle : NestsListenerState()

    /** A connect call is in flight. [step] indicates which substage. */
    data class Connecting(
        val step: ConnectStep,
    ) : NestsListenerState() {
        enum class ConnectStep {
            /** Calling `<service>/<roomId>` to obtain the MoQ endpoint + token. */
            ResolvingRoom,

            /** Opening the WebTransport ([:quic] + Extended CONNECT). */
            OpeningTransport,

            /** Running the MoQ CLIENT_SETUP / SERVER_SETUP exchange. */
            MoqHandshake,
        }
    }

    /** Connection is live. [room] reflects the (auth, endpoint, room) we connected to. */
    data class Connected(
        val room: NestsRoomConfig,
        val negotiatedMoqVersion: Long,
    ) : NestsListenerState()

    /** A connect attempt or live session failed. UI shows [reason] to the user. */
    data class Failed(
        val reason: String,
        val cause: Throwable? = null,
    ) : NestsListenerState()

    /** [close] has been called. Terminal. */
    data object Closed : NestsListenerState()
}

/**
 * IETF `draft-ietf-moq-transport-17` [NestsListener] reference
 * implementation. **Not used in production** — the production listener
 * path uses [MoqLiteNestsListener] over moq-lite Lite-03 (see
 * `nestsClient/plans/2026-04-26-moq-lite-gap.md`). Kept for the IETF
 * unit-test suite (`MoqSession`, `MoqCodec`) and for any future IETF
 * MoQ-transport target.
 *
 * Delegates to a [MoqSession] already set up on a
 * [com.vitorpamplona.nestsclient.transport.WebTransportSession];
 * construction does NOT open the transport.
 */
class DefaultNestsListener internal constructor(
    private val session: MoqSession,
    private val roomNamespace: TrackNamespace,
    private val mutableState: MutableStateFlow<NestsListenerState>,
) : NestsListener {
    override val state: StateFlow<NestsListenerState> = mutableState.asStateFlow()

    override suspend fun subscribeSpeaker(speakerPubkeyHex: String): SubscribeHandle {
        check(state.value is NestsListenerState.Connected) {
            "NestsListener.subscribeSpeaker requires Connected state, was ${state.value}"
        }
        return session.subscribe(
            namespace = roomNamespace,
            trackName = speakerPubkeyHex.encodeToByteArray(),
            filter = SubscribeFilter.LatestGroup,
        )
    }

    override suspend fun close() {
        if (state.value is NestsListenerState.Closed) return
        runCatching { session.close() }
        mutableState.value = NestsListenerState.Closed
    }
}
