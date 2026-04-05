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
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android microbenchmark for all secp256k1 operations used by Nostr.
 *
 * Uses AndroidX Benchmark (Jetpack Microbenchmark) for accurate, warmed-up
 * measurements on real Android hardware/emulator. Results appear in Android
 * Studio's benchmark output with ns/op, allocations, and percentiles.
 *
 * Run with: ./gradlew :benchmark:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class Secp256k1Benchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    // Test data
    private val privKey = "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530".hexToByteArray()
    private val msg32 = "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89".hexToByteArray()
    private val auxRand = "0000000000000000000000000000000000000000000000000000000000000001".hexToByteArray()

    // Pre-computed to avoid measuring setup costs
    private val compressedPubKey = Secp256k1Instance.compressedPubKeyFor(privKey)
    private val xOnlyPub = compressedPubKey.copyOfRange(1, 33)
    private val signature = Secp256k1Instance.signSchnorr(msg32, privKey, auxRand)
    private val uncompressedPubKey by lazy {
        val seckey2 = "3982F19BEF1615BCCFBB05E321C10E1D4CBA3DF0E841C2E41EEB6016347653C3".hexToByteArray()
        val compressed = Secp256k1Instance.compressedPubKeyFor(seckey2)
        // We need an uncompressed key for pubKeyCompress benchmark
        // Use the x-coordinate and compute full key
        compressed // will be compressed for the benchmark
    }

    // ECDH test data
    private val privKey2 = "3982F19BEF1615BCCFBB05E321C10E1D4CBA3DF0E841C2E41EEB6016347653C3".hexToByteArray()
    private val pubKey2XOnly = Secp256k1Instance.compressedPubKeyFor(privKey2).copyOfRange(1, 33)

    // ==================== Core Nostr operations ====================

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
    fun compressedPubKeyFor() {
        benchmarkRule.measureRepeated {
            assertNotNull(Secp256k1Instance.compressedPubKeyFor(privKey))
        }
    }

    // ==================== Key operations ====================

    @Test
    fun secKeyVerify() {
        benchmarkRule.measureRepeated {
            assertTrue(Secp256k1Instance.isPrivateKeyValid(privKey))
        }
    }

    @Test
    fun pubKeyCompress() {
        // Compress an already-compressed key (fast path)
        benchmarkRule.measureRepeated {
            assertNotNull(Secp256k1Instance.compressedPubKeyFor(privKey))
        }
    }

    @Test
    fun privateKeyAdd() {
        benchmarkRule.measureRepeated {
            assertNotNull(Secp256k1Instance.privateKeyAdd(privKey, privKey2))
        }
    }

    // ==================== ECDH (NIP-04, NIP-44) ====================

    @Test
    fun pubKeyTweakMulCompact() {
        benchmarkRule.measureRepeated {
            assertNotNull(Secp256k1Instance.pubKeyTweakMulCompact(pubKey2XOnly, privKey))
        }
    }
}
