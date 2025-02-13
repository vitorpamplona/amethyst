/**
 * Copyright (c) 2024 Vitor Pamplona
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
import com.vitorpamplona.quartz.CryptoUtils
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import junit.framework.TestCase.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CryptoBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    @Test
    fun getSharedKeyNip04() {
        val keyPair1 = KeyPair()
        val keyPair2 = KeyPair()

        benchmarkRule.measureRepeated {
            assertNotNull(CryptoUtils.getSharedSecretNIP04(keyPair1.privKey!!, keyPair2.pubKey))
        }
    }

    @Test
    fun getSharedKeyNip44() {
        val keyPair1 = KeyPair()
        val keyPair2 = KeyPair()

        benchmarkRule.measureRepeated {
            assertNotNull(CryptoUtils.nip44.v1.getSharedSecret(keyPair1.privKey!!, keyPair2.pubKey))
        }
    }

    @Test
    fun computeSharedKeyNip04() {
        val keyPair1 = KeyPair()
        val keyPair2 = KeyPair()

        benchmarkRule.measureRepeated {
            assertNotNull(CryptoUtils.computeSharedSecretNIP04(keyPair1.privKey!!, keyPair2.pubKey))
        }
    }

    @Test
    fun computeSharedKeyNip44() {
        val keyPair1 = KeyPair()
        val keyPair2 = KeyPair()

        benchmarkRule.measureRepeated {
            assertNotNull(CryptoUtils.nip44.v1.computeSharedSecret(keyPair1.privKey!!, keyPair2.pubKey))
        }
    }

    @Test
    fun random() {
        benchmarkRule.measureRepeated { assertNotNull(CryptoUtils.random(1000)) }
    }

    @Test
    fun sha256() {
        val keyPair = KeyPair()

        benchmarkRule.measureRepeated { assertNotNull(CryptoUtils.sha256(keyPair.pubKey)) }
    }

    @Test
    fun sign() {
        val keyPair = KeyPair()
        val msg = CryptoUtils.sha256(CryptoUtils.random(1000))

        benchmarkRule.measureRepeated { assertNotNull(CryptoUtils.sign(msg, keyPair.privKey!!)) }
    }

    @Test
    fun verify() {
        val keyPair = KeyPair()
        val msg = CryptoUtils.sha256(CryptoUtils.random(1000))
        val signature = CryptoUtils.sign(msg, keyPair.privKey!!)

        benchmarkRule.measureRepeated {
            assertNotNull(CryptoUtils.verifySignature(signature, msg, keyPair.pubKey))
        }
    }
}
