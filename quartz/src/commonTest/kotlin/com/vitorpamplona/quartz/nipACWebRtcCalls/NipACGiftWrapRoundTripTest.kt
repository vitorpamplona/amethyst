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

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallAnswerEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallHangupEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallOfferEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRejectEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRenegotiateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * NIP-AC Gift Wrap Round-Trip Tests
 *
 * These tests verify the full encrypt/decrypt pipeline for NIP-AC signaling
 * events delivered via Ephemeral Gift Wraps (kind 21059):
 *
 *   sign inner event → gift wrap (NIP-44 encrypt) → unwrap (NIP-44 decrypt) → verify inner event
 *
 * This is the critical interoperability boundary: if two implementations can
 * successfully unwrap each other's gift-wrapped signaling events and recover
 * the correct typed inner event, they can establish calls.
 *
 * All tests use real secp256k1 keys and NIP-44 encryption — no mocks.
 */
class NipACGiftWrapRoundTripTest {
    private val aliceSigner = NostrSignerInternal(KeyPair())
    private val bobSigner = NostrSignerInternal(KeyPair())
    private val carolSigner = NostrSignerInternal(KeyPair())

    private val alice = aliceSigner.pubKey
    private val bob = bobSigner.pubKey
    private val carol = carolSigner.pubKey

    private val callId = "550e8400-e29b-41d4-a716-446655440000"
    private val sdpOffer = "v=0\r\no=- 4611731400430051336 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0"
    private val sdpAnswer = "v=0\r\no=- 4611731400430051337 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0"

    private val factory = WebRtcCallFactory()

    // ========================================================================
    // 1. Call Offer: sign → wrap → unwrap → verify
    // ========================================================================

    @Test
    fun callOfferRoundTripsThroughGiftWrap() =
        runTest {
            val result = factory.createCallOffer(sdpOffer, bob, callId, CallType.VIDEO, aliceSigner)

            // Wrap is addressed to Bob
            val wrap = result.wrap
            assertEquals(EphemeralGiftWrapEvent.KIND, wrap.kind)
            assertEquals(bob, wrap.recipientPubKey())

            // Wrap pubkey is ephemeral (NOT alice)
            assertNotEquals(alice, wrap.pubKey, "Gift wrap MUST use an ephemeral pubkey, not the sender's")

            // Bob unwraps
            val inner = wrap.unwrapThrowing(bobSigner)
            assertIs<CallOfferEvent>(inner, "Deserialized inner event must be CallOfferEvent")
            assertEquals(alice, inner.pubKey, "Inner event pubkey must be the real sender (alice)")
            assertEquals(sdpOffer, inner.sdpOffer())
            assertEquals(callId, inner.callId())
            assertEquals(CallType.VIDEO, inner.callType())
            assertEquals(bob, inner.recipientPubKeys().single())
            assertTrue(inner.verify(), "Inner event signature must be valid")
        }

    @Test
    fun callOfferCannotBeDecryptedByThirdParty() =
        runTest {
            val result = factory.createCallOffer(sdpOffer, bob, callId, CallType.VOICE, aliceSigner)

            // Carol should NOT be able to decrypt a wrap addressed to Bob
            val inner = result.wrap.unwrapOrNull(carolSigner)
            assertNull(inner, "Third party MUST NOT be able to decrypt gift wrap addressed to another user")
        }

    // ========================================================================
    // 2. Call Answer: sign → wrap → unwrap → verify
    // ========================================================================

    @Test
    fun callAnswerRoundTripsThroughGiftWrap() =
        runTest {
            val result = factory.createCallAnswer(sdpAnswer, alice, callId, bobSigner)

            val wrap = result.wrap
            assertEquals(EphemeralGiftWrapEvent.KIND, wrap.kind)
            assertEquals(alice, wrap.recipientPubKey())

            // Alice unwraps
            val inner = wrap.unwrapThrowing(aliceSigner)
            assertIs<CallAnswerEvent>(inner)
            assertEquals(bob, inner.pubKey)
            assertEquals(sdpAnswer, inner.sdpAnswer())
            assertEquals(callId, inner.callId())
            assertTrue(inner.verify())
        }

