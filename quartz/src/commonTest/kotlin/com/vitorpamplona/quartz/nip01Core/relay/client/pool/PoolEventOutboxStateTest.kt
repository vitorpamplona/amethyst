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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PoolEventOutboxStateTest {
    private val relay = NormalizedRelayUrl("wss://relay.example/")

    private fun fakeEvent() =
        Event(
            id = "0".repeat(64),
            pubKey = "0".repeat(64),
            createdAt = 0L,
            kind = 1,
            tags = emptyArray(),
            content = "",
            sig = "0".repeat(128),
        )

    @Test
    fun authRequiredResponseDoesNotConsumeTryBudget() {
        val state = PoolEventOutboxState(fakeEvent(), setOf(relay))

        // Simulate 5 `auth-required:` responses — relay keeps challenging while
        // RelayAuthenticator signs + sends AUTH events asynchronously. None of
        // these should be counted against the 3-response try cap.
        repeat(5) {
            state.newResponse(relay, success = false, message = "auth-required: please authenticate")
        }

        // Even after a follow-up newTry, the relay must remain in the outbox so
        // syncFilters() can re-publish once AUTH succeeds.
        state.newTry(relay)
        assertContains(state.relaysLeft(), relay)
        assertFalse(state.isDone())
    }

    @Test
    fun anyRefusalEndsDelivery() {
        // An OK false is the relay's verdict: re-sending it on every reconnect only draws the same
        // answer, and kept a refused event resident until three of them had piled up.
        for (message in listOf("error: insert failed", "blocked: kind not accepted", "restricted: no", "rate-limited: slow down", "duplicate: have it")) {
            val state = PoolEventOutboxState(fakeEvent(), setOf(relay))
            state.newTry(relay)
            state.newResponse(relay, success = false, message = message)
            assertFalse(state.relaysLeft().contains(relay), "'$message' should end delivery")
            assertTrue(state.isDone(), "'$message' should end delivery")
        }
    }

    @Test
    fun aRateLimitWithItsBackOffStaysPending() {
        val state = PoolEventOutboxState(fakeEvent(), setOf(relay))
        state.newTry(relay)
        state.newResponse(relay, success = false, message = "rate-limited: slow down", retryAfterMs = 1_000)
        assertContains(state.relaysLeft(), relay)
        assertFalse(state.isDone())
    }

    @Test
    fun aRateLimitThatNeverEndsIsTakenAtItsWord() {
        val state = PoolEventOutboxState(fakeEvent(), setOf(relay))
        repeat(PoolEventOutboxState.MAX_BACK_OFFS) {
            state.newResponse(relay, success = false, message = "rate-limited: slow down", retryAfterMs = 1_000)
        }
        assertContains(state.relaysLeft(), relay)
        state.newResponse(relay, success = false, message = "rate-limited: slow down", retryAfterMs = 1_000)
        assertTrue(state.isDone())
    }

    @Test
    fun unansweredSendsStillGiveUpAfterTheirBudget() {
        // What the outbox is for: a send the connection lost, retried on reconnect, a bounded number of times.
        val state = PoolEventOutboxState(fakeEvent(), setOf(relay))
        repeat(3) { assertFalse(state.newTry(relay), "send ${it + 1} is within the budget") }
        assertTrue(state.newTry(relay), "the fourth unanswered send gives up")
        assertTrue(state.isDone())
    }

    @Test
    fun terminalRejectionImmediatelyDropsRelay() {
        val state = PoolEventOutboxState(fakeEvent(), setOf(relay))

        state.newResponse(relay, success = false, message = "invalid: malformed event")

        assertFalse(state.relaysLeft().contains(relay))
        assertTrue(state.isDone())
    }

    @Test
    fun successDropsRelayFromOutbox() {
        val state = PoolEventOutboxState(fakeEvent(), setOf(relay))

        state.newResponse(relay, success = true, message = "")

        assertEquals(emptySet(), state.relaysLeft())
        assertTrue(state.isDone())
    }

    @Test
    fun anAuthRequiredNobodyCanAnswerIsTheVerdict() {
        // No responder attached, or this relay's last AUTH failed: no AUTH will ever unlock the event.
        val state = PoolEventOutboxState(fakeEvent(), setOf(relay))
        state.newTry(relay)
        state.newResponse(relay, success = false, message = "auth-required: sign in", authMayLand = { false })
        assertTrue(state.isDone())
    }

    @Test
    fun anAuthRequiredThatNeverLandsIsTakenAtItsWord() {
        // Each answer used to reset the budget, so an AUTH nobody signs kept the event forever.
        val state = PoolEventOutboxState(fakeEvent(), setOf(relay))
        repeat(PoolEventOutboxState.MAX_AUTH_WAITS) {
            assertFalse(state.newTry(relay), "the send budget is not what ends it")
            state.newResponse(relay, success = false, message = "auth-required: sign in")
        }
        assertContains(state.relaysLeft(), relay)
        state.newTry(relay)
        state.newResponse(relay, success = false, message = "auth-required: sign in")
        assertTrue(state.isDone())
    }

    @Test
    fun aFailedConnectionIsNotAVerdictAndKeepsTheEventPending() {
        // A relay that restarts fails a dial or two: the event must still be there to re-send.
        val state = PoolEventOutboxState(fakeEvent(), setOf(relay))
        state.newTry(relay)
        repeat(PoolEventOutboxState.MAX_CONNECT_FAILURES - 1) {
            assertFalse(state.cannotConnect(relay), "failure ${it + 1} gives up nothing")
        }
        assertContains(state.relaysLeft(), relay)
        assertFalse(state.isDone())
    }

    @Test
    fun aRelayThatNeverComesBackIsGivenUpOn() {
        val state = PoolEventOutboxState(fakeEvent(), setOf(relay))
        repeat(PoolEventOutboxState.MAX_CONNECT_FAILURES - 1) { state.cannotConnect(relay) }
        assertTrue(state.cannotConnect(relay), "the last failure in the budget gives up on the relay")
        assertTrue(state.isDone())
        assertFalse(state.cannotConnect(relay), "and a relay it is no longer owed to is nothing to give up")
    }

    @Test
    fun theRelaysAnswerAfterAFailedConnectionStillDecides() {
        val state = PoolEventOutboxState(fakeEvent(), setOf(relay))
        state.cannotConnect(relay)
        state.newResponse(relay, success = true, message = "")
        assertTrue(state.isDone(), "an OK on the next connection ends delivery as always")
    }
}
