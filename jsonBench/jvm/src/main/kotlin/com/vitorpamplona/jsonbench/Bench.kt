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
package com.vitorpamplona.jsonbench

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import java.lang.ref.Reference

/**
 * One parser under test. Every concrete parser is its own final class, so the hot loop in
 * [parseAll] calls exactly one library entry point (monomorphic for the JIT), and each one
 * runs in a child JVM of its own (see Main.kt).
 *
 * @param I the input form the parser consumes (HARNESS.md "Input forms").
 */
abstract class BenchParser<I : Any> {
    /** Converts the raw lines into this parser's input form. Untimed. */
    abstract fun prepareAll(lines: List<ByteArray>): Array<I>

    /**
     * Parses one input and returns it as an [Event], for the digest. Untimed. Returns null
     * for baselines, which are not parsers.
     */
    abstract fun decode(input: I): Event?

    /**
     * Parses one input into the object an app would keep (the parser's own result, e.g.
     * Quartz's Event subclass), for the retained-memory measurement. Untimed. Defaults to
     * [decode]; null for baselines.
     */
    open fun keep(input: I): Any? = decode(input)

    /**
     * The timed loop: parses every input once, in order, and folds something from every
     * result into the returned sink so the JIT cannot drop the work.
     */
    abstract fun parseAll(inputs: Array<I>): Long
}

/**
 * Every parser starts from a [String]: that is what a WebSocket text frame is on the JVM
 * (OkHttp's `onMessage(webSocket, text: String)`), so whatever a parser needs to turn the
 * String into its own input is part of the parse and runs inside the timed loop.
 */
abstract class StringInputParser : BenchParser<String>() {
    override fun prepareAll(lines: List<ByteArray>): Array<String> = Array(lines.size) { String(lines[it], Charsets.UTF_8) }
}

/**
 * A parser that reads UTF-8 bytes. It is handed the frame's String like everyone else and
 * encodes it with [utf8] inside its timed loop, the adaptation an app on OkHttp would make.
 */
abstract class BytesInputParser : StringInputParser() {
    /** Parses one already-encoded input and returns it as an [Event], for the digest. */
    abstract fun decodeBytes(input: ByteArray): Event?

    final override fun decode(input: String): Event? = decodeBytes(utf8(input))

    protected fun utf8(s: String): ByteArray = s.toByteArray(Charsets.UTF_8)
}

/** A line encoded into a buffer with [PADDING] spare bytes after it; [len] is the JSON length. */
class Padded(
    val buf: ByteArray,
    val len: Int,
)

/** A SIMD parser that needs [PADDING] readable bytes past the JSON: encodes, then pads. */
abstract class PaddedInputParser : StringInputParser() {
    abstract fun decodePadded(input: Padded): Event?

    final override fun decode(input: String): Event? = decodePadded(padded(input))

    protected fun padded(s: String): Padded {
        val raw = s.toByteArray(Charsets.UTF_8)
        return Padded(raw.copyOf(raw.size + PADDING), raw.size)
    }

    companion object {
        const val PADDING = 64
    }
}

/** Raw UTF-8 lines, for the one baseline that measures what decoding a frame into a String costs. */
abstract class RawBytesInputParser : BenchParser<ByteArray>() {
    override fun prepareAll(lines: List<ByteArray>): Array<ByteArray> = Array(lines.size) { lines[it] }
}

class Settings(
    val warmupMs: Long,
    val minTimeMs: Long,
    val minRounds: Int,
) {
    companion object {
        const val MAX_ROUNDS = 10_000
    }
}

class Verification(
    val digest: String?,
    val errors: Int,
    val firstError: String?,
)

class Measurement(
    val rounds: Int,
    val medianNs: Long,
    val minNs: Long,
    val p90Ns: Long,
    val allocBytes: Long?,
    val gcCount: Long?,
    val gcMs: Long?,
)

object Runner {
    /** Keeps every round's sink alive; printed at the end of the child so nothing is dead code. */
    @JvmStatic
    @Volatile
    var blackhole: Long = 0