    // ========================================================================
    // 3. ICE Candidate: sign → wrap → unwrap → verify JSON content
    // ========================================================================

    @Test
    fun iceCandidateRoundTripsThroughGiftWrap() =
        runTest {
            val candidateJson =
                CallIceCandidateEvent.serializeCandidate(
                    "candidate:842163049 1 udp 1677729535 203.0.113.1 44323 typ srflx raddr 0.0.0.0 rport 0",
                    "0",
                    0,
                )
            val result = factory.createIceCandidate(candidateJson, bob, callId, aliceSigner)

            val inner = result.wrap.unwrapThrowing(bobSigner)
            assertIs<CallIceCandidateEvent>(inner)
            assertEquals(alice, inner.pubKey)
            assertEquals(callId, inner.callId())
            assertEquals(candidateJson, inner.candidateJson())
            assertEquals("0", inner.sdpMid())
            assertEquals(0, inner.sdpMLineIndex())
            assertTrue(
                inner.candidateSdp().contains("842163049"),
                "Candidate SDP must survive JSON escaping round-trip",
            )
            assertTrue(inner.verify())
        }

    // ========================================================================
    // 4. Hangup: sign → wrap → unwrap → verify
    // ========================================================================

    @Test
    fun hangupRoundTripsThroughGiftWrap() =
        runTest {
            val result = factory.createHangup(bob, callId, "user ended", aliceSigner)

            val inner = result.wrap.unwrapThrowing(bobSigner)
            assertIs<CallHangupEvent>(inner)
            assertEquals(alice, inner.pubKey)
            assertEquals(callId, inner.callId())
            assertEquals("user ended", inner.reason())
            assertTrue(inner.verify())
        }

    @Test
    fun hangupEmptyReasonRoundTrips() =
        runTest {
            val result = factory.createHangup(bob, callId, "", aliceSigner)

            val inner = result.wrap.unwrapThrowing(bobSigner)
            assertIs<CallHangupEvent>(inner)
            assertNull(inner.reason(), "Empty reason should parse as null")
        }

    // ========================================================================
    // 5. Reject: sign → wrap → unwrap → verify
    // ========================================================================

    @Test
    fun rejectRoundTripsThroughGiftWrap() =
        runTest {
            val result = factory.createReject(alice, callId, "", bobSigner)

            val inner = result.wrap.unwrapThrowing(aliceSigner)
            assertIs<CallRejectEvent>(inner)
            assertEquals(bob, inner.pubKey)
            assertEquals(callId, inner.callId())
            assertTrue(inner.verify())
        }

    @Test
    fun busyRejectRoundTripsThroughGiftWrap() =
        runTest {
            val result = factory.createReject(alice, callId, "busy", bobSigner)

            val inner = result.wrap.unwrapThrowing(aliceSigner)
            assertIs<CallRejectEvent>(inner)
            assertEquals("busy", inner.reason())
        }

    // ========================================================================
    // 6. Renegotiate: sign → wrap → unwrap → verify
    // ========================================================================

    @Test
    fun renegotiateRoundTripsThroughGiftWrap() =
        runTest {
            val newSdp = "v=0\r\no=- 4611731400430051338 3 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0"
            val result = factory.createRenegotiate(newSdp, bob, callId, aliceSigner)

            val inner = result.wrap.unwrapThrowing(bobSigner)
            assertIs<CallRenegotiateEvent>(inner)
            assertEquals(alice, inner.pubKey)
            assertEquals(newSdp, inner.sdpOffer())
            assertEquals(callId, inner.callId())
            assertTrue(inner.verify())
        }

    // ========================================================================
    // 7. Group Call: per-peer wraps only decryptable by intended recipient
    // ========================================================================

