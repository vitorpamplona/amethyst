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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.WebRtcCallFactory
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallAnswerEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallHangupEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallOfferEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRejectEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRenegotiateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CallManager(
    private val signer: NostrSigner,
    private val scope: CoroutineScope,
    private val isFollowing: (HexKey) -> Boolean,
    private val publishEvent: (GiftWrapEvent) -> Unit,
) {
    private val factory = WebRtcCallFactory()

    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    val state: StateFlow<CallState> = _state.asStateFlow()

    var onAnswerReceived: ((CallAnswerEvent) -> Unit)? = null
    var onIceCandidateReceived: ((CallIceCandidateEvent) -> Unit)? = null
    var onRenegotiationOfferReceived: ((CallRenegotiateEvent) -> Unit)? = null

    private var timeoutJob: Job? = null
    private var resetJob: Job? = null
    private val processedEventIds = mutableSetOf<String>()

    companion object {
        const val CALL_TIMEOUT_MS = 60_000L // 60 seconds ringing timeout
        const val ENDED_DISPLAY_MS = 2_000L // show "call ended" briefly before resetting
        const val MAX_EVENT_AGE_SECONDS = 20L // discard signaling events older than this
    }

    private fun isEventTooOld(event: Event): Boolean = TimeUtils.now() - event.createdAt > MAX_EVENT_AGE_SECONDS

    // ---- P2P call initiation ----

    suspend fun initiateCall(
        calleePubKey: HexKey,
        callType: CallType,
        callId: String,
        sdpOffer: String,
    ) {
        val result = factory.createCallOffer(sdpOffer, calleePubKey, callId, callType, signer)
        _state.value = CallState.Offering(callId, setOf(calleePubKey), callType)
        publishEvent(result.wrap)
        startTimeout(callId)
    }

    // ---- Group call initiation ----

    /**
     * Initiates a group call.  A single [CallOfferEvent] is created with `p`
     * tags for every callee and then gift-wrapped individually to each one.
     */
    suspend fun initiateGroupCall(
        calleePubKeys: Set<HexKey>,
        callType: CallType,
        callId: String,
        sdpOffer: String,
    ) {
        val result = factory.createGroupCallOffer(sdpOffer, calleePubKeys, callId, callType, signer)
        _state.value = CallState.Offering(callId, calleePubKeys, callType)
        result.wraps.forEach { publishEvent(it) }
        startTimeout(callId)
    }

    // ---- Incoming call handling ----

    fun onIncomingCallEvent(event: CallOfferEvent) {
        val callerPubKey = event.pubKey
        val callId = event.callId() ?: return
        val callType = event.callType() ?: CallType.VOICE

        if (!isFollowing(callerPubKey)) return

        if (_state.value !is CallState.Idle) {
            // Already in a call — send a "busy" reject so the caller gets
            // immediate feedback instead of waiting for the 60s timeout.
            scope.launch {
                val result = factory.createReject(callerPubKey, callId, "busy", signer = signer)
                publishEvent(result.wrap)
            }
            return
        }

        val groupMembers = event.groupMembers()

        _state.value =
            CallState.IncomingCall(
                callId = callId,
                callerPubKey = callerPubKey,
                groupMembers = groupMembers,
                callType = callType,
                sdpOffer = event.sdpOffer(),
            )
        startTimeout(callId)
    }

    suspend fun acceptCall(sdpAnswer: String) {
        val current = _state.value
        if (current !is CallState.IncomingCall) return

        _state.value = CallState.Connecting(current.callId, current.peerPubKeys(), current.callType)
        cancelTimeout()

        if (current.groupMembers.size > 2) {
            // Group call: include all members in p-tags, sign once, wrap for each.
            // Include self so other devices get notified too.
            val allRecipients = current.groupMembers + signer.pubKey
            val result = factory.createGroupCallAnswer(sdpAnswer, allRecipients, current.callId, signer)
            result.wraps.forEach { publishEvent(it) }
        } else {
            val result = factory.createCallAnswer(sdpAnswer, current.callerPubKey, current.callId, signer)
            publishEvent(result.wrap)

            // Notify other devices of this user that the call was answered here.
            val selfNotify = factory.createCallAnswer(sdpAnswer, signer.pubKey, current.callId, signer)
            publishEvent(selfNotify.wrap)
        }
    }

    suspend fun rejectCall() {
        val current = _state.value
        if (current !is CallState.IncomingCall) return

        transitionToEnded(current.callId, current.peerPubKeys(), EndReason.REJECTED)

        if (current.groupMembers.size > 2) {
            // Group call: include all members in p-tags, sign once, wrap for each.
            // Include self so other devices get notified too.
            val allRecipients = current.groupMembers + signer.pubKey
            val result = factory.createGroupReject(allRecipients, current.callId, signer = signer)
            result.wraps.forEach { publishEvent(it) }
        } else {
            val result = factory.createReject(current.callerPubKey, current.callId, signer = signer)
            publishEvent(result.wrap)

            // Notify other devices of this user that the call was rejected here.
            val selfNotify = factory.createReject(signer.pubKey, current.callId, signer = signer)
            publishEvent(selfNotify.wrap)
        }
    }

    fun onCallAnswered(event: CallAnswerEvent) {
        val current = _state.value
        val callId = event.callId()
        val answeringPeer = event.pubKey

        when (current) {
            is CallState.Offering -> {
                if (callId != current.callId) return
                // First answer: start the call immediately. Remaining peers stay pending.
                val pending = current.peerPubKeys - answeringPeer
                _state.value =
                    CallState.Connecting(
                        current.callId,
                        setOf(answeringPeer),
                        current.callType,
                        pendingPeerPubKeys = pending,
                    )
                cancelTimeout()
                onAnswerReceived?.invoke(event)
            }

            is CallState.Connecting -> {
                // Another peer answered while we're still connecting with the first
                if (callId != current.callId) return
                if (answeringPeer in current.pendingPeerPubKeys) {
                    _state.value =
                        current.copy(
                            peerPubKeys = current.peerPubKeys + answeringPeer,
                            pendingPeerPubKeys = current.pendingPeerPubKeys - answeringPeer,
                        )
                }
                // TODO: establish additional WebRTC peer connection for this peer
            }

            is CallState.IncomingCall -> {
                // Another device of this user answered the call — stop ringing.
                if (callId != current.callId) return
                transitionToEnded(current.callId, current.peerPubKeys(), EndReason.ANSWERED_ELSEWHERE)
            }

            is CallState.Connected -> {
                if (callId != current.callId) return
                if (answeringPeer in current.pendingPeerPubKeys) {
                    // A pending peer just joined the group call
                    _state.value =
                        current.copy(
                            peerPubKeys = current.peerPubKeys + answeringPeer,
                            pendingPeerPubKeys = current.pendingPeerPubKeys - answeringPeer,
                        )
                    // TODO: establish additional WebRTC peer connection for this peer
                } else {
                    // Renegotiation answer (e.g., peer accepted our video upgrade offer)
                    onAnswerReceived?.invoke(event)
                }
            }

            else -> {
                return
            }
        }
    }

    fun onCallRejected(event: CallRejectEvent) {
        val current = _state.value
        val callId = event.callId()
        val rejectingPeer = event.pubKey

        when (current) {
            is CallState.Offering -> {
                if (callId != current.callId) return
                val remaining = current.peerPubKeys - rejectingPeer
                if (remaining.isEmpty()) {
                    transitionToEnded(current.callId, current.peerPubKeys, EndReason.PEER_REJECTED)
                } else {
                    _state.value = current.copy(peerPubKeys = remaining)
                }
            }

            is CallState.Connecting -> {
                // A pending peer rejected while we're already connecting with another
                if (callId != current.callId) return
                _state.value =
                    current.copy(pendingPeerPubKeys = current.pendingPeerPubKeys - rejectingPeer)
            }

            is CallState.Connected -> {
                // A pending peer rejected while we're already in the call
                if (callId != current.callId) return
                _state.value =
                    current.copy(pendingPeerPubKeys = current.pendingPeerPubKeys - rejectingPeer)
            }

            is CallState.IncomingCall -> {
                // Another device of this user rejected the call — stop ringing.
                if (callId != current.callId) return
                transitionToEnded(current.callId, current.peerPubKeys(), EndReason.REJECTED)
            }

            else -> {
                return
            }
        }
    }

    fun onIceCandidate(event: CallIceCandidateEvent) {
        onIceCandidateReceived?.invoke(event)
    }

    fun onRenegotiate(event: CallRenegotiateEvent) {
        val current = _state.value
        val callId = event.callId()
        val currentCallId =
            when (current) {
                is CallState.Connecting -> current.callId
                is CallState.Connected -> current.callId
                else -> return
            }
        if (callId != currentCallId) return
        onRenegotiationOfferReceived?.invoke(event)
    }

    suspend fun sendRenegotiation(sdpOffer: String) {
        val callId = currentCallId() ?: return
        val peerPubKeys = currentPeerPubKeys() ?: return

        if (peerPubKeys.size > 1) {
            val result = factory.createGroupRenegotiate(sdpOffer, peerPubKeys, callId, signer)
            result.wraps.forEach { publishEvent(it) }
        } else {
            val result = factory.createRenegotiate(sdpOffer, peerPubKeys.first(), callId, signer)
            publishEvent(result.wrap)
        }
    }

    suspend fun sendRenegotiationAnswer(sdpAnswer: String) {
        val callId = currentCallId() ?: return
        val peerPubKeys = currentPeerPubKeys() ?: return

        if (peerPubKeys.size > 1) {
            val result = factory.createGroupCallAnswer(sdpAnswer, peerPubKeys, callId, signer)
            result.wraps.forEach { publishEvent(it) }
        } else {
            val result = factory.createCallAnswer(sdpAnswer, peerPubKeys.first(), callId, signer)
            publishEvent(result.wrap)
        }
    }

    fun onPeerConnected() {
        val current = _state.value
        if (current !is CallState.Connecting) return

        _state.value =
            CallState.Connected(
                callId = current.callId,
                peerPubKeys = current.peerPubKeys,
                callType = current.callType,
                startedAtEpoch = TimeUtils.now(),
                pendingPeerPubKeys = current.pendingPeerPubKeys,
            )
    }

    /** Invites a new peer into the current call by sending them an offer. */
    suspend fun invitePeer(
        peerPubKey: HexKey,
        sdpOffer: String,
    ) {
        val current = _state.value
        val callId: String
        val callType: CallType
        when (current) {
            is CallState.Connecting -> {
                callId = current.callId
                callType = current.callType
                _state.value = current.copy(pendingPeerPubKeys = current.pendingPeerPubKeys + peerPubKey)
            }

            is CallState.Connected -> {
                callId = current.callId
                callType = current.callType
                _state.value = current.copy(pendingPeerPubKeys = current.pendingPeerPubKeys + peerPubKey)
            }

            else -> {
                return
            }
        }

        val result = factory.createCallOffer(sdpOffer, peerPubKey, callId, callType, signer)
        publishEvent(result.wrap)
    }

    suspend fun hangup() {
        val peerPubKeys: Set<HexKey>
        val callId: String
        when (val current = _state.value) {
            is CallState.Offering -> {
                peerPubKeys = current.peerPubKeys
                callId = current.callId
            }

            is CallState.Connecting -> {
                peerPubKeys = current.peerPubKeys + current.pendingPeerPubKeys
                callId = current.callId
            }

            is CallState.Connected -> {
                peerPubKeys = current.allPeerPubKeys
                callId = current.callId
            }

            else -> {
                return
            }
        }

        if (peerPubKeys.size == 1) {
            val result = factory.createHangup(peerPubKeys.first(), callId, signer = signer)
            publishEvent(result.wrap)
        } else {
            val result = factory.createGroupHangup(peerPubKeys, callId, signer = signer)
            result.wraps.forEach { publishEvent(it) }
        }
        transitionToEnded(callId, peerPubKeys, EndReason.HANGUP)
    }

    fun onPeerHangup(event: CallHangupEvent) {
        val current = _state.value
        val callId = event.callId() ?: return
        val leavingPeer = event.pubKey

        when (current) {
            is CallState.Connected -> {
                if (callId != current.callId) return
                val connectedRemaining = current.peerPubKeys - leavingPeer
                val pendingRemaining = current.pendingPeerPubKeys - leavingPeer
                if (connectedRemaining.isEmpty() && pendingRemaining.isEmpty()) {
                    transitionToEnded(callId, current.allPeerPubKeys, EndReason.PEER_HANGUP)
                } else {
                    _state.value =
                        current.copy(
                            peerPubKeys = connectedRemaining,
                            pendingPeerPubKeys = pendingRemaining,
                        )
                }
            }

            is CallState.Connecting -> {
                if (callId != current.callId) return
                val connectedRemaining = current.peerPubKeys - leavingPeer
                val pendingRemaining = current.pendingPeerPubKeys - leavingPeer
                if (connectedRemaining.isEmpty() && pendingRemaining.isEmpty()) {
                    transitionToEnded(callId, current.peerPubKeys + current.pendingPeerPubKeys, EndReason.PEER_HANGUP)
                } else {
                    _state.value =
                        current.copy(
                            peerPubKeys = connectedRemaining,
                            pendingPeerPubKeys = pendingRemaining,
                        )
                }
            }

            is CallState.Offering -> {
                if (callId != current.callId) return
                val remaining = current.peerPubKeys - leavingPeer
                if (remaining.isEmpty()) {
                    transitionToEnded(callId, current.peerPubKeys, EndReason.PEER_HANGUP)
                } else {
                    _state.value = current.copy(peerPubKeys = remaining)
                }
            }

            is CallState.IncomingCall -> {
                if (callId != current.callId) return
                if (leavingPeer == current.callerPubKey) {
                    transitionToEnded(callId, current.groupMembers, EndReason.PEER_HANGUP)
                } else {
                    val remaining = current.groupMembers - leavingPeer
                    if (remaining.size <= 1) {
                        transitionToEnded(callId, current.groupMembers, EndReason.PEER_HANGUP)
                    } else {
                        _state.value = current.copy(groupMembers = remaining)
                    }
                }
            }

            else -> {
                return
            }
        }
    }

    fun onSignalingEvent(event: Event) {
        if (isEventTooOld(event)) {
            Log.d("CallManager") { "Discarding old event kind=${event.kind} age=${TimeUtils.now() - event.createdAt}s" }
            return
        }
        if (!processedEventIds.add(event.id)) return

        Log.d("CallManager") { "Processing signaling event kind=${event.kind} id=${event.id.take(8)} state=${_state.value::class.simpleName}" }

        when (event) {
            is CallOfferEvent -> onIncomingCallEvent(event)
            is CallAnswerEvent -> onCallAnswered(event)
            is CallRejectEvent -> onCallRejected(event)
            is CallHangupEvent -> onPeerHangup(event)
            is CallIceCandidateEvent -> onIceCandidate(event)
            is CallRenegotiateEvent -> onRenegotiate(event)
        }
    }

    fun currentCallId(): String? =
        when (val s = _state.value) {
            is CallState.Offering -> s.callId
            is CallState.IncomingCall -> s.callId
            is CallState.Connecting -> s.callId
            is CallState.Connected -> s.callId
            else -> null
        }

    /** Returns the first peer pubkey (for P2P calls) or null. */
    fun currentPeerPubKey(): HexKey? = currentPeerPubKeys()?.firstOrNull()

    /** Returns all peer pubkeys for the current call (connected + pending). */
    fun currentPeerPubKeys(): Set<HexKey>? =
        when (val s = _state.value) {
            is CallState.Offering -> s.peerPubKeys
            is CallState.IncomingCall -> s.peerPubKeys()
            is CallState.Connecting -> s.peerPubKeys + s.pendingPeerPubKeys
            is CallState.Connected -> s.allPeerPubKeys
            else -> null
        }

    /** True when the current call has more than one peer. */
    fun isGroupCall(): Boolean = (currentPeerPubKeys()?.size ?: 0) > 1

    fun reset() {
        _state.value = CallState.Idle
        cancelTimeout()
        resetJob?.cancel()
        resetJob = null
        processedEventIds.clear()
    }

    private fun transitionToEnded(
        callId: String,
        peerPubKeys: Set<HexKey>,
        reason: EndReason,
    ) {
        _state.value = CallState.Ended(callId, peerPubKeys, reason)
        cancelTimeout()
        resetJob?.cancel()
        resetJob =
            scope.launch {
                delay(ENDED_DISPLAY_MS)
                if (_state.value is CallState.Ended) {
                    _state.value = CallState.Idle
                }
            }
    }

    private fun startTimeout(callId: String) {
        cancelTimeout()
        timeoutJob =
            scope.launch {
                delay(CALL_TIMEOUT_MS)
                val current = _state.value
                val currentCallId =
                    when (current) {
                        is CallState.Offering -> current.callId
                        is CallState.IncomingCall -> current.callId
                        else -> null
                    }
                if (currentCallId == callId) {
                    val peerPubKeys =
                        when (current) {
                            is CallState.Offering -> current.peerPubKeys
                            is CallState.IncomingCall -> current.peerPubKeys()
                            else -> return@launch
                        }
                    transitionToEnded(callId, peerPubKeys, EndReason.TIMEOUT)
                }
            }
    }

    private fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }
}

/**
 * Convenience extension: the peers in an incoming call are all group members
 * except the local signer (i.e. ourselves) – but since we don't store the
 * local pubkey here we return all members except the caller's own pubkey
 * is already the callerPubKey field.  In practice the set of "peer" keys
 * the UI should track is groupMembers minus self, which the controller
 * resolves.  Here we simply exclude the caller from the recipients set
 * and add back the caller, resulting in the full group minus self.
 */
private fun CallState.IncomingCall.peerPubKeys(): Set<HexKey> = groupMembers
