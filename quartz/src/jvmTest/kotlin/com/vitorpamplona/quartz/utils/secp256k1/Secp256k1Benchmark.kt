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
 * the native ACINQ/secp256k1-kmp JNI bindings.
 *
 * Each benchmark runs warmup iterations, then measures the timed iterations.
 * Results are printed as ops/sec and relative speed.
 */
class Secp256k1Benchmark {
    // Test data
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
    private val pubKey2Uncompressed =
        hexToBytes(
            "040A629506E1B65CD9D2E0BA9C75DF9C4FED0DB16DC9625ED14397F0AFC836FAE5" +
                "95DC53F8B0EFE61E703075BD9B143BAC75EC0E19F82A2208CAEB32BE53414C40",
        )
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
            String.format(
                "%-25s  Native: %,8d ops/s  Kotlin: %,8d ops/s  Ratio: %.1fx slower",
                name,
                nativeOpsPerSec,
                kotlinOpsPerSec,
                ratio,
            )
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

        // --- verifySchnorr ---
        results +=
            bench(
                name = "verifySchnorr",
                warmup = 20,
                iterations = 100,
                nativeOp = { native.verifySchnorr(nativeSig, msg32, nativeXOnlyPub) },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1.verifySchnorr(
                        kotlinSig,
                        msg32,
                        kotlinXOnlyPub,
                    )
                },
            )

        // --- signSchnorr (derives pubkey from seckey each time) ---
        results +=
            bench(
                name = "signSchnorr",
                warmup = 10,
                iterations = 50,
                nativeOp = { native.signSchnorr(msg32, privKey, auxRand) },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1.signSchnorr(
                        msg32,
                        privKey,
                        auxRand,
                    )
                },
            )

        // --- signSchnorrWithPubKey (pre-computed pubkey, no self-verify — matches C) ---
        results +=
            bench(
                name = "signSchnorr (cached pk)",
                warmup = 10,
                iterations = 50,
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

        // --- pubkeyCreate ---
        results +=
            bench(
                name = "pubkeyCreate",
                warmup = 20,
                iterations = 100,
                nativeOp = { native.pubkeyCreate(privKey) },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                        .pubkeyCreate(privKey)
                },
            )

        // --- pubKeyCompress ---
        val uncompressedNative = native.pubkeyCreate(privKey)
        val uncompressedKotlin =
            com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                .pubkeyCreate(privKey)
        results +=
            bench(
                name = "pubKeyCompress",
                warmup = 50,
                iterations = 200,
                nativeOp = { native.pubKeyCompress(uncompressedNative) },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                        .pubKeyCompress(uncompressedKotlin)
                },
            )

        // --- pubKeyTweakMul (ECDH) ---
        results +=
            bench(
                name = "pubKeyTweakMul (ECDH)",
                warmup = 10,
                iterations = 50,
                nativeOp = { native.pubKeyTweakMul(pubKey2Uncompressed.copyOf(), privKey) },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1.pubKeyTweakMul(
                        pubKey2Uncompressed,
                        privKey,
                    )
                },
            )

        // --- privKeyTweakAdd ---
        results +=
            bench(
                name = "privKeyTweakAdd",
                warmup = 100,
                iterations = 1000,
                nativeOp = { native.privKeyTweakAdd(privKey.copyOf(), privKey2) },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1.privKeyTweakAdd(
                        privKey,
                        privKey2,
                    )
                },
            )

        // --- secKeyVerify ---
        results +=
            bench(
                name = "secKeyVerify",
                warmup = 100,
                iterations = 10000,
                nativeOp = { native.secKeyVerify(privKey) },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                        .secKeyVerify(privKey)
                },
            )

        // --- compressedPubKeyFor (create + compress combined) ---
        results +=
            bench(
                name = "compressedPubKeyFor",
                warmup = 10,
                iterations = 100,
                nativeOp = { native.pubKeyCompress(native.pubkeyCreate(privKey)) },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1.pubKeyCompress(
                        com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                            .pubkeyCreate(privKey),
                    )
                },
            )

        // --- pubKeyTweakMulCompact (old pattern via pubKeyTweakMul) ---
        val pub2xOnly = hexToBytes("c2f9d9948dc8c7c38321e4b85c8558872eafa0641cd269db76848a6073e69133")
        results +=
            bench(
                name = "tweakMulCompact (old)",
                warmup = 10,
                iterations = 50,
                nativeOp = { native.pubKeyTweakMul(h02 + pub2xOnly, privKey).copyOfRange(1, 33) },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                        .pubKeyTweakMul(
                            h02 + pub2xOnly,
                            privKey,
                        ).copyOfRange(1, 33)
                },
            )

        // --- ecdhXOnly (actual Nostr ECDH path) ---
        results +=
            bench(
                name = "ecdhXOnly (Nostr)",
                warmup = 10,
                iterations = 50,
                nativeOp = { native.pubKeyTweakMul(h02 + pub2xOnly, privKey).copyOfRange(1, 33) },
                kotlinOp = {
                    com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                        .ecdhXOnly(pub2xOnly, privKey)
                },
            )

        // Print results
        println()
        println("=".repeat(90))
        println("secp256k1 Benchmark: Native (ACINQ/JNI) vs Pure Kotlin")
        println("=".repeat(90))
        for (r in results) {
            println(r)
        }
        println("=".repeat(90))
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
