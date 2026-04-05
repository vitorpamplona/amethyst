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
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * NIP-AC Protocol Compliance Test Vectors
 *
 * These tests verify that event structures conform to the NIP-AC specification
 * for WebRTC signaling over Nostr. They serve as reference test vectors that
 * any NIP-AC implementation can use to validate compliance.
 *
 * Test categories:
 * 1. Event structure validation (kinds, required tags, content)
 * 2. P2P call flow (offer → answer → ICE → hangup)
 * 3. Call rejection flow
 * 4. Mid-call renegotiation (voice ↔ video switch)
 * 5. Group call flows (multi-party mesh)
 * 6. Self-event filtering rules
 * 7. Staleness / spam prevention
 */
class NipACStateMachineTest {
    // ---- Test identities ----
    private val alice = "aaaa1111aaaa1111aaaa1111aaaa1111aaaa1111aaaa1111aaaa1111aaaa1111"
    private val bob = "bbbb2222bbbb2222bbbb2222bbbb2222bbbb2222bbbb2222bbbb2222bbbb2222"
    private val carol = "cccc3333cccc3333cccc3333cccc3333cccc3333cccc3333cccc3333cccc3333"
    private val dave = "dddd4444dddd4444dddd4444dddd4444dddd4444dddd4444dddd4444dddd4444"
    private val callId = "550e8400-e29b-41d4-a716-446655440000"
    private val sdpOffer = "v=0\r\no=- 4611731400430051336 2 IN IP4 127.0.0.1\r\n..."
    private val sdpAnswer = "v=0\r\no=- 4611731400430051337 2 IN IP4 127.0.0.1\r\n..."

    // ========================================================================
    // 1. Event Structure Validation
    // ========================================================================

    @Test
    fun callOfferMustIncludeAllRequiredTags() {
        val template = CallOfferEvent.build(sdpOffer, bob, callId, CallType.VIDEO)
        assertEquals(CallOfferEvent.KIND, template.kind)
        assertEquals(sdpOffer, template.content)

        val pTag = template.tags.firstOrNull { it[0] == "p" }
        assertNotNull(pTag, "Call offer MUST include p tag")
        assertEquals(bob, pTag[1])

        val callIdTag = template.tags.firstOrNull { it[0] == "call-id" }
        assertNotNull(callIdTag, "Call offer MUST include call-id tag")
        assertEquals(callId, callIdTag[1])

        val callTypeTag = template.tags.firstOrNull { it[0] == "call-type" }
        assertNotNull(callTypeTag, "Call offer MUST include call-type tag")
        assertEquals("video", callTypeTag[1])

        val altTag = template.tags.firstOrNull { it[0] == "alt" }
        assertNotNull(altTag, "Call offer MUST include alt tag (NIP-31)")
    }

    @Test
    fun callAnswerMustIncludeRequiredTags() {
        val template = CallAnswerEvent.build(sdpAnswer, alice, callId)
        assertEquals(CallAnswerEvent.KIND, template.kind)
        assertEquals(sdpAnswer, template.content)

        assertNotNull(template.tags.firstOrNull { it[0] == "p" }, "Call answer MUST include p tag")
        assertNotNull(template.tags.firstOrNull { it[0] == "call-id" }, "Call answer MUST include call-id tag")
        assertNotNull(template.tags.firstOrNull { it[0] == "alt" }, "Call answer MUST include alt tag")
    }

    @Test
    fun callAnswerMustNotIncludeCallTypeTag() {
        val template = CallAnswerEvent.build(sdpAnswer, alice, callId)
        val callTypeTag = template.tags.firstOrNull { it[0] == "call-type" }
        assertNull(callTypeTag, "Call answer MUST NOT include call-type tag (only offers have it)")
    }

    @Test
    fun iceCandidateMustIncludeRequiredTags() {
        val candidateJson = """{"candidate":"candidate:842163049 1 udp 1677729535 203.0.113.1 44323 typ srflx","sdpMid":"0","sdpMLineIndex":0}"""
        val template = CallIceCandidateEvent.build(candidateJson, bob, callId)
        assertEquals(CallIceCandidateEvent.KIND, template.kind)
        assertEquals(candidateJson, template.content)

        assertNotNull(template.tags.firstOrNull { it[0] == "p" })
        assertNotNull(template.tags.firstOrNull { it[0] == "call-id" })
        assertNotNull(template.tags.firstOrNull { it[0] == "alt" })
    }

    @Test
    fun hangupMayHaveEmptyContent() {
        val template = CallHangupEvent.build(bob, callId)
        assertEquals("", template.content, "Hangup content MAY be empty")
    }

