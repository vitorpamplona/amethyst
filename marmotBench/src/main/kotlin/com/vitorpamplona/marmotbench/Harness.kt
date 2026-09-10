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
package com.vitorpamplona.marmotbench

import java.lang.management.ManagementFactory

/**
 * Bytes this thread has allocated, cumulative.
 *
 * `com.sun.management.ThreadMXBean` is in the JDK, so measuring GC pressure
 * costs no dependency. It counts TLAB allocation for the CALLING thread only,
 * which is why every benchmark body runs inline on the harness thread rather
 * than on a dispatcher — work handed to a coroutine on another thread would
 * allocate off-book and read as free.
 */
private val threadMx = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

private fun allocatedBytes(): Long = threadMx.getThreadAllocatedBytes(Thread.currentThread().threadId())

/**
 * Run [body] until the numbers stop being about JIT.
 *
 * `setup` runs OUTSIDE the measured window and its cost is excluded, mirroring
 * criterion's `iter_batched` with `BatchSize::PerIteration` — which is what
 * MDK's engine benches use, so the two sides measure the same span of work.
 */
fun <S> measure(
    name: String,
    iterations: Int = 200,
    warmup: Int = 50,
    setup: () -> S,
    body: (S) -> Unit,
): BenchResult {
    repeat(warmup) { body(setup()) }

    // A collection here rather than inside the measured window: the harness
    // runs SerialGC on a big heap precisely so this is the last one.
    System.gc()
    Thread.sleep(50)

    val samples = LongArray(iterations)
    var allocated = 0L
    repeat(iterations) { i ->
        val state = setup()
        val allocBefore = allocatedBytes()
        val start = System.nanoTime()
        body(state)
        samples[i] = System.nanoTime() - start
        allocated += allocatedBytes() - allocBefore
    }
    return BenchResult(name, samples, allocated / iterations, iterations)
}
