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
import kotlin.time.Duration
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
 *
 * ## Why this lives in jvmAndroidTest and not in commonTest
 *
 * The instrument only works on a runtime whose GC cost does not scale with the
 * retained set. This test deliberately keeps 60k entries alive, so the late
 * window measures a heap ~30x larger than the early one. A generational
 * collector (HotSpot, ART) does not rescan that old generation on a young
 * collection, so the growth stays invisible and the ratio reflects the outbox.
 * Kotlin/Native's non-generational tracing GC does rescan it, and the ratio
 * then reflects the GC instead.
 *
 * Measured on Kotlin/Native (linuxX64, `-opt`), publishing the same 60k events
 * into structures that are O(1) per put by construction:
 *
 * ```
 * retains nothing                       ratio 0.51 - 0.80
 * one HashMap, 60k live                 ratio 1.93 - 3.39
 * two HashMaps per put, 60k live        ratio 2.28 - 5.21
 * ```
 *
 * The last row is what Apple targets actually run: LargeCache there wraps
 * charlietap's CacheMap, whose LeftRight `mutate` applies each write to both
 * of its two maps under a lock — O(1), no copying. It still crossed the 5.0
 * threshold on a loaded machine, which is how this failed on the iOS simulator
 * without anything being wrong with the outbox. The `retains nothing` row is
 * the control: same allocations, nothing kept alive, ratio flat.
 *
 * So on Apple the O(1) guarantee comes from the data structure by
 * construction and does not need pinning here; on JVM/Android it comes from
 * LargeCache being a ConcurrentHashMap, and a regression in this class
 * (someone reintroducing a copy-on-write map, or a per-publish full scan)
 * shows up cleanly. Note that Kotlin/Native's linuxX64 LargeCache IS
 * copy-on-write today — that is a real cost, but not one a wall-clock ratio
 * can report reliably, as the numbers above show.
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
        // the late windows carry ~10-29x the backlog of the early ones, so a
        // per-entry cost shows up as a per-entry cost.
        //
        // Each side is the MINIMUM of three consecutive windows: a single
        // window is one GC pause away from a false 5x on a shared CI runner
        // (seen on the macos-latest 3-core VM), and the same GC-dominance
        // reasoning already retired this assertion on Apple targets. The min
        // keeps the intent — a real per-entry cost slows every window, a
        // stop-the-world pause only one.
        fun timedWindow(from: Int): Duration {
            val start = clock.markNow()
            publishRange(from, from + sample)
            return start.elapsedNow()
        }

        repeat(sample) { outbox.markAsSending(event(it), relays) } // warm up
        val early = (0 until 3).minOf { timedWindow(sample + it * sample) }
        publishRange(sample * 4, total - sample * 3)
        val late = (0 until 3).minOf { timedWindow(total - sample * (3 - it)) }

        assertEquals(total, outbox.activeOutboxCacheFor(relay).size, "every publish is tracked")

        val ratio = late.inWholeMicroseconds.toDouble() / early.inWholeMicroseconds.coerceAtLeast(1)
        assertTrue(
            ratio < 5.0,
            "cost per publish must not grow with the backlog: first $sample took ${early.inWholeMilliseconds}ms at " +
                "~$sample entries, last $sample took ${late.inWholeMilliseconds}ms at ~$total entries (ratio $ratio)",
        )
    }
}
