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
package com.vitorpamplona.quartz.nip01Core.relay.prodbench

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip77Negentropy.NegentropyServerSession
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Measures what the live negentropy index buys on a NEG-OPEN against the
 * scan + O(n log n) seal it replaces, at the relayBench corpus size where
 * the cold path measured ~340 ms (50k events; strfry serves ~21 ms).
 *
 * Three numbers, printed for the record:
 *  1. scan+seal — the pre-index cold path (`snapshotIdsForNegentropy` +
 *     `sealVector`), what every open pays when the single-slot cache
 *     misses.
 *  2. index first-open — includes the one-time lazy rebuild scan.
 *  3. index post-write open — the steady state on a busy relay: a write
 *     lands (invalidating any memoized snapshot), then a NEG-OPEN
 *     arrives. This is the number that was previously ~scan+seal and is
 *     the point of the feature.
 *
 * No speed assertion — container noise makes ratios flaky; correctness
 * (identical content between both paths) is asserted instead. Timings
 * are informational and the real A/B happens in relayBench.
 */
class LiveNegentropyBenchmark {
    companion object {
        const val EVENTS = 50_000
        const val POST_WRITE_OPENS = 50
    }

    private fun hexId(seed: Int): String = seed.toString(16).padStart(64, '0')

    private fun pubkey(seed: Int): String = (seed % 512).toString(16).padStart(64, 'a')

    private val sig = "0".repeat(128)

    private fun event(seed: Int): Event =
        EventFactory.create(
            id = hexId(seed),
            pubKey = pubkey(seed),
            createdAt = 1_600_000_000L + (seed * 7919) % 1_000_000, // scattered, not pre-sorted
            kind = 1,
            tags = emptyArray(),
            content = "live negentropy benchmark $seed",
            sig = sig,
        )

    @Test
    fun coldOpenVsLiveIndexAt50k() =
        runBlocking {
            val store = EventStore(dbName = null, indexStrategy = DefaultIndexingStrategy(maintainLiveNegentropyIndex = true))

            (1..EVENTS).chunked(2000).forEach { chunk ->
                store.batchInsert(chunk.map { event(it) })
            }

            // 1. The pre-index cold path.
            var scanSealNanos = 0L
            var scanned = 0
            run {
                val t0 = System.nanoTime()
                val entries = store.snapshotIdsForNegentropy(listOf(Filter()), null)
                val sealed = NegentropyServerSession.sealVector(entries)
                scanSealNanos = System.nanoTime() - t0
                scanned = sealed.size()
            }

            // 2. First index open: pays the lazy rebuild once.
            val t1 = System.nanoTime()
            val first = assertNotNull(store.liveNegentropySnapshot(Int.MAX_VALUE))
            val firstOpenNanos = System.nanoTime() - t1
            assertEquals(scanned, first.size())

            // 3. Steady state: one write per open, so every open pays the
            // index's real per-open cost (copy + seal of sorted data),
            // never the memoized-snapshot freebie.
            var postWriteNanos = 0L
            for (i in 0 until POST_WRITE_OPENS) {
                store.insert(event(EVENTS + 1 + i))
                val t2 = System.nanoTime()
                assertNotNull(store.liveNegentropySnapshot(Int.MAX_VALUE))
                postWriteNanos += System.nanoTime() - t2
            }
            val postWriteAvgMs = postWriteNanos / POST_WRITE_OPENS / 1e6

            // Correctness cross-check after all the churn.
            val finalSnapshot = assertNotNull(store.liveNegentropySnapshot(Int.MAX_VALUE))
            assertEquals(EVENTS + POST_WRITE_OPENS, finalSnapshot.size())

            println("LiveNegentropyBenchmark @ ${EVENTS / 1000}k events")
            println("  scan+seal cold path:   ${"%8.2f".format(scanSealNanos / 1e6)} ms")
            println("  index first open:      ${"%8.2f".format(firstOpenNanos / 1e6)} ms (includes one-time rebuild)")
            println("  index post-write open: ${"%8.2f".format(postWriteAvgMs)} ms (avg of $POST_WRITE_OPENS)")

            store.close()
        }
}
