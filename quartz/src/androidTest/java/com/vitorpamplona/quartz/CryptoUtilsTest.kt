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
package com.vitorpamplona.quartz

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CryptoUtilsTest {
    @Test
    fun testGetPublicFromPrivateKey() {
        val privateKey =
            "f410f88bcec6cbfda04d6a273c7b1dd8bba144cd45b71e87109cfa11dd7ed561".hexToByteArray()
        val publicKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
        assertEquals("7d4b8806f1fd713c287235411bf95aa81b7242ead892733ec84b3f2719845be6", publicKey)
    }

    @Test
    fun testSharedSecretCompatibilityWithCoracle() {
        val privateKey = "f410f88bcec6cbfda04d6a273c7b1dd8bba144cd45b71e87109cfa11dd7ed561"
        val publicKey = "765cd7cf91d3ad07423d114d5a39c61d52b2cdbc18ba055ddbbeec71fbe2aa2f"

        val key =
            CryptoUtils.getSharedSecretNIP44v1(
                privateKey = privateKey.hexToByteArray(),
                pubKey = publicKey.hexToByteArray(),
            )

        assertEquals("577c966f499dddd8e8dcc34e8f352e283cc177e53ae372794947e0b8ede7cfd8", key.toHexKey())
    }

    @Test
    fun testSharedSecret() {
        val sender = KeyPair()
        val receiver = KeyPair()

        val sharedSecret1 = CryptoUtils.getSharedSecretNIP44v1(sender.privKey!!, receiver.pubKey)
        val sharedSecret2 = CryptoUtils.getSharedSecretNIP44v1(receiver.privKey!!, sender.pubKey)

        assertEquals(sharedSecret1.toHexKey(), sharedSecret2.toHexKey())

        val secretKey1 = KeyPair(privKey = sharedSecret1)
        val secretKey2 = KeyPair(privKey = sharedSecret2)

        assertEquals(secretKey1.pubKey.toHexKey(), secretKey2.pubKey.toHexKey())
        assertEquals(secretKey1.privKey?.toHexKey(), secretKey2.privKey?.toHexKey())
    }

    @Test
    fun encryptDecryptNIP4Test() {
        val msg = "Hi"

        val privateKey = CryptoUtils.privkeyCreate()
        val publicKey = CryptoUtils.pubkeyCreate(privateKey)

        val encrypted = CryptoUtils.encryptNIP04(msg, privateKey, publicKey)
        val decrypted = CryptoUtils.decryptNIP04(encrypted, privateKey, publicKey)

        assertEquals(msg, decrypted)
    }

    @Test
    fun encryptDecryptNIP44v1Test() {
        val msg = "Hi"

        val privateKey = CryptoUtils.privkeyCreate()
        val publicKey = CryptoUtils.pubkeyCreate(privateKey)

        val encrypted = CryptoUtils.encryptNIP44v1(msg, privateKey, publicKey)
        val decrypted = CryptoUtils.decryptNIP44v1(encrypted, privateKey, publicKey)

        assertEquals(msg, decrypted)
    }

    @Test
    fun encryptSharedSecretDecryptNIP4Test() {
        val msg = "Hi"

        val privateKey = CryptoUtils.privkeyCreate()
        val publicKey = CryptoUtils.pubkeyCreate(privateKey)

        val encrypted = CryptoUtils.encryptNIP04(msg, privateKey, publicKey)
        val decrypted = CryptoUtils.decryptNIP04(encrypted, privateKey, publicKey)

        assertEquals(msg, decrypted)
    }

    @Test
    fun encryptSharedSecretDecryptNIP44v1Test() {
        val msg = "Hi"

        val privateKey = CryptoUtils.privkeyCreate()
        val publicKey = CryptoUtils.pubkeyCreate(privateKey)
        val sharedSecret = CryptoUtils.getSharedSecretNIP44v1(privateKey, publicKey)

        val encrypted = CryptoUtils.encryptNIP44v1(msg, sharedSecret)
        val decrypted = CryptoUtils.decryptNIP44v1(encrypted, sharedSecret)

        assertEquals(msg, decrypted)
    }
}
