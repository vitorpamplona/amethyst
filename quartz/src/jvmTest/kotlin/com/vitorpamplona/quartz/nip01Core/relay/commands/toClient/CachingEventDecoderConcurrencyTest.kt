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
package com.vitorpamplona.quartz.nip01Core.relay.commands.toClient

import com.vitorpamplona.quartz.nip01Core.core.Event
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The lock-free decoder tolerates its documented benign races (double parse
 * of a first-seen id, rotation dropping a generation) but must NEVER return
 * a wrong message. This hammers one shared decoder from many threads — the
 * production shape: one decoder instance shared by every relay's consumer
 * coroutine — with duplicate-heavy interleaved streams and a small capacity
 * so rotations happen constantly, then checks every single result.
 */
class CachingEventDecoderConcurrencyTest {
    private fun hexId(seed: Int) = seed.toString(16).padStart(64, '0')

    private fun event(seed: Int) =
        Event(
            id = hexId(seed),
            pubKey = hexId(seed + 500_000),
            createdAt = seed.toLong(),
            kind = 1,
            tags = arrayOf(arrayOf("t", "conc")),
            content = "concurrency test $seed",
            sig = "f".repeat(128),
        )

    @Test
    fun manyThreadsNeverReceiveAWrongMessage() {
        val uniques = 2_000
        val threads = 8
        val perThreadFrames = 10_000

        val events = (1..uniques).map { event(it) }
        // tiny capacity → constant rotation → the benign races actually fire
        val decoder = CachingEventDecoder(capacity = 256)

        val wrongResults = AtomicInteger(0)
        val decoded = AtomicInteger(0)

        val workers =
            (1..threads).map { t ->
                Thread {
                    val rnd = Random(t)
                    repeat(perThreadFrames) { i ->
                        val expected = events[rnd.nextInt(uniques)]
                        val subId = "sub-$t-${i % 7}"
                        val msg = decoder.decode("""["EVENT","$subId",${expected.toJson()}]""")
                        if (msg !is EventMessage ||
                            msg.subId != subId ||
                            msg.event.id != expected.id ||
                            msg.event.content != expected.content
                        ) {
                            wrongResults.incrementAndGet()
                        }
                        decoded.incrementAndGet()
                    }
                }
            }
        workers.forEach { it.start() }
        workers.forEach { it.join() }

        assertEquals(0, wrongResults.get(), "a race must never produce a wrong message")
        assertEquals(threads * perThreadFrames, decoded.get())
        assertEquals(
            decoded.get().toLong(),
            decoder.parsedCount + decoder.reusedCount,
            "every frame is either parsed or reused",
        )
        // Sanity that the cache functions under constant rotation. With
        // uniform random access over 2000 ids and only 2×256 cached slots,
        // the theoretical hit rate is ~25%; assert comfortably above zero
        // (wholesale cache failure) without flaking on scheduling luck.
        assertTrue(decoder.reusedCount > decoded.get() / 8, "cache should serve a meaningful share of duplicate frames")
    }
}
