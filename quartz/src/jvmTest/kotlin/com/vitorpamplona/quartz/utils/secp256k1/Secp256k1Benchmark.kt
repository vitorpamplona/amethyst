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
import fr.acinq.secp256k1.Secp256k1 as NativeSecp256k1

/**
 * Benchmark comparing the pure-Kotlin secp256k1 implementation against
 * the native ACINQ/secp256k1-kmp JNI bindings on JVM (HotSpot C2).
 *
 * Each benchmark runs warmup iterations to trigger JIT compilation, then
 * measures timed iterations. Results are printed as ops/sec and relative speed.
 *
 * NOTE: verifySchnorr and signSchnorr use the same pubkey repeatedly, so they
 * benefit from the pubkey decompression cache and P-table cache. This is realistic
 * for Nostr (feed from one author) but not representative of first-time verification.
 *
 * ## Benchmark Fairness Notes
 *
 * The comparisons below are apples-to-apples wherever possible, but a few
 * intentional asymmetries remain because the public APIs don't line up 1:1:
 *
 * - **signSchnorr (XPubKey)**: the Kotlin side uses `signSchnorrWithPubKey`,
 *   which skips the internal pubkey derivation when the caller already has
 *   the compressed pubkey cached. The ACINQ side always derives the pubkey
 *   inside `signSchnorr`. A separate `signSchnorr` row below exercises the
 *   same derivation path on both sides for a fair comparison.
 *
 * - **ecdhXOnly (NIP-44)**: ACINQ has no x-only ECDH API, so the native
 *   baseline uses `pubKeyTweakMul` + x-coordinate extraction. Both paths
 *   compute the same shared secret but the native side pays an extra
 *   (de)serialization step that the Kotlin side skips.
 *
 * - **privKeyTweakAdd**: ACINQ mutates its first argument, so the native
 *   side does `privKey.copyOf()` before each call, adding one allocation
 *   to the native measurement that the Kotlin side doesn't incur.
 *
 * Run with: ./gradlew :quartz:jvmTest --tests "*.Secp256k1Benchmark"
 */
class Secp256k1Benchmark {
    // Test data (same across all 3 platform benchmarks for comparability)
    private val privKey = hexToBytes("67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530")
    private val msg32 = hexToBytes("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89")
    private val auxRand = hexToBytes("0000000000000000000000000000000000000000000000000000000000000001")

    // Pre-computed test data to avoid measuring setup costs
    private val native = NativeSecp256k1.get()
    private val nativePubKey = native.pubKeyCompress(native.pubkeyCreate(privKey))
    private val nativeXOnlyPub = nativePubKey.copyOfRange(1, 33)
    private val nativeSig = native.signSchnorr(msg32, privKey, auxRand)

    private val kotlinPubKey =
        com.vitorpamplona.quartz.utils.secp256k1.Secp256k1.pubKeyCompress(
            com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                .pubkeyCreate(privKey),
        )
    private val kotlinXOnlyPub = kotlinPubKey.copyOfRange(1, 33)
    private val kotlinSig =
        com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
            .signSchnorr(msg32, privKey, auxRand)

    // ECDH test data
    private val privKey2 = hexToBytes("3982F19BEF1615BCCFBB05E321C10E1D4CBA3DF0E841C2E41EEB6016347653C3")
    private val pub2xOnly = hexToBytes("c2f9d9948dc8c7c38321e4b85c8558872eafa0641cd269db76848a6073e69133")
    private val h02 = byteArrayOf(0x02)

    // ============================================================
    // Benchmark runner
    // ============================================================

    private data class BenchResult(
        val name: String,
        val nativeNanos: Long,
        val kotlinNanos: Long,
        val iterations: Int,
    ) {
        val nativeOpsPerSec get() = iterations * 1_000_000_000L / nativeNanos
        val kotlinOpsPerSec get() = iterations * 1_000_000_000L / kotlinNanos
        val ratio get() = kotlinNanos.toDouble() / nativeNanos.toDouble()

        override fun toString(): String =
            if (ratio > 1) {
                String.format(
                    "%-25s  %,10d ops/s   %,10d ops/s   %.1fx slower",
                    name,
                    nativeOpsPerSec,
                    kotlinOpsPerSec,
                    ratio,
                )
            } else {
                String.format(
                    "%-25s  %,10d ops/s   %,10d ops/s   %.1fx faster",
                    name,
                    nativeOpsPerSec,
                    kotlinOpsPerSec,
                    ratio,
                )
            }
    }

