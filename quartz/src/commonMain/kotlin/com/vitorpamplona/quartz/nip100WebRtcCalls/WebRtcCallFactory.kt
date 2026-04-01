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
package com.vitorpamplona.quartz.nip100WebRtcCalls

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip100WebRtcCalls.events.CallAnswerEvent
import com.vitorpamplona.quartz.nip100WebRtcCalls.events.CallHangupEvent
import com.vitorpamplona.quartz.nip100WebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nip100WebRtcCalls.events.CallOfferEvent
import com.vitorpamplona.quartz.nip100WebRtcCalls.events.CallRejectEvent
import com.vitorpamplona.quartz.nip100WebRtcCalls.events.CallRenegotiateEvent
import com.vitorpamplona.quartz.nip100WebRtcCalls.tags.CallType
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

class WebRtcCallFactory {
    data class Result(
        val msg: Event,
        val wrap: GiftWrapEvent,
    )

    suspend fun createCallOffer(
        sdpOffer: String,
        calleePubKey: HexKey,
        callId: String,
        callType: CallType,
        signer: NostrSigner,
    ): Result {
        val template = CallOfferEvent.build(sdpOffer, calleePubKey, callId, callType)
        val signed = signer.sign(template)
        val wrap = GiftWrapEvent.create(event = signed, recipientPubKey = calleePubKey)
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
        val wrap = GiftWrapEvent.create(event = signed, recipientPubKey = callerPubKey)
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
        val wrap = GiftWrapEvent.create(event = signed, recipientPubKey = peerPubKey)
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
        val wrap = GiftWrapEvent.create(event = signed, recipientPubKey = peerPubKey)
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
        val wrap = GiftWrapEvent.create(event = signed, recipientPubKey = callerPubKey)
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
        val wrap = GiftWrapEvent.create(event = signed, recipientPubKey = peerPubKey)
        return Result(signed, wrap)
    }
}
