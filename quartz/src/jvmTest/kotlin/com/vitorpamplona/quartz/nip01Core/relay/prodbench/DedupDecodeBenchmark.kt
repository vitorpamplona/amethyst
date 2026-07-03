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

import com.vitorpamplona.geode.fixtures.SyntheticEvents
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CachingEventDecoder
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MessageDecoder
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves the [CachingEventDecoder] saves what it claims at production
 * duplicate factors (14–57% duplicate frames measured; see
 * `quartz/plans/2026-07-02-nostrclient-receiver-perf.md`): a duplicate frame
 * pays an id scan (~sub-µs) instead of a full JSON parse.
 *
 * Offline and deterministic: same frame stream through
 * [MessageDecoder.Default] and through [CachingEventDecoder]. The stream
 * interleaves 3 deliveries of each event (one per "subscription"), matching
 * how overlapping subs replay the same event on one connection.
 */
class DedupDecodeBenchmark {
    companion object {
        const val UNIQUE_EVENTS = 20_000
        const val DELIVERIES_PER_EVENT = 3 // ~66% duplicate frames
    }

    private fun buildFrames(): List<String> {
        val events =
            (1..UNIQUE_EVENTS).map {
                SyntheticEvents.fakeEvent(
                    idSeed = it,
                    kind = 1,
                    pubKey = SyntheticEvents.hexId(it % 500 + 1),
                    createdAt = it.toLong(),
                    content = "dedup decode benchmark payload $it ".repeat(6),
                )
            }
        return buildList {
            repeat(DELIVERIES_PER_EVENT) { sub ->
                events.forEach { add("""["EVENT","sub$sub",${it.toJson()}]""") }
            }
        }
    }

    private fun run(
        decoder: MessageDecoder,
        frames: List<String>,
    ): Long {
        val start = System.nanoTime()
        frames.forEach { decoder.decode(it) }
        return System.nanoTime() - start
    }

    @Test
    fun duplicateFramesDecodeFasterThroughTheCache() {
        val frames = buildFrames()
        val dupShare = 1.0 - 1.0 / DELIVERIES_PER_EVENT

        // warmup both paths
        run(MessageDecoder.Default, frames.take(20_000))
        run(CachingEventDecoder(capacity = UNIQUE_EVENTS * 2), frames.take(20_000))

        val fullNanos = run(MessageDecoder.Default, frames)

        val caching = CachingEventDecoder(capacity = UNIQUE_EVENTS * 2)
        val cachedNanos = run(caching, frames)

        val speedup = fullNanos.toDouble() / cachedNanos
        println("=== DEDUP DECODE BENCHMARK (${frames.size} frames, %.0f%% duplicates) ===".format(dupShare * 100))
        println(
            "  full parse every frame: %.1fms (%.1fµs/frame)"
                .format(fullNanos / 1e6, fullNanos / 1e3 / frames.size),
        )
        println(
            "  caching decoder:        %.1fms (%.1fµs/frame)  parsed=%d reused=%d  -> %.2fx"
                .format(cachedNanos / 1e6, cachedNanos / 1e3 / frames.size, caching.parsedCount, caching.reusedCount, speedup),
        )

        assertTrue(
            caching.parsedCount == UNIQUE_EVENTS.toLong() &&
                caching.reusedCount == (frames.size - UNIQUE_EVENTS).toLong(),
            "every duplicate must hit the cache",
        )
        // The claim that justifies the code: at a 66% duplicate share the
        // caching decoder must be meaningfully faster than parsing everything.
        // Generous threshold (1.5x) so CI noise doesn't flake; measured ~2.5-3x.
        assertTrue(speedup > 1.5, "expected >1.5x speedup, got %.2fx".format(speedup))
    }
}
