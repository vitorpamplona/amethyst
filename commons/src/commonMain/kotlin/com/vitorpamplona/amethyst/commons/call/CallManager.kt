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

    /** Called for every answer that should be applied to a PeerConnection (includes peer pubkey). */
    var onAnswerReceived: ((CallAnswerEvent) -> Unit)? = null
    var onIceCandidateReceived: ((CallIceCandidateEvent) -> Unit)? = null
    var onRenegotiationOfferReceived: ((CallRenegotiateEvent) -> Unit)? = null

    /** Called when a new peer joins the group call (callee-to-callee mesh setup). */
    var onNewPeerInGroupCall: ((peerPubKey: HexKey) -> Unit)? = null

    /** Called when a mid-call offer is received from another callee in a group call. */
    var onMidCallOfferReceived: ((peerPubKey: HexKey, sdpOffer: String) -> Unit)? = null

    /** Called when a peer leaves the call (hangup) but the call continues with remaining peers. */
    var onPeerLeft: ((peerPubKey: HexKey) -> Unit)? = null

    private var timeoutJob: Job? = null
    private var resetJob: Job? = null
    private val processedEventIds = mutableSetOf<String>()

    /** Peers whose answers we saw while still ringing (IncomingCall).
     *  After we accept, we trigger callee-to-callee mesh setup with them. */
    private val discoveredCalleePeers = mutableSetOf<HexKey>()

    companion object {
        const val CALL_TIMEOUT_MS = 60_000L // 60 seconds ringing timeout
        const val ENDED_DISPLAY_MS = 2_000L // show "call ended" briefly before resetting
        const val MAX_EVENT_AGE_SECONDS = 20L // discard signaling events older than this
    }

    private fun isEventTooOld(event: Event): Boolean = TimeUtils.now() - event.createdAt > MAX_EVENT_AGE_SECONDS

    // ---- Call initiation (state + publish) ----

    /**
     * Sets state to Offering. Called by CallController before creating
     * per-peer offers in group calls.
     */
    fun beginOffering(
        callId: String,
        calleePubKeys: Set<HexKey>,
        callType: CallType,
    ) {
        _state.value = CallState.Offering(callId, calleePubKeys, callType)
        startTimeout(callId)
    }

    /**
     * Publishes a per-peer call offer (group context: p-tags for all members).
     * Called once per callee so each gets their own SDP.
     */
    suspend fun publishOfferToPeer(
        calleePubKey: HexKey,
        allCalleePubKeys: Set<HexKey>,
        callType: CallType,
        callId: String,
        sdpOffer: String,
    ) {
        Log.d("CallManager") { "publishOfferToPeer: to=${calleePubKey.take(8)}, sdpLength=${sdpOffer.length}" }
        val result = factory.createCallOffer(sdpOffer, calleePubKey, allCalleePubKeys, callId, callType, signer)
        publishEvent(result.wrap)
    }

    /**
     * Publishes a callee-to-callee offer within an existing call.
     * Uses the current call context for call-id, call-type, and group members.
     */
    suspend fun publishOfferToPeer(
        peerPubKey: HexKey,
        sdpOffer: String,
    ) {
        val callId = currentCallId() ?: return
        val callType = currentCallType() ?: return
        val allMembers = (currentPeerPubKeys() ?: emptySet()) + signer.pubKey
        Log.d("CallManager") { "publishOfferToPeer (mid-call): to=${peerPubKey.take(8)}, sdpLength=${sdpOffer.length}" }
        val result = factory.createCallOffer(sdpOffer, peerPubKey, allMembers, callId, callType, signer)
        publishEvent(result.wrap)
    }

    /**
     * Publishes a callee-to-callee answer within an existing call.
     */
    suspend fun publishAnswerToPeer(
        peerPubKey: HexKey,
        sdpAnswer: String,
    ) {
        val callId = currentCallId() ?: return
        val allMembers = (currentPeerPubKeys() ?: emptySet()) + signer.pubKey
        Log.d("CallManager") { "publishAnswerToPeer: to=${peerPubKey.take(8)}, sdpLength=${sdpAnswer.length}" }
        val result = factory.createCallAnswer(sdpAnswer, peerPubKey, allMembers, callId, signer)
        publishEvent(result.wrap)
    }

    // ---- P2P call initiation (convenience) ----

    suspend fun initiateCall(
        calleePubKey: HexKey,
        callType: CallType,
        callId: String,
        sdpOffer: String,
    ) {
        Log.d("CallManager") { "initiateCall: callId=$callId, callee=${calleePubKey.take(8)}, type=$callType, sdpLength=${sdpOffer.length}" }
        val result = factory.createCallOffer(sdpOffer, calleePubKey, callId, callType, signer)
        _state.value = CallState.Offering(callId, setOf(calleePubKey), callType)
        publishEvent(result.wrap)
        startTimeout(callId)
        Log.d("CallManager") { "initiateCall: offer published, timeout started" }
    }

    // ---- Incoming call handling ----

    fun onIncomingCallEvent(event: CallOfferEvent) {
        val callerPubKey = event.pubKey
        val callId = event.callId() ?: return
        val callType = event.callType() ?: CallType.VOICE

        Log.d("CallManager") { "onIncomingCallEvent: from=${callerPubKey.take(8)}, callId=$callId, type=$callType, sdpOfferLength=${event.sdpOffer().length}" }

        if (!isFollowing(callerPubKey)) {
            Log.d("CallManager") { "onIncomingCallEvent: caller not followed — ignoring" }
            return
        }

        val currentState = _state.value

        // Mid-call offer: same call-id, we're already in the call
        if (currentState is CallState.Connecting || currentState is CallState.Connected) {
            val currentCallId =
                when (currentState) {
                    is CallState.Connecting -> currentState.callId
                    is CallState.Connected -> currentState.callId
                }
            if (callId == currentCallId) {
                Log.d("CallManager") { "Mid-call offer from ${callerPubKey.take(8)} for current call — callee-to-callee" }
                onMidCallOfferReceived?.invoke(callerPubKey, event.sdpOffer())
                return
            }
        }

        if (currentState !is CallState.Idle) {
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
        if (current !is CallState.IncomingCall) {
            Log.d("CallManager") { "acceptCall: state is ${current::class.simpleName}, not IncomingCall — ignoring" }
            return
        }

        Log.d("CallManager") { "acceptCall: callId=${current.callId}, transitioning to Connecting, sdpAnswerLength=${sdpAnswer.length}" }
        _state.value = CallState.Connecting(current.callId, current.peerPubKeys() - signer.pubKey, current.callType)
        cancelTimeout()

        val allRecipients = current.groupMembers + signer.pubKey
        Log.d("CallManager") { "acceptCall: publishing answer to ${allRecipients.size} recipients" }
        val result = factory.createGroupCallAnswer(sdpAnswer, allRecipients, current.callId, signer)
        result.wraps.forEach { publishEvent(it) }
        Log.d("CallManager") { "acceptCall: answer published, now in Connecting state" }

        // Trigger callee-to-callee mesh connections with peers we discovered
        // while still ringing (their answers arrived before we accepted).
        val discovered = discoveredCalleePeers.toSet()
        discoveredCalleePeers.clear()
        if (discovered.isNotEmpty()) {
            Log.d("CallManager") { "acceptCall: triggering mesh setup with ${discovered.size} discovered peers: ${discovered.map { it.take(8) }}" }
            for (peer in discovered) {
                onNewPeerInGroupCall?.invoke(peer)
            }
        }
    }

    suspend fun rejectCall() {
        val current = _state.value
        if (current !is CallState.IncomingCall) return

        transitionToEnded(current.callId, current.peerPubKeys(), EndReason.REJECTED)

        val allRecipients = current.groupMembers + signer.pubKey
        val result = factory.createGroupReject(allRecipients, current.callId, signer = signer)
        result.wraps.forEach { publishEvent(it) }
    }

    fun onCallAnswered(event: CallAnswerEvent) {
        val current = _state.value
        val callId = event.callId()
        val answeringPeer = event.pubKey

        Log.d("CallManager") {
            "onCallAnswered: from=${answeringPeer.take(8)}, callId=$callId, " +
                "currentState=${current::class.simpleName}, sdpAnswerLength=${event.sdpAnswer().length}"
        }

        // Self-answer: only meaningful as "answered elsewhere" in IncomingCall.
        // In all other states it's our own echo from the relay — ignore it.
        if (answeringPeer == signer.pubKey) {
            if (current is CallState.IncomingCall && callId == current.callId) {
                Log.d("CallManager") { "onCallAnswered: self-answer detected in IncomingCall — answered elsewhere" }
                transitionToEnded(current.callId, current.peerPubKeys(), EndReason.ANSWERED_ELSEWHERE)
            } else {
                Log.d("CallManager") { "onCallAnswered: ignoring self-answer echo in ${current::class.simpleName}" }
            }
            return
        }

        when (current) {
            is CallState.Offering -> {
                if (callId != current.callId) {
                    Log.d("CallManager") { "onCallAnswered: callId mismatch (got=$callId, expected=${current.callId})" }
                    return
                }
                val pending = current.peerPubKeys - answeringPeer
                _state.value =
                    CallState.Connecting(
                        current.callId,
                        setOf(answeringPeer),
                        current.callType,
                        pendingPeerPubKeys = pending,
                    )
                cancelTimeout()
                Log.d("CallManager") { "onCallAnswered: Offering -> Connecting, forwarding answer to CallController" }
                onAnswerReceived?.invoke(event)
            }

            is CallState.Connecting -> {
                if (callId != current.callId) return
                if (answeringPeer in current.pendingPeerPubKeys) {
                    _state.value =
                        current.copy(
                            peerPubKeys = current.peerPubKeys + answeringPeer,
                            pendingPeerPubKeys = current.pendingPeerPubKeys - answeringPeer,
                        )
                }
                // Forward to CallController — it routes to the correct PeerSession
                // and internally triggers callee-to-callee mesh setup if needed.
                Log.d("CallManager") { "onCallAnswered: additional peer ${answeringPeer.take(8)} in Connecting, forwarding to CallController" }
                onAnswerReceived?.invoke(event)
            }

            is CallState.IncomingCall -> {
                if (callId != current.callId) return
                // Another group member answered — remember them for mesh setup
                // after we accept the call ourselves.
                Log.d("CallManager") { "onCallAnswered: peer ${answeringPeer.take(8)} answered while we're still ringing, storing for later mesh" }
                discoveredCalleePeers.add(answeringPeer)
            }

            is CallState.Connected -> {
                if (callId != current.callId) return
                if (answeringPeer in current.pendingPeerPubKeys) {
                    _state.value =
                        current.copy(
                            peerPubKeys = current.peerPubKeys + answeringPeer,
                            pendingPeerPubKeys = current.pendingPeerPubKeys - answeringPeer,
                        )
                }
                // Forward to CallController — it routes to the correct PeerSession
                // and internally triggers callee-to-callee mesh setup if needed.
                Log.d("CallManager") { "onCallAnswered: peer ${answeringPeer.take(8)} answer in Connected, forwarding to CallController" }
                onAnswerReceived?.invoke(event)
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
                if (callId != current.callId) return
                _state.value =
                    current.copy(pendingPeerPubKeys = current.pendingPeerPubKeys - rejectingPeer)
            }

            is CallState.Connected -> {
                if (callId != current.callId) return
                _state.value =
                    current.copy(pendingPeerPubKeys = current.pendingPeerPubKeys - rejectingPeer)
            }

            is CallState.IncomingCall -> {
                if (callId != current.callId) return
                if (rejectingPeer == signer.pubKey) {
                    transitionToEnded(current.callId, current.peerPubKeys(), EndReason.REJECTED)
                }
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

    suspend fun sendRenegotiation(
        sdpOffer: String,
        peerPubKey: HexKey,
    ) {
        val callId = currentCallId() ?: return
        val peerPubKeys = currentPeerPubKeys() ?: return
        val result = factory.createRenegotiate(sdpOffer, peerPubKey, peerPubKeys, callId, signer)
        publishEvent(result.wrap)
    }

    suspend fun sendRenegotiationAnswer(
        sdpAnswer: String,
        peerPubKey: HexKey,
    ) {
        val callId = currentCallId() ?: return
        val peerPubKeys = currentPeerPubKeys() ?: return
        val result = factory.createCallAnswer(sdpAnswer, peerPubKey, peerPubKeys, callId, signer)
        publishEvent(result.wrap)
    }

    fun onPeerConnected() {
        val current = _state.value
        if (current !is CallState.Connecting) {
            Log.d("CallManager") { "onPeerConnected: state is ${current::class.simpleName}, not Connecting — ignoring" }
            return
        }

        Log.d("CallManager") { "onPeerConnected: Connecting -> Connected! callId=${current.callId}" }
        _state.value =
            CallState.Connected(
                callId = current.callId,
                peerPubKeys = current.peerPubKeys,
                callType = current.callType,
                startedAtEpoch = TimeUtils.now(),
                pendingPeerPubKeys = current.pendingPeerPubKeys,
            )
    }

    suspend fun invitePeer(
        peerPubKey: HexKey,
        sdpOffer: String,
    ) {
        val current = _state.value
        val callId: String
        val callType: CallType
        val existingMembers: Set<HexKey>
        when (current) {
            is CallState.Connecting -> {
                callId = current.callId
                callType = current.callType
                existingMembers = current.peerPubKeys + current.pendingPeerPubKeys
                _state.value = current.copy(pendingPeerPubKeys = current.pendingPeerPubKeys + peerPubKey)
            }

            is CallState.Connected -> {
                callId = current.callId
                callType = current.callType
                existingMembers = current.allPeerPubKeys
                _state.value = current.copy(pendingPeerPubKeys = current.pendingPeerPubKeys + peerPubKey)
            }

            else -> {
                return
            }
        }

        val allMembers = existingMembers + peerPubKey + signer.pubKey
        val result = factory.createCallOffer(sdpOffer, peerPubKey, allMembers, callId, callType, signer)
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

        val result = factory.createGroupHangup(peerPubKeys, callId, signer = signer)
        result.wraps.forEach { publishEvent(it) }
        transitionToEnded(callId, peerPubKeys, EndReason.HANGUP)
    }

    fun onPeerHangup(event: CallHangupEvent) {
        val current = _state.value
        val callId = event.callId() ?: return
        val leavingPeer = event.pubKey

        Log.d("CallManager") { "onPeerHangup: from=${leavingPeer.take(8)}, callId=$callId, state=${current::class.simpleName}" }

        when (current) {
            is CallState.Connected -> {
                if (callId != current.callId) return
                val connectedRemaining = current.peerPubKeys - leavingPeer
                val pendingRemaining = current.pendingPeerPubKeys - leavingPeer
                if (connectedRemaining.isEmpty() && pendingRemaining.isEmpty()) {
                    Log.d("CallManager") { "onPeerHangup: last peer left, ending call" }
                    transitionToEnded(callId, current.allPeerPubKeys, EndReason.PEER_HANGUP)
                } else {
                    Log.d("CallManager") { "onPeerHangup: ${leavingPeer.take(8)} left, remaining=${connectedRemaining.map { it.take(8) }}, pending=${pendingRemaining.map { it.take(8) }}" }
                    _state.value =
                        current.copy(
                            peerPubKeys = connectedRemaining,
                            pendingPeerPubKeys = pendingRemaining,
                        )
                    onPeerLeft?.invoke(leavingPeer)
                }
            }

            is CallState.Connecting -> {
                if (callId != current.callId) return
                val connectedRemaining = current.peerPubKeys - leavingPeer
                val pendingRemaining = current.pendingPeerPubKeys - leavingPeer
                if (connectedRemaining.isEmpty() && pendingRemaining.isEmpty()) {
                    Log.d("CallManager") { "onPeerHangup: last peer left during connecting, ending call" }
                    transitionToEnded(callId, current.peerPubKeys + current.pendingPeerPubKeys, EndReason.PEER_HANGUP)
                } else {
                    Log.d("CallManager") { "onPeerHangup: ${leavingPeer.take(8)} left during connecting, remaining=${connectedRemaining.map { it.take(8) }}" }
                    _state.value =
                        current.copy(
                            peerPubKeys = connectedRemaining,
                            pendingPeerPubKeys = pendingRemaining,
                        )
                    onPeerLeft?.invoke(leavingPeer)
                }
            }

            is CallState.Offering -> {
                if (callId != current.callId) return
                val remaining = current.peerPubKeys - leavingPeer
                if (remaining.isEmpty()) {
                    transitionToEnded(callId, current.peerPubKeys, EndReason.PEER_HANGUP)
                } else {
                    _state.value = current.copy(peerPubKeys = remaining)
                    onPeerLeft?.invoke(leavingPeer)
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

        // Filter out our own ICE candidates and hangups echoed back from relays.
        // These are never useful: ICE candidates are for the remote peer, and
        // hangups are already handled locally by hangup() → transitionToEnded.
        // Self-answers and self-rejects are NOT filtered here because they serve
        // as "answered/rejected elsewhere" signals when in IncomingCall state.
        if (event.pubKey == signer.pubKey && (event is CallIceCandidateEvent || event is CallHangupEvent)) {
            Log.d("CallManager") { "Ignoring self-event kind=${event.kind} id=${event.id.take(8)}" }
            return
        }

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

    fun currentPeerPubKey(): HexKey? = currentPeerPubKeys()?.firstOrNull()

    fun currentPeerPubKeys(): Set<HexKey>? =
        when (val s = _state.value) {
            is CallState.Offering -> s.peerPubKeys
            is CallState.IncomingCall -> s.peerPubKeys()
            is CallState.Connecting -> s.peerPubKeys + s.pendingPeerPubKeys
            is CallState.Connected -> s.allPeerPubKeys
            else -> null
        }

    fun currentCallType(): CallType? =
        when (val s = _state.value) {
            is CallState.Offering -> s.callType
            is CallState.IncomingCall -> s.callType
            is CallState.Connecting -> s.callType
            is CallState.Connected -> s.callType
            else -> null
        }

    fun isGroupCall(): Boolean = (currentPeerPubKeys()?.size ?: 0) > 1

    fun reset() {
        _state.value = CallState.Idle
        cancelTimeout()
        resetJob?.cancel()
        resetJob = null
        processedEventIds.clear()
        discoveredCalleePeers.clear()
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
                    processedEventIds.clear()
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

private fun CallState.IncomingCall.peerPubKeys(): Set<HexKey> = groupMembers
