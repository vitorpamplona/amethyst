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
package com.vitorpamplona.quartz.utils.secp256k1

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Field-level micro-benchmark for the Fe4-based secp256k1 implementation.
 *
 * Measures the core field operations using Fe4 struct types (no array bounds checks).
 * Compare against the pre-migration LongArray baseline to verify the performance gain.
 *
 * Pre-migration baseline (LongArray, HotSpot C2):
 *   FieldP.mul:  40 ns/op
 *   FieldP.sqr:  48 ns/op
 *   FieldP.add:   9 ns/op
 *   FieldP.sub:  10 ns/op
 *
 * Run with: ./gradlew :quartz:jvmTest --tests "*.Fe4Benchmark"
 */
class Fe4Benchmark {
    private val aBytes = hexToBytes("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798")
    private val bBytes = hexToBytes("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8")

    private val a = U256.fromBytes(aBytes)
    private val b = U256.fromBytes(bBytes)
    private val out = Fe4()
    private val w = Wide8()

    private data class BenchResult(
        val name: String,
        val nanos: Long,
        val iterations: Int,
    ) {
        val nsPerOp get() = nanos / iterations
    }

    private inline fun bench(
        name: String,
        warmup: Int,
        iterations: Int,
        crossinline op: () -> Unit,
    ): BenchResult {
        repeat(warmup) { op() }
        var best = Long.MAX_VALUE
        for (round in 0 until 3) {
            val start = System.nanoTime()
            repeat(iterations) { op() }
            best = minOf(best, System.nanoTime() - start)
        }
        return BenchResult(name, best, iterations)
    }

    @Test
    fun benchmarkFieldOps() {
        val results = mutableListOf<BenchResult>()

        out.copyFrom(a)
        results += bench("FieldP.mul", 10000, 200000) { FieldP.mul(out, out, b, w) }

        out.copyFrom(a)
        results += bench("FieldP.sqr", 10000, 200000) { FieldP.sqr(out, out, w) }

        out.copyFrom(a)
        results += bench("FieldP.add", 10000, 500000) { FieldP.add(out, out, b) }

        out.copyFrom(a)
        results += bench("FieldP.sub", 10000, 500000) { FieldP.sub(out, out, b) }

        results += bench("U256.mulWide", 10000, 200000) { U256.mulWide(w, a, b) }

        results += bench("U256.sqrWide", 10000, 200000) { U256.sqrWide(w, a) }

        println()
        println("=".repeat(70))
        println("Fe4 Field Micro-Benchmarks: JVM21/HotSpot C2 (post-migration)")
        println("=".repeat(70))
        for (r in results) {
            println("  ${r.name.padEnd(20)} ${r.nsPerOp.toString().padStart(8)} ns/op")
        }
        println("=".repeat(70))

        assertTrue(out.l0 != Long.MIN_VALUE)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length / 2
        val result = ByteArray(len)
        for (i in 0 until len) {
            result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }
}
