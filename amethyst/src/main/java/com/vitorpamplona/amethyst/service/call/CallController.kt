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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.call.AnswerRouteAction
import com.vitorpamplona.amethyst.commons.call.CallManager
import com.vitorpamplona.amethyst.commons.call.CallState
import com.vitorpamplona.amethyst.commons.call.IceCandidateData
import com.vitorpamplona.amethyst.commons.call.PeerSession
import com.vitorpamplona.amethyst.commons.call.PeerSessionManager
import com.vitorpamplona.amethyst.commons.call.SdpType
import com.vitorpamplona.amethyst.commons.call.SignalingState
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.WebRtcCallFactory
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRenegotiateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.VideoTrack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "CallController"
private const val VIDEO_MAX_BITRATE_BPS_DEFAULT = 1_500_000

@Stable
class CallController(
    private val context: Context,
    val callManager: CallManager,
    private val scope: CoroutineScope,
    private val publishWrap: suspend (EphemeralGiftWrapEvent) -> Unit,
    private val signerProvider: suspend () -> com.vitorpamplona.quartz.nip01Core.signers.NostrSigner,
    localPubKey: HexKey,
    private val settingsProvider: () -> com.vitorpamplona.amethyst.model.AccountSettings,
) {
    private var peerSessionMgr = PeerSessionManager(localPubKey)

    private fun webRtcSession(peerPubKey: HexKey): WebRtcCallSession? = (peerSessionMgr.getSession(peerPubKey)?.session as? WebRtcPeerSessionAdapter)?.webRtcSession

    val mediaManager = CallMediaManager(context)
    val audioManager = CallAudioManager(context)
    val videoMonitor = RemoteVideoMonitor(scope)
    private val callFactory = WebRtcCallFactory()

    // ---- UI-exposed state ----

    val localVideoTrack: StateFlow<VideoTrack?> = mediaManager.localVideoTrackFlow
    val remoteVideoTrack: StateFlow<VideoTrack?> = videoMonitor.remoteVideoTrack
    val remoteVideoTracks: StateFlow<Map<HexKey, VideoTrack>> = videoMonitor.remoteVideoTracks

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val isRemoteVideoActive: StateFlow<Boolean> = videoMonitor.isRemoteVideoActive
    val remoteVideoAspectRatio: StateFlow<Float?> = videoMonitor.remoteVideoAspectRatio
    val activePeerVideos: StateFlow<Set<HexKey>> = videoMonitor.activePeerVideos

    private val _isAudioMuted = MutableStateFlow(false)
    val isAudioMuted: StateFlow<Boolean> = _isAudioMuted.asStateFlow()
    val isVideoEnabled: StateFlow<Boolean> = mediaManager.isVideoEnabled
    val isFrontCamera: StateFlow<Boolean> = mediaManager.isFrontCamera
    val audioRoute: StateFlow<AudioRoute> = audioManager.audioRoute
    val isBluetoothAvailable: StateFlow<Boolean> = audioManager.isBluetoothAvailable

    @Volatile private var videoPausedByProximity = false

    @Volatile private var foregroundServiceStarted = false
    private val cleanedUp = AtomicBoolean(false)
    private val videoSenders = ConcurrentHashMap<HexKey, org.webrtc.RtpSender>()

    private val pendingRenegotiation = ConcurrentHashMap<HexKey, Boolean>()

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallbackRegistered = false
    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG) { "Network available — triggering ICE restart on all peers" }
                restartIceOnAllPeers()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                Log.d(TAG) { "Network capabilities changed — triggering ICE restart on all peers" }
                restartIceOnAllPeers()
            }
        }

    // ---- Initialization ----

    init {
        callManager.onRenegotiationOfferReceived = { event -> onRenegotiationOfferReceived(event) }

        scope.launch {
            callManager.state.collect { state ->
                when (state) {
                    is CallState.IncomingCall -> {
                        // A new call session begins — arm cleanup() so it will
                        // run for this session's Ended transition. This MUST
                        // happen for every incoming call (even ones the user
                        // ends up rejecting), otherwise a stale cleanedUp flag
                        // from the previous call session would turn cleanup()
                        // into a no-op and leave the ringtone/notification
                        // playing forever. See the bug where rejecting a
                        // second consecutive call required killing the app.
                        cleanedUp.set(false)
                        ensureForegroundService()
                        withContext(Dispatchers.IO) { audioManager.startRinging() }
                        scope.launch {
                            NotificationUtils.showIncomingCallNotification(state.callerPubKey, context)
                        }
                    }

                    is CallState.Offering -> {
                        // Same rationale as IncomingCall above: ensure cleanup
                        // can run for this session even if the previous one
                        // already ran cleanup().
                        cleanedUp.set(false)
                        audioManager.startRingbackTone()
                        ensureForegroundService()
                    }

                    is CallState.Connecting -> {
                        audioManager.stopRinging()
                        audioManager.stopRingbackTone()
                        withContext(Dispatchers.IO) { audioManager.switchToCallAudioMode() }
                        audioManager.acquireProximityWakeLock()
                        NotificationUtils.cancelCallNotification(context)
                        updateForegroundServiceNotification()
                        registerNetworkCallback()
                    }

                    is CallState.Connected -> {
                        audioManager.stopRinging()
                        audioManager.stopRingbackTone()
                        withContext(Dispatchers.IO) { audioManager.switchToCallAudioMode() }
                        audioManager.acquireProximityWakeLock()
                        NotificationUtils.cancelCallNotification(context)
                        updateForegroundServiceNotification()
                        registerNetworkCallback()
                    }

                    is CallState.Ended -> {
                        cleanup()
                    }

                    is CallState.Idle -> {
                        // No cleanup here — Ended already handled it.
                        // Ended auto-resets to Idle after 2 seconds;
                        // running cleanup twice is wasteful and logs errors.
                    }
                }
            }
        }

        scope.launch {
            audioManager.isNearEar.collect { nearEar ->
                val videoTrack = mediaManager.localVideoTrack ?: return@collect
                if (nearEar && mediaManager.isVideoEnabled.value && !videoPausedByProximity) {
                    videoPausedByProximity = true
                    videoTrack.setEnabled(false)
                    mediaManager.stopCamera()
                } else if (!nearEar && videoPausedByProximity) {
                    videoPausedByProximity = false
                    videoTrack.setEnabled(true)
                    mediaManager.startCamera()
                }
            }
        }
    }

    private fun applyCallSettings() {
        val settings = settingsProvider()
        val resolution = settings.callVideoResolution
        mediaManager.setCaptureResolution(resolution.width, resolution.height, resolution.fps)
    }

    // ---- Call initiation (caller side) ----

    fun initiateGroupCall(
        peerPubKeys: Set<String>,
        callType: CallType,
    ) {
        Log.d(TAG) { "initiateCallInternal: peers=${peerPubKeys.map { it.take(8) }}, callType=$callType" }
        scope.launch {
            val callId = UUID.randomUUID().toString()
            _errorMessage.value = null

            // A new call session begins — arm cleanup() so it will run again
            // for this session's Ended transition.
            cleanedUp.set(false)
            applyCallSettings()
            try {
                withContext(Dispatchers.IO) { mediaManager.initialize(callType) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize WebRTC", e)
                _errorMessage.value = "Failed to start call: ${e.message}"
                callManager.hangup()
                return@launch
            }

            callManager.beginOffering(callId, peerPubKeys, callType)

            var successCount = 0
            for (peerPubKey in peerPubKeys) {
                try {
                    val webRtcSession = withContext(Dispatchers.IO) { createWebRtcSession(peerPubKey) }
                    val adapter = WebRtcPeerSessionAdapter(webRtcSession)
                    peerSessionMgr.registerSession(peerPubKey, adapter)
                    webRtcSession.createOffer { sdp ->
                        scope.launch {
                            callManager.publishOfferToPeer(peerPubKey, peerPubKeys, callType, callId, sdp.description)
                        }
                    }
                    successCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create PeerConnection for ${peerPubKey.take(8)}", e)
                }
            }
            if (successCount == 0) {
                Log.e(TAG, "All PeerConnection creations failed, hanging up")
                _errorMessage.value = "Failed to start call: could not create any connections"
                callManager.hangup()
            }
        }
    }

    // ---- Accept incoming call (callee side) ----

    fun acceptIncomingCall(sdpOffer: String) {
        val state = callManager.state.value
        if (state !is CallState.IncomingCall) return

        val callerPubKey = state.callerPubKey
        scope.launch {
            _errorMessage.value = null

            // A new call session begins — arm cleanup() so it will run again
            // for this session's Ended transition.
            cleanedUp.set(false)
            applyCallSettings()
            try {
                withContext(Dispatchers.IO) { mediaManager.initialize(state.callType) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize WebRTC", e)
                _errorMessage.value = "Failed to accept call: ${e.message}"
                callManager.rejectCall()
                return@launch
            }

            val webRtcSession =
                try {
                    withContext(Dispatchers.IO) { createWebRtcSession(callerPubKey) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create PeerConnection", e)
                    _errorMessage.value = "Failed to accept call: ${e.message}"
                    callManager.rejectCall()
                    return@launch
                }

            val adapter = WebRtcPeerSessionAdapter(webRtcSession)
            peerSessionMgr.registerSession(callerPubKey, adapter)
            adapter.setRemoteDescription(SdpType.OFFER, sdpOffer)
            peerSessionMgr.flushPendingIceCandidates(callerPubKey)

            webRtcSession.createAnswer { sdp ->
                scope.launch {
                    callManager.acceptCall(sdp.description)
                }
            }
        }
    }

    // ---- Answer routing ----

    fun onCallAnswerReceived(
        peerPubKey: HexKey,
        sdpAnswer: String,
    ) {
        val action = peerSessionMgr.routeAnswer(peerPubKey, sdpAnswer)
        when (action) {
            AnswerRouteAction.APPLIED -> {
                Log.d(TAG) { "Answer applied for ${peerPubKey.take(8)}" }
            }

            AnswerRouteAction.NO_SESSION -> {
                // Unknown peer answering the current call. Two cases:
                //
                // 1. We are in Connected state — another participant invited a
                //    new peer mid-call and the invitee is broadcasting their
                //    acceptance to us. The invitee stays passive, so we MUST
                //    unconditionally initiate a mesh offer to them. The
                //    lower-pubkey tiebreaker does NOT apply here because only
                //    one side (the existing Connected callee) reacts to the
                //    broadcast answer.
                //
                // 2. We are still in Connecting state — both callees are
                //    handshaking in parallel during an initial group call and
                //    are observing each other's answers. Use the lower-pubkey
                //    tiebreaker via onNewPeerInGroupCall() to avoid glare,
                //    since the symmetric peer will apply the same rule.
                if (callManager.state.value is CallState.Connected) {
                    Log.d(TAG) { "Mid-call invite: ${peerPubKey.take(8)} joined — initiating mesh offer" }
                    scope.launch { createAndOfferToPeer(peerPubKey) }
                } else {
                    Log.d(TAG) { "Answer from unknown peer ${peerPubKey.take(8)} — triggering callee-to-callee" }
                    onNewPeerInGroupCall(peerPubKey)
                }
            }

            AnswerRouteAction.IGNORED_WRONG_STATE -> {
                Log.d(TAG) { "Ignoring answer from ${peerPubKey.take(8)} (no pending local offer)" }
            }
        }
    }

    // ---- ICE candidate routing ----

    fun onIceCandidateReceived(event: CallIceCandidateEvent) {
        try {
            val candidate = IceCandidateData(event.candidateSdp(), event.sdpMid(), event.sdpMLineIndex())
            peerSessionMgr.routeIceCandidate(event.pubKey, candidate)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ICE candidate", e)
        }
    }

    private fun onLocalIceCandidate(
        peerPubKey: HexKey,
        candidate: IceCandidate,
    ) {
        val callId = callManager.currentCallId() ?: return
        val candidateJson = CallIceCandidateEvent.serializeCandidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
        scope.launch {
            val signer = signerProvider()
            val result = callFactory.createIceCandidate(candidateJson, peerPubKey, callId, signer)
            publishWrap(result.wrap)
        }
    }

    // ---- Callee-to-callee mesh connections ----

    fun onNewPeerInGroupCall(peerPubKey: HexKey) {
        if (peerSessionMgr.hasSession(peerPubKey)) return
        scope.launch {
            if (peerSessionMgr.shouldInitiateOffer(peerPubKey)) {
                createAndOfferToPeer(peerPubKey)
            }
        }
    }

    private suspend fun createAndOfferToPeer(peerPubKey: HexKey) {
        if (mediaManager.peerConnectionFactory == null) return
        val webRtcSession =
            try {
                withContext(Dispatchers.IO) { createWebRtcSession(peerPubKey) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create PeerConnection for ${peerPubKey.take(8)}", e)
                return
            }
        val adapter = WebRtcPeerSessionAdapter(webRtcSession)
        peerSessionMgr.registerSession(peerPubKey, adapter)
        webRtcSession.createOffer { sdp ->
            scope.launch { callManager.publishOfferToPeer(peerPubKey, sdp.description) }
        }
    }

    fun onMidCallOfferReceived(
        peerPubKey: HexKey,
        sdpOffer: String,
    ) {
        if (peerSessionMgr.hasSession(peerPubKey)) return
        scope.launch {
            if (mediaManager.peerConnectionFactory == null) return@launch
            val webRtcSession =
                try {
                    withContext(Dispatchers.IO) { createWebRtcSession(peerPubKey) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create PeerConnection for mid-call offer from ${peerPubKey.take(8)}", e)
                    return@launch
                }
            val adapter = WebRtcPeerSessionAdapter(webRtcSession)
            peerSessionMgr.registerSession(peerPubKey, adapter)
            adapter.setRemoteDescription(SdpType.OFFER, sdpOffer)
            peerSessionMgr.flushPendingIceCandidates(peerPubKey)
            webRtcSession.createAnswer { sdp ->
                scope.launch { callManager.publishAnswerToPeer(peerPubKey, sdp.description) }
            }
        }
    }

    // ---- Renegotiation ----

    private fun onRenegotiationOfferReceived(event: CallRenegotiateEvent) {
        val peerPubKey = event.pubKey
        val sdpOffer = event.sdpOffer()
        scope.launch {
            peerSessionMgr.resolveRenegotiationGlare(peerPubKey, sdpOffer) { entry ->
                applyRenegotiationOffer(entry.session, peerPubKey, sdpOffer)
            }
        }
    }

    private fun applyRenegotiationOffer(
        session: PeerSession,
        peerPubKey: HexKey,
        sdpOffer: String,
    ) {
        session.setRemoteDescription(SdpType.OFFER, sdpOffer)
        session.createAnswer { sdpAnswer ->
            scope.launch { callManager.sendRenegotiationAnswer(sdpAnswer, peerPubKey) }
        }
    }

    private fun performRenegotiation(peerPubKey: HexKey) {
        if (pendingRenegotiation.putIfAbsent(peerPubKey, true) != null) return
        val webRtcSession =
            webRtcSession(peerPubKey) ?: run {
                pendingRenegotiation.remove(peerPubKey)
                return
            }
        val state = callManager.state.value
        if (state !is CallState.Connected && state !is CallState.Connecting) {
            pendingRenegotiation.remove(peerPubKey)
            return
        }
        webRtcSession.createOffer { sdp ->
            pendingRenegotiation.remove(peerPubKey)
            scope.launch { callManager.sendRenegotiation(sdp.description, peerPubKey) }
        }
    }

    // ---- Network change handling ----

    private fun restartIceOnAllPeers() {
        val state = callManager.state.value
        if (state !is CallState.Connected && state !is CallState.Connecting) return
        peerSessionMgr.allSessionKeys().forEach { key ->
            webRtcSession(key)?.triggerIceRestart()
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        try {
            val request =
                NetworkRequest
                    .Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            networkCallbackRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
        networkCallbackRegistered = false
    }

    // ---- UI toggle controls ----

    fun toggleAudioMute() {
        val muted = !_isAudioMuted.value
        _isAudioMuted.value = muted
        mediaManager.setAudioMuted(muted)
    }

    fun toggleVideo() {
        val enabling = !mediaManager.isVideoEnabled.value
        if (enabling) {
            if (mediaManager.localVideoTrack == null) {
                mediaManager.createVideoResources()
            } else {
                mediaManager.enableVideo()
            }
            // Add video track to all peer connections
            peerSessionMgr.allSessionKeys().forEach { key ->
                if (videoSenders[key] == null) {
                    webRtcSession(key)?.let { session ->
                        mediaManager.localVideoTrack?.let { track ->
                            val sender = session.addTrack(track, settingsProvider().callMaxBitrateBps)
                            if (sender != null) {
                                videoSenders[key] = sender
                            }
                        }
                    }
                }
            }
        } else {
            // Remove video track senders so remote peers see track removal
            peerSessionMgr.allSessionKeys().forEach { key ->
                videoSenders.remove(key)?.let { sender ->
                    webRtcSession(key)?.removeTrack(sender)
                }
            }
            mediaManager.disableVideo()
        }
    }

    fun switchCamera() {
        mediaManager.switchCamera()
    }

    fun cycleAudioRoute() {
        audioManager.cycleAudioRoute()
    }

    fun getEglBase(): EglBase? = mediaManager.sharedEglBase

    fun invitePeer(peerPubKey: String) {
        scope.launch {
            if (mediaManager.peerConnectionFactory == null) return@launch
            val webRtcSession =
                try {
                    withContext(Dispatchers.IO) { createWebRtcSession(peerPubKey) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create PeerConnection for invite ${peerPubKey.take(8)}", e)
                    return@launch
                }
            val adapter = WebRtcPeerSessionAdapter(webRtcSession)
            peerSessionMgr.registerSession(peerPubKey, adapter)
            webRtcSession.createOffer { sdp ->
                scope.launch { callManager.invitePeer(peerPubKey, sdp.description) }
            }
        }
    }

    fun hangup() {
        scope.launch { callManager.hangup() }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // ---- Per-peer PeerConnection creation ----

    private fun createWebRtcSession(peerPubKey: HexKey): WebRtcCallSession {
        val factory = mediaManager.peerConnectionFactory ?: throw IllegalStateException("PeerConnectionFactory not initialized")
        val session =
            WebRtcCallSession(
                peerConnectionFactory = factory,
                iceServers = IceServerConfig.buildIceServers(settingsProvider().callTurnServers),
                onIceCandidate = { candidate -> onLocalIceCandidate(peerPubKey, candidate) },
                onPeerConnected = {
                    Log.d(TAG) { "Peer ${peerPubKey.take(8)} connected!" }
                    scope.launch {
                        callManager.onPeerConnected()
                        if (callManager.state.value is CallState.Connected) {
                            ensureForegroundService()
                        }
                    }
                },
                onRemoteVideoTrack = { track -> videoMonitor.onRemoteVideoTrack(peerPubKey, track) },
                onDisconnected = { scope.launch { onPeerDisconnected(peerPubKey) } },
                onError = { error -> _errorMessage.value = error },
                onRenegotiationNeeded = { performRenegotiation(peerPubKey) },
                onIceRestartOffer = { sdp ->
                    scope.launch { callManager.sendRenegotiation(sdp.description, peerPubKey) }
                },
            )
        try {
            session.createPeerConnection()
        } catch (e: Exception) {
            session.dispose()
            throw e
        }
        mediaManager.localAudioTrack?.let { session.addTrack(it) }
        mediaManager.localVideoTrack?.let { track ->
            val sender = session.addTrack(track, settingsProvider().callMaxBitrateBps)
            if (sender != null) {
                videoSenders[peerPubKey] = sender
            }
        }
        return session
    }

    private fun onPeerDisconnected(peerPubKey: HexKey) {
        Log.d(TAG) { "Peer ${peerPubKey.take(8)} disconnected (ICE FAILED)" }
        val allDisconnected =
            peerSessionMgr.allSessionKeys().all { key ->
                key == peerPubKey ||
                    peerSessionMgr.getSession(key)?.session?.getSignalingState() == SignalingState.CLOSED ||
                    peerSessionMgr.getSession(key)?.remoteDescriptionSet != true
            }
        if (allDisconnected) {
            scope.launch { callManager.hangup() }
        }
    }

    // ---- Per-peer cleanup ----

    fun disposePeerSession(peerPubKey: HexKey) {
        videoSenders.remove(peerPubKey)
        val entry = peerSessionMgr.removeSession(peerPubKey)
        if (entry != null) {
            try {
                entry.session.dispose()
            } catch (e: Exception) {
                Log.e(TAG, "disposePeerSession: dispose() failed for ${peerPubKey.take(8)}", e)
            }
            videoMonitor.onPeerRemoved(peerPubKey)
        }
    }

    // ---- Cleanup ----

    /**
     * Releases all WebRTC, audio, and foreground-service resources for the
     * current call session.
     *
     * Stopping the ringtone, ringback tone, and incoming-call notification
     * runs UNCONDITIONALLY on every invocation — even if the heavy teardown
     * has already been done. These three operations are idempotent and must
     * never be skipped, otherwise a stale [cleanedUp] flag could leave the
     * ringtone playing and the notification stuck on screen until the app is
     * force-killed.
     *
     * The remainder (peer-session disposal, media manager, network callbacks,
     * etc.) is guarded by [cleanedUp] and runs at most once per call session.
     * The guard is re-armed when a new call starts: outgoing calls re-arm in
     * [initiateGroupCall], accepted incoming calls in [acceptIncomingCall],
     * and any new IncomingCall/Offering state transition in the state
     * collector. This last path is critical for incoming calls the user
     * rejects (or that the remote party hangs up), since neither
     * [initiateGroupCall] nor [acceptIncomingCall] is invoked for them.
     */
    fun cleanup() {
        // Stop the ringtone, ringback tone, and incoming-call notification
        // UNCONDITIONALLY — these are idempotent and absolutely critical for
        // the user experience. They must run on every cleanup() call, even if
        // the heavy resource teardown below is already complete (cleanedUp ==
        // true). Otherwise an unexpected double-cleanup or a stale cleanedUp
        // flag from a previous session could leave the ringtone playing and
        // the notification stuck on screen until the app is force-killed.
        try {
            audioManager.stopRinging()
        } catch (e: Exception) {
            Log.e(TAG, "cleanup: audioManager.stopRinging() failed", e)
        }
        try {
            audioManager.stopRingbackTone()
        } catch (e: Exception) {
            Log.e(TAG, "cleanup: audioManager.stopRingbackTone() failed", e)
        }
        try {
            NotificationUtils.cancelCallNotification(context)
        } catch (e: Exception) {
            Log.e(TAG, "cleanup: cancelCallNotification() failed", e)
        }

        if (!cleanedUp.compareAndSet(false, true)) return
        unregisterNetworkCallback()
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

        videoMonitor.dispose()

        try {
            peerSessionMgr.disposeAll()
        } catch (e: Exception) {
            Log.e(TAG, "cleanup: sessionManager.disposeAll() failed", e)
        }
        peerSessionMgr = PeerSessionManager(peerSessionMgr.localPubKey)

        mediaManager.dispose()

        _isAudioMuted.value = false
        videoPausedByProximity = false
        videoSenders.clear()
        pendingRenegotiation.clear()
        // NOTE: cleanedUp intentionally stays true here so that a second,
        // sequential cleanup() in the same call session is a no-op for the
        // heavy teardown above. The flag is re-armed when a new call session
        // begins, in initiateGroupCall() / acceptIncomingCall() AND in the
        // state collector for IncomingCall / Offering transitions, so
        // subsequent sessions still clean up correctly.
    }

    // ---- Foreground service ----

    private fun ensureForegroundService() {
        if (foregroundServiceStarted) return
        foregroundServiceStarted = true
        startForegroundService()
    }

    private fun startForegroundService() {
        try {
            val peerName = callManager.currentPeerPubKey() ?: ""
            val isVideo = mediaManager.isVideoEnabled.value
            val isRinging = callManager.state.value is CallState.IncomingCall || callManager.state.value is CallState.Offering
            val intent =
                Intent(context, CallForegroundService::class.java).apply {
                    action = CallForegroundService.ACTION_START
                    putExtra(CallForegroundService.EXTRA_PEER_NAME, peerName)
                    putExtra(CallForegroundService.EXTRA_IS_VIDEO, isVideo)
                    putExtra(CallForegroundService.EXTRA_IS_RINGING, isRinging)
                }
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
    }

    private fun updateForegroundServiceNotification() {
        if (!foregroundServiceStarted) return
        try {
            val peerName = callManager.currentPeerPubKey() ?: ""
            val isVideo = mediaManager.isVideoEnabled.value
            val isRinging = callManager.state.value is CallState.IncomingCall || callManager.state.value is CallState.Offering
            val intent =
                Intent(context, CallForegroundService::class.java).apply {
                    action = CallForegroundService.ACTION_UPDATE
                    putExtra(CallForegroundService.EXTRA_PEER_NAME, peerName)
                    putExtra(CallForegroundService.EXTRA_IS_VIDEO, isVideo)
                    putExtra(CallForegroundService.EXTRA_IS_RINGING, isRinging)
                }
            context.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update foreground service notification", e)
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