    @Test
    fun groupCallOfferPerPeerWrapsEachDecryptableOnlyByTarget() =
        runTest {
            val result =
                factory.createGroupCallOffer(
                    sdpOffer,
                    setOf(bob, carol),
                    callId,
                    CallType.VIDEO,
                    aliceSigner,
                )

            // Inner event has p-tags for all callees
            val inner = result.msg
            assertIs<CallOfferEvent>(inner)
            assertEquals(setOf(bob, carol), inner.recipientPubKeys())

            // Two wraps — one per callee
            assertEquals(2, result.wraps.size)

            for (wrap in result.wraps) {
                val recipient = wrap.recipientPubKey()
                assertNotNull(recipient)

                val recipientSigner =
                    when (recipient) {
                        bob -> bobSigner
                        carol -> carolSigner
                        else -> error("Unexpected recipient: $recipient")
                    }

                val unwrapped = wrap.unwrapThrowing(recipientSigner)
                assertIs<CallOfferEvent>(unwrapped)
                assertEquals(alice, unwrapped.pubKey)
                assertEquals(sdpOffer, unwrapped.sdpOffer())
                assertEquals(callId, unwrapped.callId())
                assertTrue(unwrapped.verify())

                // The OTHER member should NOT be able to decrypt this wrap
                val otherSigner = if (recipient == bob) carolSigner else bobSigner
                assertNull(
                    wrap.unwrapOrNull(otherSigner),
                    "Wrap for $recipient must not be decryptable by other member",
                )
            }
        }

    @Test
    fun groupCallAnswerBroadcastWrapsEachDecryptableByTarget() =
        runTest {
            val allMembers = setOf(alice, bob, carol)
            val result = factory.createGroupCallAnswer(sdpAnswer, allMembers, callId, bobSigner)

            assertEquals(allMembers.size, result.wraps.size)

            for (wrap in result.wraps) {
                val recipient = wrap.recipientPubKey()
                assertNotNull(recipient)
                assertTrue(recipient in allMembers)

                val recipientSigner =
                    when (recipient) {
                        alice -> aliceSigner
                        bob -> bobSigner
                        carol -> carolSigner
                        else -> error("Unexpected recipient")
                    }

                val unwrapped = wrap.unwrapThrowing(recipientSigner)
                assertIs<CallAnswerEvent>(unwrapped)
                assertEquals(bob, unwrapped.pubKey, "Inner event author should be bob (answerer)")
                assertEquals(sdpAnswer, unwrapped.sdpAnswer())
                assertEquals(allMembers, unwrapped.recipientPubKeys())
                assertTrue(unwrapped.verify())
            }
        }

    @Test
    fun groupHangupAllMembersReceiveIdenticalInnerEvent() =
        runTest {
            val members = setOf(bob, carol)
            val result = factory.createGroupHangup(members, callId, "leaving", aliceSigner)

            assertEquals(members.size, result.wraps.size)

            // All wraps should contain the same signed inner event
            val innerIds = mutableSetOf<String>()
            for (wrap in result.wraps) {
                val recipient = wrap.recipientPubKey()!!
                val recipientSigner = if (recipient == bob) bobSigner else carolSigner

                val unwrapped = wrap.unwrapThrowing(recipientSigner)
                assertIs<CallHangupEvent>(unwrapped)
                assertEquals(alice, unwrapped.pubKey)
                assertEquals("leaving", unwrapped.reason())
                innerIds.add(unwrapped.id)
            }

            // "Sign once, wrap per recipient" — same inner event ID
            assertEquals(1, innerIds.size, "Group hangup must sign once and wrap per recipient (same inner event id)")
        }

    @Test
    fun groupRejectAllMembersReceiveIdenticalInnerEvent() =
        runTest {
            val members = setOf(alice, carol)
            val result = factory.createGroupReject(members, callId, "busy", bobSigner)

            val innerIds = mutableSetOf<String>()
            for (wrap in result.wraps) {
                val recipient = wrap.recipientPubKey()!!
                val recipientSigner = if (recipient == alice) aliceSigner else carolSigner

                val unwrapped = wrap.unwrapThrowing(recipientSigner)
                assertIs<CallRejectEvent>(unwrapped)
                assertEquals(bob, unwrapped.pubKey)
                innerIds.add(unwrapped.id)
            }

            assertEquals(1, innerIds.size, "Group reject must sign once and wrap per recipient")
        }

    // ========================================================================
    // 8. Full P2P Call Flow: Alice calls Bob, complete gift-wrap round-trip
    // ========================================================================

