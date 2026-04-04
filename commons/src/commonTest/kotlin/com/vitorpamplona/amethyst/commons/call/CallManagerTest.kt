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
package com.vitorpamplona.amethyst.commons.call

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallAnswerEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallHangupEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallOfferEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRejectEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRenegotiateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * CallManager State Machine Tests
 *
 * Tests the NIP-AC call state machine implementation against all specified
 * transitions from the spec:
 *
 * ```
 *   Idle ──> Offering ──> Connecting ──> Connected ──> Ended ──> Idle
 *   Idle ──> IncomingCall ──> Connecting ──> Connected ──> Ended ──> Idle
 *   Idle ──> IncomingCall ──> Ended (reject)
 *   Idle ──> Offering ──> Ended (rejected / timeout)
 * ```
 *
 * These tests construct events directly (without signing) to verify
 * state machine logic independently of cryptographic operations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallManagerTest {
    // Real crypto identities — all tests use actual KeyPairs so that
    // signer.pubKey matches the pubkey used in constructed events.
    private val aliceSigner = NostrSignerInternal(KeyPair())
    private val bobSigner = NostrSignerInternal(KeyPair())
    private val carolSigner = NostrSignerInternal(KeyPair())

    private val alice = aliceSigner.pubKey
    private val bob = bobSigner.pubKey
    private val carol = carolSigner.pubKey

    private val callId = "550e8400-e29b-41d4-a716-446655440000"
    private val callId2 = "660e8400-e29b-41d4-a716-446655440001"
    private val sdpOffer = "v=0\r\no=- 4611731400430051336 2 IN IP4 127.0.0.1\r\n..."
    private val sdpAnswer = "v=0\r\no=- 4611731400430051337 2 IN IP4 127.0.0.1\r\n..."

    private val signers = mapOf(alice to aliceSigner, bob to bobSigner, carol to carolSigner)

    /**
     * Creates a CallManager backed by a real NostrSignerInternal. Tests the full
     * pipeline: CallManager → WebRtcCallFactory → sign → gift wrap → publish.
     */
    private fun TestScope.createManager(
        localPubKey: HexKey = bob,
        followedKeys: Set<HexKey> = setOf(alice, carol),
    ): Pair<CallManager, MutableList<EphemeralGiftWrapEvent>> {
        val published = mutableListOf<EphemeralGiftWrapEvent>()
        val signer = signers[localPubKey] ?: error("Unknown test identity: $localPubKey")
        val manager =
            CallManager(
                signer = signer,
                scope = this,
                isFollowing = { it in followedKeys },
                publishEvent = { published.add(it) },
            )
        return manager to published
    }

    // ---- Event construction helpers ----

    private var eventCounter = 0

    private fun makeOffer(
        from: HexKey,
        to: HexKey,
        callId: String = this.callId,
        callType: CallType = CallType.VOICE,
        sdp: String = sdpOffer,
        createdAt: Long = TimeUtils.now(),
    ): CallOfferEvent {
        val tags =
            arrayOf(
                arrayOf("p", to),
                arrayOf("call-id", callId),
                arrayOf("call-type", callType.value),
                arrayOf("alt", "WebRTC call offer"),
            )
        return CallOfferEvent("offer${eventCounter++}", from, createdAt, tags, sdp, "sig")
    }

    private fun makeGroupOffer(
        from: HexKey,
        members: Set<HexKey>,
        callId: String = this.callId,
        callType: CallType = CallType.VOICE,
        sdp: String = sdpOffer,
        createdAt: Long = TimeUtils.now(),
    ): CallOfferEvent {
        val pTags = members.map { arrayOf("p", it) }.toTypedArray()
        val tags =
            pTags +
                arrayOf(
                    arrayOf("call-id", callId),
                    arrayOf("call-type", callType.value),
                    arrayOf("alt", "WebRTC call offer"),
                )
        return CallOfferEvent("offer${eventCounter++}", from, createdAt, tags, sdp, "sig")
    }

    private fun makeAnswer(
        from: HexKey,
        to: HexKey,
        callId: String = this.callId,
        sdp: String = sdpAnswer,
        createdAt: Long = TimeUtils.now(),
    ): CallAnswerEvent {
        val tags =
            arrayOf(
                arrayOf("p", to),
                arrayOf("call-id", callId),
                arrayOf("alt", "WebRTC call answer"),
            )
        return CallAnswerEvent("answer${eventCounter++}", from, createdAt, tags, sdp, "sig")
    }

    private fun makeGroupAnswer(
        from: HexKey,
        members: Set<HexKey>,
        callId: String = this.callId,
        sdp: String = sdpAnswer,
        createdAt: Long = TimeUtils.now(),
    ): CallAnswerEvent {
        val pTags = members.map { arrayOf("p", it) }.toTypedArray()
        val tags =
            pTags +
                arrayOf(
                    arrayOf("call-id", callId),
                    arrayOf("alt", "WebRTC call answer"),
                )
        return CallAnswerEvent("answer${eventCounter++}", from, createdAt, tags, sdp, "sig")
    }

    private fun makeHangup(
        from: HexKey,
        to: HexKey,
        callId: String = this.callId,
        reason: String = "",
        createdAt: Long = TimeUtils.now(),
    ): CallHangupEvent {
        val tags =
            arrayOf(
                arrayOf("p", to),
                arrayOf("call-id", callId),
                arrayOf("alt", "WebRTC call hangup"),
            )
        return CallHangupEvent("hangup${eventCounter++}", from, createdAt, tags, reason, "sig")
    }

    private fun makeReject(
        from: HexKey,
        to: HexKey,
        callId: String = this.callId,
        reason: String = "",
        createdAt: Long = TimeUtils.now(),
    ): CallRejectEvent {
        val tags =
            arrayOf(
                arrayOf("p", to),
                arrayOf("call-id", callId),
                arrayOf("alt", "WebRTC call rejection"),
            )
        return CallRejectEvent("reject${eventCounter++}", from, createdAt, tags, reason, "sig")
    }

    private fun makeIceCandidate(
        from: HexKey,
        to: HexKey,
        callId: String = this.callId,
        createdAt: Long = TimeUtils.now(),
    ): CallIceCandidateEvent {
        val json = """{"candidate":"candidate:1","sdpMid":"0","sdpMLineIndex":0}"""
        val tags =
            arrayOf(
                arrayOf("p", to),
                arrayOf("call-id", callId),
                arrayOf("alt", "WebRTC ICE candidate"),
            )
        return CallIceCandidateEvent("ice${eventCounter++}", from, createdAt, tags, json, "sig")
    }

    private fun makeRenegotiate(
        from: HexKey,
        to: HexKey,
        callId: String = this.callId,
        sdp: String = sdpOffer,
        createdAt: Long = TimeUtils.now(),
    ): CallRenegotiateEvent {
        val tags =
            arrayOf(
                arrayOf("p", to),
                arrayOf("call-id", callId),
                arrayOf("alt", "WebRTC call renegotiation"),
            )
        return CallRenegotiateEvent("renego${eventCounter++}", from, createdAt, tags, sdp, "sig")
    }

    // ========================================================================
    // 1. P2P Call: Idle → IncomingCall → Connecting → Connected → Ended
    // ========================================================================

    @Test
    fun incomingCallFromFollowedUser_transitionsToIncomingCall() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)

            val state = manager.state.value
            assertIs<CallState.IncomingCall>(state)
            assertEquals(callId, state.callId)
            assertEquals(alice, state.callerPubKey)
            assertEquals(CallType.VOICE, state.callType)
            assertEquals(sdpOffer, state.sdpOffer)
        }

    @Test
    fun incomingCallFromNonFollowed_isIgnored() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob, followedKeys = emptySet())

            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)

            assertIs<CallState.Idle>(manager.state.value)
        }

    @Test
    fun acceptingCall_transitionsToConnecting() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)
            assertIs<CallState.IncomingCall>(manager.state.value)

            manager.acceptCall(sdpAnswer)

            val state = manager.state.value
            assertIs<CallState.Connecting>(state)
            assertEquals(callId, state.callId)
        }

    @Test
    fun peerConnected_transitionsConnectingToConnected() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            assertIs<CallState.Connecting>(manager.state.value)

            manager.onPeerConnected()

            val state = manager.state.value
            assertIs<CallState.Connected>(state)
            assertEquals(callId, state.callId)
        }

    @Test
    fun peerHangup_endsConnectedCall() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            val hangup = makeHangup(from = alice, to = bob)
            manager.onSignalingEvent(hangup)

            val state = manager.state.value
            assertIs<CallState.Ended>(state)
            assertEquals(EndReason.PEER_HANGUP, state.reason)
        }

    @Test
    fun endedState_autoResetsToIdle() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            manager.onSignalingEvent(makeHangup(from = alice, to = bob))

            assertIs<CallState.Ended>(manager.state.value)

            // Advance past the ENDED_DISPLAY_MS (2 seconds)
            advanceUntilIdle()

            assertIs<CallState.Idle>(manager.state.value)
        }

    // ========================================================================
    // 2. Caller Side: Idle → Offering → Connecting → Connected
    // ========================================================================

    @Test
    fun initiateCall_transitionsToOffering() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice)

            manager.initiateCall(bob, CallType.VIDEO, callId, sdpOffer)

            val state = manager.state.value
            assertIs<CallState.Offering>(state)
            assertEquals(callId, state.callId)
            assertEquals(setOf(bob), state.peerPubKeys)
            assertEquals(CallType.VIDEO, state.callType)
        }

    @Test
    fun receivingAnswer_transitionsOfferingToConnecting() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob))

            manager.initiateCall(bob, CallType.VOICE, callId, sdpOffer)
            assertIs<CallState.Offering>(manager.state.value)

            var answerReceived = false
            manager.onAnswerReceived = { answerReceived = true }

            val answer = makeAnswer(from = bob, to = alice)
            manager.onSignalingEvent(answer)

            val state = manager.state.value
            assertIs<CallState.Connecting>(state)
            assertTrue(answerReceived, "onAnswerReceived callback should fire")
        }

    // ========================================================================
    // 3. Call Rejection
    // ========================================================================

    @Test
    fun rejectingIncomingCall_transitionsToEnded() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            assertIs<CallState.IncomingCall>(manager.state.value)

            manager.rejectCall()

            val state = manager.state.value
            assertIs<CallState.Ended>(state)
            assertEquals(EndReason.REJECTED, state.reason)
        }

    @Test
    fun receivingReject_endsOfferingCall() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob))

            manager.initiateCall(bob, CallType.VOICE, callId, sdpOffer)
            assertIs<CallState.Offering>(manager.state.value)

            val reject = makeReject(from = bob, to = alice)
            manager.onSignalingEvent(reject)

            val state = manager.state.value
            assertIs<CallState.Ended>(state)
            assertEquals(EndReason.PEER_REJECTED, state.reason)
        }

    // ========================================================================
    // 4. Busy Auto-Reject
    // ========================================================================

    @Test
    fun incomingCallWhileInActiveCall_autoRejectsBusy() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob)

            // First call: accepted and connected
            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)
            published.clear()

            // Second call arrives while connected
            val secondOffer = makeOffer(from = carol, to = bob, callId = callId2)
            manager.onSignalingEvent(secondOffer)
            advanceUntilIdle()

            // Should still be in the original call
            assertIs<CallState.Connected>(manager.state.value)

            // Should have published a reject (busy)
            assertTrue(published.isNotEmpty(), "Should publish a busy reject")
        }

    // ========================================================================
    // 5. Staleness: Old Events Discarded
    // ========================================================================

    @Test
    fun staleEvents_areDiscarded() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            // Event from 30 seconds ago (beyond 20s threshold)
            val staleOffer = makeOffer(from = alice, to = bob, createdAt = TimeUtils.now() - 30)
            manager.onSignalingEvent(staleOffer)

            assertIs<CallState.Idle>(manager.state.value, "Stale events (>20s old) MUST be discarded")
        }

    @Test
    fun freshEvents_areProcessed() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            // Event from 5 seconds ago (within 20s threshold)
            val freshOffer = makeOffer(from = alice, to = bob, createdAt = TimeUtils.now() - 5)
            manager.onSignalingEvent(freshOffer)

            assertIs<CallState.IncomingCall>(manager.state.value, "Fresh events (<20s old) should be processed")
        }

    // ========================================================================
    // 6. Deduplication
    // ========================================================================

    @Test
    fun duplicateEvents_areIgnored() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)
            assertIs<CallState.IncomingCall>(manager.state.value)

            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            // Re-deliver the same offer (same event ID)
            manager.onSignalingEvent(offer)

            // Should still be Connected, not re-processing the offer
            assertIs<CallState.Connected>(manager.state.value)
        }

    // ========================================================================
    // 7. Self-Event Filtering
    // ========================================================================

    @Test
    fun selfIceCandidates_areAlwaysIgnored() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)

            var iceCalled = false
            manager.onIceCandidateReceived = { iceCalled = true }

            // ICE candidate from self (echoed back by relay)
            val selfIce = makeIceCandidate(from = bob, to = alice)
            manager.onSignalingEvent(selfIce)

            assertTrue(!iceCalled, "Self ICE candidates MUST be ignored")
        }

    @Test
    fun selfHangup_isAlwaysIgnored() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            // Self hangup echo from relay
            val selfHangup = makeHangup(from = bob, to = alice)
            manager.onSignalingEvent(selfHangup)

            // Should still be Connected (self hangup is handled locally)
            assertIs<CallState.Connected>(manager.state.value)
        }

    @Test
    fun selfAnswer_inIncomingCall_meansAnsweredElsewhere() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            assertIs<CallState.IncomingCall>(manager.state.value)

            // Self-answer from another device
            val selfAnswer = makeAnswer(from = bob, to = alice)
            manager.onSignalingEvent(selfAnswer)

            val state = manager.state.value
            assertIs<CallState.Ended>(state)
            assertEquals(EndReason.ANSWERED_ELSEWHERE, state.reason)
        }

    @Test
    fun selfAnswer_inOfferingState_isIgnored() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob))

            manager.initiateCall(bob, CallType.VOICE, callId, sdpOffer)
            assertIs<CallState.Offering>(manager.state.value)

            // Self-answer echo — should be ignored in Offering state
            val selfAnswer = makeAnswer(from = alice, to = bob)
            manager.onSignalingEvent(selfAnswer)

            assertIs<CallState.Offering>(manager.state.value, "Self-answer in Offering should be ignored")
        }

    // ========================================================================
    // 8. ICE Candidate Forwarding
    // ========================================================================

    @Test
    fun iceCandidates_areForwardedViaCallback() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)

            var receivedIce: CallIceCandidateEvent? = null
            manager.onIceCandidateReceived = { receivedIce = it }

            val ice = makeIceCandidate(from = alice, to = bob)
            manager.onSignalingEvent(ice)

            assertNotNull(receivedIce, "ICE candidate should be forwarded via callback")
        }

    // ========================================================================
    // 9. Mid-Call Renegotiation (Voice → Video Switch)
    // ========================================================================

    @Test
    fun renegotiation_inConnectedState_isForwarded() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            var receivedRenego: CallRenegotiateEvent? = null
            manager.onRenegotiationOfferReceived = { receivedRenego = it }

            val renego = makeRenegotiate(from = alice, to = bob)
            manager.onSignalingEvent(renego)

            assertNotNull(receivedRenego, "Renegotiation should be forwarded in Connected state")
        }

    @Test
    fun renegotiation_inConnectingState_isForwarded() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            assertIs<CallState.Connecting>(manager.state.value)

            var receivedRenego: CallRenegotiateEvent? = null
            manager.onRenegotiationOfferReceived = { receivedRenego = it }

            val renego = makeRenegotiate(from = alice, to = bob)
            manager.onSignalingEvent(renego)

            assertNotNull(receivedRenego)
        }

    @Test
    fun renegotiation_inIdleState_isIgnored() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            var receivedRenego: CallRenegotiateEvent? = null
            manager.onRenegotiationOfferReceived = { receivedRenego = it }

            val renego = makeRenegotiate(from = alice, to = bob)
            manager.onSignalingEvent(renego)

            assertIs<CallState.Idle>(manager.state.value)
            assertEquals(null, receivedRenego, "Renegotiation in Idle state should be ignored")
        }

    @Test
    fun renegotiation_wrongCallId_isIgnored() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()

            var receivedRenego: CallRenegotiateEvent? = null
            manager.onRenegotiationOfferReceived = { receivedRenego = it }

            val renego = makeRenegotiate(from = alice, to = bob, callId = "wrong-call-id")
            manager.onSignalingEvent(renego)

            assertEquals(null, receivedRenego, "Renegotiation for wrong call-id should be ignored")
        }

    // ========================================================================
    // 10. Hangup from Any Active State
    // ========================================================================

    @Test
    fun hangup_fromOffering_transitionsToEnded() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob))

            manager.initiateCall(bob, CallType.VOICE, callId, sdpOffer)
            assertIs<CallState.Offering>(manager.state.value)

            manager.hangup()

            val state = manager.state.value
            assertIs<CallState.Ended>(state)
            assertEquals(EndReason.HANGUP, state.reason)
        }

    @Test
    fun hangup_fromConnecting_transitionsToEnded() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            assertIs<CallState.Connecting>(manager.state.value)

            manager.hangup()

            assertIs<CallState.Ended>(manager.state.value)
        }

    @Test
    fun hangup_fromConnected_transitionsToEnded() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()

            manager.hangup()

            val state = manager.state.value
            assertIs<CallState.Ended>(state)
            assertEquals(EndReason.HANGUP, state.reason)
        }

    @Test
    fun hangup_fromIdle_isNoop() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob)

            manager.hangup()

            assertIs<CallState.Idle>(manager.state.value)
            assertTrue(published.isEmpty())
        }

    // ========================================================================
    // 11. Group Call: Multiple Peers
    // ========================================================================

    @Test
    fun groupCallOffer_detectsMultipleMembers() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            val offer = makeGroupOffer(from = alice, members = setOf(bob, carol))
            manager.onSignalingEvent(offer)

            val state = manager.state.value
            assertIs<CallState.IncomingCall>(state)
            assertTrue(state.groupMembers.containsAll(setOf(alice, bob, carol)))
        }

    @Test
    fun groupCall_peerReject_removesFromGroup() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob, carol))

            manager.beginOffering(callId, setOf(bob, carol), CallType.VOICE)
            assertIs<CallState.Offering>(manager.state.value)

            // Bob rejects
            val reject = makeReject(from = bob, to = alice)
            manager.onSignalingEvent(reject)

            val state = manager.state.value
            assertIs<CallState.Offering>(state)
            assertTrue(bob !in state.peerPubKeys, "Rejected peer should be removed")
            assertTrue(carol in state.peerPubKeys, "Remaining peers should stay")
        }

    @Test
    fun groupCall_allPeersReject_endsCall() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob, carol))

            manager.beginOffering(callId, setOf(bob, carol), CallType.VOICE)

            manager.onSignalingEvent(makeReject(from = bob, to = alice))
            manager.onSignalingEvent(makeReject(from = carol, to = alice))

            assertIs<CallState.Ended>(manager.state.value)
        }

    @Test
    fun groupCall_partialDisconnect_continuesWithRemainingPeers() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob, carol))

            manager.beginOffering(callId, setOf(bob, carol), CallType.VOICE)

            // Bob answers first
            val bobAnswer = makeAnswer(from = bob, to = alice)
            manager.onSignalingEvent(bobAnswer)
            assertIs<CallState.Connecting>(manager.state.value)

            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            // Carol answers
            val carolAnswer = makeAnswer(from = carol, to = alice)
            manager.onSignalingEvent(carolAnswer)

            // Bob hangs up
            manager.onSignalingEvent(makeHangup(from = bob, to = alice))

            // Call should continue with carol
            val state = manager.state.value
            assertIs<CallState.Connected>(state)
            assertTrue(bob !in state.peerPubKeys, "Bob should be removed")
        }

    @Test
    fun groupCall_lastPeerLeaves_endsCall() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob, carol))

            manager.beginOffering(callId, setOf(bob, carol), CallType.VOICE)

            // Both answer
            manager.onSignalingEvent(makeAnswer(from = bob, to = alice))
            manager.onPeerConnected()

            val carolAnswer = makeAnswer(from = carol, to = alice)
            manager.onSignalingEvent(carolAnswer)

            // Both leave
            manager.onSignalingEvent(makeHangup(from = bob, to = alice))
            manager.onSignalingEvent(makeHangup(from = carol, to = alice))

            assertIs<CallState.Ended>(manager.state.value)
        }

    // ========================================================================
    // 12. Group Call: Callee-to-Callee Mesh Discovery
    // ========================================================================

    @Test
    fun groupCall_discoversPeerWhileRinging() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            // Alice calls bob and carol
            val offer = makeGroupOffer(from = alice, members = setOf(bob, carol))
            manager.onSignalingEvent(offer)
            assertIs<CallState.IncomingCall>(manager.state.value)

            // Carol answers (bob sees this while ringing)
            val carolAnswer = makeGroupAnswer(from = carol, members = setOf(alice, bob, carol))
            manager.onSignalingEvent(carolAnswer)

            // Bob still ringing
            assertIs<CallState.IncomingCall>(manager.state.value)

            // Track mesh setup callback
            val newPeers = mutableListOf<HexKey>()
            manager.onNewPeerInGroupCall = { newPeers.add(it) }

            // Bob accepts
            manager.acceptCall(sdpAnswer)

            // Should trigger callee-to-callee mesh setup with carol
            assertTrue(carol in newPeers, "Should discover carol for mesh setup after accepting")
        }

    // ========================================================================
    // 13. Mid-Call Offer (Callee-to-Callee)
    // ========================================================================

    @Test
    fun midCallOffer_sameCallId_isForwardedAsMidCallOffer() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob, followedKeys = setOf(alice, carol))

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            var midCallPeer: HexKey? = null
            var midCallSdp: String? = null
            manager.onMidCallOfferReceived = { peer, sdp ->
                midCallPeer = peer
                midCallSdp = sdp
            }

            // Carol sends a mid-call offer (callee-to-callee mesh)
            val carolOffer = makeOffer(from = carol, to = bob, callId = callId, sdp = "carol-sdp")
            manager.onSignalingEvent(carolOffer)

            assertEquals(carol, midCallPeer)
            assertEquals("carol-sdp", midCallSdp)
        }

    // ========================================================================
    // 14. Call-ID Mismatch Ignored
    // ========================================================================

    @Test
    fun answer_wrongCallId_isIgnored() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob))

            manager.initiateCall(bob, CallType.VOICE, callId, sdpOffer)
            assertIs<CallState.Offering>(manager.state.value)

            val wrongAnswer = makeAnswer(from = bob, to = alice, callId = "wrong-id")
            manager.onSignalingEvent(wrongAnswer)

            assertIs<CallState.Offering>(manager.state.value, "Answer with wrong call-id should be ignored")
        }

    @Test
    fun hangup_wrongCallId_isIgnored() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            val wrongHangup = makeHangup(from = alice, to = bob, callId = "wrong-id")
            manager.onSignalingEvent(wrongHangup)

            assertIs<CallState.Connected>(manager.state.value, "Hangup with wrong call-id should be ignored")
        }

    // ========================================================================
    // 15. Peer Left Callback
    // ========================================================================

    @Test
    fun peerLeft_callback_firesOnHangup() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob, carol))

            manager.beginOffering(callId, setOf(bob, carol), CallType.VOICE)
            manager.onSignalingEvent(makeAnswer(from = bob, to = alice))
            manager.onPeerConnected()
            manager.onSignalingEvent(makeAnswer(from = carol, to = alice))

            val leftPeers = mutableListOf<HexKey>()
            manager.onPeerLeft = { leftPeers.add(it) }

            manager.onSignalingEvent(makeHangup(from = bob, to = alice))

            assertTrue(bob in leftPeers, "onPeerLeft should fire when a peer hangs up")
        }

    // ========================================================================
    // 16. Reset
    // ========================================================================

    @Test
    fun reset_returnsToIdle() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            assertIs<CallState.IncomingCall>(manager.state.value)

            manager.reset()

            assertIs<CallState.Idle>(manager.state.value)
        }

    // ========================================================================
    // 17. Video Call Type Preserved
    // ========================================================================

    @Test
    fun videoCallType_preservedThroughStates() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            val videoOffer = makeOffer(from = alice, to = bob, callType = CallType.VIDEO)
            manager.onSignalingEvent(videoOffer)

            val incoming = manager.state.value
            assertIs<CallState.IncomingCall>(incoming)
            assertEquals(CallType.VIDEO, incoming.callType)

            manager.acceptCall(sdpAnswer)
            val connecting = manager.state.value
            assertIs<CallState.Connecting>(connecting)
            assertEquals(CallType.VIDEO, connecting.callType)

            manager.onPeerConnected()
            val connected = manager.state.value
            assertIs<CallState.Connected>(connected)
            assertEquals(CallType.VIDEO, connected.callType)
        }

    // ========================================================================
    // 18. Caller Cancels (Hangup While Ringing)
    // ========================================================================

    @Test
    fun callerHangup_whileRinging_endsIncomingCall() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            assertIs<CallState.IncomingCall>(manager.state.value)

            // Caller cancels
            manager.onSignalingEvent(makeHangup(from = alice, to = bob))

            val state = manager.state.value
            assertIs<CallState.Ended>(state)
            assertEquals(EndReason.PEER_HANGUP, state.reason)
        }
    // ========================================================================
    // INTERFACE-LEVEL TESTS (Real Signers, Full Pipeline)
    // ========================================================================
    // These tests use real NostrSignerInternal with actual crypto keys to verify
    // the full pipeline: CallManager → WebRtcCallFactory → sign → gift wrap → publish

    @Test
    fun interface_initiateCall_publishesGiftWrappedOffer() =
        runTest {
            val (manager, published) = createManager(localPubKey = alice, followedKeys = setOf(bob))

            manager.initiateCall(bob, CallType.VIDEO, callId, sdpOffer)

            assertIs<CallState.Offering>(manager.state.value)
            assertEquals(1, published.size, "Should publish exactly one gift-wrapped offer")
            assertEquals(EphemeralGiftWrapEvent.KIND, published[0].kind, "Wrap must be kind 21059")
        }

    @Test
    fun interface_acceptCall_publishesGiftWrappedAnswer() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, followedKeys = setOf(alice))

            // Simulate incoming offer from alice
            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)
            assertIs<CallState.IncomingCall>(manager.state.value)
            published.clear()

            manager.acceptCall(sdpAnswer)

            assertIs<CallState.Connecting>(manager.state.value)
            // Should publish answer wrapped for all recipients (alice + self for multi-device)
            assertTrue(published.isNotEmpty(), "Should publish gift-wrapped answer(s)")
            published.forEach { wrap ->
                assertEquals(EphemeralGiftWrapEvent.KIND, wrap.kind)
            }
        }

    @Test
    fun interface_rejectCall_publishesGiftWrappedReject() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, followedKeys = setOf(alice))

            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)
            assertIs<CallState.IncomingCall>(manager.state.value)
            published.clear()

            manager.rejectCall()

            assertIs<CallState.Ended>(manager.state.value)
            assertTrue(published.isNotEmpty(), "Should publish gift-wrapped reject(s)")
        }

    @Test
    fun interface_hangup_publishesGiftWrappedHangup() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, followedKeys = setOf(alice))

            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)
            published.clear()

            manager.hangup()

            assertIs<CallState.Ended>(manager.state.value)
            assertTrue(published.isNotEmpty(), "Should publish gift-wrapped hangup(s)")
        }

    @Test
    fun interface_sendRenegotiation_publishesGiftWrappedRenegotiate() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, followedKeys = setOf(alice))

            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)
            published.clear()

            val newSdp = "v=0\r\nnew-sdp-for-video"
            manager.sendRenegotiation(newSdp, alice)

            assertEquals(1, published.size, "Should publish exactly one gift-wrapped renegotiate")
            assertEquals(EphemeralGiftWrapEvent.KIND, published[0].kind)
        }

    @Test
    fun interface_sendRenegotiationAnswer_publishesGiftWrappedAnswer() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, followedKeys = setOf(alice))

            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            published.clear()

            manager.sendRenegotiationAnswer("renegotiation-answer-sdp", alice)

            assertEquals(1, published.size)
            assertEquals(EphemeralGiftWrapEvent.KIND, published[0].kind)
        }

    @Test
    fun interface_busyAutoReject_publishesRejectEvent() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, followedKeys = setOf(alice, carol))

            // Accept first call
            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            published.clear()

            // Second call from carol while in active call
            val secondOffer = makeOffer(from = carol, to = bob, callId = callId2)
            manager.onSignalingEvent(secondOffer)
            advanceUntilIdle()

            // Should remain in original call
            assertIs<CallState.Connected>(manager.state.value)
            // Should have published auto-reject
            assertTrue(published.isNotEmpty(), "Should publish busy auto-reject")
        }

    @Test
    fun interface_groupCall_publishesPerPeerOffers() =
        runTest {
            val (manager, published) = createManager(localPubKey = alice, followedKeys = setOf(bob, carol))

            manager.beginOffering(callId, setOf(bob, carol), CallType.VOICE)

            // Publish per-peer offers
            manager.publishOfferToPeer(bob, setOf(bob, carol), CallType.VOICE, callId, sdpOffer)
            manager.publishOfferToPeer(carol, setOf(bob, carol), CallType.VOICE, callId, "carol-sdp")

            assertEquals(2, published.size, "Should publish one gift-wrapped offer per peer")
        }

    @Test
    fun interface_invitePeer_publishesOfferToNewPeer() =
        runTest {
            val (manager, published) = createManager(localPubKey = alice, followedKeys = setOf(bob))

            manager.initiateCall(bob, CallType.VOICE, callId, sdpOffer)
            val answer = makeAnswer(from = bob, to = alice)
            manager.onSignalingEvent(answer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)
            published.clear()

            manager.invitePeer(carol, "invite-sdp")

            assertEquals(1, published.size, "Should publish one gift-wrapped invite offer")

            val state = manager.state.value
            assertIs<CallState.Connected>(state)
            assertTrue(carol in state.pendingPeerPubKeys, "Invited peer should be in pending set")
        }

    @Test
    fun interface_fullP2PCallFlow_withRealSigners() =
        runTest {
            // Full end-to-end P2P call: Alice calls Bob
            val (aliceManager, alicePublished) = createManager(localPubKey = alice, followedKeys = setOf(bob))
            val (bobManager, bobPublished) = createManager(localPubKey = bob, followedKeys = setOf(alice))

            // Step 1: Alice initiates call
            aliceManager.initiateCall(bob, CallType.VIDEO, callId, sdpOffer)
            assertIs<CallState.Offering>(aliceManager.state.value)
            assertEquals(1, alicePublished.size)

            // Step 2: Bob receives offer (simulated, since we can't decrypt the gift wrap)
            val bobOffer = makeOffer(from = alice, to = bob, callType = CallType.VIDEO)
            bobManager.onSignalingEvent(bobOffer)
            assertIs<CallState.IncomingCall>(bobManager.state.value)

            // Step 3: Bob accepts
            bobManager.acceptCall(sdpAnswer)
            assertIs<CallState.Connecting>(bobManager.state.value)
            assertTrue(bobPublished.isNotEmpty())

            // Step 4: Alice receives answer
            val aliceAnswer = makeAnswer(from = bob, to = alice)
            aliceManager.onSignalingEvent(aliceAnswer)
            assertIs<CallState.Connecting>(aliceManager.state.value)

            // Step 5: Both sides report peer connected
            aliceManager.onPeerConnected()
            bobManager.onPeerConnected()
            assertIs<CallState.Connected>(aliceManager.state.value)
            assertIs<CallState.Connected>(bobManager.state.value)

            // Step 6: Alice sends renegotiation (add video)
            alicePublished.clear()
            aliceManager.sendRenegotiation("new-video-sdp", bob)
            assertEquals(1, alicePublished.size)

            // Step 7: Bob receives renegotiate and responds
            var renegoReceived = false
            bobManager.onRenegotiationOfferReceived = { renegoReceived = true }
            bobManager.onSignalingEvent(makeRenegotiate(from = alice, to = bob))
            assertTrue(renegoReceived)

            bobPublished.clear()
            bobManager.sendRenegotiationAnswer("renego-answer-sdp", alice)
            assertEquals(1, bobPublished.size)

            // Step 8: Alice hangs up
            alicePublished.clear()
            aliceManager.hangup()
            assertIs<CallState.Ended>(aliceManager.state.value)
            assertTrue(alicePublished.isNotEmpty())

            // Step 9: Bob receives hangup
            bobManager.onSignalingEvent(makeHangup(from = alice, to = bob))
            assertIs<CallState.Ended>(bobManager.state.value)

            // Step 10: Both auto-reset to Idle
            advanceUntilIdle()
            assertIs<CallState.Idle>(aliceManager.state.value)
            assertIs<CallState.Idle>(bobManager.state.value)
        }
}
