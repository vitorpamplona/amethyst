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
package com.vitorpamplona.amethyst.service.call

import android.content.Context
import android.content.Intent
import com.vitorpamplona.amethyst.commons.call.CallManager
import com.vitorpamplona.amethyst.commons.call.CallState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.WebRtcCallFactory
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription
import java.util.UUID

class CallController(
    private val context: Context,
    private val callManager: CallManager,
    private val scope: CoroutineScope,
    private val publishWrap: suspend (GiftWrapEvent) -> Unit,
    private val signerProvider: suspend () -> com.vitorpamplona.quartz.nip01Core.signers.NostrSigner,
) {
    private var webRtcSession: WebRtcCallSession? = null
    private val callFactory = WebRtcCallFactory()
    private var currentCallId: String? = null
    private var currentPeerPubKey: HexKey? = null

    init {
        scope.launch {
            callManager.state.collect { state ->
                if (state is CallState.Ended && webRtcSession != null) {
                    cleanup()
                }
            }
        }
    }

    fun initiateCall(
        peerPubKey: HexKey,
        callType: CallType,
    ) {
        val callId = UUID.randomUUID().toString()
        currentCallId = callId
        currentPeerPubKey = peerPubKey

        createWebRtcSession()
        webRtcSession?.addAudioTrack()
        if (callType == CallType.VIDEO) {
            webRtcSession?.addVideoTrack()
        }

        webRtcSession?.createOffer { sdp ->
            scope.launch {
                callManager.initiateCall(peerPubKey, callType, callId, sdp.description)
            }
        }
    }

    fun acceptIncomingCall(sdpOffer: String) {
        val state = callManager.state.value
        if (state !is CallState.IncomingCall) return

        currentCallId = state.callId
        currentPeerPubKey = state.callerPubKey

        createWebRtcSession()
        webRtcSession?.addAudioTrack()
        if (state.callType == CallType.VIDEO) {
            webRtcSession?.addVideoTrack()
        }

        webRtcSession?.setRemoteDescription(
            SessionDescription(SessionDescription.Type.OFFER, sdpOffer),
        )

        webRtcSession?.createAnswer { sdp ->
            scope.launch {
                callManager.acceptCall(sdp.description)
            }
        }
    }

    fun onCallAnswerReceived(sdpAnswer: String) {
        webRtcSession?.setRemoteDescription(
            SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer),
        )
    }

    fun onIceCandidateReceived(event: CallIceCandidateEvent) {
        val json = event.candidateJson()
        try {
            val candidate = parseIceCandidate(json)
            webRtcSession?.addIceCandidate(candidate)
        } catch (_: Exception) {
            // Ignore malformed ICE candidates
        }
    }

    fun hangup() {
        scope.launch { callManager.hangup() }
        cleanup()
    }

    fun cleanup() {
        stopForegroundService()
        webRtcSession?.dispose()
        webRtcSession = null
        currentCallId = null
        currentPeerPubKey = null
    }

    private fun createWebRtcSession() {
        val iceServers = IceServerConfig.buildIceServers()

        webRtcSession =
            WebRtcCallSession(
                context = context,
                iceServers = iceServers,
                onIceCandidate = { candidate -> onLocalIceCandidate(candidate) },
                onPeerConnected = {
                    callManager.onPeerConnected()
                    startForegroundService()
                },
                onRemoteStream = { _: MediaStream -> },
                onDisconnected = {
                    scope.launch { callManager.hangup() }
                    cleanup()
                },
            )
        webRtcSession?.initialize()
        webRtcSession?.createPeerConnection()
    }

    private fun onLocalIceCandidate(candidate: IceCandidate) {
        val callId = currentCallId ?: return
        val peerPubKey = currentPeerPubKey ?: return
        val candidateJson = serializeIceCandidate(candidate)

        scope.launch {
            val signer = signerProvider()
            val result = callFactory.createIceCandidate(candidateJson, peerPubKey, callId, signer)
            publishWrap(result.wrap)
        }
    }

    private fun startForegroundService() {
        val intent =
            Intent(context, CallForegroundService::class.java).apply {
                action = CallForegroundService.ACTION_START
                putExtra(CallForegroundService.EXTRA_PEER_NAME, currentPeerPubKey ?: "")
            }
        context.startForegroundService(intent)
    }

    private fun stopForegroundService() {
        val intent =
            Intent(context, CallForegroundService::class.java).apply {
                action = CallForegroundService.ACTION_STOP
            }
        context.startService(intent)
    }

    companion object {
        fun serializeIceCandidate(candidate: IceCandidate): String = """{"candidate":"${candidate.sdp}","sdpMid":"${candidate.sdpMid}","sdpMLineIndex":${candidate.sdpMLineIndex}}"""

        fun parseIceCandidate(json: String): IceCandidate {
            val candidateRegex = """"candidate"\s*:\s*"([^"]*)"""".toRegex()
            val sdpMidRegex = """"sdpMid"\s*:\s*"([^"]*)"""".toRegex()
            val sdpMLineIndexRegex = """"sdpMLineIndex"\s*:\s*(\d+)""".toRegex()

            val sdp = candidateRegex.find(json)?.groupValues?.get(1) ?: ""
            val sdpMid = sdpMidRegex.find(json)?.groupValues?.get(1) ?: "0"
            val sdpMLineIndex =
                sdpMLineIndexRegex
                    .find(json)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull() ?: 0

            return IceCandidate(sdpMid, sdpMLineIndex, sdp)
        }
    }
}
