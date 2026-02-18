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
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip44Encryption.Nip44v2
import com.vitorpamplona.quartz.nip44Encryption.crypto.Hkdf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HkdfBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    companion object {
        val nip44v2 = Nip44v2()
        val msg = "Hi, how are you? this is supposed to be representative of an average message on Nostr"

        val privateKey = Nip01Crypto.privKeyCreate()
        val publicKey = Nip01Crypto.pubKeyCreate(privateKey)

        val sharedKey = nip44v2.getConversationKey(privateKey, publicKey)
        val encrypted = nip44v2.encrypt(msg, sharedKey)
    }

    @Test
    fun hkdfExpandWithCheck() {
        val hkdf = Hkdf()
        benchmarkRule.measureRepeated {
            hkdf.fastExpand(sharedKey, encrypted.nonce, encrypted.ciphertext, encrypted.mac)
        }
    }

    @Test
    fun hkdfFastExpand() {
        val hkdf = Hkdf()
        benchmarkRule.measureRepeated {
            hkdf.fastExpand(sharedKey, encrypted.nonce)
        }
    }

    /*
    @Test
    fun hkdfExpandOld() {
        val hkdf = Hkdf()
        benchmarkRule.measureRepeated {
            hkdf.expand(sharedKey, encrypted.nonce, 76)
        }
    }
     */

    @Test
    fun hkdfExtract() {
        val messageKey = nip44v2.getMessageKeys(sharedKey, encrypted.nonce)
        val hkdf = Hkdf()
        benchmarkRule.measureRepeated {
            hkdf.extract(encrypted.nonce, encrypted.ciphertext, messageKey.hmacKey)
        }
    }
}
