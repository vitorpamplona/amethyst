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
package com.vitorpamplona.amethyst.commons.call

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Manages per-peer session state and ICE candidate buffering.
 *
 * This class encapsulates the NIP-AC spec requirements for:
 * - **Two-layer ICE buffering**: global buffer (before session exists) and
 *   per-session buffer (before remote description is set)
 * - **Renegotiation glare handling**: pubkey comparison tiebreaker
 * - **Callee-to-callee mesh**: lower pubkey initiates
 *
 * It is platform-independent and testable without real WebRTC.
 */
class PeerSessionManager(
    val localPubKey: HexKey,
) {
    data class SessionEntry(
        val session: PeerSession,
        var remoteDescriptionSet: Boolean = false,
        val pendingIceCandidates: MutableList<IceCandidateData> = mutableListOf(),
    )

    private val sessions = mutableMapOf<HexKey, SessionEntry>()

    /** Candidates received before a session exists for the sender. */
    private val globalPendingIce = mutableMapOf<HexKey, MutableList<IceCandidateData>>()

    // ---- Session management ----

    fun registerSession(
        peerPubKey: HexKey,
        session: PeerSession,
    ): SessionEntry {
        val globalPending = globalPendingIce.remove(peerPubKey) ?: emptyList()
        val entry = SessionEntry(session)
        entry.pendingIceCandidates.addAll(globalPending)
        sessions[peerPubKey] = entry
        return entry
    }

    fun getSession(peerPubKey: HexKey): SessionEntry? = sessions[peerPubKey]

    fun hasSession(peerPubKey: HexKey): Boolean = sessions.containsKey(peerPubKey)

    fun removeSession(peerPubKey: HexKey): SessionEntry? {
        globalPendingIce.remove(peerPubKey)
        return sessions.remove(peerPubKey)
    }

    fun allSessionKeys(): Set<HexKey> = sessions.keys.toSet()

    // ---- ICE candidate routing (two-layer buffering) ----

    /**
     * Routes an incoming ICE candidate to the correct destination:
     * 1. If session exists AND remote description is set -> add directly
     * 2. If session exists but remote description NOT set -> buffer per-session
     * 3. If no session exists -> buffer globally (keyed by sender)
     *
     * Returns the action taken for testability.
     */
    fun routeIceCandidate(
        senderPubKey: HexKey,
        candidate: IceCandidateData,
    ): IceRouteAction {
        val entry = sessions[senderPubKey]
        return when {
            entry != null && entry.remoteDescriptionSet -> {
                entry.session.addIceCandidate(candidate)
                IceRouteAction.ADDED_DIRECTLY
            }

            entry != null -> {
                entry.pendingIceCandidates.add(candidate)
                IceRouteAction.BUFFERED_PER_SESSION
            }

            else -> {
                globalPendingIce.getOrPut(senderPubKey) { mutableListOf() }.add(candidate)
                IceRouteAction.BUFFERED_GLOBALLY
            }
        }
    }

    /**
     * Flushes all per-session buffered candidates into the PeerConnection.
     * Called after setRemoteDescription succeeds.
     */
    fun flushPendingIceCandidates(peerPubKey: HexKey): Int {
        val entry = sessions[peerPubKey] ?: return 0
        entry.remoteDescriptionSet = true
        val candidates = entry.pendingIceCandidates.toList()
        entry.pendingIceCandidates.clear()
        candidates.forEach { entry.session.addIceCandidate(it) }
        return candidates.size
    }

    fun globalPendingCount(peerPubKey: HexKey): Int = globalPendingIce[peerPubKey]?.size ?: 0

    fun sessionPendingCount(peerPubKey: HexKey): Int = sessions[peerPubKey]?.pendingIceCandidates?.size ?: 0

    // ---- Renegotiation glare handling ----

    /**
     * Determines how to handle a renegotiation offer when we may have a pending
     * local offer (glare condition).
     *
     * Per NIP-AC spec: "the peer with the higher pubkey wins (their offer takes priority).
     * The losing peer MUST roll back their local offer."
     */
    fun resolveRenegotiationGlare(
        peerPubKey: HexKey,
        remoteSdpOffer: String,
        onAcceptRemote: (SessionEntry) -> Unit,
    ): GlareResolution {
        val entry = sessions[peerPubKey] ?: return GlareResolution.NO_SESSION

        val signalingState = entry.session.getSignalingState()
        if (signalingState != SignalingState.HAVE_LOCAL_OFFER) {
            onAcceptRemote(entry)
            return GlareResolution.NO_GLARE
        }

        // Glare detected: both sides sent offers simultaneously
        return if (localPubKey > peerPubKey) {
            GlareResolution.LOCAL_WINS
        } else {
            entry.session.rollback {
                onAcceptRemote(entry)
            }
            GlareResolution.REMOTE_WINS_ROLLBACK
        }
    }

    // ---- Callee-to-callee mesh ----

    /**
     * Determines if the local peer should initiate the offer to [peerPubKey].
     * Per NIP-AC spec: "the peer with the lexicographically lower pubkey initiates."
     */
    fun shouldInitiateOffer(peerPubKey: HexKey): Boolean = localPubKey < peerPubKey

    // ---- Answer routing ----

    /**
     * Routes an incoming SDP answer to the correct session.
     * Returns the action taken.
     */
    fun routeAnswer(
        peerPubKey: HexKey,
        sdpAnswer: String,
    ): AnswerRouteAction {
        val entry = sessions[peerPubKey] ?: return AnswerRouteAction.NO_SESSION

        val signalingState = entry.session.getSignalingState()
        if (signalingState != SignalingState.HAVE_LOCAL_OFFER) {
            return AnswerRouteAction.IGNORED_WRONG_STATE
        }

        entry.session.setRemoteDescription(SdpType.ANSWER, sdpAnswer)
        flushPendingIceCandidates(peerPubKey)
        return AnswerRouteAction.APPLIED
    }

    // ---- Cleanup ----

    fun disposeAll() {
        for (entry in sessions.values) {
            entry.session.dispose()
        }
        sessions.clear()
        globalPendingIce.clear()
    }
}

enum class IceRouteAction {
    ADDED_DIRECTLY,
    BUFFERED_PER_SESSION,
    BUFFERED_GLOBALLY,
}

enum class GlareResolution {
    NO_SESSION,
    NO_GLARE,
    LOCAL_WINS,
    REMOTE_WINS_ROLLBACK,
}

enum class AnswerRouteAction {
    APPLIED,
    NO_SESSION,
    IGNORED_WRONG_STATE,
}
