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
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.IngestQueue
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Isolates the receipt➜queryable latency that relayBench measured geode
 * losing to strfry (1M corpus: geode 4.68 ms p50 vs strfry 2.32 ms). The
 * probe there publishes ONE event to an idle relay and polls REQ until it
 * comes back, so what it times is a single event's trip through the ingest
 * pipeline — where geode's group-commit [IngestQueue] pays two channel
 * handoffs (submit→verifier→writer) for zero batching benefit.
 *
 * This times `submit → onComplete` (which fires *after* COMMIT, so it's the
 * moment the row is queryable) for isolated single events, against a direct
 * `store.batchInsert(listOf(e))` — the delta is exactly the pipeline's
 * coroutine-handoff overhead, the thing a single-event fast path would save.
 *
 * Prints percentiles; no speed assertion (container-noisy).
 */
class IngestLatencyBenchmark {
    private val hexChars = "0123456789abcdef".toCharArray()

    private fun mix(seed: Long): Long {
        var z = seed + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    private fun idFor(index: Int): String {
        val out = CharArray(64)
        for (w in 0 until 4) {
            val v = mix(index.toLong() * 4 + w)
            for (b in 0 until 8) {
                val byte = ((v ushr (b * 8)) and 0xFF).toInt()
                out[(w * 8 + b) * 2] = hexChars[byte ushr 4]
                out[(w * 8 + b) * 2 + 1] = hexChars[byte and 0xF]
            }
        }
        return String(out)
    }

    private val pubkey = "00".repeat(32)
    private val sig = "0".repeat(128)

    private fun event(i: Int): Event = EventFactory.create(idFor(i), pubkey, 1_700_000_000L + i, 1, emptyArray(), "note $i", sig)

    private fun pct(
        sorted: DoubleArray,
        p: Double,
    ) = sorted[(p * (sorted.size - 1)).toInt()]

    @Test
    fun singleEventVisibilityLatency() =
        runBlocking {
            val warmup = 500
            val runs = 3_000

            // ── Path A: through the group-commit IngestQueue (production). ──
            val storeA = EventStore(dbName = null, indexStrategy = DefaultIndexingStrategy())
            val queue = IngestQueue(storeA, Dispatchers.Default + SupervisorJob())
            val queueLat = DoubleArray(runs)
            var idx = 0
            repeat(warmup + runs) { i ->
                val done = CompletableDeferred<IEventStore.InsertOutcome>()
                val t0 = System.nanoTime()
                queue.submit(event(idx++)) { done.complete(it) }
                done.await()
                val dt = (System.nanoTime() - t0) / 1e6
                if (i >= warmup) queueLat[i - warmup] = dt
            }
            queue.close()
            storeA.close()

            // ── Path B: direct single-event batchInsert (no pipeline). ──
            val storeB = EventStore(dbName = null, indexStrategy = DefaultIndexingStrategy())
            val directLat = DoubleArray(runs)
            repeat(warmup + runs) { i ->
                val t0 = System.nanoTime()
                storeB.batchInsert(listOf(event(idx++)))
                val dt = (System.nanoTime() - t0) / 1e6
                if (i >= warmup) directLat[i - warmup] = dt
            }
            storeB.close()

            queueLat.sort()
            directLat.sort()
            println("─ IngestLatencyBenchmark: single isolated events (idle relay), $runs samples ─")
            println("  path                     p50        p90        p99")
            println(
                "  IngestQueue (submit→OK)  ${"%6.3f".format(pct(queueLat, 0.50))} ms  ${"%6.3f".format(pct(queueLat, 0.90))} ms  ${"%6.3f".format(pct(queueLat, 0.99))} ms",
            )
            println(
                "  direct batchInsert       ${"%6.3f".format(pct(directLat, 0.50))} ms  ${"%6.3f".format(pct(directLat, 0.90))} ms  ${"%6.3f".format(pct(directLat, 0.99))} ms",
            )
            println("  pipeline overhead p50:   ${"%.3f".format(pct(queueLat, 0.50) - pct(directLat, 0.50))} ms")
        }
}
