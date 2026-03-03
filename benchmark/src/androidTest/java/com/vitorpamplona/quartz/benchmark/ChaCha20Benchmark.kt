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
import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20
import com.vitorpamplona.quartz.utils.RandomInstance
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChaCha20Benchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    companion object Companion {
        val nip44v2 = Nip44v2()
        val msg = "Hi, how are you? this is supposed to be representative of an average message on Nostr"

        val privateKey = Nip01Crypto.privKeyCreate()
        val publicKey = Nip01Crypto.pubKeyCreate(privateKey)

        val sharedKey = nip44v2.getConversationKey(privateKey, publicKey)

        val nonce = RandomInstance.bytes(32)
        val messageKeys = nip44v2.getMessageKeys(sharedKey, nonce)
        val padded = nip44v2.pad(msg)

        val chaCha = ChaCha20()
    }

    @Test
    fun encryptLibSodium() {
        benchmarkRule.measureRepeated {
            chaCha.encryptLibSodium(padded, messageKeys.chachaNonce, messageKeys.chachaKey)
        }
    }

    /*
        Removed in the conversion to KMP
    @Test
    fun encryptNative() {
        benchmarkRule.measureRepeated {
            chaCha.encryptNative(padded, messageKeys.chachaNonce, messageKeys.chachaKey)
        }
    }
     */

    @Test
    fun decryptLibSodium() {
        benchmarkRule.measureRepeated {
            chaCha.decryptLibSodium(padded, messageKeys.chachaNonce, messageKeys.chachaKey)
        }
    }

    /*
    Removed in the conversion to KMP
    @Test
    fun decryptNative() {
        benchmarkRule.measureRepeated {
            chaCha.decryptNative(padded, messageKeys.chachaNonce, messageKeys.chachaKey)
        }
    }
     */
}
