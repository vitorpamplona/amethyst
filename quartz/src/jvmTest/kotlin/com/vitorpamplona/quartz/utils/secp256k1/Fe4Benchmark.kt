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
 * Benchmark comparing LongArray(4) vs Fe4 (struct with 4 @JvmField Long fields)
 * for secp256k1 field element operations.
 *
 * The hypothesis: Fe4 eliminates array bounds checks (laload/lastore → getfield/putfield),
 * which should measurably improve performance on platforms where the JIT cannot reliably
 * eliminate bounds checks for parameter arrays (Android ART, Kotlin/Native LLVM).
 *
 * On JVM HotSpot C2, the difference may be smaller because C2 can profile array sizes
 * and eliminate constant-index bounds checks. But even on HotSpot, the Fe4 approach
 * avoids the array length field load + comparison that precedes each array access.
 *
 * Run with: ./gradlew :quartz:jvmTest --tests "*.Fe4Benchmark"
 */
class Fe4Benchmark {
    // Test vectors: secp256k1 generator point coordinates
    private val aBytes = hexToBytes("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798")
    private val bBytes = hexToBytes("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8")

    // LongArray representations
    private val aArr = U256.fromBytes(aBytes)
    private val bArr = U256.fromBytes(bBytes)
    private val outArr = LongArray(4)
    private val wArr = LongArray(8)

    // Fe4 representations (same values)
    private val aFe4 = Fe4(aArr[0], aArr[1], aArr[2], aArr[3])
    private val bFe4 = Fe4(bArr[0], bArr[1], bArr[2], bArr[3])
    private val outFe4 = Fe4()
    private val wFe8 = Wide8()

    private data class BenchResult(
        val name: String,
        val arrayNanos: Long,
        val fe4Nanos: Long,
        val iterations: Int,
    ) {
        val arrayNsPerOp get() = arrayNanos / iterations
        val fe4NsPerOp get() = fe4Nanos / iterations
        val speedup get() = arrayNanos.toDouble() / fe4Nanos.toDouble()

        override fun toString(): String {
            val pct = ((speedup - 1.0) * 100).let { if (it >= 0) "+%.1f%%".format(it) else "%.1f%%".format(it) }
            return String.format(
                "  %-20s  LongArray: %,8d ns/op   Fe4: %,8d ns/op   %s",
                name,
                arrayNsPerOp,
                fe4NsPerOp,
                pct,
            )
        }
    }

    private inline fun bench(
        name: String,
        warmup: Int,
        iterations: Int,
        crossinline arrayOp: () -> Unit,
        crossinline fe4Op: () -> Unit,
    ): BenchResult {
        // Warmup both generously (C2 compiles at ~10K invocations)
        repeat(warmup) { arrayOp() }
        repeat(warmup) { fe4Op() }

        // Run 3 rounds, alternating order, take best of each to reduce noise
        var bestArr = Long.MAX_VALUE
        var bestFe4 = Long.MAX_VALUE
        for (round in 0 until 3) {
            if (round % 2 == 0) {
                // LongArray first
                val arrStart = System.nanoTime()
                repeat(iterations) { arrayOp() }
                bestArr = minOf(bestArr, System.nanoTime() - arrStart)

                val fe4Start = System.nanoTime()
                repeat(iterations) { fe4Op() }
                bestFe4 = minOf(bestFe4, System.nanoTime() - fe4Start)
            } else {
                // Fe4 first
                val fe4Start = System.nanoTime()
                repeat(iterations) { fe4Op() }
                bestFe4 = minOf(bestFe4, System.nanoTime() - fe4Start)

                val arrStart = System.nanoTime()
                repeat(iterations) { arrayOp() }
                bestArr = minOf(bestArr, System.nanoTime() - arrStart)
            }
        }

        return BenchResult(name, bestArr, bestFe4, iterations)
    }