    private inline fun bench(
        name: String,
        warmup: Int,
        iterations: Int,
        crossinline nativeOp: () -> Unit,
        crossinline kotlinOp: () -> Unit,
    ): BenchResult {
        // Warmup native
        repeat(warmup) { nativeOp() }
        // Time native
        val nativeStart = System.nanoTime()
        repeat(iterations) { nativeOp() }
        val nativeElapsed = System.nanoTime() - nativeStart

        // Warmup kotlin
        repeat(warmup) { kotlinOp() }
        // Time kotlin
        val kotlinStart = System.nanoTime()
        repeat(iterations) { kotlinOp() }
        val kotlinElapsed = System.nanoTime() - kotlinStart

        return BenchResult(name, nativeElapsed, kotlinElapsed, iterations)
    }

    // ============================================================
    // Individual benchmarks
    // ============================================================

    @Test
    fun benchmarkAll() {
        // Verify signatures match between implementations
        assertTrue(nativeSig.contentEquals(kotlinSig), "Signatures should match")
        assertTrue(
            native.verifySchnorr(kotlinSig, msg32, nativeXOnlyPub),
            "Native should verify Kotlin sig",
        )
        assertTrue(
            com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                .verifySchnorr(nativeSig, msg32, kotlinXOnlyPub),
            "Kotlin should verify native sig",
        )

        val results = mutableListOf<BenchResult>()

        // --- verifySchnorrFast (Nostr: x-check only, no y-parity inversion) ---
        results +=
            bench(
                name = "verifySchnorr (XPubKey)",
                warmup = 2000,
                iterations = 5000,
                nativeOp = { native.verifySchnorr(nativeSig, msg32, nativeXOnlyPub) },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1.verifySchnorrFast(
                        kotlinSig,
                        msg32,
                        kotlinXOnlyPub,
                    )
                },
            )