    @Test
    fun fullP2PFlowAllSignalingEventsRoundTrip() =
        runTest {
            // Step 1: Alice sends offer to Bob
            val offerResult = factory.createCallOffer(sdpOffer, bob, callId, CallType.VIDEO, aliceSigner)
            val offerInner = offerResult.wrap.unwrapThrowing(bobSigner)
            assertIs<CallOfferEvent>(offerInner)
            assertEquals(sdpOffer, offerInner.sdpOffer())

            // Step 2: Bob sends answer to Alice
            val answerResult = factory.createCallAnswer(sdpAnswer, alice, callId, bobSigner)
            val answerInner = answerResult.wrap.unwrapThrowing(aliceSigner)
            assertIs<CallAnswerEvent>(answerInner)
            assertEquals(sdpAnswer, answerInner.sdpAnswer())

            // Step 3: Both exchange ICE candidates
            val aliceIceJson = CallIceCandidateEvent.serializeCandidate("candidate:1 1 udp 2122260223 192.168.1.1 44323 typ host", "0", 0)
            val aliceIceResult = factory.createIceCandidate(aliceIceJson, bob, callId, aliceSigner)
            val aliceIceInner = aliceIceResult.wrap.unwrapThrowing(bobSigner)
            assertIs<CallIceCandidateEvent>(aliceIceInner)

            val bobIceJson = CallIceCandidateEvent.serializeCandidate("candidate:2 1 udp 2122260223 192.168.1.2 44324 typ host", "0", 0)
            val bobIceResult = factory.createIceCandidate(bobIceJson, alice, callId, bobSigner)
            val bobIceInner = bobIceResult.wrap.unwrapThrowing(aliceSigner)
            assertIs<CallIceCandidateEvent>(bobIceInner)

            // Step 4: Alice sends renegotiation (voice → video)
            val renegoResult = factory.createRenegotiate("new-video-sdp", bob, callId, aliceSigner)
            val renegoInner = renegoResult.wrap.unwrapThrowing(bobSigner)
            assertIs<CallRenegotiateEvent>(renegoInner)
            assertEquals("new-video-sdp", renegoInner.sdpOffer())

            // Step 5: Bob answers renegotiation
            val renegoAnswerResult = factory.createCallAnswer("renego-answer-sdp", alice, callId, bobSigner)
            val renegoAnswerInner = renegoAnswerResult.wrap.unwrapThrowing(aliceSigner)
            assertIs<CallAnswerEvent>(renegoAnswerInner)

            // Step 6: Alice hangs up
            val hangupResult = factory.createHangup(bob, callId, signer = aliceSigner)
            val hangupInner = hangupResult.wrap.unwrapThrowing(bobSigner)
            assertIs<CallHangupEvent>(hangupInner)
            assertEquals(callId, hangupInner.callId())

            // All inner events signed by real keys, all verified
            for (event in listOf(offerInner, answerInner, aliceIceInner, bobIceInner, renegoInner, renegoAnswerInner, hangupInner)) {
                assertTrue(event.verify(), "Inner event ${event::class.simpleName} signature must be valid")
            }
        }

    // ========================================================================
    // 9. Wrap Metadata: ephemeral key, p-tag, kind
    // ========================================================================

    @Test
    fun wrapMetadataIsCorrect() =
        runTest {
            val result = factory.createCallOffer(sdpOffer, bob, callId, CallType.VOICE, aliceSigner)
            val wrap = result.wrap

            assertEquals(EphemeralGiftWrapEvent.KIND, wrap.kind, "Wrap must be kind 21059")
            assertNotEquals(alice, wrap.pubKey, "Wrap pubkey must be ephemeral, not sender")
            assertEquals(bob, wrap.recipientPubKey(), "Wrap p-tag must be the recipient")
            assertTrue(wrap.content.isNotEmpty(), "Wrap content must be non-empty (encrypted)")
            assertTrue(wrap.verify(), "Wrap signature must be valid (signed by ephemeral key)")
        }

    @Test
    fun eachWrapUsesUniqueEphemeralKey() =
        runTest {
            val result1 = factory.createCallOffer(sdpOffer, bob, callId, CallType.VOICE, aliceSigner)
            val result2 = factory.createCallOffer(sdpOffer, bob, callId, CallType.VOICE, aliceSigner)

            assertNotEquals(
                result1.wrap.pubKey,
                result2.wrap.pubKey,
                "Each gift wrap MUST use a fresh ephemeral key",
            )
        }

