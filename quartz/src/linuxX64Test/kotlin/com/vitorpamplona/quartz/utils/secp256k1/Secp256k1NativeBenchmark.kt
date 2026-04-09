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
import kotlin.time.TimeSource

/**
 * Benchmark for the pure-Kotlin secp256k1 implementation on Kotlin/Native (LLVM backend).
 *
 * Measures the same operations as the JVM benchmark (Secp256k1Benchmark.kt) so results
 * can be directly compared across runtimes:
 *   - Kotlin/Native LLVM AOT (this benchmark)
 *   - JVM HotSpot C2 JIT (jvmTest/Secp256k1Benchmark.kt)
 *   - Android ART JIT (benchmark module)
 *   - Native C libsecp256k1 (JVM benchmark's native baseline)
 *
 * Run with: ./gradlew :quartz:linuxX64Test --tests "*.Secp256k1NativeBenchmark"
 *
 * To inspect the LLVM IR or assembly generated for hot functions:
 *   ./gradlew :quartz:linkDebugTestLinuxX64  (or linkReleaseTestLinuxX64)
 *   objdump -d build/bin/linuxX64/debugTest/test.kexe | grep -A 50 "fieldMulApi"
 */
class Secp256k1NativeBenchmark {
    // Test data (same as JVM benchmark for comparable results)
    private val privKey = hexToBytes("67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530")
    private val msg32 = hexToBytes("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89")
    private val auxRand = hexToBytes("0000000000000000000000000000000000000000000000000000000000000001")

    // Pre-computed test data
    private val pubKey = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(privKey))
    private val xOnlyPub = pubKey.copyOfRange(1, 33)
    private val sig = Secp256k1.signSchnorr(msg32, privKey, auxRand)
    private val privKey2 = hexToBytes("3982F19BEF1615BCCFBB05E321C10E1D4CBA3DF0E841C2E41EEB6016347653C3")
    private val h02 = byteArrayOf(0x02)
    private val pub2xOnly = hexToBytes("c2f9d9948dc8c7c38321e4b85c8558872eafa0641cd269db76848a6073e69133")

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

        override fun toString(): String = "$name: ${nsPerOp}ns/op  ($opsPerSec ops/s)"
    }

    private inline fun bench(
        name: String,
        warmup: Int,
        iterations: Int,
        crossinline op: () -> Unit,
    ): BenchResult {
        // Warmup (LLVM AOT doesn't need warmup, but keeps methodology consistent)
        repeat(warmup) { op() }

        val mark = TimeSource.Monotonic.markNow()
        repeat(iterations) { op() }
        val elapsed = mark.elapsedNow().inWholeNanoseconds

        return BenchResult(name, elapsed, iterations)
    }

    // ============================================================
    // Individual benchmarks
    // ============================================================

    @Test
    fun benchmarkAll() {
        // Verify our test data is valid
        assertTrue(Secp256k1.verifySchnorr(sig, msg32, xOnlyPub), "Self-verify failed")

        val results = mutableListOf<BenchResult>()

        // --- verifySchnorr ---
        results +=
            bench("verifySchnorr", 2000, 5000) {
                Secp256k1.verifySchnorr(sig, msg32, xOnlyPub)
            }

        // --- signSchnorr ---
        results +=
            bench("signSchnorr", 1000, 3000) {
                Secp256k1.signSchnorr(msg32, privKey, auxRand)
            }

        // --- signSchnorr (cached pubkey) ---
        results +=
            bench("signSchnorr (cached pk)", 1000, 5000) {
                Secp256k1.signSchnorrWithPubKey(msg32, privKey, pubKey, auxRand)
            }

        // --- pubkeyCreate ---
        results +=
            bench("pubkeyCreate", 1000, 5000) {
                Secp256k1.pubkeyCreate(privKey)
            }

        // --- pubKeyCompress ---
        val uncompressed = Secp256k1.pubkeyCreate(privKey)
        results +=
            bench("pubKeyCompress", 2000, 50000) {
                Secp256k1.pubKeyCompress(uncompressed)
            }

        // --- privKeyTweakAdd ---
        results +=
            bench("privKeyTweakAdd", 1000, 50000) {
                Secp256k1.privKeyTweakAdd(privKey, privKey2)
            }

        // --- secKeyVerify ---
        results +=
            bench("secKeyVerify", 5000, 200000) {
                Secp256k1.secKeyVerify(privKey)
            }

        // --- compressedPubKeyFor (create + compress) ---
        results +=
            bench("compressedPubKeyFor", 1000, 5000) {
                Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(privKey))
            }

        // --- ecdhXOnly ---
        results +=
            bench("ecdhXOnly (Nostr)", 1000, 3000) {
                Secp256k1.ecdhXOnly(pub2xOnly, privKey)
            }

        // --- pubKeyTweakMul ---
        results +=
            bench("pubKeyTweakMul", 1000, 3000) {
                Secp256k1.pubKeyTweakMul(h02 + pub2xOnly, privKey)
            }

        // Print results
        println()
        println("=".repeat(80))
        println("secp256k1 Benchmark: Kotlin/Native (LLVM AOT) on ${getArch()}")
        println("=".repeat(80))
        for (r in results) {
            println("  ${r.name.padEnd(25)} ${r.nsPerOp.toString().padStart(10)} ns/op    ${r.opsPerSec.toString().padStart(8)} ops/s")
        }
        println("=".repeat(80))
        println()
    }

    // ============================================================
    // Field-level micro-benchmarks (to compare LLVM codegen vs JVM JIT)
    // ============================================================

    @Test
    fun benchmarkFieldOps() {
        val a = U256.fromBytes(hexToBytes("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798"))
        val b = U256.fromBytes(hexToBytes("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8"))
        val out = LongArray(4)
        val w = LongArray(8)

        val results = mutableListOf<BenchResult>()

        // --- FieldP.mul (the hottest operation) ---
        results +=
            bench("FieldP.mul", 5000, 100000) {
                FieldP.mul(out, a, b, w)
            }

        // --- FieldP.sqr ---
        results +=
            bench("FieldP.sqr", 5000, 100000) {
                FieldP.sqr(out, a, w)
            }

        // --- FieldP.add ---
        results +=
            bench("FieldP.add", 5000, 500000) {
                FieldP.add(out, a, b)
            }

        // --- FieldP.sub ---
        results +=
            bench("FieldP.sub", 5000, 500000) {
                FieldP.sub(out, a, b)
            }

        // --- FieldP.inv ---
        results +=
            bench("FieldP.inv", 500, 5000) {
                FieldP.inv(out, a)
            }

        // --- unsignedMultiplyHigh ---
        var sink = 0L
        results +=
            bench("unsignedMultiplyHigh", 10000, 1000000) {
                sink = unsignedMultiplyHigh(a[0], b[0])
            }

        // --- uLt ---
        var bsink = false
        results +=
            bench("uLt", 10000, 1000000) {
                bsink = uLt(a[0], b[0])
            }

        println()
        println("=".repeat(80))
        println("secp256k1 Field Micro-Benchmarks: Kotlin/Native (LLVM AOT) on ${getArch()}")
        println("=".repeat(80))
        for (r in results) {
            println("  ${r.name.padEnd(25)} ${r.nsPerOp.toString().padStart(10)} ns/op    ${r.opsPerSec.toString().padStart(8)} ops/s")
        }
        println("=".repeat(80))
        println()

        // Use sinks to prevent dead code elimination
        assertTrue(sink != Long.MIN_VALUE || !bsink || true)
    }

    private fun getArch(): String =
        try {
            "linuxX64"
        } catch (_: Exception) {
            "unknown"
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
