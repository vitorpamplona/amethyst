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
package com.vitorpamplona.quartz.nip01Core

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import com.vitorpamplona.quartz.utils.sha256.sha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Nip01CryptoTest {
    private val privateKey = "f410f88bcec6cbfda04d6a273c7b1dd8bba144cd45b71e87109cfa11dd7ed561".hexToByteArray()

    @Test
    fun testGetPublicFromPrivateKey() {
        assertEquals(
            "7d4b8806f1fd713c287235411bf95aa81b7242ead892733ec84b3f2719845be6",
            Nip01Crypto.pubKeyCreate(privateKey).toHexKey(),
        )
    }

    @Test
    fun testGetPublicCompressedWith2() {
        val key = "e6159851715b4aa6190c22b899b0c792847de0a4435ac5b678f35738351c43b0".hexToByteArray()
        assertEquals(
            "029fa4ce8c87ca546b196e6518db80a6780e1bd5552b61f9f17bafee5d4e34e09b",
            Secp256k1Instance.compressedPubKeyFor(key).toHexKey(),
        )
    }

    @Test
    fun testGetPublicCompressedWith3() {
        val key = "65f039136f8da8d3e87b4818746b53318d5481e24b2673f162815144223a0b5a".hexToByteArray()
        assertEquals(
            "033dcef7585efbdb68747d919152bd481e21f5e952aaaef5a19604fbd096a93dd5",
            Secp256k1Instance.compressedPubKeyFor(key).toHexKey(),
        )
    }

    @Test
    fun testDeterministicSign() {
        assertEquals(
            "1484d0e0bd62165e822e31f1f4cc8e1ce8e20c30a060e24fb0ecd7baf7c624f661fb7a3e4f0ddb43018e5f0b4892c929af64d8b7a86021aa081ec8231e3dfa37",
            Nip01Crypto.sign(sha256("Test".toByteArray()), privateKey, null).toHexKey(),
        )
    }

    @Test
    fun testSha256() {
        assertEquals(
            "532eaabd9574880dbf76b9b8cc00832c20a6ec113d682299550d7a6e0f345e25",
            sha256("Test".toByteArray()).toHexKey(),
        )
    }

    @Test
    fun testDeterministicVerify() {
        assertTrue(
            Nip01Crypto.verify(
                "1484d0e0bd62165e822e31f1f4cc8e1ce8e20c30a060e24fb0ecd7baf7c624f661fb7a3e4f0ddb43018e5f0b4892c929af64d8b7a86021aa081ec8231e3dfa37".hexToByteArray(),
                sha256("Test".toByteArray()),
                Nip01Crypto.pubKeyCreate(privateKey),
            ),
        )
    }

    @Test
    fun testNonDeterministicSign() {
        assertNotEquals(
            "1484d0e0bd62165e822e31f1f4cc8e1ce8e20c30a060e24fb0ecd7baf7c624f661fb7a3e4f0ddb43018e5f0b4892c929af64d8b7a86021aa081ec8231e3dfa37",
            Nip01Crypto.sign(sha256("Test".toByteArray()), privateKey).toHexKey(),
        )
    }

    @Test
    fun testNonDeterministicSignVerify() {
        val signature = Nip01Crypto.sign(sha256("Test".toByteArray()), privateKey)
        assertTrue(
            Nip01Crypto.verify(
                signature,
                sha256("Test".toByteArray()),
                Nip01Crypto.pubKeyCreate(privateKey),
            ),
        )
    }
}