    // ========================================================================
    // 10. Inner Event Signature Verification
    // ========================================================================

    @Test
    fun innerEventSignatureIsVerifiableAfterUnwrap() =
        runTest {
            val result = factory.createCallOffer(sdpOffer, bob, callId, CallType.VIDEO, aliceSigner)
            val inner = result.wrap.unwrapThrowing(bobSigner)

            // Verify that the inner event's signature is valid against alice's pubkey
            assertEquals(alice, inner.pubKey)
            assertTrue(inner.verify(), "Recipient must be able to verify sender's signature on inner event")
        }

    // ========================================================================
    // 11. SDP Content with Special Characters Survives Round-Trip
    // ========================================================================

    @Test
    fun sdpWithSpecialCharsSurvivesGiftWrapRoundTrip() =
        runTest {
            val complexSdp =
                "v=0\r\n" +
                    "o=- 4611731400430051336 2 IN IP4 127.0.0.1\r\n" +
                    "s=-\r\n" +
                    "t=0 0\r\n" +
                    "a=group:BUNDLE 0 1\r\n" +
                    "a=msid-semantic: WMS stream0\r\n" +
                    "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n" +
                    "c=IN IP4 0.0.0.0\r\n" +
                    "a=rtcp:9 IN IP4 0.0.0.0\r\n" +
                    "a=ice-ufrag:abc+def/ghi\r\n" +
                    "a=ice-pwd:jkl=mno+pqr/stu\r\n" +
                    "a=fingerprint:sha-256 AB:CD:EF:01:23:45:67:89\r\n"

            val result = factory.createCallOffer(complexSdp, bob, callId, CallType.VOICE, aliceSigner)
            val inner = result.wrap.unwrapThrowing(bobSigner)
            assertIs<CallOfferEvent>(inner)
            assertEquals(complexSdp, inner.sdpOffer(), "Complex SDP with special chars must survive gift wrap round-trip")
        }

    @Test
    fun iceCandidateWithSpecialCharsSurvivesGiftWrapRoundTrip() =
        runTest {
            val candidateSdp = """candidate:842163049 1 udp 1677729535 203.0.113.1 44323 typ srflx raddr 0.0.0.0 rport 0 generation 0 ufrag abc+def network-id 1"""
            val json = CallIceCandidateEvent.serializeCandidate(candidateSdp, "audio", 0)
            val result = factory.createIceCandidate(json, bob, callId, aliceSigner)

            val inner = result.wrap.unwrapThrowing(bobSigner)
            assertIs<CallIceCandidateEvent>(inner)
            assertEquals(candidateSdp, inner.candidateSdp(), "ICE candidate SDP must survive JSON+NIP44 round-trip")
            assertEquals("audio", inner.sdpMid())
        }

    // ========================================================================
    // 12. Group Offer with Context: per-peer SDP, all p-tags visible
    // ========================================================================

    @Test
    fun groupOfferWithContextPerPeerSdpAllPTagsVisible() =
        runTest {
            // Caller creates per-peer offer using the group-context overload
            val bobResult =
                factory.createCallOffer(
                    "bob-specific-sdp",
                    bob,
                    setOf(bob, carol),
                    callId,
                    CallType.VIDEO,
                    aliceSigner,
                )

            // Wrap is only for Bob
            assertEquals(bob, bobResult.wrap.recipientPubKey())

            // Bob unwraps and sees all group members in p-tags
            val inner = bobResult.wrap.unwrapThrowing(bobSigner)
            assertIs<CallOfferEvent>(inner)
            assertEquals("bob-specific-sdp", inner.sdpOffer())
            assertEquals(setOf(bob, carol), inner.recipientPubKeys(), "Inner event must list all group members")
            assertTrue(inner.isGroupCall(), "Multiple p-tags should indicate a group call")
            assertTrue(inner.verify())

            // Carol cannot decrypt Bob's wrap
            assertNull(bobResult.wrap.unwrapOrNull(carolSigner))
        }
}
