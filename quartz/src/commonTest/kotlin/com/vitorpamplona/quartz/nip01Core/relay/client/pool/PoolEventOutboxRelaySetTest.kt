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
import kotlin.test.assertEquals

/**
 * The relay set the pool keeps open must track what is actually pending.
 *
 * Additions are exact and immediate — a publish unions in its own relays.
 * Removals are swept in batches (see `PoolEventOutbox.pendingSweep`), because
 * retiring a relay means asking whether ANY remaining entry still wants it,
 * which is O(outbox); holding a connection slightly too long is cheaper than
 * scanning the whole backlog on every ack. Emptying the outbox forces the
 * sweep, so a finished push never strands a connection open.
 *
 * Pure bookkeeping, identical on every target — the per-publish cost curve
 * that motivated the batching is pinned separately in PoolEventOutboxScaleTest
 * (jvmAndroidTest, where a wall-clock ratio is a valid instrument).
 */
class PoolEventOutboxRelaySetTest {
    private fun event(i: Int) =
        Event(
            id = i.toString(16).padStart(64, '0'),
            pubKey = "00".repeat(32),
            createdAt = 1_700_000_000L,
            kind = 1,
            tags = emptyArray(),
            content = "hello",
            sig = "00".repeat(64),
        )

    @Test
    fun `the relay set still reflects what is pending`() {
        val outbox = PoolEventOutbox()
        val a = NormalizedRelayUrl("wss://a.relay.test")
        val b = NormalizedRelayUrl("wss://b.relay.test")

        outbox.markAsSending(event(1), setOf(a))
        assertEquals(setOf(a), outbox.relays.value, "a publish adds its relay immediately")

        outbox.markAsSending(event(2), setOf(b))
        assertEquals(setOf(a, b), outbox.relays.value, "a second relay joins without a rebuild")

        // Draining every entry must clear the set — the sweep is batched, but
        // emptying the outbox forces it, so a finished push does not strand a
        // connection open forever.
        outbox.newResponse(event(1).id, a, true, "")
        outbox.newResponse(event(2).id, b, true, "")
        assertEquals(emptySet(), outbox.relays.value, "an empty outbox wants no relays")
    }
}
