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
package com.vitorpamplona.amethyst.service.relayClient.chatDelivery

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How an outgoing chat message's delivery state answers "did this send?".
 *
 * The gap this closes: the tracker only ever listened for `OK true`, so a refused message sat on the
 * pending clock forever, indistinguishable from one still in flight. That is the whole of what a
 * banned Buzz member saw — the relay enforces a tenant ban (kind 9040) or timeout (9042) at publish
 * time and answers `OK false`, with no event for the client to fold, so the refusal reaches the user
 * here or nowhere.
 *
 * Drives real `OK` frames through the real listener the tracker installs, rather than poking at its
 * internals.
 */
class ChatDeliveryTrackerTest {
    private val relayA = NormalizedRelayUrl("wss://a.example/")
    private val relayB = NormalizedRelayUrl("wss://b.example/")
    private val noteId: HexKey = "aa".repeat(32)
    private val wrapId: HexKey = "bb".repeat(32)
    private val alice: HexKey = "c1".repeat(32)
    private val bob: HexKey = "c2".repeat(32)

    private class Harness {
        val client = CapturingClient()
        val tracker = ChatDeliveryTracker(client)

        fun ok(
            eventId: HexKey,
            relay: NormalizedRelayUrl,
        ) = deliver(eventId, relay, OkMessage.accepted(eventId))

        fun refuse(
            eventId: HexKey,
            relay: NormalizedRelayUrl,
            reason: String,
        ) = deliver(eventId, relay, OkMessage(eventId, false, reason))

        private fun deliver(
            eventId: HexKey,
            relay: NormalizedRelayUrl,
            msg: OkMessage,
        ) {
            client.listener!!.onIncomingMessage(relayClient(relay), msg.toJson(), msg)
        }

        fun stateOf(id: HexKey) = tracker.currentFor(id)
    }

    @Test
    fun aRefusalIsRecordedWithTheRelaysOwnReason() {
        val h = Harness()
        h.tracker.trackPublic(noteId, setOf(relayA))

        h.refuse(noteId, relayA, "restricted: you are banned from posting here")

        val delivery = h.stateOf(noteId)!!
        assertEquals(1, delivery.rejections.size)
        assertEquals(relayA, delivery.rejections[0].relay)
        assertEquals("restricted: you are banned from posting here", delivery.rejections[0].reason)
        assertEquals(MachineReadablePrefix.RESTRICTED, delivery.rejections[0].prefix)
        // The single-relay room case — a Buzz workspace channel — so one refusal is the whole answer.
        assertTrue(delivery.isRefused)
        assertFalse(delivery.isFullyAccepted)
    }

    @Test
    fun aBanIsNotOfferedAsRetryableButARateLimitIs() {
        val h = Harness()
        h.tracker.trackPublic(noteId, setOf(relayA))
        h.refuse(noteId, relayA, "blocked: pubkey is banned")
        assertFalse(h.stateOf(noteId)!!.firstRejection!!.isTransient)

        val other = "dd".repeat(32)
        h.tracker.trackPublic(other, setOf(relayA))
        h.refuse(other, relayA, "rate-limited: slow down")
        assertTrue(h.stateOf(other)!!.firstRejection!!.isTransient)
    }

    @Test
    fun oneRelayRefusingWhileAnotherAcceptsIsStillDelivered() {
        // The refusal is worth recording (it shows in the detail rows) but the message landed, so the
        // bubble must not claim otherwise.
        val h = Harness()
        h.tracker.trackPublic(noteId, setOf(relayA, relayB))

        h.refuse(noteId, relayA, "invalid: kind not accepted here")
        h.ok(noteId, relayB)

        val delivery = h.stateOf(noteId)!!
        assertEquals(setOf(relayB), delivery.acceptedRelays)
        assertEquals(1, delivery.rejections.size)
        assertFalse(delivery.isRefused)
    }

    @Test
    fun aRelayThatHasNotAnsweredYetKeepsTheMessagePendingRatherThanRefused() {
        val h = Harness()
        h.tracker.trackPublic(noteId, setOf(relayA, relayB))

        h.refuse(noteId, relayA, "error: try again later")

        // relayB is still out. Refused means "nowhere left to land", which isn't true yet.
        assertFalse(h.stateOf(noteId)!!.isRefused)

        h.refuse(noteId, relayB, "error: try again later")
        assertTrue(h.stateOf(noteId)!!.isRefused)
    }

    @Test
    fun duplicateCountsAsDeliveredEvenWhenSentAsARefusal() {
        // Relays disagree on whether `duplicate:` rides an OK true or an OK false; either way the
        // relay holds the event, so reporting it as a failed send would be wrong.
        val h = Harness()
        h.tracker.trackPublic(noteId, setOf(relayA))

        h.refuse(noteId, relayA, "duplicate: have this event")

        val delivery = h.stateOf(noteId)!!
        assertEquals(setOf(relayA), delivery.acceptedRelays)
        assertTrue(delivery.rejections.isEmpty())
        assertFalse(delivery.isRefused)
        assertTrue(delivery.isFullyAccepted)
    }