    @Test
    fun benchmarkFieldOps() {
        // Verify correctness first: both implementations must produce identical results
        verifySameResults()

        val results = mutableListOf<BenchResult>()

        // --- FieldP.mul (the hottest operation: ~1900 calls/verify) ---
        // Use out as input to next iteration to create data dependency chain
        aArr.copyInto(outArr)
        aFe4.copyFrom(Fe4(aArr[0], aArr[1], aArr[2], aArr[3]))
        outFe4.copyFrom(aFe4)
        results +=
            bench(
                "FieldP.mul",
                10000,
                200000,
                arrayOp = { FieldP.mul(outArr, outArr, bArr, wArr) },
                fe4Op = { Fe4FieldP.mul(outFe4, outFe4, bFe4, wFe8) },
            )

        // --- FieldP.sqr (second hottest: ~1900 calls/verify) ---
        aArr.copyInto(outArr)
        outFe4.copyFrom(aFe4)
        results +=
            bench(
                "FieldP.sqr",
                10000,
                200000,
                arrayOp = { FieldP.sqr(outArr, outArr, wArr) },
                fe4Op = { Fe4FieldP.sqr(outFe4, outFe4, wFe8) },
            )

        // --- FieldP.add (~750 calls/verify) ---
        aArr.copyInto(outArr)
        outFe4.copyFrom(aFe4)
        results +=
            bench(
                "FieldP.add",
                10000,
                500000,
                arrayOp = { FieldP.add(outArr, outArr, bArr) },
                fe4Op = { Fe4FieldP.add(outFe4, outFe4, bFe4) },
            )

        // --- FieldP.sub (~500 calls/verify) ---
        aArr.copyInto(outArr)
        outFe4.copyFrom(aFe4)
        results +=
            bench(
                "FieldP.sub",
                10000,
                500000,
                arrayOp = { FieldP.sub(outArr, outArr, bArr) },
                fe4Op = { Fe4FieldP.sub(outFe4, outFe4, bFe4) },
            )

        // --- U256.mulWide (raw wide multiply, no reduction) ---
        results +=
            bench(
                "U256.mulWide",
                10000,
                200000,
                arrayOp = { U256.mulWide(wArr, aArr, bArr) },
                fe4Op = { Fe4U256.mulWide(wFe8, aFe4, bFe4) },
            )

        // --- U256.sqrWide (raw wide square) ---
        results +=
            bench(
                "U256.sqrWide",
                10000,
                200000,
                arrayOp = { U256.sqrWide(wArr, aArr) },
                fe4Op = { Fe4U256.sqrWide(wFe8, aFe4) },
            )

        // Print results
        println()
        println("=".repeat(80))
        println("Fe4 vs LongArray Benchmark: JVM21/HotSpot C2")
        println("=".repeat(80))
        println("  Hypothesis: Fe4 (named fields) eliminates array bounds checks,")
        println("  producing faster code especially on ART/Native where JIT is weaker.")
        println("-".repeat(80))
        for (r in results) {
            println(r)
        }
        println("=".repeat(80))
        println()
        println("  Bytecode analysis:")
        println("    LongArray access: aload + iconst + laload (3 insns + implicit bounds check)")
        println("    Fe4 field access: aload + getfield         (2 insns, no check)")
        println()
        println("  U256.class:          150 laload/lastore operations (each bounds-checked)")
        println("  FieldP.class:        119 laload/lastore operations")
        println("  FieldMulFusedKt:     195 laload/lastore operations")
        println("  Total:               464 bounds checks in 3 core files")
        println()

        // Prevent dead code elimination
        assertTrue(outArr[0] != Long.MIN_VALUE || outFe4.l0 != Long.MIN_VALUE)
    }

    private fun verifySameResults() {
        // Test mul
        FieldP.mul(outArr, aArr, bArr, wArr)
        Fe4FieldP.mul(outFe4, aFe4, bFe4, wFe8)
        assertFe4EqualsArray("mul", outArr, outFe4)

        // Test sqr
        FieldP.sqr(outArr, aArr, wArr)
        Fe4FieldP.sqr(outFe4, aFe4, wFe8)
        assertFe4EqualsArray("sqr", outArr, outFe4)

        // Test add
        FieldP.add(outArr, aArr, bArr)
        Fe4FieldP.add(outFe4, aFe4, bFe4)
        assertFe4EqualsArray("add", outArr, outFe4)

        // Test sub
        FieldP.sub(outArr, aArr, bArr)
        Fe4FieldP.sub(outFe4, aFe4, bFe4)
        assertFe4EqualsArray("sub", outArr, outFe4)
    }

    private fun assertFe4EqualsArray(
        op: String,
        arr: LongArray,
        fe: Fe4,
    ) {
        assertTrue(
            arr[0] == fe.l0 && arr[1] == fe.l1 && arr[2] == fe.l2 && arr[3] == fe.l3,
            "$op: LongArray[${arr[0]},${arr[1]},${arr[2]},${arr[3]}] != " +
                "Fe4[${fe.l0},${fe.l1},${fe.l2},${fe.l3}]",
        )
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
