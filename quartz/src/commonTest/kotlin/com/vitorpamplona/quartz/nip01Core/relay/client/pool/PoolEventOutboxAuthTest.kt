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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression coverage for NIP-42 handling in the outgoing-event retry queue.
 *
 * An `auth-required` NAK must be treated as "deferred pending AUTH", never as a
 * retry that eats the [PoolEventOutboxState.Tries] budget — otherwise a relay
 * that NAKs every unauthenticated EVENT would drop the message before the AUTH
 * handshake completes.
 */
class PoolEventOutboxAuthTest {
    private val relay = NormalizedRelayUrl("wss://auth.relay.test")

    private fun event(id: String) =
        Event(
            id = id,
            pubKey = "00".repeat(32),
            createdAt = 1_700_000_000L,
            kind = 1,
            tags = emptyArray(),
            content = "hello",
            sig = "00".repeat(64),
        )

    private fun PoolEventOutbox.publish(
        event: Event,
        relays: Set<NormalizedRelayUrl>,
    ) {
        markAsSending(event, relays)
        // simulate the actual EVENT frame going out on the wire
        relays.forEach { onSent(it, EventCmd(event)) }
    }

    private fun PoolEventOutbox.nak(
        event: Event,
        relay: NormalizedRelayUrl,
        message: String,
    ) = onIncomingMessage(relay, OkMessage(event.id, false, message))

    private fun PoolEventOutbox.ok(
        event: Event,
        relay: NormalizedRelayUrl,
    ) = onIncomingMessage(relay, OkMessage(event.id, true, ""))

    @Test
    fun authRequiredSurvivesResendAfterAuth() =
        runTest {
            val outbox = PoolEventOutbox()
            val ev = event("aa".repeat(32))

            outbox.publish(ev, setOf(relay))

            // Relay NAKs every unauthed EVENT with auth-required, more times than the
            // 3-response retry budget. These must NOT be recorded as failures, or the
            // post-auth resend below would trip Tries.isDone() and drop the event.
            repeat(3) { outbox.nak(ev, relay, "auth-required: we can't serve unauthed writes") }
            assertEquals(setOf(relay), outbox.pendingRelaysFor(ev.id))

            // AUTH completes -> syncFilters re-sends the still-pending event.
            val resent = mutableListOf<Command>()
            outbox.syncState(relay) { resent.add(it) }
            assertEquals(1, resent.size)
            assertTrue(resent.first() is EventCmd)

            // The resend records a new try. With the pre-fix behavior the poisoned
            // budget would drop the event right here; it must still be pending.
            outbox.onSent(relay, resent.first())
            assertEquals(setOf(relay), outbox.pendingRelaysFor(ev.id))

            // Relay now accepts the authenticated event.
            outbox.ok(ev, relay)
            assertNull(outbox.pendingRelaysFor(ev.id))
        }

    @Test
    fun terminalRejectionStillDiscardsImmediately() {
        val outbox = PoolEventOutbox()
        val ev = event("cc".repeat(32))

        outbox.publish(ev, setOf(relay))
        outbox.nak(ev, relay, "invalid: bad signature")

        assertNull(outbox.pendingRelaysFor(ev.id))
    }

    @Test
    fun ordinaryTransientFailureStillBounded() {
        val outbox = PoolEventOutbox()
        val ev = event("dd".repeat(32))

        outbox.publish(ev, setOf(relay))
        // 3 non-auth error responses exhaust the retry budget. Responses only
        // accumulate here; the drop happens on the next send attempt.
        repeat(3) { outbox.nak(ev, relay, "error: rate-limited") }
        assertEquals(setOf(relay), outbox.pendingRelaysFor(ev.id))

        // The next resend attempt observes the exhausted budget and drops the event
        // (unlike auth-required, which never poisons the budget).
        outbox.onSent(relay, EventCmd(ev))
        assertNull(outbox.pendingRelaysFor(ev.id))
    }
}
