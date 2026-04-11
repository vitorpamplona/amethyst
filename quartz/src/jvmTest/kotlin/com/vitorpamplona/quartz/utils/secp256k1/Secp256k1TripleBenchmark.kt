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

import com.vitorpamplona.quartz.utils.Secp256k1InstanceC
import kotlin.test.Test
import kotlin.test.assertTrue
import fr.acinq.secp256k1.Secp256k1 as NativeSecp256k1

/**
 * Three-way benchmark comparing:
 * 1. ACINQ C (libsecp256k1 via JNI) — the established reference
 * 2. Pure Kotlin (our KMP implementation) — portable, no native deps
 * 3. Custom C (our new C implementation via JNI) — maximum performance target
 *
 * Run with: ./gradlew :quartz:jvmTest --tests "*.Secp256k1TripleBenchmark"
 */
class Secp256k1TripleBenchmark {
    private val privKey = hexToBytes("67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530")
    private val msg32 = hexToBytes("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89")
    private val auxRand = hexToBytes("0000000000000000000000000000000000000000000000000000000000000001")
    private val privKey2 = hexToBytes("3982F19BEF1615BCCFBB05E321C10E1D4CBA3DF0E841C2E41EEB6016347653C3")
    private val pub2xOnly = hexToBytes("c2f9d9948dc8c7c38321e4b85c8558872eafa0641cd269db76848a6073e69133")
    private val h02 = byteArrayOf(0x02)

    private val acinq = NativeSecp256k1.get()
    private val acinqPubKey = acinq.pubKeyCompress(acinq.pubkeyCreate(privKey))
    private val acinqXOnlyPub = acinqPubKey.copyOfRange(1, 33)
    private val acinqSig = acinq.signSchnorr(msg32, privKey, auxRand)

