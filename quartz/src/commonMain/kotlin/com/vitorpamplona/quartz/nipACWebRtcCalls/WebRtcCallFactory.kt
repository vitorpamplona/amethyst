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
package com.vitorpamplona.quartz.nipACWebRtcCalls

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallAnswerEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallHangupEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallOfferEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRejectEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRenegotiateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType

class WebRtcCallFactory {
    /** Result for a single-recipient signaling message (P2P). */
    data class Result(
        val msg: Event,
        val wrap: GiftWrapEvent,
    )

    /** Result for a signaling message gift-wrapped to multiple recipients (group calls). */
    data class GroupResult(
        val msg: Event,
        val wraps: List<GiftWrapEvent>,
    )

    companion object {
        const val WRAP_EXPIRATION_SECONDS = 20L
    }

    // ---- P2P (single recipient) methods ----

    suspend fun createCallOffer(
        sdpOffer: String,
        calleePubKey: HexKey,
        callId: String,
        callType: CallType,
        signer: NostrSigner,
    ): Result {
        val template = CallOfferEvent.build(sdpOffer, calleePubKey, callId, callType)
        val signed = signer.sign(template)
        val wrap = GiftWrapEvent.create(event = signed, recipientPubKey = calleePubKey, expirationDelta = WRAP_EXPIRATION_SECONDS)
        return Result(signed, wrap)
    }

    suspend fun createCallAnswer(
        sdpAnswer: String,
        callerPubKey: HexKey,
        callId: String,
        signer: NostrSigner,
    ): Result {
        val template = CallAnswerEvent.build(sdpAnswer, callerPubKey, callId)
        val signed = signer.sign(template)
        val wrap = GiftWrapEvent.create(event = signed, recipientPubKey = callerPubKey, expirationDelta = WRAP_EXPIRATION_SECONDS)
        return Result(signed, wrap)
    }

    suspend fun createIceCandidate(
        candidateJson: String,
        peerPubKey: HexKey,
        callId: String,
        signer: NostrSigner,
    ): Result {
        val template = CallIceCandidateEvent.build(candidateJson, peerPubKey, callId)
        val signed = signer.sign(template)
        val wrap = GiftWrapEvent.create(event = signed, recipientPubKey = peerPubKey, expirationDelta = WRAP_EXPIRATION_SECONDS)
        return Result(signed, wrap)
    }

    suspend fun createHangup(
        peerPubKey: HexKey,
        callId: String,
        reason: String = "",
        signer: NostrSigner,
    ): Result {
        val template = CallHangupEvent.build(peerPubKey, callId, reason)
        val signed = signer.sign(template)
        val wrap = GiftWrapEvent.create(event = signed, recipientPubKey = peerPubKey, expirationDelta = WRAP_EXPIRATION_SECONDS)
        return Result(signed, wrap)
    }

    suspend fun createReject(
        callerPubKey: HexKey,
        callId: String,
        reason: String = "",
        signer: NostrSigner,
    ): Result {
        val template = CallRejectEvent.build(callerPubKey, callId, reason)
        val signed = signer.sign(template)
        val wrap = GiftWrapEvent.create(event = signed, recipientPubKey = callerPubKey, expirationDelta = WRAP_EXPIRATION_SECONDS)
        return Result(signed, wrap)
    }

    suspend fun createRenegotiate(
        sdpOffer: String,
        peerPubKey: HexKey,
        callId: String,
        signer: NostrSigner,
    ): Result {
        val template = CallRenegotiateEvent.build(sdpOffer, peerPubKey, callId)
        val signed = signer.sign(template)
        val wrap = GiftWrapEvent.create(event = signed, recipientPubKey = peerPubKey, expirationDelta = WRAP_EXPIRATION_SECONDS)
        return Result(signed, wrap)
    }

    // ---- Group call methods (multiple recipients) ----

    /**
     * Creates a call offer for a group call.  The signed inner event contains
     * `p` tags for **every** callee so each recipient knows the full group.
     * A separate [GiftWrapEvent] is produced for each callee.
     */
    suspend fun createGroupCallOffer(
        sdpOffer: String,
        calleePubKeys: Set<HexKey>,
        callId: String,
        callType: CallType,
        signer: NostrSigner,
    ): GroupResult {
        val template = CallOfferEvent.build(sdpOffer, calleePubKeys, callId, callType)
        val signed = signer.sign(template)
        val wraps =
            calleePubKeys.map { pubKey ->
                GiftWrapEvent.create(event = signed, recipientPubKey = pubKey, expirationDelta = WRAP_EXPIRATION_SECONDS)
            }
        return GroupResult(signed, wraps)
    }

    /**
     * Sends a hangup to every peer in a group call.  Each peer receives its
     * own gift-wrapped hangup event.
     */
    suspend fun createGroupHangup(
        peerPubKeys: Set<HexKey>,
        callId: String,
        reason: String = "",
        signer: NostrSigner,
    ): GroupResult {
        // Each peer gets its own signed hangup with the correct `p` tag
        // targeting that specific recipient.
        var firstSigned: Event? = null
        val wraps =
            peerPubKeys.map { pubKey ->
                val template = CallHangupEvent.build(pubKey, callId, reason)
                val signed = signer.sign(template)
                if (firstSigned == null) firstSigned = signed
                GiftWrapEvent.create(event = signed, recipientPubKey = pubKey, expirationDelta = WRAP_EXPIRATION_SECONDS)
            }
        return GroupResult(firstSigned!!, wraps)
    }

    /**
     * Rejects a group call offer.  Sends the rejection to the caller and
     * notifies self (for multi-device support).
     */
    suspend fun createGroupReject(
        callerPubKey: HexKey,
        callId: String,
        reason: String = "",
        signer: NostrSigner,
    ): GroupResult {
        val template = CallRejectEvent.build(callerPubKey, callId, reason)
        val signed = signer.sign(template)
        val wraps =
            listOf(callerPubKey, signer.pubKey).distinct().map { pubKey ->
                GiftWrapEvent.create(event = signed, recipientPubKey = pubKey, expirationDelta = WRAP_EXPIRATION_SECONDS)
            }
        return GroupResult(signed, wraps)
    }
}