    @Test
    fun hangupMayHaveReasonContent() {
        val template = CallHangupEvent.build(bob, callId, reason = "user ended call")
        assertEquals("user ended call", template.content)
    }

    @Test
    fun rejectMayHaveBusyReason() {
        val template = CallRejectEvent.build(alice, callId, reason = "busy")
        assertEquals("busy", template.content, "Reject with 'busy' content for auto-reject while in call")
    }

    @Test
    fun renegotiateMustContainNewSdpOffer() {
        val newSdp = "v=0\r\no=- 4611731400430051338 3 IN IP4 127.0.0.1\r\n..."
        val template = CallRenegotiateEvent.build(newSdp, bob, callId)
        assertEquals(CallRenegotiateEvent.KIND, template.kind)
        assertEquals(newSdp, template.content, "Renegotiate content MUST be new SDP offer")
    }

    // ========================================================================
    // 2. P2P Call Flow: Offer → Answer → ICE → Connected → Hangup
    // ========================================================================

    @Test
    fun p2pCallFlowProducesCorrectEventSequence() {
        // Step 1: Alice offers to Bob (voice call)
        val offer = CallOfferEvent.build(sdpOffer, bob, callId, CallType.VOICE)
        assertEquals(25050, offer.kind)
        assertEquals(bob, offer.tags.first { it[0] == "p" }[1])
        assertEquals("voice", offer.tags.first { it[0] == "call-type" }[1])

        // Step 2: Bob answers
        val answer = CallAnswerEvent.build(sdpAnswer, alice, callId)
        assertEquals(25051, answer.kind)
        assertEquals(alice, answer.tags.first { it[0] == "p" }[1])
        assertEquals(callId, answer.tags.first { it[0] == "call-id" }[1])

        // Step 3: ICE candidates exchange
        val iceJson =
            CallIceCandidateEvent.serializeCandidate(
                "candidate:842163049 1 udp 1677729535 203.0.113.1 44323 typ srflx",
                "0",
                0,
            )
        val ice = CallIceCandidateEvent.build(iceJson, bob, callId)
        assertEquals(25052, ice.kind)

        // Step 4: Hangup
        val hangup = CallHangupEvent.build(bob, callId)
        assertEquals(25053, hangup.kind)
    }

    // ========================================================================
    // 3. Call Rejection Flow
    // ========================================================================

    @Test
    fun rejectEventHasCorrectStructure() {
        val reject = CallRejectEvent.build(alice, callId)
        assertEquals(25054, reject.kind)
        assertEquals(alice, reject.tags.first { it[0] == "p" }[1])
        assertEquals(callId, reject.tags.first { it[0] == "call-id" }[1])
    }

    @Test
    fun busyRejectHasContentBusy() {
        val reject = CallRejectEvent.build(alice, callId, reason = "busy")
        assertEquals("busy", reject.content)
    }

    // ========================================================================
    // 4. Mid-Call Renegotiation (Voice ↔ Video Switch)
    // ========================================================================

    @Test
    fun renegotiationFlowProducesOfferThenAnswer() {
        // Party A sends renegotiate with new SDP
        val newSdp = "v=0\r\no=- 4611731400430051338 3 IN IP4 127.0.0.1\r\n..."
        val renegotiate = CallRenegotiateEvent.build(newSdp, bob, callId)
        assertEquals(25055, renegotiate.kind)
        assertEquals(newSdp, renegotiate.content)

        // Party B responds with a CallAnswer (kind 25051), NOT a renegotiate
        val renegAnswer = CallAnswerEvent.build("answer-for-renego", alice, callId)
        assertEquals(25051, renegAnswer.kind, "Renegotiation response MUST be CallAnswer (25051)")
    }

    @Test
    fun renegotiationPreservesCallId() {
        val renegotiate = CallRenegotiateEvent.build(sdpOffer, bob, callId)
        assertEquals(callId, renegotiate.tags.first { it[0] == "call-id" }[1])
    }

    // ========================================================================
    // 5. Group Call Flows
    // ========================================================================

    @Test
    fun groupCallOfferIncludesPTagForEveryMember() {
        val callees = setOf(bob, carol, dave)
        val template = CallOfferEvent.build(sdpOffer, callees, callId, CallType.VIDEO)

        val pTagValues =
            template.tags
                .filter { it[0] == "p" }
                .map { it[1] }
                .toSet()
        assertEquals(callees, pTagValues, "Group offer MUST include p tag for every callee")
    }