    private val kotlinPubKey = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(privKey))
    private val kotlinXOnlyPub = kotlinPubKey.copyOfRange(1, 33)
    private val kotlinSig = Secp256k1.signSchnorr(msg32, privKey, auxRand)

    private data class TripleResult(
        val name: String,
        val acinqNanos: Long,
        val kotlinNanos: Long,
        val cNanos: Long,
        val iterations: Int,
    ) {
        val acinqOps get() = iterations * 1_000_000_000L / acinqNanos
        val kotlinOps get() = iterations * 1_000_000_000L / kotlinNanos
        val cOps get() = if (cNanos > 0) iterations * 1_000_000_000L / cNanos else 0L
        val kotlinRatio get() = kotlinNanos.toDouble() / acinqNanos
        val cRatio get() = if (cNanos > 0) cNanos.toDouble() / acinqNanos else 0.0

        fun format(): String {
            val cStr = if (cNanos > 0) String.format("%,10d ops/s", cOps) else "   (N/A)   "
            val cRat = if (cNanos > 0) String.format("%.2fx", cRatio) else " N/A "
            return String.format(
                "%-26s %,10d ops/s  %,10d ops/s  %s  %.2fx  %s",
                name,
                acinqOps,
                kotlinOps,
                cStr,
                kotlinRatio,
                cRat,
            )
        }
    }

    private inline fun benchTriple(
        name: String,
        warmup: Int,
        iterations: Int,
        crossinline acinqOp: () -> Unit,
        crossinline kotlinOp: () -> Unit,
        crossinline cOp: (() -> Unit)? = null,
    ): TripleResult {
        repeat(warmup) { acinqOp() }
        val acinqStart = System.nanoTime()
        repeat(iterations) { acinqOp() }
        val acinqNs = System.nanoTime() - acinqStart

        repeat(warmup) { kotlinOp() }
        val kotlinStart = System.nanoTime()
        repeat(iterations) { kotlinOp() }
        val kotlinNs = System.nanoTime() - kotlinStart

        var cNs = 0L
        if (cOp != null) {
            repeat(warmup) { cOp() }
            val cStart = System.nanoTime()
            repeat(iterations) { cOp() }
            cNs = System.nanoTime() - cStart
        }

        return TripleResult(name, acinqNs, kotlinNs, cNs, iterations)
    }

    @Test
    fun benchmarkAllThree() {
        // Verify signature compatibility
        assertTrue(acinqSig.contentEquals(kotlinSig), "ACINQ and Kotlin signatures must match")

        // Try to load C library (may not be available in all environments)
        var cAvailable = false
        try {
            Secp256k1InstanceC.init()
            cAvailable = true
        } catch (e: UnsatisfiedLinkError) {
            println("NOTE: Custom C library not available (${e.message})")
            println("      Build it with: cd quartz/src/main/c/secp256k1 && mkdir build && cd build && cmake .. && make")
            println("      Then add build/ to java.library.path")
        }

        val cPubKey = if (cAvailable) Secp256k1InstanceC.compressedPubKeyFor(privKey) else null
        val cXOnlyPub = cPubKey?.copyOfRange(1, 33)
        val cSig =
            if (cAvailable) {
                Secp256k1InstanceC.signSchnorr(msg32, privKey, auxRand)
            } else {
                null
            }

        if (cAvailable && cSig != null) {
            // Verify C signatures are compatible
            assertTrue(
                acinq.verifySchnorr(cSig, msg32, acinqXOnlyPub),
                "ACINQ should verify C signature",
            )
            assertTrue(
                Secp256k1InstanceC.verifySchnorr(acinqSig, msg32, acinqXOnlyPub),
                "C should verify ACINQ signature",
            )
        }

        val results = mutableListOf<TripleResult>()

        // --- Verify Schnorr (fast, x-check only) ---
        results +=
            benchTriple(
                name = "verifySchnorrFast",
                warmup = 2000,
                iterations = 5000,
                acinqOp = { acinq.verifySchnorr(acinqSig, msg32, acinqXOnlyPub) },
                kotlinOp = { Secp256k1.verifySchnorrFast(kotlinSig, msg32, kotlinXOnlyPub) },
                cOp =
                    if (cAvailable && cSig != null && cXOnlyPub != null) {
                        { Secp256k1InstanceC.verifySchnorrFast(cSig, msg32, cXOnlyPub) }
                    } else {
                        null
                    },
            )

        // --- Verify Schnorr (strict BIP-340) ---
        results +=
            benchTriple(
                name = "verifySchnorr",
                warmup = 2000,
                iterations = 5000,
                acinqOp = { acinq.verifySchnorr(acinqSig, msg32, acinqXOnlyPub) },
                kotlinOp = { Secp256k1.verifySchnorr(kotlinSig, msg32, kotlinXOnlyPub) },
                cOp =
                    if (cAvailable && cSig != null && cXOnlyPub != null) {
                        { Secp256k1InstanceC.verifySchnorr(cSig, msg32, cXOnlyPub) }
                    } else {
                        null
                    },
            )

        // --- Sign Schnorr (with cached x-only pubkey) ---
        results +=
            benchTriple(
                name = "signSchnorr (cached pk)",
                warmup = 1000,
                iterations = 5000,
                acinqOp = { acinq.signSchnorr(msg32, privKey, auxRand) },
                kotlinOp = { Secp256k1.signSchnorrWithXOnlyPubKey(msg32, privKey, kotlinXOnlyPub, auxRand) },
                cOp =
                    if (cAvailable && cXOnlyPub != null) {
                        { Secp256k1InstanceC.signSchnorrWithXOnlyPubKey(msg32, privKey, cXOnlyPub, auxRand) }
                    } else {
                        null
                    },
            )

        // --- Sign Schnorr (derives pubkey) ---
        results +=
            benchTriple(
                name = "signSchnorr",
                warmup = 1000,
                iterations = 3000,
                acinqOp = { acinq.signSchnorr(msg32, privKey, auxRand) },
                kotlinOp = { Secp256k1.signSchnorr(msg32, privKey, auxRand) },
                cOp =
                    if (cAvailable) {
                        { Secp256k1InstanceC.signSchnorr(msg32, privKey, auxRand) }
                    } else {
                        null
                    },
            )

        // --- Pubkey create + compress ---
        results +=
            benchTriple(
                name = "pubkeyCreate+Compress",
                warmup = 1000,
                iterations = 5000,
                acinqOp = { acinq.pubKeyCompress(acinq.pubkeyCreate(privKey)) },
                kotlinOp = { Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(privKey)) },
                cOp =
                    if (cAvailable) {
                        { Secp256k1InstanceC.compressedPubKeyFor(privKey) }
                    } else {
                        null
                    },
            )

        // --- ECDH ---
        results +=
            benchTriple(
                name = "ecdhXOnly (NIP-44)",
                warmup = 1000,
                iterations = 3000,
                acinqOp = { acinq.pubKeyTweakMul(h02 + pub2xOnly, privKey).copyOfRange(1, 33) },
                kotlinOp = { Secp256k1.ecdhXOnly(pub2xOnly, privKey) },
                cOp =
                    if (cAvailable) {
                        { Secp256k1InstanceC.ecdhXOnly(pub2xOnly, privKey) }
                    } else {
                        null
                    },
            )

        // Print header
        println()
        println("=".repeat(100))
        println("secp256k1 Three-Way Benchmark: ACINQ (C/JNI) vs Kotlin (KMP) vs Custom C (JNI)")
        println("=".repeat(100))
        println(
            String.format(
                "%-26s %14s  %14s  %14s  %s  %s",
                "Operation",
                "ACINQ(C/JNI)",
                "Kotlin(KMP)",
                "Custom C(JNI)",
                "Kt/A",
                "C/A",
            ),
        )
        println("-".repeat(100))
        for (r in results) {
            println(r.format())
        }

        // Batch verification benchmarks
        println("-".repeat(100))
        println("Batch Verification (same pubkey, N events)")
        println("-".repeat(100))

        val batchPub = kotlinXOnlyPub
        for (batchSize in intArrayOf(8, 16, 32, 200)) {
            val sigs = mutableListOf<ByteArray>()
            val msgs = mutableListOf<ByteArray>()
            for (i in 0 until batchSize) {
                val m = ByteArray(32) { (i * 7 + it).toByte() }
                sigs.add(Secp256k1.signSchnorr(m, privKey, auxRand))
                msgs.add(m)
            }
            val iters = 1000

            // Warmup
            repeat(500) { Secp256k1.verifySchnorrBatch(batchPub, sigs, msgs) }

            // Time individual ACINQ
            repeat(500) { sigs.forEach { sig -> acinq.verifySchnorr(sig, msgs[sigs.indexOf(sig)], acinqXOnlyPub) } }
            val acinqStart = System.nanoTime()
            repeat(iters) { sigs.forEachIndexed { j, sig -> acinq.verifySchnorr(sig, msgs[j], acinqXOnlyPub) } }
            val acinqNs = System.nanoTime() - acinqStart
            val acinqEvSec = iters.toLong() * batchSize * 1_000_000_000L / acinqNs

            // Time Kotlin batch
            val ktBatchStart = System.nanoTime()
            repeat(iters) { Secp256k1.verifySchnorrBatch(batchPub, sigs, msgs) }
            val ktBatchNs = System.nanoTime() - ktBatchStart
            val ktBatchEvSec = iters.toLong() * batchSize * 1_000_000_000L / ktBatchNs

            // Time C batch (if available)
            var cBatchEvSec = 0L
            if (cAvailable) {
                repeat(500) { Secp256k1InstanceC.verifySchnorrBatch(batchPub, sigs, msgs) }
                val cBatchStart = System.nanoTime()
                repeat(iters) { Secp256k1InstanceC.verifySchnorrBatch(batchPub, sigs, msgs) }
                val cBatchNs = System.nanoTime() - cBatchStart
                cBatchEvSec = iters.toLong() * batchSize * 1_000_000_000L / cBatchNs
            }

            val batchSpeedup = acinqNs.toDouble() / ktBatchNs
            val cStr = if (cAvailable) String.format("%,10d ev/s", cBatchEvSec) else "   (N/A)   "
            println(
                String.format(
                    "  batch(%3d) %,10d ev/s  %,10d ev/s  %s  %.1fx",
                    batchSize,
                    acinqEvSec,
                    ktBatchEvSec,
                    cStr,
                    batchSpeedup,
                ),
            )
        }
        println("=".repeat(100))
        println()
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
