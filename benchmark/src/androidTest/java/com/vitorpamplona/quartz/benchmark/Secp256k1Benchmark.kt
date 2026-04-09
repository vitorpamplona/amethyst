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
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import com.vitorpamplona.quartz.utils.Secp256k1InstanceKotlin
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android microbenchmark for all secp256k1 operations used by Nostr.
 *
 * Measures the same operations as the JVM benchmark (Secp256k1Benchmark.kt) and the
 * K/Native benchmark (Secp256k1NativeBenchmark.kt) for cross-platform comparability.
 *
 * Tests are structured as matched pairs: "Foo" uses the native C library (ACINQ/JNI),
 * "FooOurs" uses the pure-Kotlin implementation.
 *
 * NOTE: verifySchnorr uses the same pubkey repeatedly, so it benefits from the
 * pubkey decompression cache and P-table cache. This is realistic for Nostr
 * (feed from one author) but not representative of first-time verification.
 *
 * Test data is the same across all 3 platform benchmarks for cross-platform comparability.
 *
 * Run with: ./gradlew :benchmark:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class Secp256k1Benchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    // Test data (same across all 3 platform benchmarks for comparability)
    private val privKey = "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530".hexToByteArray()
    private val msg32 = "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89".hexToByteArray()
    private val auxRand = "0000000000000000000000000000000000000000000000000000000000000001".hexToByteArray()

    // Pre-computed to avoid measuring setup costs
    private val compressedPubKey = Secp256k1Instance.compressedPubKeyFor(privKey)
    private val xOnlyPub = compressedPubKey.copyOfRange(1, 33)
    private val signature = Secp256k1Instance.signSchnorr(msg32, privKey, auxRand)

    // ECDH test data
    private val privKey2 = "3982F19BEF1615BCCFBB05E321C10E1D4CBA3DF0E841C2E41EEB6016347653C3".hexToByteArray()
    private val pubKey2XOnly = Secp256k1Instance.compressedPubKeyFor(privKey2).copyOfRange(1, 33)

    // Batch verification data
    private val batch8 = buildBatch(8)
    private val batch16 = buildBatch(16)
    private val batch32 = buildBatch(32)
    private val batch200 = buildBatch(200)

    private fun buildBatch(size: Int): Pair<List<ByteArray>, List<ByteArray>> {
        val sigs = mutableListOf<ByteArray>()
        val msgs = mutableListOf<ByteArray>()
        for (i in 0 until size) {
            val m = ByteArray(32) { (i * 7 + it).toByte() }
            sigs.add(Secp256k1InstanceKotlin.signSchnorr(m, privKey, auxRand))
            msgs.add(m)
        }
        return Pair(sigs, msgs)
    }

    // ==================== Native C (ACINQ/JNI) ====================

    @Test
    fun verifySchnorr() {
        benchmarkRule.measureRepeated {
            assertTrue(Secp256k1Instance.verifySchnorr(signature, msg32, xOnlyPub))
        }
    }

    @Test
    fun signSchnorr() {
        benchmarkRule.measureRepeated {
            assertNotNull(Secp256k1Instance.signSchnorr(msg32, privKey, auxRand))
        }
    }

    @Test
    fun xPubKeyFor() {
        benchmarkRule.measureRepeated {
            assertNotNull(Secp256k1Instance.compressedPubKeyFor(privKey))
        }
    }

    @Test
    fun secKeyVerify() {
        benchmarkRule.measureRepeated {
            assertTrue(Secp256k1Instance.isPrivateKeyValid(privKey))
        }
    }

    @Test
    fun privateKeyTweakAdd() {
        benchmarkRule.measureRepeated {
            assertNotNull(Secp256k1Instance.privateKeyAdd(privKey, privKey2))
        }
    }

    @Test
    fun pubKeyTweakMul() {
        benchmarkRule.measureRepeated {
            assertNotNull(Secp256k1Instance.pubKeyTweakMulCompact(pubKey2XOnly, privKey))
        }
    }

    // ==================== Pure Kotlin ====================

    @Test
    fun verifySchnorrKotlin() {
        benchmarkRule.measureRepeated {
            assertTrue(Secp256k1InstanceKotlin.verifySchnorr(signature, msg32, xOnlyPub))
        }
    }

    @Test
    fun verifySchnorrXPubkeyKotlin() {
        benchmarkRule.measureRepeated {
            assertTrue(Secp256k1InstanceKotlin.verifySchnorrFast(signature, msg32, xOnlyPub))
        }
    }

    @Test
    fun signSchnorrKotlin() {
        benchmarkRule.measureRepeated {
            assertNotNull(Secp256k1InstanceKotlin.signSchnorr(msg32, privKey, auxRand))
        }
    }

    @Test
    fun signSchnorrXPubkeyKotlin() {
        benchmarkRule.measureRepeated {
            assertNotNull(Secp256k1InstanceKotlin.signSchnorrWithXOnlyPubKey(msg32, privKey, xOnlyPub, auxRand))
        }
    }

    @Test
    fun xPubKeyForKotlin() {
        benchmarkRule.measureRepeated {
            assertNotNull(Secp256k1InstanceKotlin.compressedPubKeyFor(privKey))
        }
    }

    @Test
    fun secKeyVerifyKotlin() {
        benchmarkRule.measureRepeated {
            assertTrue(Secp256k1InstanceKotlin.isPrivateKeyValid(privKey))
        }
    }

    @Test
    fun privateKeyTweakAddKotlin() {
        benchmarkRule.measureRepeated {
            assertNotNull(Secp256k1InstanceKotlin.privateKeyAdd(privKey, privKey2))
        }
    }

    @Test
    fun pubKeyTweakMulKotlin() {
        benchmarkRule.measureRepeated {
            assertNotNull(Secp256k1InstanceKotlin.pubKeyTweakMulCompact(pubKey2XOnly, privKey))
        }
    }

    // ==================== Batch verification (Pure Kotlin) ====================
    // Same pubkey, n events — typical Nostr pattern (feed from one author).
    // Per-event cost = ns/op ÷ batchSize.

    @Test
    fun verifyBatch8Kotlin() {
        benchmarkRule.measureRepeated {
            assertTrue(Secp256k1InstanceKotlin.verifySchnorrBatch(xOnlyPub, batch8.first, batch8.second))
        }
    }

    @Test
    fun verifyBatch16Kotlin() {
        benchmarkRule.measureRepeated {
            assertTrue(Secp256k1InstanceKotlin.verifySchnorrBatch(xOnlyPub, batch16.first, batch16.second))
        }
    }

    @Test
    fun verifyBatch32Kotlin() {
        benchmarkRule.measureRepeated {
            assertTrue(Secp256k1InstanceKotlin.verifySchnorrBatch(xOnlyPub, batch32.first, batch32.second))
        }
    }

    @Test
    fun verifyBatch200Kotlin() {
        benchmarkRule.measureRepeated {
            assertTrue(Secp256k1InstanceKotlin.verifySchnorrBatch(xOnlyPub, batch200.first, batch200.second))
        }
    }
}
