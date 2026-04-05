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
import coil3.request.allowHardware
import com.vitorpamplona.amethyst.commons.call.AnswerRouteAction
import com.vitorpamplona.amethyst.commons.call.CallManager
import com.vitorpamplona.amethyst.commons.call.CallState
import com.vitorpamplona.amethyst.commons.call.IceCandidateData
import com.vitorpamplona.amethyst.commons.call.PeerSession
import com.vitorpamplona.amethyst.commons.call.PeerSessionManager
import com.vitorpamplona.amethyst.commons.call.SdpType
import com.vitorpamplona.amethyst.commons.call.SignalingState
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
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
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "CallController"
private const val VIDEO_MAX_BITRATE_BPS = 1_500_000

class CallController(
    private val context: Context,
    val callManager: CallManager,
    private val scope: CoroutineScope,
    private val publishWrap: suspend (EphemeralGiftWrapEvent) -> Unit,
    private val signerProvider: suspend () -> com.vitorpamplona.quartz.nip01Core.signers.NostrSigner,
) {
    // ---- Per-peer session state (delegated to PeerSessionManager) ----

    // Lazily initialized — needs signerProvider() for localPubKey
    private var peerSessionMgr: PeerSessionManager? = null

    private suspend fun sessionManager(): PeerSessionManager {
        if (peerSessionMgr == null) {
            peerSessionMgr = PeerSessionManager(signerProvider().pubKey)
        }
        return peerSessionMgr!!
    }

    /** Map from peer pubkey to the WebRtcCallSession for direct access to WebRTC-specific APIs. */
    private val webRtcSessions = ConcurrentHashMap<HexKey, WebRtcCallSession>()

    // ---- Shared WebRTC resources ----

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var sharedEglBase: EglBase? = null
    private var localAudioSource: AudioSource? = null
    private var localVideoSource: VideoSource? = null
    private var localAudioTrackInternal: AudioTrack? = null
    private var localVideoTrackInternal: VideoTrack? = null
    private var cameraCapturer: CameraVideoCapturer? = null

    private val callFactory = WebRtcCallFactory()
    val audioManager = CallAudioManager(context)

    // ---- UI-exposed state ----

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    // Primary remote track (first connected peer) for backward-compat with P2P UI
    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    // All remote tracks keyed by peer pubkey (for group call UI)
    private val _remoteVideoTracks = MutableStateFlow<Map<HexKey, VideoTrack>>(emptyMap())
    val remoteVideoTracks: StateFlow<Map<HexKey, VideoTrack>> = _remoteVideoTracks.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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

    // Per-peer video activity monitoring for group calls
    private val perPeerFrameSinks = ConcurrentHashMap<HexKey, VideoSink>()
    private val perPeerLastFrameTimeMs = ConcurrentHashMap<HexKey, AtomicLong>()
    private var groupVideoMonitorJob: kotlinx.coroutines.Job? = null

    // Set of peer pubkeys that are actively sending video frames
    private val _activePeerVideos = MutableStateFlow<Set<HexKey>>(emptySet())
    val activePeerVideos: StateFlow<Set<HexKey>> = _activePeerVideos.asStateFlow()

    private val _isAudioMuted = MutableStateFlow(false)
    val isAudioMuted: StateFlow<Boolean> = _isAudioMuted.asStateFlow()
    private val _isVideoEnabled = MutableStateFlow(false)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()
    val audioRoute: StateFlow<AudioRoute> = audioManager.audioRoute
    val isBluetoothAvailable: StateFlow<Boolean> = audioManager.isBluetoothAvailable

    private var videoPausedByProximity = false
    private var foregroundServiceStarted = false

    // ---- Initialization ----

    init {
        callManager.onRenegotiationOfferReceived = { event -> onRenegotiationOfferReceived(event) }

        scope.launch {
            callManager.state.collect { state ->
                when (state) {
                    is CallState.IncomingCall -> {
                        withContext(Dispatchers.IO) { audioManager.startRinging() }
                        // Launch notification in a separate coroutine so that
                        // long-running network I/O (profile picture download)
                        // does not block the state collector.  StateFlow is
                        // conflated — if the collector is suspended when the
                        // state transitions Ended → Idle, the Ended emission
                        // is lost and cleanup/stopRinging never runs.
                        scope.launch { showIncomingCallNotification(state.callerPubKey) }
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
                        // Stop ringing/ringback in case the Connecting state
                        // was skipped due to StateFlow conflation (the value
                        // can change Offering → Connecting → Connected before
                        // the collector processes Connecting).
                        audioManager.stopRinging()
                        audioManager.stopRingbackTone()
                        withContext(Dispatchers.IO) { audioManager.switchToCallAudioMode() }
                        audioManager.acquireProximityWakeLock()
                        NotificationUtils.cancelCallNotification(context)
                    }

                    is CallState.Ended -> {
                        cleanup()
                    }

                    is CallState.Idle -> {
                        // Safety net: full cleanup in case the Ended state
                        // was missed due to StateFlow conflation.  cleanup()
                        // is idempotent — calling it twice is harmless because
                        // each resource is null-checked and nulled out.
                        cleanup()
                    }
                }
            }
        }

        scope.launch {
            audioManager.isNearEar.collect { nearEar ->
                val videoTrack = localVideoTrackInternal ?: return@collect
                if (nearEar && _isVideoEnabled.value && !videoPausedByProximity) {
                    videoPausedByProximity = true
                    videoTrack.setEnabled(false)
                    stopCamera()
                } else if (!nearEar && videoPausedByProximity) {
                    videoPausedByProximity = false
                    videoTrack.setEnabled(true)
                    startCamera()
                }
            }
        }
    }

    // ---- Call initiation (caller side) ----

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

    /**
     * Creates a separate PeerConnection (and SDP offer) for each callee,
     * establishing full-mesh connectivity for group calls.
     */
    private fun initiateCallInternal(
        peerPubKeys: Set<String>,
        callType: CallType,
    ) {
        Log.d(TAG) { "initiateCallInternal: peers=${peerPubKeys.map { it.take(8) }}, callType=$callType" }
        scope.launch {
            val callId = UUID.randomUUID().toString()
            _errorMessage.value = null

            try {
                withContext(Dispatchers.IO) { initializeSharedResources(callType) }
                Log.d(TAG) { "initiateCall: shared resources initialized" }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize WebRTC", e)
                _errorMessage.value = "Failed to start call: ${e.message}"
                return@launch
            }

            // Set state to Offering before creating peer sessions
            callManager.beginOffering(callId, peerPubKeys, callType)

            val mgr = sessionManager()

            // Create a PeerConnection + offer for each callee
            for (peerPubKey in peerPubKeys) {
                try {
                    val webRtcSession = withContext(Dispatchers.IO) { createWebRtcSession(peerPubKey) }
                    val adapter = WebRtcPeerSessionAdapter(webRtcSession)
                    mgr.registerSession(peerPubKey, adapter)
                    Log.d(TAG) { "initiateCall: PeerConnection created for ${peerPubKey.take(8)}" }
                    webRtcSession.createOffer { sdp ->
                        Log.d(TAG) { "initiateCall: offer created for ${peerPubKey.take(8)}, sdpLength=${sdp.description.length}" }
                        scope.launch {
                            callManager.publishOfferToPeer(peerPubKey, peerPubKeys, callType, callId, sdp.description)
                            Log.d(TAG) { "initiateCall: offer published for ${peerPubKey.take(8)}" }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create PeerConnection for ${peerPubKey.take(8)}", e)
                }
            }
        }
    }

    // ---- Accept incoming call (callee side) ----

    fun acceptIncomingCall(sdpOffer: String) {
        val state = callManager.state.value
        if (state !is CallState.IncomingCall) {
            Log.d(TAG) { "acceptIncomingCall: state is ${state::class.simpleName}, not IncomingCall — ignoring" }
            return
        }

        val callerPubKey = state.callerPubKey
        Log.d(TAG) { "acceptIncomingCall: callId=${state.callId}, callType=${state.callType}, sdpOfferLength=${sdpOffer.length}" }

        scope.launch {
            _errorMessage.value = null

            try {
                withContext(Dispatchers.IO) { initializeSharedResources(state.callType) }
                Log.d(TAG) { "acceptIncomingCall: shared resources initialized" }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize WebRTC", e)
                _errorMessage.value = "Failed to accept call: ${e.message}"
                return@launch
            }

            val mgr = sessionManager()

            val webRtcSession =
                try {
                    withContext(Dispatchers.IO) { createWebRtcSession(callerPubKey) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create PeerConnection", e)
                    _errorMessage.value = "Failed to accept call: ${e.message}"
                    return@launch
                }

            val adapter = WebRtcPeerSessionAdapter(webRtcSession)
            val entry = mgr.registerSession(callerPubKey, adapter)

            Log.d(TAG) { "acceptIncomingCall: setting remote description (OFFER)..." }
            adapter.setRemoteDescription(SdpType.OFFER, sdpOffer)
            Log.d(TAG) { "acceptIncomingCall: flushing ${entry.pendingIceCandidates.size} pending ICE candidates..." }
            mgr.flushPendingIceCandidates(callerPubKey)

            Log.d(TAG) { "acceptIncomingCall: creating answer..." }
            webRtcSession.createAnswer { sdp ->
                Log.d(TAG) { "acceptIncomingCall: answer created, sdpLength=${sdp.description.length}, publishing..." }
                scope.launch {
                    callManager.acceptCall(sdp.description)
                    Log.d(TAG) { "acceptIncomingCall: answer published, state=${callManager.state.value::class.simpleName}" }
                }
            }
        }
    }

    // ---- Answer routing ----

    /**
     * Routes an answer to the correct per-peer PeerConnection.
     * For the caller: the answer is for a PeerConnection we already created.
     * For a callee seeing another callee's answer: we don't have a session
     * for them yet, so we trigger callee-to-callee connection.
     */
    fun onCallAnswerReceived(
        peerPubKey: HexKey,
        sdpAnswer: String,
    ) {
        val mgr = peerSessionMgr
        if (mgr == null) {
            Log.d(TAG) { "onCallAnswerReceived: sessionManager not initialized — ignoring" }
            return
        }

        Log.d(TAG) { "onCallAnswerReceived: from=${peerPubKey.take(8)}, knownSessions=${mgr.allSessionKeys().map { it.take(8) }}" }

        val action = mgr.routeAnswer(peerPubKey, sdpAnswer)
        Log.d(TAG) { "onCallAnswerReceived: action=$action" }
        when (action) {
            AnswerRouteAction.APPLIED -> {
                Log.d(TAG) { "Answer applied for ${peerPubKey.take(8)}" }
            }

            AnswerRouteAction.NO_SESSION -> {
                Log.d(TAG) { "Answer from unknown peer ${peerPubKey.take(8)} — triggering callee-to-callee connection" }
                onNewPeerInGroupCall(peerPubKey)
            }

            AnswerRouteAction.IGNORED_WRONG_STATE -> {
                Log.d(TAG) { "Ignoring answer from ${peerPubKey.take(8)} (no pending local offer)" }
            }
        }
    }

    // ---- ICE candidate routing ----

    /**
     * Routes an incoming ICE candidate to the correct per-peer session.
     * Before a session exists for a peer, candidates are buffered globally.
     */
    fun onIceCandidateReceived(event: CallIceCandidateEvent) {
        try {
            val senderPubKey = event.pubKey
            val candidate = IceCandidateData(event.candidateSdp(), event.sdpMid(), event.sdpMLineIndex())
            val mgr = peerSessionMgr
            if (mgr == null) {
                Log.d(TAG) { "Buffering ICE candidate from ${senderPubKey.take(8)} (manager not initialized)" }
                return
            }
            val action = mgr.routeIceCandidate(senderPubKey, candidate)
            Log.d(TAG) { "ICE candidate from ${senderPubKey.take(8)}: $action" }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ICE candidate", e)
        }
    }

    private fun onLocalIceCandidate(
        peerPubKey: HexKey,
        candidate: IceCandidate,
    ) {
        Log.d(TAG) { "Local ICE candidate for ${peerPubKey.take(8)}: ${candidate.sdp.take(50)}" }
        val callId = callManager.currentCallId() ?: return
        val candidateJson = CallIceCandidateEvent.serializeCandidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)

        scope.launch {
            val signer = signerProvider()
            val result = callFactory.createIceCandidate(candidateJson, peerPubKey, callId, signer)
            publishWrap(result.wrap)
        }
    }

    // ---- Callee-to-callee mesh connections ----

    /**
     * Another callee joined the group call. Establish a direct PeerConnection
     * to them. To avoid glare (both sides sending offers simultaneously), the
     * peer with the lexicographically lower pubkey initiates.
     */
    fun onNewPeerInGroupCall(peerPubKey: HexKey) {
        val mgr = peerSessionMgr
        if (mgr != null && mgr.hasSession(peerPubKey)) {
            Log.d(TAG) { "onNewPeerInGroupCall: session already exists for ${peerPubKey.take(8)} — skipping" }
            return
        }

        scope.launch {
            val sm = sessionManager()
            Log.d(TAG) { "onNewPeerInGroupCall: peer=${peerPubKey.take(8)}, shouldInitiate=${sm.shouldInitiateOffer(peerPubKey)}" }
            if (sm.shouldInitiateOffer(peerPubKey)) {
                Log.d(TAG) { "Initiating callee-to-callee connection to ${peerPubKey.take(8)} (I have lower pubkey)" }
                createAndOfferToPeer(peerPubKey)
            } else {
                Log.d(TAG) { "Waiting for callee-to-callee offer from ${peerPubKey.take(8)} (they have lower pubkey)" }
            }
        }
    }

    private suspend fun createAndOfferToPeer(peerPubKey: HexKey) {
        if (peerConnectionFactory == null) return
        val mgr = sessionManager()

        val webRtcSession =
            try {
                withContext(Dispatchers.IO) { createWebRtcSession(peerPubKey) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create PeerConnection for ${peerPubKey.take(8)}", e)
                return
            }

        val adapter = WebRtcPeerSessionAdapter(webRtcSession)
        mgr.registerSession(peerPubKey, adapter)

        webRtcSession.createOffer { sdp ->
            Log.d(TAG) { "Callee-to-callee offer created for ${peerPubKey.take(8)}, sdpLength=${sdp.description.length}" }
            scope.launch {
                callManager.publishOfferToPeer(peerPubKey, sdp.description)
            }
        }
    }

    /**
     * A mid-call offer was received from another callee in the group.
     * Create a PeerSession, accept their offer, and send back an answer.
     */
    fun onMidCallOfferReceived(
        peerPubKey: HexKey,
        sdpOffer: String,
    ) {
        val mgr = peerSessionMgr
        if (mgr != null && mgr.hasSession(peerPubKey)) {
            Log.d(TAG) { "Mid-call offer from ${peerPubKey.take(8)} but session already exists — ignoring" }
            return
        }

        Log.d(TAG) { "Mid-call offer from ${peerPubKey.take(8)}, sdpLength=${sdpOffer.length}" }
        scope.launch {
            if (peerConnectionFactory == null) {
                Log.e(TAG, "Mid-call offer but factory not initialized")
                return@launch
            }

            val sm = sessionManager()

            val webRtcSession =
                try {
                    withContext(Dispatchers.IO) { createWebRtcSession(peerPubKey) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create PeerConnection for mid-call offer from ${peerPubKey.take(8)}", e)
                    return@launch
                }

            val adapter = WebRtcPeerSessionAdapter(webRtcSession)
            sm.registerSession(peerPubKey, adapter)
            adapter.setRemoteDescription(SdpType.OFFER, sdpOffer)
            sm.flushPendingIceCandidates(peerPubKey)

            webRtcSession.createAnswer { sdp ->
                Log.d(TAG) { "Callee-to-callee answer for ${peerPubKey.take(8)}, sdpLength=${sdp.description.length}" }
                scope.launch {
                    callManager.publishAnswerToPeer(peerPubKey, sdp.description)
                }
            }
        }
    }

    // ---- Renegotiation ----

    private fun onRenegotiationOfferReceived(event: CallRenegotiateEvent) {
        val peerPubKey = event.pubKey
        val mgr = peerSessionMgr ?: return
        val sdpOffer = event.sdpOffer()
        Log.d(TAG) { "Renegotiation offer from ${peerPubKey.take(8)}, sdpLength=${sdpOffer.length}" }

        scope.launch {
            val resolution =
                mgr.resolveRenegotiationGlare(peerPubKey, sdpOffer) { entry ->
                    applyRenegotiationOffer(entry.session, peerPubKey, sdpOffer)
                }
            Log.d(TAG) { "Renegotiation glare resolution with ${peerPubKey.take(8)}: $resolution" }
        }
    }

    private fun applyRenegotiationOffer(
        session: PeerSession,
        peerPubKey: HexKey,
        sdpOffer: String,
    ) {
        session.setRemoteDescription(SdpType.OFFER, sdpOffer)
        session.createAnswer { sdpAnswer ->
            scope.launch {
                callManager.sendRenegotiationAnswer(sdpAnswer, peerPubKey)
            }
        }
    }

    private fun performRenegotiation(peerPubKey: HexKey) {
        val webRtcSession = webRtcSessions[peerPubKey] ?: return
        val state = callManager.state.value
        if (state !is CallState.Connected && state !is CallState.Connecting) return

        Log.d(TAG) { "Starting renegotiation with ${peerPubKey.take(8)}" }
        webRtcSession.createOffer { sdp ->
            scope.launch {
                callManager.sendRenegotiation(sdp.description, peerPubKey)
            }
        }
    }

    // ---- UI toggle controls ----

    fun toggleAudioMute() {
        val muted = !_isAudioMuted.value
        _isAudioMuted.value = muted
        localAudioTrackInternal?.setEnabled(!muted)
    }

    fun toggleVideo() {
        val enabling = !_isVideoEnabled.value

        if (enabling) {
            if (localVideoTrackInternal == null) {
                // Voice → video upgrade: create video source/track and add to all sessions
                createVideoResources()
                webRtcSessions.values.forEach { session ->
                    localVideoTrackInternal?.let { session.addTrack(it, VIDEO_MAX_BITRATE_BPS) }
                }
            } else {
                localVideoTrackInternal?.setEnabled(true)
                startCamera()
            }
            _localVideoTrack.value = localVideoTrackInternal
            _isVideoEnabled.value = true
        } else {
            localVideoTrackInternal?.setEnabled(false)
            stopCamera()
            _isVideoEnabled.value = false
        }
    }

    fun cycleAudioRoute() {
        audioManager.cycleAudioRoute()
    }

    fun getEglBase(): EglBase? = sharedEglBase

    fun invitePeer(peerPubKey: String) {
        scope.launch {
            createAndOfferToPeer(peerPubKey)
        }
    }

    fun hangup() {
        scope.launch {
            callManager.hangup()
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // ---- Shared resource management ----

    private fun initializeSharedResources(callType: CallType) {
        if (peerConnectionFactory != null) return // already initialized

        sharedEglBase = EglBase.create()

        PeerConnectionFactory.initialize(
            PeerConnectionFactory
                .InitializationOptions
                .builder(context)
                .createInitializationOptions(),
        )

        peerConnectionFactory =
            PeerConnectionFactory
                .builder()
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(sharedEglBase!!.eglBaseContext))
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(sharedEglBase!!.eglBaseContext, true, true))
                .createPeerConnectionFactory()

        // Audio
        localAudioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrackInternal = peerConnectionFactory?.createAudioTrack("audio0", localAudioSource)

        // Video (if video call)
        if (callType == CallType.VIDEO) {
            createVideoResources()
        }
    }

    private fun createVideoResources() {
        if (localVideoSource != null) return
        val factory = peerConnectionFactory ?: return

        localVideoSource = factory.createVideoSource(false)
        localVideoTrackInternal = factory.createVideoTrack("video0", localVideoSource)
        _localVideoTrack.value = localVideoTrackInternal
        _isVideoEnabled.value = true
        startCamera()
    }

    private fun startCamera() {
        if (cameraCapturer != null) return
        val source = localVideoSource ?: return
        val egl = sharedEglBase ?: return

        val enumerator = Camera2Enumerator(context)
        val frontCamera = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        val camera = frontCamera ?: enumerator.deviceNames.firstOrNull() ?: return

        cameraCapturer =
            enumerator.createCapturer(camera, null)?.also {
                it.initialize(
                    SurfaceTextureHelper.create("CaptureThread", egl.eglBaseContext),
                    context,
                    source.capturerObserver,
                )
                it.startCapture(1280, 720, 30)
            }
    }

    private fun stopCamera() {
        cameraCapturer?.stopCapture()
        cameraCapturer?.dispose()
        cameraCapturer = null
    }

    // ---- Per-peer PeerConnection creation ----

    private fun createWebRtcSession(peerPubKey: HexKey): WebRtcCallSession {
        Log.d(TAG) { "createWebRtcSession: ${peerPubKey.take(8)}, existing sessions=${webRtcSessions.keys.map { it.take(8) }}" }
        val factory = peerConnectionFactory ?: throw IllegalStateException("PeerConnectionFactory not initialized")

        val session =
            WebRtcCallSession(
                peerConnectionFactory = factory,
                iceServers = IceServerConfig.buildIceServers(),
                onIceCandidate = { candidate -> onLocalIceCandidate(peerPubKey, candidate) },
                onPeerConnected = {
                    Log.d(TAG) { "Peer ${peerPubKey.take(8)} connected!" }
                    callManager.onPeerConnected()
                    if (!foregroundServiceStarted) {
                        foregroundServiceStarted = true
                        startForegroundService()
                    }
                },
                onRemoteVideoTrack = { track -> onRemoteVideoTrack(peerPubKey, track) },
                onDisconnected = { onPeerDisconnected(peerPubKey) },
                onError = { error -> _errorMessage.value = error },
                onRenegotiationNeeded = { performRenegotiation(peerPubKey) },
            )

        try {
            session.createPeerConnection()
        } catch (e: Exception) {
            session.dispose()
            throw e
        }

        // Add shared local tracks to this PeerConnection
        localAudioTrackInternal?.let { session.addTrack(it) }
        localVideoTrackInternal?.let { session.addTrack(it, VIDEO_MAX_BITRATE_BPS) }

        webRtcSessions[peerPubKey] = session
        return session
    }

    private fun onRemoteVideoTrack(
        peerPubKey: HexKey,
        track: VideoTrack,
    ) {
        Log.d(TAG) { "Remote video track from ${peerPubKey.take(8)}" }
        _remoteVideoTracks.value = _remoteVideoTracks.value + (peerPubKey to track)
        // Backward-compat: set the first remote track as the primary
        if (_remoteVideoTrack.value == null) {
            _remoteVideoTrack.value = track
            startRemoteVideoMonitor(track)
        }
        // Monitor this peer's video activity for group call UI
        startPeerVideoMonitor(peerPubKey, track)
    }

    private fun startPeerVideoMonitor(
        peerPubKey: HexKey,
        track: VideoTrack,
    ) {
        // Remove any existing sink for this peer
        stopPeerVideoMonitor(peerPubKey)

        val lastFrameTime = AtomicLong(System.currentTimeMillis())
        perPeerLastFrameTimeMs[peerPubKey] = lastFrameTime
        val sink =
            VideoSink { frame: VideoFrame ->
                lastFrameTime.set(System.currentTimeMillis())
            }
        perPeerFrameSinks[peerPubKey] = sink
        track.addSink(sink)
        ensureGroupVideoMonitorRunning()
    }

    private fun stopPeerVideoMonitor(peerPubKey: HexKey) {
        val sink = perPeerFrameSinks.remove(peerPubKey) ?: return
        perPeerLastFrameTimeMs.remove(peerPubKey)
        val track = _remoteVideoTracks.value[peerPubKey]
        try {
            track?.removeSink(sink)
        } catch (_: Exception) {
        }
    }

    private fun ensureGroupVideoMonitorRunning() {
        if (groupVideoMonitorJob != null) return
        groupVideoMonitorJob =
            scope.launch {
                while (true) {
                    delay(1500)
                    val now = System.currentTimeMillis()
                    val activePeers = mutableSetOf<HexKey>()
                    for ((peerKey, lastFrame) in perPeerLastFrameTimeMs) {
                        if (now - lastFrame.get() < 2000) {
                            activePeers.add(peerKey)
                        }
                    }
                    _activePeerVideos.value = activePeers
                    val anyActive = activePeers.isNotEmpty()
                    _isRemoteVideoActive.value = anyActive || (now - lastRemoteFrameTimeMs.get() < 2000)
                }
            }
    }

    private fun onPeerDisconnected(peerPubKey: HexKey) {
        Log.d(TAG) { "Peer ${peerPubKey.take(8)} disconnected (ICE FAILED)" }
        val mgr = peerSessionMgr
        if (mgr == null) {
            scope.launch { callManager.hangup() }
            return
        }

        val allDisconnected =
            mgr.allSessionKeys().all { key ->
                key == peerPubKey ||
                    mgr.getSession(key)?.session?.getSignalingState() == SignalingState.CLOSED ||
                    mgr.getSession(key)?.remoteDescriptionSet != true
            }
        if (allDisconnected) {
            Log.d(TAG) { "onPeerDisconnected: all peers disconnected, hanging up" }
            scope.launch { callManager.hangup() }
        } else {
            Log.d(TAG) { "onPeerDisconnected: other peers still active, continuing call" }
        }
    }

    // ---- Per-peer cleanup ----

    /**
     * Disposes a single peer's WebRTC session when they leave the call
     * but the call continues with remaining peers.
     */
    fun disposePeerSession(peerPubKey: HexKey) {
        val entry = peerSessionMgr?.removeSession(peerPubKey)
        val webRtcSession = webRtcSessions.remove(peerPubKey)
        if (entry != null || webRtcSession != null) {
            Log.d(TAG) { "disposePeerSession: closing session for ${peerPubKey.take(8)}" }
            try {
                entry?.session?.dispose()
            } catch (e: Exception) {
                Log.e(TAG, "disposePeerSession: dispose() failed for ${peerPubKey.take(8)}", e)
            }
            // Clean up per-peer video monitor
            stopPeerVideoMonitor(peerPubKey)
            // Update remote video tracks
            val currentTracks = _remoteVideoTracks.value
            if (peerPubKey in currentTracks) {
                _remoteVideoTracks.value = currentTracks - peerPubKey
                // If the disposed peer was the primary remote track, pick a new one
                if (_remoteVideoTrack.value == currentTracks[peerPubKey]) {
                    stopRemoteVideoMonitor()
                    val nextTrack = _remoteVideoTracks.value.values.firstOrNull()
                    _remoteVideoTrack.value = nextTrack
                    if (nextTrack != null) {
                        startRemoteVideoMonitor(nextTrack)
                    }
                }
            }
        } else {
            Log.d(TAG) { "disposePeerSession: no session for ${peerPubKey.take(8)} (already disposed or never created)" }
        }
    }

    // ---- Cleanup ----

    fun cleanup() {
        Log.d(TAG) { "cleanup: disposing ${webRtcSessions.size} peer sessions, state=${callManager.state.value::class.simpleName}" }
        // Each block is wrapped individually so that a failure in one
        // (e.g. a WebRTC native crash) does not prevent the rest from
        // running.  Without this, a single exception could leave the
        // camera open, audio mode stuck, or the foreground service alive.
        try {
            audioManager.release()
        } catch (e: Exception) {
            Log.e(TAG, "cleanup: audioManager.release() failed", e)
        }
        try {
            stopForegroundService()
        } catch (e: Exception) {
            Log.e(TAG, "cleanup: stopForegroundService() failed", e)
        }
        foregroundServiceStarted = false
        NotificationUtils.cancelCallNotification(context)
        stopRemoteVideoMonitor()

        // Clean up per-peer video monitors
        for (peerPubKey in perPeerFrameSinks.keys.toList()) {
            stopPeerVideoMonitor(peerPubKey)
        }

        // Dispose all peer sessions
        try {
            peerSessionMgr?.disposeAll()
        } catch (e: Exception) {
            Log.e(TAG, "cleanup: sessionManager.disposeAll() failed", e)
        }
        peerSessionMgr = null
        webRtcSessions.clear()

        // Dispose shared resources — each in its own try-catch so one
        // failure does not prevent the others from being released.
        try {
            stopCamera()
        } catch (e: Exception) {
            Log.e(TAG, "cleanup: stopCamera() failed", e)
        }
        try {
            localAudioTrackInternal?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "cleanup: localAudioTrack.dispose() failed", e)
        }
        try {
            localVideoTrackInternal?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "cleanup: localVideoTrack.dispose() failed", e)
        }
        try {
            localAudioSource?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "cleanup: localAudioSource.dispose() failed", e)
        }
        try {
            localVideoSource?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "cleanup: localVideoSource.dispose() failed", e)
        }
        try {
            peerConnectionFactory?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "cleanup: peerConnectionFactory.dispose() failed", e)
        }
        try {
            sharedEglBase?.release()
        } catch (e: Exception) {
            Log.e(TAG, "cleanup: sharedEglBase.release() failed", e)
        }

        localAudioTrackInternal = null
        localVideoTrackInternal = null
        localAudioSource = null
        localVideoSource = null
        peerConnectionFactory = null
        sharedEglBase = null

        _remoteVideoTrack.value = null
        _remoteVideoTracks.value = emptyMap()
        _localVideoTrack.value = null
        _isAudioMuted.value = false
        _isVideoEnabled.value = false
        _isRemoteVideoActive.value = false
        _remoteVideoAspectRatio.value = null
        _activePeerVideos.value = emptySet()
        videoPausedByProximity = false
    }

    // ---- Remote video monitoring ----

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
        groupVideoMonitorJob?.cancel()
        groupVideoMonitorJob = null
        try {
            _remoteVideoTrack.value?.removeSink(remoteFrameSink)
        } catch (_: Exception) {
        }
    }

    // ---- Foreground service ----

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

    // ---- Incoming call notification ----

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
