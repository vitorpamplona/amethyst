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
package com.vitorpamplona.quartz.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.utils.Secp256k1InstanceKotlin
import com.vitorpamplona.schnorr256k1.Schnorr256k1
import fr.acinq.secp256k1.Secp256k1
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android benchmark comparing three secp256k1 implementations:
 * 1. ACINQ C (libsecp256k1 via JNI) — "Foo"
 * 2. Pure Kotlin — "FooOurs"
 * 3. Custom C (libschnorr256k1 via JNI) — "FooC"
 *
 * Run with: ./gradlew :benchmark:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class Secp256k1CBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val privKey = hexToBytes("67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530")
    private val msg32 = hexToBytes("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89")
    private val auxRand = hexToBytes("0000000000000000000000000000000000000000000000000000000000000001")

    private val native = Secp256k1.get()
    private val nativePubKey = native.pubKeyCompress(native.pubkeyCreate(privKey))
    private val nativeXOnlyPub = nativePubKey.copyOfRange(1, 33)
    private val nativeSig = native.signSchnorr(msg32, privKey, auxRand)

    private val kotlinSig =
        com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
            .signSchnorr(msg32, privKey, auxRand)
    private val kotlinXOnlyPub =
        com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
            .pubKeyCompress(
                com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                    .pubkeyCreate(privKey),
            ).copyOfRange(1, 33)

    // ==================== ACINQ C (Reference) ====================

    @Test fun verifySchnorr() = benchmarkRule.measureRepeated { native.verifySchnorr(nativeSig, msg32, nativeXOnlyPub) }

    @Test fun signSchnorr() = benchmarkRule.measureRepeated { native.signSchnorr(msg32, privKey, auxRand) }

    @Test
    fun pubkeyCreate() = benchmarkRule.measureRepeated { native.pubKeyCompress(native.pubkeyCreate(privKey)) }

    // ==================== Pure Kotlin ====================

    @Test
    fun verifySchnorrOurs() =
        benchmarkRule.measureRepeated {
            com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                .verifySchnorr(kotlinSig, msg32, kotlinXOnlyPub)
        }

    @Test
    fun verifySchnorrFastOurs() =
        benchmarkRule.measureRepeated {
            com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                .verifySchnorrFast(kotlinSig, msg32, kotlinXOnlyPub)
        }

    @Test
    fun signSchnorrOurs() =
        benchmarkRule.measureRepeated {
            com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                .signSchnorr(msg32, privKey, auxRand)
        }

    @Test
    fun signSchnorrXOnlyOurs() =
        benchmarkRule.measureRepeated {
            com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                .signSchnorrWithXOnlyPubKey(msg32, privKey, kotlinXOnlyPub, auxRand)
        }

    @Test
    fun pubkeyCreateOurs() =
        benchmarkRule.measureRepeated {
            com.vitorpamplona.quartz.utils.secp256k1.Secp256k1.pubKeyCompress(
                com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                    .pubkeyCreate(privKey),
            )
        }

    // ==================== Custom C (libschnorr256k1 JNI) ====================

    @Test
    fun verifySchnorrC() {
        val cSig = Schnorr256k1.schnorrSign(msg32, privKey, auxRand)!!
        val cXOnly = Schnorr256k1.pubkeyCompress(Schnorr256k1.pubkeyCreate(privKey)!!)!!.copyOfRange(1, 33)
        benchmarkRule.measureRepeated { Schnorr256k1.schnorrVerify(cSig, msg32, cXOnly) }
    }

    @Test
    fun verifySchnorrFastC() {
        val cSig = Schnorr256k1.schnorrSign(msg32, privKey, auxRand)!!
        val cXOnly = Schnorr256k1.pubkeyCompress(Schnorr256k1.pubkeyCreate(privKey)!!)!!.copyOfRange(1, 33)
        benchmarkRule.measureRepeated { Schnorr256k1.schnorrVerifyFast(cSig, msg32, cXOnly) }
    }

    @Test
    fun signSchnorrC() {
        benchmarkRule.measureRepeated { Schnorr256k1.schnorrSign(msg32, privKey, auxRand) }
    }

    @Test
    fun signSchnorrXOnlyC() {
        val cXOnly = Schnorr256k1.pubkeyCompress(Schnorr256k1.pubkeyCreate(privKey)!!)!!.copyOfRange(1, 33)
        benchmarkRule.measureRepeated { Schnorr256k1.schnorrSignXOnly(msg32, privKey, cXOnly, auxRand) }
    }

    @Test
    fun pubkeyCreateC() {
        benchmarkRule.measureRepeated { Schnorr256k1.pubkeyCompress(Schnorr256k1.pubkeyCreate(privKey)!!) }
    }

    @Test
    fun ecdhXOnlyC() {
        val pub2xOnly = hexToBytes("c2f9d9948dc8c7c38321e4b85c8558872eafa0641cd269db76848a6073e69133")
        benchmarkRule.measureRepeated { Schnorr256k1.ecdhXOnly(pub2xOnly, privKey) }
    }

    // ==================== Batch Verification (all three) ====================

    @Test
    fun verifySchnorrBatch16Ours() {
        val sigs =
            (0 until 16).map { i ->
                val m = ByteArray(32) { (i * 7 + it).toByte() }
                com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                    .signSchnorr(m, privKey, auxRand)
            }
        val msgs = (0 until 16).map { i -> ByteArray(32) { (i * 7 + it).toByte() } }
        benchmarkRule.measureRepeated {
            Secp256k1InstanceKotlin.verifySchnorrBatch(kotlinXOnlyPub, sigs, msgs)
        }
    }

    @Test
    fun verifySchnorrBatch16C() {
        val cXOnly = Schnorr256k1.pubkeyCompress(Schnorr256k1.pubkeyCreate(privKey)!!)!!.copyOfRange(1, 33)
        val sigs =
            (0 until 16).map { i ->
                val m = ByteArray(32) { (i * 7 + it).toByte() }
                Schnorr256k1.schnorrSign(m, privKey, auxRand)!!
            }
        val msgs = (0 until 16).map { i -> ByteArray(32) { (i * 7 + it).toByte() } }
        benchmarkRule.measureRepeated {
            Schnorr256k1.schnorrVerifyBatch(cXOnly, sigs, msgs)
        }
    }

    @Test
    fun verifySchnorrBatch200Ours() {
        val sigs =
            (0 until 200).map { i ->
                val m = ByteArray(32) { (i * 7 + it).toByte() }
                com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                    .signSchnorr(m, privKey, auxRand)
            }
        val msgs = (0 until 200).map { i -> ByteArray(32) { (i * 7 + it).toByte() } }
        benchmarkRule.measureRepeated {
            Secp256k1InstanceKotlin.verifySchnorrBatch(kotlinXOnlyPub, sigs, msgs)
        }
    }

    @Test
    fun verifySchnorrBatch200C() {
        val cXOnly = Schnorr256k1.pubkeyCompress(Schnorr256k1.pubkeyCreate(privKey)!!)!!.copyOfRange(1, 33)
        val sigs =
            (0 until 200).map { i ->
                val m = ByteArray(32) { (i * 7 + it).toByte() }
                Schnorr256k1.schnorrSign(m, privKey, auxRand)!!
            }
        val msgs = (0 until 200).map { i -> ByteArray(32) { (i * 7 + it).toByte() } }
        benchmarkRule.measureRepeated {
            Schnorr256k1.schnorrVerifyBatch(cXOnly, sigs, msgs)
        }
    }

    // ==================== SHA-256 Comparison ====================

    private val sha256Input = ByteArray(160) { it.toByte() } // BIP-340 tagged hash size

    @Test
    fun sha256Android() {
        // Android's native SHA-256 (BoringSSL with ARM64 Crypto Extensions)
        val md = java.security.MessageDigest.getInstance("SHA-256")
        benchmarkRule.measureRepeated {
            md.reset()
            md.update(sha256Input)
            md.digest()
        }
    }

    @Test
    fun sha256OurC() {
        // libschnorr256k1 SHA-256 (ARM64 CE hardware acceleration where available)
        benchmarkRule.measureRepeated {
            Schnorr256k1.sha256(sha256Input)
        }
    }

    @Test
    fun sha256Kotlin() {
        // Kotlin SHA-256 (uses platform MessageDigest on Android)
        benchmarkRule.measureRepeated {
            com.vitorpamplona.quartz.utils.sha256
                .sha256(sha256Input)
        }
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
