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

import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallAnswerEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallHangupEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallOfferEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRejectEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRenegotiateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
import kotlin.test.Test
import kotlin.test.assertEquals

class CallEventsTest {
    @Test
    fun callOfferEventHasCorrectKind() {
        assertEquals(25050, CallOfferEvent.KIND)
    }

    @Test
    fun callAnswerEventHasCorrectKind() {
        assertEquals(25051, CallAnswerEvent.KIND)
    }

    @Test
    fun callIceCandidateEventHasCorrectKind() {
        assertEquals(25052, CallIceCandidateEvent.KIND)
    }

    @Test
    fun callHangupEventHasCorrectKind() {
        assertEquals(25053, CallHangupEvent.KIND)
    }

    @Test
    fun callRejectEventHasCorrectKind() {
        assertEquals(25054, CallRejectEvent.KIND)
    }

    @Test
    fun callRenegotiateEventHasCorrectKind() {
        assertEquals(25055, CallRenegotiateEvent.KIND)
    }

    @Test
    fun callOfferBuildIncludesCallIdTag() {
        val template =
            CallOfferEvent.build(
                sdpOffer = "v=0\r\n...",
                calleePubKey = "abc123",
                callId = "test-call-id",
                type = CallType.VOICE,
            )
        assertEquals(CallOfferEvent.KIND, template.kind)
        assertEquals("v=0\r\n...", template.content)

        val callIdTag = template.tags.firstOrNull { it[0] == "call-id" }
        assertEquals("test-call-id", callIdTag?.get(1))
    }

    @Test
    fun callOfferBuildIncludesCallTypeTag() {
        val template =
            CallOfferEvent.build(
                sdpOffer = "sdp",
                calleePubKey = "abc123",
                callId = "id",
                type = CallType.VIDEO,
            )
        val callTypeTag = template.tags.firstOrNull { it[0] == "call-type" }
        assertEquals("video", callTypeTag?.get(1))
    }

    @Test
    fun callOfferBuildIncludesPTag() {
        val template =
            CallOfferEvent.build(
                sdpOffer = "sdp",
                calleePubKey = "recipient-hex-key",
                callId = "id",
                type = CallType.VOICE,
            )
        val pTag = template.tags.firstOrNull { it[0] == "p" }
        assertEquals("recipient-hex-key", pTag?.get(1))
    }

    @Test
    fun callOfferBuildIncludesExpirationTag() {
        val template =
            CallOfferEvent.build(
                sdpOffer = "sdp",
                calleePubKey = "abc",
                callId = "id",
                type = CallType.VOICE,
                createdAt = 1000L,
            )
        val expirationTag = template.tags.firstOrNull { it[0] == "expiration" }
        assertEquals((1000L + CallOfferEvent.EXPIRATION_SECONDS).toString(), expirationTag?.get(1))
    }

    @Test
    fun callOfferExpirationIs20Seconds() {
        assertEquals(20L, CallOfferEvent.EXPIRATION_SECONDS)
    }

    @Test
    fun callAnswerBuildContainsSdp() {
        val template =
            CallAnswerEvent.build(
                sdpAnswer = "answer-sdp",
                callerPubKey = "caller",
                callId = "call-1",
            )
        assertEquals("answer-sdp", template.content)
        assertEquals(CallAnswerEvent.KIND, template.kind)
    }

    @Test
    fun callIceCandidateBuildContainsJson() {
        val candidateJson = """{"candidate":"candidate:1","sdpMid":"0","sdpMLineIndex":0}"""
        val template =
            CallIceCandidateEvent.build(
                candidateJson = candidateJson,
                peerPubKey = "peer",
                callId = "call-1",
            )
        assertEquals(candidateJson, template.content)
    }

    @Test
    fun callHangupBuildAllowsEmptyReason() {
        val template =
            CallHangupEvent.build(
                peerPubKey = "peer",
                callId = "call-1",
            )
        assertEquals("", template.content)
    }

    @Test
    fun callHangupBuildAllowsCustomReason() {
        val template =
            CallHangupEvent.build(
                peerPubKey = "peer",
                callId = "call-1",
                reason = "busy",
            )
        assertEquals("busy", template.content)
    }

    @Test
    fun callRejectBuildHasCorrectKind() {
        val template =
            CallRejectEvent.build(
                callerPubKey = "caller",
                callId = "call-1",
            )
        assertEquals(CallRejectEvent.KIND, template.kind)
    }