    @Test
    fun anAcceptanceAfterARefusalClearsIt() {
        // The auth-required -> AUTH -> republish round trip: the same relay refuses, then stores.
        val h = Harness()
        h.tracker.trackPublic(noteId, setOf(relayA))

        h.refuse(noteId, relayA, "auth-required: we only accept events from authenticated users")
        assertTrue(h.stateOf(noteId)!!.isRefused)

        h.ok(noteId, relayA)

        val delivery = h.stateOf(noteId)!!
        assertTrue(delivery.rejections.isEmpty())
        assertFalse(delivery.isRefused)
        assertTrue(delivery.isFullyAccepted)
    }

    @Test
    fun aRepeatedRefusalFromOneRelayIsRecordedOnce() {
        val h = Harness()
        h.tracker.trackPublic(noteId, setOf(relayA))

        h.refuse(noteId, relayA, "blocked: nope")
        h.refuse(noteId, relayA, "blocked: nope")

        assertEquals(1, h.stateOf(noteId)!!.rejections.size)
    }

    @Test
    fun aWrappedRoomMessageAttributesTheRefusalToTheDisplayedNote() {
        // Concord: the relay OKs the encrypted wrap, but the feed shows the inner rumor.
        val h = Harness()
        h.tracker.trackWrappedPublic(noteId, wrapId, setOf(relayA))

        h.refuse(wrapId, relayA, "restricted: not a member")

        val delivery = h.stateOf(noteId)!!
        assertEquals(1, delivery.rejections.size)
        assertTrue(delivery.isRefused)
    }

    @Test
    fun aDmRefusalIsAttributedToTheRecipientWhoseWrapItWas() {
        val h = Harness()
        val aliceWrap = "e1".repeat(32)
        val bobWrap = "e2".repeat(32)
        h.tracker.trackWrap(noteId, alice, aliceWrap, setOf(relayA))
        h.tracker.trackWrap(noteId, bob, bobWrap, setOf(relayB))

        h.refuse(aliceWrap, relayA, "blocked: sender not allowed")
        h.ok(bobWrap, relayB)

        val delivery = h.stateOf(noteId)!!
        val byRecipient = delivery.otherRecipients!!.associateBy { it.recipient }
        assertTrue(byRecipient.getValue(alice).isRefused)
        assertFalse(byRecipient.getValue(alice).isDelivered)
        assertTrue(byRecipient.getValue(bob).isDelivered)
        assertFalse(byRecipient.getValue(bob).isRefused)
        // Reached one of the two, so the message as a whole is neither refused nor fully delivered.
        assertFalse(delivery.isRefused)
        assertFalse(delivery.isFullyAccepted)
    }

    @Test
    fun aDmRefusedForEveryRecipientIsRefused() {
        val h = Harness()
        val aliceWrap = "e1".repeat(32)
        val bobWrap = "e2".repeat(32)
        h.tracker.trackWrap(noteId, alice, aliceWrap, setOf(relayA))
        h.tracker.trackWrap(noteId, bob, bobWrap, setOf(relayB))

        h.refuse(aliceWrap, relayA, "blocked: nope")
        h.refuse(bobWrap, relayB, "blocked: nope")

        assertTrue(h.stateOf(noteId)!!.isRefused)
    }

    @Test
    fun theSendersSelfCopyDoesNotDecideTheVerdict() {
        // The self-copy exists for multi-device sync; "did this reach the other person" is about the
        // other participants, refusals included.
        val h = Harness()
        val selfWrap = "e3".repeat(32)
        val aliceWrap = "e1".repeat(32)
        h.tracker.trackWrap(noteId, alice, aliceWrap, setOf(relayA))
        h.tracker.trackWrap(noteId, bob, selfWrap, setOf(relayB), isSelf = true)

        h.ok(selfWrap, relayB)
        h.refuse(aliceWrap, relayA, "blocked: nope")

        assertTrue(h.stateOf(noteId)!!.isRefused)
    }

    @Test
    fun refusalsForUntrackedEventsAreIgnored() {
        val h = Harness()
        h.refuse("ff".repeat(32), relayA, "blocked: nope")
        assertNull(h.stateOf("ff".repeat(32))?.rejections?.firstOrNull())
    }

    /** Minimal [INostrClient] (delegating to [EmptyNostrClient]) that just captures the listener. */
    private class CapturingClient(
        private val delegate: INostrClient = EmptyNostrClient(),
    ) : INostrClient by delegate {
        var listener: RelayConnectionListener? = null

        override fun addConnectionListener(listener: RelayConnectionListener) {
            this.listener = listener
        }

        override fun removeConnectionListener(listener: RelayConnectionListener) {
            if (this.listener === listener) this.listener = null
        }
    }
}

private fun relayClient(relayUrl: NormalizedRelayUrl): IRelayClient =
    object : IRelayClient {
        override val url = relayUrl

        override fun connect() = error("unused")

        override fun needsToReconnect() = error("unused")

        override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) = error("unused")

        override fun isConnected() = error("unused")

        override fun sendOrConnectAndSync(cmd: Command) = error("unused")

        override fun sendIfConnected(cmd: Command) = error("unused")

        override fun disconnect() = error("unused")
    }
