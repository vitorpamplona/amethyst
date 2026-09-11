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

/**
 * One measured operation.
 *
 * Latency percentiles AND allocation, because the two answer different
 * questions and only one of them is visible in a wall-clock number. A JVM that
 * allocates 40x per operation can still win a microbenchmark — the cost shows
 * up later as GC pauses on a phone, which is exactly what we are trying not to
 * ship.
 */
class BenchResult(
    val name: String,
    val samples: LongArray,
    val bytesPerOp: Long,
    val iterations: Int,
) {
    private fun percentile(p: Double): Long {
        val sorted = samples.sortedArray()
        val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    val p50 get() = percentile(0.50)
    val p90 get() = percentile(0.90)
    val p99 get() = percentile(0.99)
    val mean get() = if (samples.isEmpty()) 0L else samples.sum() / samples.size
}
