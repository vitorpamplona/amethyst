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
    fun regularRejectionStillBoundedByTryCap() {
        val state = PoolEventOutboxState(fakeEvent(), setOf(relay))

        // 3 non-AUTH rejections accumulate normally.
        repeat(3) {
            state.newResponse(relay, success = false, message = "error: rate limited")
        }
        state.newTry(relay)

        // After the 4th newTry (with 3 prior responses already in flight), the
        // Tries cap kicks in and the relay is dropped from the outbox.
        assertFalse(state.relaysLeft().contains(relay))
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
}
