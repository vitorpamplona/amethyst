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
import android.media.AudioManager
import com.vitorpamplona.amethyst.commons.call.CallManager
import com.vitorpamplona.amethyst.commons.call.CallState
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.WebRtcCallFactory
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

private const val TAG = "CallController"

class CallController(
    private val context: Context,
    val callManager: CallManager,
    private val scope: CoroutineScope,
    private val publishWrap: suspend (GiftWrapEvent) -> Unit,
    private val signerProvider: suspend () -> com.vitorpamplona.quartz.nip01Core.signers.NostrSigner,
) {
    private var webRtcSession: WebRtcCallSession? = null
    private val callFactory = WebRtcCallFactory()
    private var remoteDescriptionSet = false
    private val pendingIceCandidates = CopyOnWriteArrayList<IceCandidate>()
    val audioManager = CallAudioManager(context)

    // Video tracks exposed to UI
    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()
    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    // Error state exposed to UI
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Audio/video toggle state (UI concerns, not domain state)
    private val _isAudioMuted = MutableStateFlow(false)
    val isAudioMuted: StateFlow<Boolean> = _isAudioMuted.asStateFlow()
    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()
    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    init {
        scope.launch {
            callManager.state.collect { state ->
                when (state) {
                    is CallState.IncomingCall -> {
                        audioManager.startRinging()
                    }

                    is CallState.Offering -> {
                        audioManager.startRingbackTone()
                    }

                    is CallState.Connecting -> {
                        audioManager.stopRinging()
                        audioManager.stopRingbackTone()
                        audioManager.switchToCallAudioMode()
                        audioManager.acquireProximityWakeLock()
                    }

                    is CallState.Connected -> {
                        audioManager.acquireProximityWakeLock()
                    }

                    is CallState.Ended -> {
                        cleanup()
                    }

                    else -> {}
                }
            }
        }
    }

    fun initiateCall(
        peerPubKey: String,
        callType: CallType,
    ) {
        val callId = UUID.randomUUID().toString()
        _errorMessage.value = null
        remoteDescriptionSet = false
        pendingIceCandidates.clear()

        try {
            createWebRtcSession()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create WebRTC session", e)
            _errorMessage.value = "Failed to start call: ${e.message}"
            return
        }

        val session =
            webRtcSession ?: run {
                _errorMessage.value = "Failed to create WebRTC session"
                return
            }

        session.addAudioTrack()
        if (callType == CallType.VIDEO) {
            session.addVideoTrack()
            _localVideoTrack.value = session.getLocalVideoTrack()
        }

        session.createOffer { sdp ->
            scope.launch {
                callManager.initiateCall(peerPubKey, callType, callId, sdp.description)
            }
        }
    }

    fun acceptIncomingCall(sdpOffer: String) {
        val state = callManager.state.value
        if (state !is CallState.IncomingCall) return

        _errorMessage.value = null
        remoteDescriptionSet = false
        pendingIceCandidates.clear()

        try {
            createWebRtcSession()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create WebRTC session", e)
            _errorMessage.value = "Failed to accept call: ${e.message}"
            return
        }

        val session =
            webRtcSession ?: run {
                _errorMessage.value = "Failed to create WebRTC session"
                return
            }

        session.addAudioTrack()
        if (state.callType == CallType.VIDEO) {
            session.addVideoTrack()
            _localVideoTrack.value = session.getLocalVideoTrack()
        }

        session.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, sdpOffer))
        flushPendingIceCandidates()

        session.createAnswer { sdp ->
            scope.launch {
                callManager.acceptCall(sdp.description)
            }
        }
    }

    fun onCallAnswerReceived(sdpAnswer: String) {
        Log.d(TAG) { "Answer received, SDP length=${sdpAnswer.length}, session=${webRtcSession != null}" }
        webRtcSession?.setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer))
        flushPendingIceCandidates()
    }

    fun onIceCandidateReceived(event: CallIceCandidateEvent) {
        try {
            val candidate = IceCandidate(event.sdpMid(), event.sdpMLineIndex(), event.candidateSdp())
            if (webRtcSession != null && remoteDescriptionSet) {
                Log.d(TAG) { "Adding ICE candidate directly: ${candidate.sdp.take(50)}" }
                webRtcSession?.addIceCandidate(candidate)
            } else {
                Log.d(TAG) { "Buffering ICE candidate (session=${webRtcSession != null}, remoteSet=$remoteDescriptionSet)" }
                pendingIceCandidates.add(candidate)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ICE candidate", e)
        }
    }

    private fun flushPendingIceCandidates() {
        remoteDescriptionSet = true
        val session = webRtcSession ?: return
        val candidates = pendingIceCandidates.toList()
        Log.d(TAG) { "Flushing ${candidates.size} buffered ICE candidates" }
        pendingIceCandidates.clear()
        candidates.forEach { session.addIceCandidate(it) }
    }

    // UI toggle controls
    fun toggleAudioMute() {
        val muted = !_isAudioMuted.value
        _isAudioMuted.value = muted
        webRtcSession?.setAudioEnabled(!muted)
    }

    fun toggleVideo() {
        val enabled = !_isVideoEnabled.value
        _isVideoEnabled.value = enabled
        webRtcSession?.setVideoEnabled(enabled)
    }

    fun toggleSpeaker() {
        val on = !_isSpeakerOn.value
        _isSpeakerOn.value = on
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.isSpeakerphoneOn = on
    }

    fun getEglBase() = webRtcSession?.eglBase

    fun hangup() {
        scope.launch { callManager.hangup() }
        cleanup()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun cleanup() {
        audioManager.release()
        stopForegroundService()
        NotificationUtils.cancelCallNotification(context)
        _remoteVideoTrack.value = null
        _localVideoTrack.value = null
        _isAudioMuted.value = false
        _isVideoEnabled.value = true
        _isSpeakerOn.value = false
        webRtcSession?.dispose()
        webRtcSession = null
        remoteDescriptionSet = false
        pendingIceCandidates.clear()
    }

    private fun createWebRtcSession() {
        webRtcSession =
            WebRtcCallSession(
                context = context,
                iceServers = IceServerConfig.buildIceServers(),
                onIceCandidate = { candidate -> onLocalIceCandidate(candidate) },
                onPeerConnected = {
                    callManager.onPeerConnected()
                    startForegroundService()
                },
                onRemoteStream = { stream: MediaStream ->
                    stream.videoTracks?.firstOrNull()?.let { _remoteVideoTrack.value = it }
                },
                onDisconnected = { scope.launch { callManager.hangup() } },
                onError = { error -> _errorMessage.value = error },
            )
        webRtcSession?.initialize()
        webRtcSession?.createPeerConnection()
    }

    private fun onLocalIceCandidate(candidate: IceCandidate) {
        Log.d(TAG) { "Local ICE candidate: ${candidate.sdp.take(50)}" }
        val callId = callManager.currentCallId() ?: return
        val peerPubKey = callManager.currentPeerPubKey() ?: return
        val candidateJson = CallIceCandidateEvent.serializeCandidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)

        scope.launch {
            val signer = signerProvider()
            val result = callFactory.createIceCandidate(candidateJson, peerPubKey, callId, signer)
            publishWrap(result.wrap)
        }
    }

    private fun startForegroundService() {
        try {
            val peerName = callManager.currentPeerPubKey() ?: ""
            val intent =
                Intent(context, CallForegroundService::class.java).apply {
                    action = CallForegroundService.ACTION_START
                    putExtra(CallForegroundService.EXTRA_PEER_NAME, peerName)
                }
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
    }

    private fun stopForegroundService() {
        try {
            val intent =
                Intent(context, CallForegroundService::class.java).apply {
                    action = CallForegroundService.ACTION_STOP
                }
            context.startService(intent)
        } catch (_: Exception) {
        }
    }
}
