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

import com.vitorpamplona.quartz.utils.Log
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

private const val TAG = "WebRtcCallSession"

/**
 * Lightweight wrapper around a single [PeerConnection].
 *
 * The caller is responsible for creating and managing the shared
 * [PeerConnectionFactory], media sources, tracks, and camera.  This class
 * only owns the [PeerConnection] itself and delegates lifecycle callbacks
 * through the constructor lambdas.
 */
class WebRtcCallSession(
    private val peerConnectionFactory: PeerConnectionFactory,
    private val iceServers: List<PeerConnection.IceServer>,
    private val onIceCandidate: (IceCandidate) -> Unit,
    private val onPeerConnected: () -> Unit,
    private val onRemoteVideoTrack: (VideoTrack) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onError: (String) -> Unit = {},
    private val onRenegotiationNeeded: () -> Unit = {},
) {
    private var peerConnection: PeerConnection? = null

    fun createPeerConnection() {
        val rtcConfig =
            PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

        peerConnection =
            peerConnectionFactory.createPeerConnection(
                rtcConfig,
                object : PeerConnection.Observer {
                    override fun onIceCandidate(candidate: IceCandidate?) {
                        candidate?.let { this@WebRtcCallSession.onIceCandidate(it) }
                    }

                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

                    override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                        Log.d(TAG) { "Signaling state changed: $state" }
                    }

                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                        Log.d(TAG) { "ICE connection state: $state" }
                        when (state) {
                            PeerConnection.IceConnectionState.NEW -> {
                                Log.d(TAG) { "ICE: NEW - waiting for candidates" }
                            }

                            PeerConnection.IceConnectionState.CHECKING -> {
                                Log.d(TAG) { "ICE: CHECKING - testing candidate pairs" }
                            }

                            PeerConnection.IceConnectionState.CONNECTED -> {
                                Log.d(TAG) { "ICE: CONNECTED - peer connection established!" }
                                onPeerConnected()
                            }

                            PeerConnection.IceConnectionState.COMPLETED -> {
                                Log.d(TAG) { "ICE: COMPLETED - all candidate pairs tested, connection stable" }
                            }

                            PeerConnection.IceConnectionState.FAILED -> {
                                Log.e(TAG, "ICE: FAILED - could not establish connection")
                                onError("Connection failed - check network")
                                onDisconnected()
                            }

                            PeerConnection.IceConnectionState.DISCONNECTED -> {
                                Log.d(TAG) { "ICE: DISCONNECTED (transient, waiting for recovery or FAILED)" }
                            }

                            PeerConnection.IceConnectionState.CLOSED -> {
                                Log.d(TAG) { "ICE: CLOSED" }
                            }

                            else -> {}
                        }
                    }

                    override fun onIceConnectionReceivingChange(receiving: Boolean) {
                        Log.d(TAG) { "ICE receiving change: $receiving" }
                    }

                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                        Log.d(TAG) { "ICE gathering state: $state" }
                    }

                    override fun onAddStream(stream: MediaStream?) {
                        stream?.videoTracks?.firstOrNull()?.let { onRemoteVideoTrack(it) }
                    }

                    override fun onRemoveStream(stream: MediaStream?) {}

                    override fun onDataChannel(channel: DataChannel?) {}

                    override fun onRenegotiationNeeded() {
                        Log.d(TAG) { "Renegotiation needed" }
                        this@WebRtcCallSession.onRenegotiationNeeded()
                    }

                    override fun onAddTrack(
                        receiver: RtpReceiver?,
                        streams: Array<out MediaStream>?,
                    ) {
                        val track = receiver?.track()
                        if (track is VideoTrack) {
                            onRemoteVideoTrack(track)
                        }
                    }
                },
            )
    }

    /**
     * Adds an existing track to this PeerConnection.
     *
     * @param maxBitrateBps optional per-sender bitrate cap (e.g. 1_500_000 for 720p video)
     */
    fun addTrack(
        track: MediaStreamTrack,
        maxBitrateBps: Int? = null,
    ): RtpSender? {
        val sender = peerConnection?.addTrack(track) ?: return null
        if (maxBitrateBps != null) {
            val params = sender.parameters
            params.encodings.forEach { encoding ->
                encoding.maxBitrateBps = maxBitrateBps
            }
            sender.parameters = params
        }
        return sender
    }

    fun createOffer(onSdpCreated: (SessionDescription) -> Unit) {
        val constraints =
            MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }

        peerConnection?.createOffer(
            object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    sdp?.let {
                        Log.d(TAG) { "Offer created, sdpLength=${it.description.length}, setting local description" }
                        peerConnection?.setLocalDescription(loggingSdpObserver("setLocalDescription(OFFER)"), it)
                        onSdpCreated(it)
                    }
                }

                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "Create offer failed: $error")
                    error?.let { onError("Create offer failed: $it") }
                }

                override fun onSetSuccess() {}

                override fun onSetFailure(error: String?) {}
            },
            constraints,
        )
    }

    fun createAnswer(onSdpCreated: (SessionDescription) -> Unit) {
        val constraints =
            MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }

        peerConnection?.createAnswer(
            object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    sdp?.let {
                        Log.d(TAG) { "Answer created, sdpLength=${it.description.length}, setting local description" }
                        peerConnection?.setLocalDescription(loggingSdpObserver("setLocalDescription(ANSWER)"), it)
                        onSdpCreated(it)
                    }
                }

                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "Create answer failed: $error")
                    error?.let { onError("Create answer failed: $it") }
                }

                override fun onSetSuccess() {}

                override fun onSetFailure(error: String?) {}
            },
            constraints,
        )
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        Log.d(TAG) { "setRemoteDescription type=${sdp.type} sdpLength=${sdp.description.length}" }
        peerConnection?.setRemoteDescription(
            object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {}

                override fun onCreateFailure(error: String?) {}

                override fun onSetSuccess() {
                    Log.d(TAG) { "setRemoteDescription SUCCESS (type=${sdp.type})" }
                }

                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "setRemoteDescription FAILED: $error (type=${sdp.type})")
                    error?.let { onError("SDP error: $it") }
                }
            },
            sdp,
        )
    }

    fun addIceCandidate(candidate: IceCandidate) {
        val added = peerConnection?.addIceCandidate(candidate)
        Log.d(TAG) { "addIceCandidate result=$added sdpMid=${candidate.sdpMid} sdp=${candidate.sdp.take(60)}" }
    }

    fun getSignalingState(): PeerConnection.SignalingState? = peerConnection?.signalingState()

    fun dispose() {
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
    }

    private fun loggingSdpObserver(label: String) =
        object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                Log.d(TAG) { "$label onCreateSuccess" }
            }

            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "$label onCreateFailure: $error")
                error?.let { onError("SDP error: $it") }
            }

            override fun onSetSuccess() {
                Log.d(TAG) { "$label onSetSuccess" }
            }

            override fun onSetFailure(error: String?) {
                Log.e(TAG, "$label onSetFailure: $error")
                error?.let { onError("SDP error: $it") }
            }
        }
}
