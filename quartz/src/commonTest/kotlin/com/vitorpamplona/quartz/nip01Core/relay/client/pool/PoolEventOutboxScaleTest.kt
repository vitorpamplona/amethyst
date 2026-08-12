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
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * The outbox must not get slower as it fills.
 *
 * It used to: the map was immutable and every `markAsSending` rebuilt it with
 * `eventOutbox + Pair(...)`, so publishing N events copied 1 + 2 + … + N
 * entries. Measured on a real bulk push at ~970k entries resident, that was
 * ~20.5ms of the 22.7ms each event cost, and the rate visibly decayed as the
 * backlog grew (45.6 -> 44.6 -> 43.2 ev/s over three windows). The relay-set
 * bookkeeping was the other half — two full scans of every entry, per publish.
 *
 * This asserts the SHAPE of the cost rather than a wall-clock budget: a
 * quadratic makes the second half of a run dramatically slower than the first,
 * whatever the machine. A constant factor cannot be pinned in a unit test, but
 * a growth curve can.
 */
class PoolEventOutboxScaleTest {
    private val relay = NormalizedRelayUrl("wss://scale.relay.test")

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
    fun `publishing stays flat as the outbox fills`() {
        val outbox = PoolEventOutbox()
        val relays = setOf(relay)
        val clock = TimeSource.Monotonic
        val sample = 2_000
        val total = 60_000

        fun publishRange(
            from: Int,
            until: Int,
        ) {
            for (i in from until until) outbox.markAsSending(event(i), relays)
        }

        // Equal-sized windows at the START and the END of a long run. Halves
        // would not do: over 20k publishes the average backlog only grows from
        // ~7k to ~17k, a 2.4x expected ratio that hides inside JIT noise. Here
        // the late window carries ~29x the backlog of the early one, so a
        // per-entry cost shows up as a per-entry cost.
        repeat(sample) { outbox.markAsSending(event(it), relays) } // warm up
        val early =
            clock.markNow().let { start ->
                publishRange(sample, sample * 2)
                start.elapsedNow()
            }
        publishRange(sample * 2, total - sample)
        val late =
            clock.markNow().let { start ->
                publishRange(total - sample, total)
                start.elapsedNow()
            }

        assertEquals(total, outbox.activeOutboxCacheFor(relay).size, "every publish is tracked")

        val ratio = late.inWholeMicroseconds.toDouble() / early.inWholeMicroseconds.coerceAtLeast(1)
        assertTrue(
            ratio < 5.0,
            "cost per publish must not grow with the backlog: first $sample took ${early.inWholeMilliseconds}ms at " +
                "~$sample entries, last $sample took ${late.inWholeMilliseconds}ms at ~$total entries (ratio $ratio)",
        )
    }

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