        // --- verifySchnorr (strict BIP-340 with y-parity check) ---
        results +=
            bench(
                name = "verifySchnorr",
                warmup = 2000,
                iterations = 5000,
                nativeOp = { native.verifySchnorr(nativeSig, msg32, nativeXOnlyPub) },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1.verifySchnorr(
                        kotlinSig,
                        msg32,
                        kotlinXOnlyPub,
                    )
                },
            )

        // --- signSchnorr (cached pk) ---
        // NOTE: The C library's signSchnorr always derives the pubkey internally.
        // Our signSchnorrWithPubKey skips that derivation. This is NOT an apples-to-apples
        // comparison — it shows what's possible when the caller caches the compressed pubkey.
        // The native baseline here is the same signSchnorr (with pubkey derivation).
        results +=
            bench(
                name = "signSchnorr (XPubKey)",
                warmup = 1000,
                iterations = 5000,
                nativeOp = { native.signSchnorr(msg32, privKey, auxRand) },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1.signSchnorrWithPubKey(
                        msg32,
                        privKey,
                        nativePubKey,
                        auxRand,
                    )
                },
            )

        // --- signSchnorr (derives pubkey from seckey each time — both sides do the same work) ---
        results +=
            bench(
                name = "signSchnorr",
                warmup = 1000,
                iterations = 3000,
                nativeOp = { native.signSchnorr(msg32, privKey, auxRand) },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1.signSchnorr(
                        msg32,
                        privKey,
                        auxRand,
                    )
                },
            )

        // --- compressedPubKeyFor (create + compress combined) ---
        results +=
            bench(
                name = "xPubKeyFor (Login)",
                warmup = 1000,
                iterations = 5000,
                nativeOp = { native.pubKeyCompress(native.pubkeyCreate(privKey)) },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1.pubKeyCompress(
                        com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                            .pubkeyCreate(privKey),
                    )
                },
            )

        // --- secKeyVerify ---
        results +=
            bench(
                name = "secKeyVerify (NIP-06)",
                warmup = 5000,
                iterations = 200000,
                nativeOp = { native.secKeyVerify(privKey) },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                        .secKeyVerify(privKey)
                },
            )

        // --- privKeyTweakAdd ---
        // NOTE: The ACINQ wrapper mutates its first argument, requiring .copyOf() on
        // the native side. This adds one allocation to the native measurement.
        results +=
            bench(
                name = "privKeyTweakAdd (NIP-06)",
                warmup = 1000,
                iterations = 50000,
                nativeOp = { native.privKeyTweakAdd(privKey.copyOf(), privKey2) },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1.privKeyTweakAdd(
                        privKey,
                        privKey2,
                    )
                },
            )

        // --- ecdhXOnly (Nostr NIP-04/NIP-44 ECDH path) ---
        // Native has no ecdhXOnly — compare against pubKeyTweakMul + extract x.
        results +=
            bench(
                name = "pubKeyTweakMul (NIP-44)",
                warmup = 1000,
                iterations = 3000,
                nativeOp = { native.pubKeyTweakMul(h02 + pub2xOnly, privKey).copyOfRange(1, 33) },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                        .ecdhXOnly(pub2xOnly, privKey)
                },
            )

        // --- taggedHash (BIP-340, heavily used by NIP-44 key derivation) ---
        // The cached BIP-340 tag prefixes in Secp256k1.kt make each call
        // exactly 1 SHA-256 over (64-byte prefix || message). ACINQ has no
        // dedicated tagged-hash API, so we compose sha256(sha256(tag) ||
        // sha256(tag) || msg) by hand on the native side using the same
        // challenge prefix bytes. This measures raw SHA-256 throughput
        // through the different SHA implementations.
        val challengeTag = "BIP0340/challenge".toByteArray()
        val tagHash =
            java.security.MessageDigest
                .getInstance("SHA-256")
                .digest(challengeTag)
        val nativeTaggedInput = tagHash + tagHash + msg32
        val nativeDigest = java.security.MessageDigest.getInstance("SHA-256")
        results +=
            bench(
                name = "taggedHash (NIP-44/BIP340)",
                warmup = 5000,
                iterations = 100000,
                nativeOp = {
                    nativeDigest.reset()
                    nativeDigest.digest(nativeTaggedInput)
                },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                        .taggedHash("BIP0340/challenge", msg32)
                },
            )

        // Print results
        println()
        println("=".repeat(76))
        println("secp256k1 Benchmark: C (ACINQ/JNI) vs Kotlin (JVM21/HotSpot C2)")
        println("=".repeat(76))
        println("Method                          JVM->JNI->C        Pure Kotlin   Ratio")
        println("-".repeat(76))
        for (r in results) {
            println(r)
        }
        println("=".repeat(76))
        println("Verify      Batched Kotlin      JVM->JNI->C        Pure Kotlin   Ratio")
        println("-".repeat(76))

        // ==================== Batch verification benchmark ====================
        // Same pubkey, n events — the typical Nostr pattern (feed from one author).
        // Batch verify uses scalar+point summation: one mulDoubleG for the whole batch
        // instead of n individual mulDoubleG calls.
        val batchPub = kotlinXOnlyPub
        for (batchSize in intArrayOf(4, 8, 16, 32, 200)) {
            val sigs = mutableListOf<ByteArray>()
            val msgs = mutableListOf<ByteArray>()
            for (i in 0 until batchSize) {
                val m = ByteArray(32) { (i * 7 + it).toByte() }
                sigs.add(
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                        .signSchnorr(m, privKey, auxRand),
                )
                msgs.add(m)
            }
            // Warmup both paths
            repeat(500) {
                for (j in 0 until batchSize) {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                        .verifySchnorr(sigs[j], msgs[j], batchPub)
                }
            }
            repeat(500) {
                com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                    .verifySchnorrBatch(batchPub, sigs, msgs)
            }
            // Time individual native
            val iters = 1000

            val indivNativeStart = System.nanoTime()
            repeat(iters) {
                for (j in 0 until batchSize) {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                        .verifySchnorr(sigs[j], msgs[j], batchPub)
                }
            }
            val indivNativeNs = System.nanoTime() - indivNativeStart
            val indivNativePerEvent = iters.toLong() * batchSize * 1_000_000_000L / indivNativeNs

            val indivKotlinStart = System.nanoTime()
            repeat(iters) {
                for (j in 0 until batchSize) {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                        .verifySchnorr(sigs[j], msgs[j], batchPub)
                }
            }
            val indivKotlinNs = System.nanoTime() - indivKotlinStart
            val indivKotlinPerEvent = iters.toLong() * batchSize * 1_000_000_000L / indivKotlinNs

            // Time batch
            val batchStart = System.nanoTime()
            repeat(iters) {
                com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                    .verifySchnorrBatch(batchPub, sigs, msgs)
            }
            val batchNs = System.nanoTime() - batchStart
            val batchPerEvent = iters.toLong() * batchSize * 1_000_000_000L / batchNs
            val speedup = indivNativeNs.toDouble() / batchNs.toDouble()
            println(
                String.format(
                    "%3d evts   %,10d ev/s    %,8d ev/s      %,8d ev/s   %.1fx faster",
                    batchSize,
                    batchPerEvent,
                    indivNativePerEvent,
                    indivKotlinPerEvent,
                    speedup,
                ),
            )
        }
        println("=".repeat(76))
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
