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
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip04Dm.crypto.Encryption
import com.vitorpamplona.quartz.nip04Dm.crypto.Nip04
import com.vitorpamplona.quartz.nip44Encryption.Nip44v2
import junit.framework.TestCase.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedKeyBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    @Test
    fun getSharedKeyNip04() {
        val keyPair1 = KeyPair()
        val keyPair2 = KeyPair()

        benchmarkRule.measureRepeated {
            assertNotNull(Nip04.getSharedSecret(keyPair1.privKey!!, keyPair2.pubKey))
        }
    }

    @Test
    fun getSharedKeyNip44() {
        val keyPair1 = KeyPair()
        val keyPair2 = KeyPair()
        val nip44v2 = Nip44v2()

        benchmarkRule.measureRepeated {
            assertNotNull(nip44v2.getConversationKey(keyPair1.privKey!!, keyPair2.pubKey))
        }
    }

    @Test
    fun computeSharedKeyNip04() {
        val keyPair1 = KeyPair()
        val keyPair2 = KeyPair()
        val nip04 = Encryption()

        benchmarkRule.measureRepeated {
            assertNotNull(nip04.computeSharedSecret(keyPair1.privKey!!, keyPair2.pubKey))
        }
    }

    @Test
    fun computeSharedKeyNip44() {
        val keyPair1 = KeyPair()
        val keyPair2 = KeyPair()
        val nip44v2 = Nip44v2()

        benchmarkRule.measureRepeated {
            assertNotNull(nip44v2.computeConversationKey(keyPair1.privKey!!, keyPair2.pubKey))
        }
    }
}
