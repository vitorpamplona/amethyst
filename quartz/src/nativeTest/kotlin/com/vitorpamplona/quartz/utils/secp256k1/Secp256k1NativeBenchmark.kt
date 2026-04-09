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

import com.vitorpamplona.quartz.utils.platform
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Benchmark for the pure-Kotlin secp256k1 implementation on Kotlin/Native (LLVM AOT).
 *
 * Measures the same operations as the JVM benchmark (Secp256k1Benchmark.kt) and the
 * Android benchmark (benchmark module) so results can be compared across runtimes:
 *   - Kotlin/Native LLVM AOT (this benchmark)
 *   - JVM HotSpot C2 JIT (jvmTest/Secp256k1Benchmark.kt)
 *   - Android ART JIT (benchmark module)
 *
 * No native C comparison is available (JNI doesn't exist on K/N).
 * Compare against the JVM benchmark's native column for the C baseline.
 *
 * NOTE: verifySchnorr uses the same pubkey repeatedly, so it benefits from the
 * pubkey decompression cache and P-table cache. This is realistic for Nostr
 * (feed from one author) but not representative of first-time verification.
 *
 * Test data is the same across all 3 platform benchmarks for cross-platform comparability.
 *
 * Run with:
 *   ./gradlew :quartz:linuxX64Test --tests "*.Secp256k1NativeBenchmark"
 *   ./gradlew :quartz:macosArm64Test --tests "*.Secp256k1NativeBenchmark"
 *
 * IMPORTANT: Requires -opt in the compiler options for meaningful results.
 * Without it, K/N compiles in debug mode (~12x slower). See build.gradle.kts.
 */
class Secp256k1NativeBenchmark {
    // Test data (same across all 3 platform benchmarks for comparability)
    private val privKey = hexToBytes("67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530")
    private val msg32 = hexToBytes("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89")
    private val auxRand = hexToBytes("0000000000000000000000000000000000000000000000000000000000000001")

    // Pre-computed test data
    private val pubKey = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(privKey))
    private val xOnlyPub = pubKey.copyOfRange(1, 33)
    private val sig = Secp256k1.signSchnorr(msg32, privKey, auxRand)
    private val privKey2 = hexToBytes("3982F19BEF1615BCCFBB05E321C10E1D4CBA3DF0E841C2E41EEB6016347653C3")
    private val pub2xOnly = hexToBytes("c2f9d9948dc8c7c38321e4b85c8558872eafa0641cd269db76848a6073e69133")
    private val h02 = byteArrayOf(0x02)

    // ============================================================
    // Benchmark runner
    // ============================================================

    private data class BenchResult(
        val name: String,
        val nanos: Long,
        val iterations: Int,
    ) {
        val opsPerSec get() = iterations * 1_000_000_000L / nanos
        val nsPerOp get() = nanos / iterations
    }

    private inline fun bench(
        name: String,
        warmup: Int,
        iterations: Int,
        crossinline op: () -> Unit,
    ): BenchResult {
        // Warmup (LLVM AOT doesn't JIT, but warms up caches and branch predictors)
        repeat(warmup) { op() }

        val mark = TimeSource.Monotonic.markNow()
        repeat(iterations) { op() }
        val elapsed = mark.elapsedNow().inWholeNanoseconds

        return BenchResult(name, elapsed, iterations)
    }

    private fun printResults(
        title: String,
        results: List<BenchResult>,
    ) {
        println()
        println("=".repeat(80))
        println(title)
        println("=".repeat(80))
        for (r in results) {
            println(
                "  ${r.name.padEnd(25)} ${
                    r.nsPerOp.toString().padStart(10)
                } ns/op    ${r.opsPerSec.toString().padStart(8)} ops/s",
            )
        }
        println("=".repeat(80))
    }

    // ============================================================
    // Individual benchmarks (same operations as JVM and Android)
    // ============================================================

    @Test
    fun benchmarkAll() {
        // Verify our test data is valid
        assertTrue(Secp256k1.verifySchnorr(sig, msg32, xOnlyPub), "Self-verify failed")

        val results = mutableListOf<BenchResult>()

        // --- verifySchnorr (same pubkey = cache-warm, typical Nostr pattern) ---
        results +=
            bench("verifySchnorr", 2000, 5000) {
                Secp256k1.verifySchnorr(sig, msg32, xOnlyPub)
            }

        // --- verifySchnorrFast (Nostr: x-check only, no y-parity inversion) ---
        results +=
            bench("verifySchnorrFast", 2000, 5000) {
                Secp256k1.verifySchnorrFast(sig, msg32, xOnlyPub)
            }

        // --- signSchnorr (derives pubkey each time) ---
        results +=
            bench("signSchnorr", 1000, 3000) {
                Secp256k1.signSchnorr(msg32, privKey, auxRand)
            }

        // --- signSchnorr (cached pubkey — skips G multiplication for pubkey derivation) ---
        results +=
            bench("signSchnorr (cached pk)", 1000, 5000) {
                Secp256k1.signSchnorrWithPubKey(msg32, privKey, pubKey, auxRand)
            }

        // --- compressedPubKeyFor (create + compress combined) ---
        results +=
            bench("compressedPubKeyFor", 1000, 5000) {
                Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(privKey))
            }

        // --- secKeyVerify ---
        results +=
            bench("secKeyVerify", 5000, 200000) {
                Secp256k1.secKeyVerify(privKey)
            }

        // --- privKeyTweakAdd ---
        results +=
            bench("privKeyTweakAdd", 1000, 50000) {
                Secp256k1.privKeyTweakAdd(privKey, privKey2)
            }

        // --- ecdhXOnly (Nostr NIP-04/NIP-44 ECDH path) ---
        results +=
            bench("ecdhXOnly (Nostr)", 1000, 3000) {
                Secp256k1.ecdhXOnly(pub2xOnly, privKey)
            }

        printResults("secp256k1 Benchmark: Kotlin/Native (LLVM AOT) on ${platform()}", results)

        // ==================== Batch verification ====================
        // Same pubkey, n events — the typical Nostr pattern (feed from one author).
        println()
        for (batchSize in intArrayOf(4, 8, 16, 32)) {
            val sigs = mutableListOf<ByteArray>()
            val msgs = mutableListOf<ByteArray>()
            for (i in 0 until batchSize) {
                val m = ByteArray(32) { (i * 7 + it).toByte() }
                sigs.add(Secp256k1.signSchnorr(m, privKey, auxRand))
                msgs.add(m)
            }
            // Warmup
            repeat(500) {
                for (j in 0 until batchSize) Secp256k1.verifySchnorr(sigs[j], msgs[j], xOnlyPub)
            }
            repeat(500) { Secp256k1.verifySchnorrBatch(xOnlyPub, sigs, msgs) }
            // Time individual
            val iters = 1000
            val indivMark = TimeSource.Monotonic.markNow()
            repeat(iters) {
                for (j in 0 until batchSize) Secp256k1.verifySchnorr(sigs[j], msgs[j], xOnlyPub)
            }
            val indivNs = indivMark.elapsedNow().inWholeNanoseconds
            val indivPerEvent = iters.toLong() * batchSize * 1_000_000_000L / indivNs
            // Time batch
            val batchMark = TimeSource.Monotonic.markNow()
            repeat(iters) { Secp256k1.verifySchnorrBatch(xOnlyPub, sigs, msgs) }
            val batchNs = batchMark.elapsedNow().inWholeNanoseconds
            val batchPerEvent = iters.toLong() * batchSize * 1_000_000_000L / batchNs
            val speedup = indivNs.toDouble() / batchNs.toDouble()
            println(
                "  batch(${batchSize.toString().padStart(2)}): " +
                    "individual ${indivPerEvent.toString().padStart(7)} ev/s  " +
                    "batch ${batchPerEvent.toString().padStart(7)} ev/s  " +
                    "speedup ${((speedup * 10).toLong() / 10.0)}x",
            )
        }
        println("=".repeat(80))
        println()
    }

    // ============================================================
    // Field-level micro-benchmarks (to compare LLVM codegen vs JVM JIT)
    // ============================================================

    @Test
    fun benchmarkFieldOps() {
        // Use result-dependent chains to prevent LLVM from hoisting constant computations
        // out of the loop (dead code elimination risk with constant inputs).
        val a = U256.fromBytes(hexToBytes("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798"))
        val b = U256.fromBytes(hexToBytes("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8"))
        val out = Fe4()
        val w = Wide8()

        val results = mutableListOf<BenchResult>()

        // --- FieldP.mul (the hottest operation) ---
        // Uses `out` as both output and next input to create a data dependency chain.
        out.copyFrom(a)
        results +=
            bench("FieldP.mul", 5000, 100000) {
                FieldP.mul(out, out, b, w)
            }

        // --- FieldP.sqr ---
        out.copyFrom(a)
        results +=
            bench("FieldP.sqr", 5000, 100000) {
                FieldP.sqr(out, out, w)
            }

        // --- FieldP.add ---
        out.copyFrom(a)
        results +=
            bench("FieldP.add", 5000, 500000) {
                FieldP.add(out, out, b)
            }

        // --- FieldP.sub ---
        out.copyFrom(a)
        results +=
            bench("FieldP.sub", 5000, 500000) {
                FieldP.sub(out, out, b)
            }

        // --- FieldP.inv ---
        results +=
            bench("FieldP.inv", 500, 5000) {
                FieldP.inv(out, a)
            }

        // --- unsignedMultiplyHigh (with varying input to prevent hoisting) ---
        var hiSink = 0L
        results +=
            bench("unsignedMultiplyHigh", 10000, 1000000) {
                hiSink = unsignedMultiplyHigh(a.l0 xor hiSink, b.l0)
            }

        // --- uLt (with varying input to prevent hoisting) ---
        var ltSink = 0L
        results +=
            bench("uLt", 10000, 1000000) {
                ltSink = if (uLt(a.l0 xor ltSink, b.l0)) 1L else 0L
            }

        printResults("secp256k1 Field Micro-Benchmarks: Kotlin/Native (LLVM AOT) on ${platform()}", results)

        // Use sinks to prevent dead code elimination of the entire benchmark
        assertTrue(hiSink != Long.MIN_VALUE || ltSink != Long.MIN_VALUE || out.l0 != Long.MIN_VALUE)
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