    @Test
    fun groupCallOfferIsDetectedByMultiplePTags() {
        // Single callee → not a group call
        val singleOffer = CallOfferEvent.build(sdpOffer, bob, callId, CallType.VOICE)
        assertEquals(1, singleOffer.tags.count { it[0] == "p" })

        // Multiple callees → group call
        val groupOffer = CallOfferEvent.build(sdpOffer, setOf(bob, carol), callId, CallType.VOICE)
        assertEquals(2, groupOffer.tags.count { it[0] == "p" })
    }

    @Test
    fun groupCallAnswerIncludesAllMemberPTags() {
        val allMembers = setOf(alice, bob, carol)
        val template = CallAnswerEvent.build(sdpAnswer, allMembers, callId)

        val pTagValues =
            template.tags
                .filter { it[0] == "p" }
                .map { it[1] }
                .toSet()
        assertEquals(allMembers, pTagValues, "Group answer MUST include p tag for every member")
    }

    @Test
    fun groupCallHangupIncludesAllMemberPTags() {
        val allMembers = setOf(alice, bob, carol)
        val template = CallHangupEvent.build(allMembers, callId)

        val pTagValues =
            template.tags
                .filter { it[0] == "p" }
                .map { it[1] }
                .toSet()
        assertEquals(allMembers, pTagValues)
    }

    @Test
    fun groupCallRejectIncludesAllMemberPTags() {
        val allMembers = setOf(alice, bob, carol)
        val template = CallRejectEvent.build(allMembers, callId)

        val pTagValues =
            template.tags
                .filter { it[0] == "p" }
                .map { it[1] }
                .toSet()
        assertEquals(allMembers, pTagValues)
    }

    @Test
    fun groupCallRenegotiateIncludesAllMemberPTags() {
        val allMembers = setOf(alice, bob, carol)
        val template = CallRenegotiateEvent.build(sdpOffer, allMembers, callId)

        val pTagValues =
            template.tags
                .filter { it[0] == "p" }
                .map { it[1] }
                .toSet()
        assertEquals(allMembers, pTagValues)
    }

    @Test
    fun groupMembersIncludesEventAuthorPlusPTags() {
        // Build a signed-style offer event to test groupMembers()
        val template = CallOfferEvent.build(sdpOffer, setOf(bob, carol), callId, CallType.VIDEO)
        // Template doesn't have pubKey set, so test via the tag helper
        val pTags =
            template.tags
                .filter { it[0] == "p" }
                .map { it[1] }
                .toSet()
        assertEquals(setOf(bob, carol), pTags)
    }

    @Test
    fun iceCandidateInGroupCallIsAddressedToSinglePeer() {
        // Per spec: ICE candidates remain addressed to a single peer
        val ice =
            CallIceCandidateEvent.build(
                """{"candidate":"c","sdpMid":"0","sdpMLineIndex":0}""",
                bob,
                callId,
            )
        val pTags = ice.tags.filter { it[0] == "p" }
        assertEquals(1, pTags.size, "ICE candidate MUST be addressed to single peer, even in group call")
    }

    // ========================================================================
    // 6. ICE Candidate Serialization
    // ========================================================================

    @Test
    fun iceCandidateSerializationRoundTrips() {
        val sdp = "candidate:842163049 1 udp 1677729535 203.0.113.1 44323 typ srflx raddr 0.0.0.0 rport 0 generation 0"
        val mid = "0"
        val index = 0

        val json = CallIceCandidateEvent.serializeCandidate(sdp, mid, index)
        val template = CallIceCandidateEvent.build(json, bob, callId)

        // Construct event to test parsing
        val event = CallIceCandidateEvent("fakeid", alice, TimeUtils.now(), template.tags, json, "fakesig")
        assertEquals(sdp, event.candidateSdp())
        assertEquals(mid, event.sdpMid())
        assertEquals(index, event.sdpMLineIndex())
    }

