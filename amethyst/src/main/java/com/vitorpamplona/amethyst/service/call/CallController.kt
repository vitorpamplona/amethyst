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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.vitorpamplona.amethyst.commons.call.CallManager
import com.vitorpamplona.amethyst.commons.call.CallState
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils
import com.vitorpamplona.quartz.nip01Core.core.HexKey
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
    private val callManager: CallManager,
    private val scope: CoroutineScope,
    private val publishWrap: suspend (GiftWrapEvent) -> Unit,
    private val signerProvider: suspend () -> com.vitorpamplona.quartz.nip01Core.signers.NostrSigner,
) {
    private var webRtcSession: WebRtcCallSession? = null
    private val callFactory = WebRtcCallFactory()
    private var currentCallId: String? = null
    private var currentPeerPubKey: HexKey? = null
    private var remoteDescriptionSet = false
    private val pendingIceCandidates = CopyOnWriteArrayList<IceCandidate>()
    val audioManager = CallAudioManager(context)

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isAudioMuted = MutableStateFlow(false)
    val isAudioMuted: StateFlow<Boolean> = _isAudioMuted.asStateFlow()

    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

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
                        registerNetworkCallback()
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
        peerPubKey: HexKey,
        callType: CallType,
    ) {
        try {
            val callId = UUID.randomUUID().toString()
            currentCallId = callId
            currentPeerPubKey = peerPubKey
            remoteDescriptionSet = false
            pendingIceCandidates.clear()
            _errorMessage.value = null

            createWebRtcSession()
            webRtcSession?.addAudioTrack()
            if (callType == CallType.VIDEO) {
                webRtcSession?.addVideoTrack()
                _localVideoTrack.value = webRtcSession?.getLocalVideoTrack()
            }

            webRtcSession?.createOffer { sdp ->
                scope.launch {
                    callManager.initiateCall(peerPubKey, callType, callId, sdp.description)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate call", e)
            _errorMessage.value = "Failed to start call: ${e.message}"
            cleanup()
        }
    }

    fun acceptIncomingCall(sdpOffer: String) {
        try {
            val state = callManager.state.value
            if (state !is CallState.IncomingCall) return

            currentCallId = state.callId
            currentPeerPubKey = state.callerPubKey
            remoteDescriptionSet = false
            pendingIceCandidates.clear()
            _errorMessage.value = null

            createWebRtcSession()
            webRtcSession?.addAudioTrack()
            if (state.callType == CallType.VIDEO) {
                webRtcSession?.addVideoTrack()
                _localVideoTrack.value = webRtcSession?.getLocalVideoTrack()
            }

            webRtcSession?.setRemoteDescription(
                SessionDescription(SessionDescription.Type.OFFER, sdpOffer),
            )
            flushPendingIceCandidates()

            webRtcSession?.createAnswer { sdp ->
                scope.launch {
                    callManager.acceptCall(sdp.description)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to accept call", e)
            _errorMessage.value = "Failed to accept call: ${e.message}"
            cleanup()
        }
    }

    fun onCallAnswerReceived(sdpAnswer: String) {
        Log.d(TAG) { "Answer received, SDP length=${sdpAnswer.length}, session=${webRtcSession != null}" }
        webRtcSession?.setRemoteDescription(
            SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer),
        )
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
        unregisterNetworkCallback()
        stopForegroundService()
        NotificationUtils.cancelCallNotification(context)
        _remoteVideoTrack.value = null
        _localVideoTrack.value = null
        _isAudioMuted.value = false
        _isVideoEnabled.value = true
        _isSpeakerOn.value = false
        webRtcSession?.dispose()
        webRtcSession = null
        currentCallId = null
        currentPeerPubKey = null
        remoteDescriptionSet = false
        pendingIceCandidates.clear()
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
                onRemoteStream = { stream: MediaStream ->
                    stream.videoTracks?.firstOrNull()?.let {
                        _remoteVideoTrack.value = it
                    }
                },
                onDisconnected = {
                    scope.launch { callManager.hangup() }
                },
                onError = { error ->
                    _errorMessage.value = error
                },
            )
        webRtcSession?.initialize()
        webRtcSession?.createPeerConnection()
    }

    private fun registerNetworkCallback() {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request =
                NetworkRequest
                    .Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.d(TAG) { "Network available, ICE restart may be needed" }
                    }

                    override fun onLost(network: Network) {
                        Log.d(TAG) { "Network lost during call" }
                    }
                }
            connectivityManager.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {
        }
        networkCallback = null
    }

    private fun onLocalIceCandidate(candidate: IceCandidate) {
        Log.d(TAG) { "Local ICE candidate: ${candidate.sdp.take(50)}" }
        val callId = currentCallId ?: return
        val peerPubKey = currentPeerPubKey ?: return
        val candidateJson = CallIceCandidateEvent.serializeCandidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)

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