    fun <I : Any> verify(
        parser: BenchParser<I>,
        inputs: Array<I>,
    ): Verification {
        val digest = EventDigest()
        var errors = 0
        var firstError: String? = null
        var baseline = false
        for ((index, input) in inputs.withIndex()) {
            try {
                val event = parser.decode(input)
                if (event == null) {
                    baseline = true
                    break
                }
                digest.update(event)
            } catch (t: Throwable) {
                errors++
                if (firstError == null) firstError = "event #$index: ${t.javaClass.name}: ${t.message?.take(300)}"
            }
        }
        return Verification(if (baseline) null else digest.hex(), errors, firstError)
    }

    /**
     * Heap still in use after parsing every input of the workload and keeping all results
     * alive, as a client keeps the events it shows (HARNESS.md "retained"). This is where
     * de-duplication such as String.intern() pays off: equal pubkeys, ids and tag values
     * are then one object instead of one per event. Median of three runs, each measured
     * between full collections; the array holding the results is not counted.
     */
    fun <I : Any> retained(
        parser: BenchParser<I>,
        inputs: Array<I>,
    ): Long? {
        if (parser.keep(inputs[0]) == null) return null
        val runs = LongArray(3)
        for (r in runs.indices) {
            val kept = arrayOfNulls<Any>(inputs.size)
            fullGc()
            val before = usedHeap()
            for (i in inputs.indices) kept[i] = parser.keep(inputs[i])
            fullGc()
            val after = usedHeap()
            Reference.reachabilityFence(kept)
            runs[r] = after - before
        }
        runs.sort()
        return runs[1]
    }

    private fun usedHeap(): Long = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used

    /** Collects until the used heap stops shrinking, so soft garbage doesn't count. */
    private fun fullGc() {
        var last = Long.MAX_VALUE
        repeat(10) {
            System.gc()
            val used = usedHeap()
            if (used >= last) return
            last = used
        }
    }

    fun <I : Any> measure(
        parser: BenchParser<I>,
        inputs: Array<I>,
        settings: Settings,
    ): Measurement {
        var sink = 0L

        // Warm-up: whole rounds for at least warmupMs (and at least one round).
        val warmupEnd = System.nanoTime() + settings.warmupMs * 1_000_000
        do {
            sink += parser.parseAll(inputs)
        } while (System.nanoTime() < warmupEnd)

        // Start the measurement from a collected heap, JMH-style, so the GC numbers belong
        // to the measured rounds rather than to the warm-up's leftovers.
        System.gc()

        val times = LongArray(Settings.MAX_ROUNDS)
        val minTimeNs = settings.minTimeMs * 1_000_000
        val threads = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        val threadId = Thread.currentThread().threadId()
        val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()

        val gcCountBefore = gcBeans.sumOf { it.collectionCount.coerceAtLeast(0) }
        val gcTimeBefore = gcBeans.sumOf { it.collectionTime.coerceAtLeast(0) }
        val allocBefore = threads?.getThreadAllocatedBytes(threadId) ?: -1

        var rounds = 0
        val start = System.nanoTime()
        while (rounds < Settings.MAX_ROUNDS) {
            val t0 = System.nanoTime()
            sink += parser.parseAll(inputs)
            val t1 = System.nanoTime()
            times[rounds++] = t1 - t0
            if (rounds >= settings.minRounds && t1 - start >= minTimeNs) break
        }

        val allocAfter = threads?.getThreadAllocatedBytes(threadId) ?: -1
        val gcCountAfter = gcBeans.sumOf { it.collectionCount.coerceAtLeast(0) }
        val gcTimeAfter = gcBeans.sumOf { it.collectionTime.coerceAtLeast(0) }

        blackhole += sink

        val sorted = times.copyOf(rounds).also { it.sort() }
        val median =
            if (rounds % 2 == 1) {
                sorted[rounds / 2]
            } else {
                (sorted[rounds / 2 - 1] + sorted[rounds / 2]) / 2
            }
        val p90Index = (Math.ceil(0.9 * rounds).toInt() - 1).coerceIn(0, rounds - 1)

        return Measurement(
            rounds = rounds,
            medianNs = median,
            minNs = sorted[0],
            p90Ns = sorted[p90Index],
            allocBytes = if (allocBefore >= 0 && allocAfter >= 0) allocAfter - allocBefore else null,
            gcCount = if (gcBeans.isEmpty()) null else gcCountAfter - gcCountBefore,
            gcMs = if (gcBeans.isEmpty()) null else gcTimeAfter - gcTimeBefore,
        )
    }
}
