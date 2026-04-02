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
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
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

    private var timeoutJob: Job? = null
    private var resetJob: Job? = null

    companion object {
        const val CALL_TIMEOUT_MS = 60_000L // 60 seconds ringing timeout
        const val ENDED_DISPLAY_MS = 2_000L // show "call ended" briefly before resetting
        const val MAX_EVENT_AGE_SECONDS = 30L // discard signaling events older than this
    }

    private fun isEventTooOld(event: Event): Boolean = TimeUtils.now() - event.createdAt > MAX_EVENT_AGE_SECONDS

    suspend fun initiateCall(
        calleePubKey: HexKey,
        callType: CallType,
        callId: String,
        sdpOffer: String,
    ) {
        val result = factory.createCallOffer(sdpOffer, calleePubKey, callId, callType, signer)
        _state.value = CallState.Offering(callId, calleePubKey, callType)
        publishEvent(result.wrap)
        startTimeout(callId)
    }

    fun onIncomingCallEvent(event: CallOfferEvent) {
        val callerPubKey = event.pubKey
        val callId = event.callId() ?: return
        val callType = event.callType() ?: CallType.VOICE

        if (!isFollowing(callerPubKey)) return

        if (_state.value !is CallState.Idle) return

        _state.value =
            CallState.IncomingCall(
                callId = callId,
                callerPubKey = callerPubKey,
                callType = callType,
                sdpOffer = event.sdpOffer(),
            )
        startTimeout(callId)
    }

    suspend fun acceptCall(sdpAnswer: String) {
        val current = _state.value
        if (current !is CallState.IncomingCall) return

        val result = factory.createCallAnswer(sdpAnswer, current.callerPubKey, current.callId, signer)
        _state.value = CallState.Connecting(current.callId, current.callerPubKey, current.callType)
        cancelTimeout()
        publishEvent(result.wrap)
    }

    suspend fun rejectCall() {
        val current = _state.value
        if (current !is CallState.IncomingCall) return

        val result = factory.createReject(current.callerPubKey, current.callId, signer = signer)
        transitionToEnded(current.callId, current.callerPubKey, EndReason.REJECTED)
        publishEvent(result.wrap)
    }

    fun onCallAnswered(event: CallAnswerEvent) {
        val current = _state.value
        if (current !is CallState.Offering) return
        if (event.callId() != current.callId) return

        _state.value = CallState.Connecting(current.callId, current.peerPubKey, current.callType)
        cancelTimeout()
        onAnswerReceived?.invoke(event)
    }

    fun onCallRejected(event: CallRejectEvent) {
        val current = _state.value
        if (current !is CallState.Offering) return
        if (event.callId() != current.callId) return

        transitionToEnded(current.callId, current.peerPubKey, EndReason.PEER_REJECTED)
    }

    fun onIceCandidate(event: CallIceCandidateEvent) {
        onIceCandidateReceived?.invoke(event)
    }

    fun onPeerConnected() {
        val current = _state.value
        if (current !is CallState.Connecting) return

        _state.value =
            CallState.Connected(
                callId = current.callId,
                peerPubKey = current.peerPubKey,
                callType = current.callType,
                startedAtEpoch = TimeUtils.now(),
            )
    }

    suspend fun hangup() {
        val peerPubKey: HexKey
        val callId: String
        when (val current = _state.value) {
            is CallState.Offering -> {
                peerPubKey = current.peerPubKey
                callId = current.callId
            }

            is CallState.Connecting -> {
                peerPubKey = current.peerPubKey
                callId = current.callId
            }

            is CallState.Connected -> {
                peerPubKey = current.peerPubKey
                callId = current.callId
            }

            else -> {
                return
            }
        }

        val result = factory.createHangup(peerPubKey, callId, signer = signer)
        transitionToEnded(callId, peerPubKey, EndReason.HANGUP)
        publishEvent(result.wrap)
    }

    fun onPeerHangup(event: CallHangupEvent) {
        val current = _state.value
        val callId = event.callId() ?: return
        val currentCallId =
            when (current) {
                is CallState.Offering -> current.callId
                is CallState.Connecting -> current.callId
                is CallState.Connected -> current.callId
                is CallState.IncomingCall -> current.callId
                else -> return
            }
        if (callId != currentCallId) return

        val peerPubKey = event.pubKey
        transitionToEnded(callId, peerPubKey, EndReason.PEER_HANGUP)
    }

    fun onSignalingEvent(event: Event) {
        if (isEventTooOld(event)) return

        when (event) {
            is CallOfferEvent -> onIncomingCallEvent(event)
            is CallAnswerEvent -> onCallAnswered(event)
            is CallRejectEvent -> onCallRejected(event)
            is CallHangupEvent -> onPeerHangup(event)
            is CallIceCandidateEvent -> onIceCandidate(event)
        }
    }

    fun toggleAudioMute() {
        val current = _state.value
        if (current is CallState.Connected) {
            _state.value = current.copy(isAudioMuted = !current.isAudioMuted)
        }
    }

    fun toggleVideo() {
        val current = _state.value
        if (current is CallState.Connected) {
            _state.value = current.copy(isVideoEnabled = !current.isVideoEnabled)
        }
    }

    fun toggleSpeaker() {
        val current = _state.value
        if (current is CallState.Connected) {
            _state.value = current.copy(isSpeakerOn = !current.isSpeakerOn)
        }
    }

    fun reset() {
        _state.value = CallState.Idle
        cancelTimeout()
        resetJob?.cancel()
        resetJob = null
    }

    private fun transitionToEnded(
        callId: String,
        peerPubKey: HexKey,
        reason: EndReason,
    ) {
        _state.value = CallState.Ended(callId, peerPubKey, reason)
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
                    val peerPubKey =
                        when (current) {
                            is CallState.Offering -> current.peerPubKey
                            is CallState.IncomingCall -> current.callerPubKey
                            else -> return@launch
                        }
                    transitionToEnded(callId, peerPubKey, EndReason.TIMEOUT)
                }
            }
    }

    private fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }
}