    @Test
    fun callRenegotiateBuildContainsSdp() {
        val template =
            CallRenegotiateEvent.build(
                sdpOffer = "new-sdp-offer",
                peerPubKey = "peer",
                callId = "call-1",
            )
        assertEquals("new-sdp-offer", template.content)
        assertEquals(CallRenegotiateEvent.KIND, template.kind)
    }

    // ---- Group call offer tests ----

    @Test
    fun groupCallOfferBuildIncludesAllPTags() {
        val callees = setOf("alice", "bob", "carol")
        val template =
            CallOfferEvent.build(
                sdpOffer = "group-sdp",
                calleePubKeys = callees,
                callId = "group-call-1",
                type = CallType.VIDEO,
            )
        val pTagValues =
            template.tags
                .filter { it[0] == "p" }
                .map { it[1] }
                .toSet()
        assertEquals(callees, pTagValues)
    }

    @Test
    fun groupCallOfferBuildIncludesCallIdTag() {
        val template =
            CallOfferEvent.build(
                sdpOffer = "sdp",
                calleePubKeys = setOf("alice", "bob"),
                callId = "group-call-id",
                type = CallType.VOICE,
            )
        val callIdTag = template.tags.firstOrNull { it[0] == "call-id" }
        assertEquals("group-call-id", callIdTag?.get(1))
    }

    @Test
    fun groupCallOfferBuildIncludesCallTypeTag() {
        val template =
            CallOfferEvent.build(
                sdpOffer = "sdp",
                calleePubKeys = setOf("alice", "bob"),
                callId = "id",
                type = CallType.VIDEO,
            )
        val callTypeTag = template.tags.firstOrNull { it[0] == "call-type" }
        assertEquals("video", callTypeTag?.get(1))
    }

    @Test
    fun groupCallOfferBuildIncludesExpirationTag() {
        val template =
            CallOfferEvent.build(
                sdpOffer = "sdp",
                calleePubKeys = setOf("alice", "bob"),
                callId = "id",
                type = CallType.VOICE,
                createdAt = 2000L,
            )
        val expirationTag = template.tags.firstOrNull { it[0] == "expiration" }
        assertEquals((2000L + CallOfferEvent.EXPIRATION_SECONDS).toString(), expirationTag?.get(1))
    }

    @Test
    fun groupCallAnswerBuildIncludesAllPTags() {
        val members = setOf("alice", "bob", "carol")
        val template =
            CallAnswerEvent.build(
                sdpAnswer = "answer-sdp",
                memberPubKeys = members,
                callId = "group-call-1",
            )
        val pTagValues =
            template.tags
                .filter { it[0] == "p" }
                .map { it[1] }
                .toSet()
        assertEquals(members, pTagValues)
    }

    @Test
    fun groupCallHangupBuildIncludesAllPTags() {
        val members = setOf("alice", "bob", "carol")
        val template =
            CallHangupEvent.build(
                memberPubKeys = members,
                callId = "group-call-1",
            )
        val pTagValues =
            template.tags
                .filter { it[0] == "p" }
                .map { it[1] }
                .toSet()
        assertEquals(members, pTagValues)
    }

    @Test
    fun groupCallRejectBuildIncludesAllPTags() {
        val members = setOf("alice", "bob", "carol")
        val template =
            CallRejectEvent.build(
                memberPubKeys = members,
                callId = "group-call-1",
            )
        val pTagValues =
            template.tags
                .filter { it[0] == "p" }
                .map { it[1] }
                .toSet()
        assertEquals(members, pTagValues)
    }

    @Test
    fun groupCallRenegotiateBuildIncludesAllPTags() {
        val members = setOf("alice", "bob", "carol")
        val template =
            CallRenegotiateEvent.build(
                sdpOffer = "new-sdp-offer",
                memberPubKeys = members,
                callId = "group-call-1",
            )
        val pTagValues =
            template.tags
                .filter { it[0] == "p" }
                .map { it[1] }
                .toSet()
        assertEquals(members, pTagValues)
    }

    @Test
    fun singleCalleeOfferIsNotGroupCall() {
        val template =
            CallOfferEvent.build(
                sdpOffer = "sdp",
                calleePubKey = "alice",
                callId = "id",
                type = CallType.VOICE,
            )
        val pTags = template.tags.filter { it[0] == "p" }
        assertEquals(1, pTags.size)
    }
}
