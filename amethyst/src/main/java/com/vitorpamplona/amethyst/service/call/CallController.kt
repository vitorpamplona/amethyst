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
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import com.vitorpamplona.amethyst.commons.call.CallManager
import com.vitorpamplona.amethyst.commons.call.CallState
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.WebRtcCallFactory
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRenegotiateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

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
    private val remoteDescriptionSet =
        java.util.concurrent.atomic
            .AtomicBoolean(false)
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

    // Remote video frame monitoring — detects when peer stops sending video
    private val _isRemoteVideoActive = MutableStateFlow(false)
    val isRemoteVideoActive: StateFlow<Boolean> = _isRemoteVideoActive.asStateFlow()
    private val _remoteVideoAspectRatio = MutableStateFlow<Float?>(null)
    val remoteVideoAspectRatio: StateFlow<Float?> = _remoteVideoAspectRatio.asStateFlow()
    private val lastRemoteFrameTimeMs = AtomicLong(0L)
    private var remoteVideoMonitorJob: kotlinx.coroutines.Job? = null
    private val remoteFrameSink =
        VideoSink { frame: VideoFrame ->
            lastRemoteFrameTimeMs.set(System.currentTimeMillis())
            val w = frame.rotatedWidth
            val h = frame.rotatedHeight
            if (w > 0 && h > 0) {
                _remoteVideoAspectRatio.value = w.toFloat() / h.toFloat()
            }
        }

    // Audio/video toggle state (UI concerns, not domain state)
    private val _isAudioMuted = MutableStateFlow(false)
    val isAudioMuted: StateFlow<Boolean> = _isAudioMuted.asStateFlow()
    private val _isVideoEnabled = MutableStateFlow(false)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()
    val audioRoute: StateFlow<AudioRoute> = audioManager.audioRoute
    val isBluetoothAvailable: StateFlow<Boolean> = audioManager.isBluetoothAvailable

    // Tracks whether video is paused because the phone is near the ear
    private var videoPausedByProximity = false

    init {
        callManager.onRenegotiationOfferReceived = { event -> onRenegotiationOfferReceived(event) }

        scope.launch {
            callManager.state.collect { state ->
                when (state) {
                    is CallState.IncomingCall -> {
                        withContext(Dispatchers.IO) { audioManager.startRinging() }
                        showIncomingCallNotification(state.callerPubKey)
                    }

                    is CallState.Offering -> {
                        audioManager.startRingbackTone()
                    }

                    is CallState.Connecting -> {
                        audioManager.stopRinging()
                        audioManager.stopRingbackTone()
                        withContext(Dispatchers.IO) { audioManager.switchToCallAudioMode() }
                        audioManager.acquireProximityWakeLock()
                        NotificationUtils.cancelCallNotification(context)
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

        // Pause outgoing video when the phone is held near the ear
        scope.launch {
            audioManager.isNearEar.collect { nearEar ->
                val session = webRtcSession ?: return@collect
                if (nearEar && _isVideoEnabled.value && !videoPausedByProximity) {
                    videoPausedByProximity = true
                    session.setVideoEnabled(false)
                    session.stopCamera()
                } else if (!nearEar && videoPausedByProximity) {
                    videoPausedByProximity = false
                    session.setVideoEnabled(true)
                    session.startCamera()
                }
            }
        }
    }

    fun initiateCall(
        peerPubKey: String,
        callType: CallType,
    ) {
        initiateCallInternal(setOf(peerPubKey), callType)
    }

    fun initiateGroupCall(
        peerPubKeys: Set<String>,
        callType: CallType,
    ) {
        initiateCallInternal(peerPubKeys, callType)
    }

    private fun initiateCallInternal(
        peerPubKeys: Set<String>,
        callType: CallType,
    ) {
        Log.d(TAG) { "initiateCallInternal: peers=${peerPubKeys.map { it.take(8) }}, callType=$callType" }
        scope.launch {
            val callId = UUID.randomUUID().toString()
            _errorMessage.value = null
            remoteDescriptionSet.set(false)
            pendingIceCandidates.clear()

            try {
                withContext(Dispatchers.IO) { createWebRtcSession() }
                Log.d(TAG) { "initiateCall: WebRTC session created" }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create WebRTC session", e)
                _errorMessage.value = "Failed to start call: ${e.message}"
                return@launch
            }

            val session =
                webRtcSession ?: run {
                    _errorMessage.value = "Failed to create WebRTC session"
                    return@launch
                }

            session.addAudioTrack()
            if (callType == CallType.VIDEO) {
                session.addVideoTrack()
                _localVideoTrack.value = session.getLocalVideoTrack()
                _isVideoEnabled.value = true
            }

            Log.d(TAG) { "initiateCall: creating offer..." }
            session.createOffer { sdp ->
                Log.d(TAG) { "initiateCall: offer created, sdpLength=${sdp.description.length}, publishing..." }
                scope.launch {
                    if (peerPubKeys.size == 1) {
                        callManager.initiateCall(peerPubKeys.first(), callType, callId, sdp.description)
                    } else {
                        callManager.initiateGroupCall(peerPubKeys, callType, callId, sdp.description)
                    }
                    Log.d(TAG) { "initiateCall: offer published, callId=$callId, state=${callManager.state.value::class.simpleName}" }
                }
            }
        }
    }

    fun acceptIncomingCall(sdpOffer: String) {
        val state = callManager.state.value
        if (state !is CallState.IncomingCall) {
            Log.d(TAG) { "acceptIncomingCall: state is ${state::class.simpleName}, not IncomingCall — ignoring" }
            return
        }

        Log.d(TAG) { "acceptIncomingCall: callId=${state.callId}, callType=${state.callType}, sdpOfferLength=${sdpOffer.length}, pendingICE=${pendingIceCandidates.size}" }

        scope.launch {
            _errorMessage.value = null
            remoteDescriptionSet.set(false)
            // Don't clear pendingIceCandidates here — they were buffered while ringing

            try {
                withContext(Dispatchers.IO) { createWebRtcSession() }
                Log.d(TAG) { "acceptIncomingCall: WebRTC session created" }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create WebRTC session", e)
                _errorMessage.value = "Failed to accept call: ${e.message}"
                return@launch
            }

            val session =
                webRtcSession ?: run {
                    Log.e(TAG, "acceptIncomingCall: webRtcSession is null after creation")
                    _errorMessage.value = "Failed to create WebRTC session"
                    return@launch
                }

            session.addAudioTrack()
            Log.d(TAG) { "acceptIncomingCall: audio track added" }
            if (state.callType == CallType.VIDEO) {
                session.addVideoTrack()
                _localVideoTrack.value = session.getLocalVideoTrack()
                _isVideoEnabled.value = true
                Log.d(TAG) { "acceptIncomingCall: video track added" }
            }

            Log.d(TAG) { "acceptIncomingCall: setting remote description (OFFER)..." }
            session.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, sdpOffer))
            Log.d(TAG) { "acceptIncomingCall: flushing ${pendingIceCandidates.size} pending ICE candidates..." }
            flushPendingIceCandidates()

            Log.d(TAG) { "acceptIncomingCall: creating answer..." }
            session.createAnswer { sdp ->
                Log.d(TAG) { "acceptIncomingCall: answer created, sdpLength=${sdp.description.length}, publishing..." }
                scope.launch {
                    callManager.acceptCall(sdp.description)
                    Log.d(TAG) { "acceptIncomingCall: answer published, state=${callManager.state.value::class.simpleName}" }
                }
            }
        }
    }

    fun onCallAnswerReceived(sdpAnswer: String) {
        val session = webRtcSession
        if (session == null) {
            Log.e(TAG, "Answer received but webRtcSession is NULL — cannot set remote description")
            return
        }
        val signalingState = session.getSignalingState()
        Log.d(TAG) {
            "Answer received, SDP length=${sdpAnswer.length}, signalingState=$signalingState, " +
                "remoteDescriptionSet=${remoteDescriptionSet.get()}, pendingICE=${pendingIceCandidates.size}, " +
                "callState=${callManager.state.value::class.simpleName}"
        }

        // An answer is only valid when we have a pending local offer.
        // Ignore stale answers (e.g. our own renegotiation answer echoed back by the relay).
        if (signalingState != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
            Log.d(TAG) { "Ignoring answer in $signalingState state (no pending local offer)" }
            return
        }

        Log.d(TAG) { "Setting remote description (ANSWER)..." }
        session.setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer))
        Log.d(TAG) { "Flushing pending ICE candidates after answer..." }
        flushPendingIceCandidates()
    }

    fun onIceCandidateReceived(event: CallIceCandidateEvent) {
        try {
            val candidate = IceCandidate(event.sdpMid(), event.sdpMLineIndex(), event.candidateSdp())
            if (webRtcSession != null && remoteDescriptionSet.get()) {
                Log.d(TAG) { "Adding ICE candidate directly: ${candidate.sdp.take(50)}" }
                webRtcSession?.addIceCandidate(candidate)
            } else {
                Log.d(TAG) { "Buffering ICE candidate (session=${webRtcSession != null}, remoteSet=${remoteDescriptionSet.get()})" }
                pendingIceCandidates.add(candidate)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ICE candidate", e)
        }
    }

    private fun flushPendingIceCandidates() {
        remoteDescriptionSet.set(true)
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
        val session = webRtcSession ?: return
        val enabling = !_isVideoEnabled.value

        if (enabling) {
            if (session.getLocalVideoTrack() == null) {
                // No video track yet — create one (voice → video upgrade)
                session.addVideoTrack()
            } else {
                // Track exists but camera was stopped — restart it
                session.setVideoEnabled(true)
                session.startCamera()
            }
            _localVideoTrack.value = session.getLocalVideoTrack()
            _isVideoEnabled.value = true
        } else {
            // Disable video: stop camera and disable track
            session.setVideoEnabled(false)
            session.stopCamera()
            _isVideoEnabled.value = false
        }
    }

    fun cycleAudioRoute() {
        audioManager.cycleAudioRoute()
    }

    fun getEglBase() = webRtcSession?.eglBase

    fun invitePeer(peerPubKey: String) {
        scope.launch {
            val session = webRtcSession ?: return@launch
            session.createOffer { sdp ->
                scope.launch {
                    callManager.invitePeer(peerPubKey, sdp.description)
                }
            }
        }
    }

    fun hangup() {
        scope.launch {
            callManager.hangup()
            // cleanup is triggered by the state collector when Ended is reached
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun onRenegotiationOfferReceived(event: CallRenegotiateEvent) {
        val session = webRtcSession ?: return
        val sdpOffer = event.sdpOffer()
        val peerPubKey = event.pubKey
        Log.d(TAG) { "Renegotiation offer received, SDP length=${sdpOffer.length}" }

        scope.launch {
            session.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, sdpOffer))
            session.createAnswer { sdp ->
                scope.launch {
                    callManager.sendRenegotiationAnswer(sdp.description, peerPubKey)
                }
            }
        }
    }

    private fun performRenegotiation() {
        val session = webRtcSession ?: return
        val state = callManager.state.value
        if (state !is CallState.Connected && state !is CallState.Connecting) return
        val peerPubKey = callManager.currentPeerPubKey() ?: return

        Log.d(TAG) { "Starting renegotiation" }
        session.createOffer { sdp ->
            scope.launch {
                callManager.sendRenegotiation(sdp.description, peerPubKey)
            }
        }
    }

    fun cleanup() {
        audioManager.release()
        stopForegroundService()
        NotificationUtils.cancelCallNotification(context)
        stopRemoteVideoMonitor()
        _remoteVideoTrack.value = null
        _localVideoTrack.value = null
        _isAudioMuted.value = false
        _isVideoEnabled.value = false
        _isRemoteVideoActive.value = false
        _remoteVideoAspectRatio.value = null
        videoPausedByProximity = false
        webRtcSession?.dispose()
        webRtcSession = null
        remoteDescriptionSet.set(false)
        pendingIceCandidates.clear()
    }

    private fun startRemoteVideoMonitor(track: VideoTrack) {
        stopRemoteVideoMonitor()
        lastRemoteFrameTimeMs.set(System.currentTimeMillis())
        track.addSink(remoteFrameSink)
        remoteVideoMonitorJob =
            scope.launch {
                while (true) {
                    delay(1500)
                    val elapsed = System.currentTimeMillis() - lastRemoteFrameTimeMs.get()
                    _isRemoteVideoActive.value = elapsed < 2000
                }
            }
    }

    private fun stopRemoteVideoMonitor() {
        remoteVideoMonitorJob?.cancel()
        remoteVideoMonitorJob = null
        try {
            _remoteVideoTrack.value?.removeSink(remoteFrameSink)
        } catch (_: Exception) {
        }
    }

    private fun createWebRtcSession() {
        val session =
            WebRtcCallSession(
                context = context,
                iceServers = IceServerConfig.buildIceServers(),
                onIceCandidate = { candidate -> onLocalIceCandidate(candidate) },
                onPeerConnected = {
                    callManager.onPeerConnected()
                    startForegroundService()
                },
                onRemoteVideoTrack = { track ->
                    _remoteVideoTrack.value = track
                    startRemoteVideoMonitor(track)
                },
                onDisconnected = { scope.launch { callManager.hangup() } },
                onError = { error -> _errorMessage.value = error },
                onRenegotiationNeeded = { performRenegotiation() },
            )
        try {
            session.initialize()
            session.createPeerConnection()
            webRtcSession = session
        } catch (e: Exception) {
            session.dispose()
            throw e
        }
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
            val isVideo = _isVideoEnabled.value
            val intent =
                Intent(context, CallForegroundService::class.java).apply {
                    action = CallForegroundService.ACTION_START
                    putExtra(CallForegroundService.EXTRA_PEER_NAME, peerName)
                    putExtra(CallForegroundService.EXTRA_IS_VIDEO, isVideo)
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

    private suspend fun showIncomingCallNotification(callerPubKey: String) {
        val callerUser = LocalCache.getUserIfExists(callerPubKey)
        val callerName = callerUser?.toBestDisplayName() ?: callerPubKey.take(8) + "..."
        val uri = "nostr:${callerPubKey.hexToByteArray().toNpub()}"

        val callerBitmap =
            callerUser?.profilePicture()?.let { pictureUrl ->
                withContext(Dispatchers.IO) {
                    try {
                        val request =
                            ImageRequest
                                .Builder(context)
                                .data(pictureUrl)
                                .allowHardware(false)
                                .build()
                        val result = ImageLoader(context).execute(request)
                        (result.image?.asDrawable(context.resources) as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    } catch (_: Exception) {
                        null
                    }
                }
            }

        NotificationUtils.sendCallNotification(
            callerName = callerName,
            callerBitmap = callerBitmap,
            uri = uri,
            applicationContext = context,
        )
    }
}