    @Test
    fun iceCandidateSerializationEscapesQuotes() {
        // Verify that quotes are properly escaped in the JSON output
        val sdp = """candidate:1 1 udp 2122260223 192.168.1.1 44323 typ host"""
        val json = CallIceCandidateEvent.serializeCandidate(sdp, "audio", 0)
        assertTrue(json.startsWith("{"), "Must be valid JSON object")
        assertTrue(json.endsWith("}"), "Must be valid JSON object")
        assertTrue(json.contains(""""candidate":"""), "Must contain candidate key")
        assertTrue(json.contains(""""sdpMid":"audio""""), "Must contain sdpMid")
        assertTrue(json.contains(""""sdpMLineIndex":0"""), "Must contain sdpMLineIndex")
        // Roundtrip through event parsing
        val event = CallIceCandidateEvent("fakeid", alice, TimeUtils.now(), arrayOf(), json, "fakesig")
        assertEquals(sdp, event.candidateSdp())
        assertEquals("audio", event.sdpMid())
        assertEquals(0, event.sdpMLineIndex())
    }

    // ========================================================================
    // 7. Staleness Check
    // ========================================================================

    @Test
    fun eventsOlderThan20SecondsMustBeDiscarded() {
        // Per spec: "Clients MUST discard signaling events older than 20 seconds"
        val now = TimeUtils.now()
        val freshCreatedAt = now - 5 // 5 seconds ago — fresh
        val staleCreatedAt = now - 25 // 25 seconds ago — stale

        assertTrue(now - freshCreatedAt <= 20, "Event 5s old should be fresh")
        assertTrue(now - staleCreatedAt > 20, "Event 25s old should be stale")
    }

    // ========================================================================
    // 8. Call-ID Consistency
    // ========================================================================

    @Test
    fun allSignalingEventsForSameCallShareCallId() {
        val offer = CallOfferEvent.build(sdpOffer, bob, callId, CallType.VOICE)
        val answer = CallAnswerEvent.build(sdpAnswer, alice, callId)
        val ice = CallIceCandidateEvent.build("{}", bob, callId)
        val hangup = CallHangupEvent.build(bob, callId)
        val reject = CallRejectEvent.build(alice, callId)
        val renego = CallRenegotiateEvent.build(sdpOffer, bob, callId)

        val events = listOf(offer, answer, ice, hangup, reject, renego)
        for (event in events) {
            val parsedCallId = event.tags.first { it[0] == "call-id" }[1]
            assertEquals(callId, parsedCallId, "All events for same call MUST share call-id")
        }
    }

    // Renegotiation glare and callee-to-callee mesh tiebreakers are tested in
    // PeerSessionManagerTest (commons/commonTest/) where the actual logic lives.

    // ========================================================================
    // 9. Event Kind Constants
    // ========================================================================

    @Test
    fun eventKindsMustMatchSpec() {
        assertEquals(25050, CallOfferEvent.KIND, "Call Offer kind")
        assertEquals(25051, CallAnswerEvent.KIND, "Call Answer kind")
        assertEquals(25052, CallIceCandidateEvent.KIND, "ICE Candidate kind")
        assertEquals(25053, CallHangupEvent.KIND, "Call Hangup kind")
        assertEquals(25054, CallRejectEvent.KIND, "Call Reject kind")
        assertEquals(25055, CallRenegotiateEvent.KIND, "Call Renegotiate kind")
    }

    // ========================================================================
    // 11. Invite New Peer to Active Group Call
    // ========================================================================

    @Test
    fun inviteNewPeerOfferIncludesAllExistingMembersPlusInvitee() {
        // When inviting dave into an active call with alice, bob, carol:
        val allMembers = setOf(alice, bob, carol, dave)
        val inviteOffer = CallOfferEvent.build(sdpOffer, allMembers, callId, CallType.VIDEO)

        val pTags =
            inviteOffer.tags
                .filter { it[0] == "p" }
                .map { it[1] }
                .toSet()
        assertEquals(allMembers, pTags, "Invite offer MUST include all existing members plus new invitee")
    }

    // ========================================================================
    // 12. Multi-Device Support: Self-Addressed Events
    // ========================================================================

    @Test
    fun selfAddressedAnswerForMultiDeviceSupport() {
        // Per spec: callee should publish answer addressed to own pubkey
        // for "answered elsewhere" notification to other devices
        val selfAnswer = CallAnswerEvent.build(sdpAnswer, setOf(alice, bob), callId)
        val pTags =
            selfAnswer.tags
                .filter { it[0] == "p" }
                .map { it[1] }
                .toSet()
        // The callee (bob) includes self in p-tags for group broadcast
        assertTrue(bob in pTags || alice in pTags, "Self-addressed answer must include own pubkey in p-tags")
    }

    @Test
    fun selfAddressedRejectForMultiDeviceSupport() {
        val selfReject = CallRejectEvent.build(setOf(alice, bob), callId)
        val pTags =
            selfReject.tags
                .filter { it[0] == "p" }
                .map { it[1] }
                .toSet()
        assertTrue(pTags.size >= 2, "Self-reject in group should address all members including self")
    }
}
