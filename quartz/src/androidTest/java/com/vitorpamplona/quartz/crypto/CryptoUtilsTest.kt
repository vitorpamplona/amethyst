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
package com.vitorpamplona.quartz.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
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
            CryptoUtils.nip44.v1.getSharedSecret(
                privateKey = privateKey.hexToByteArray(),
                pubKey = publicKey.hexToByteArray(),
            )

        assertEquals("577c966f499dddd8e8dcc34e8f352e283cc177e53ae372794947e0b8ede7cfd8", key.toHexKey())
    }

    @Test
    fun testSharedSecret() {
        val sender = KeyPair()
        val receiver = KeyPair()

        val sharedSecret1 = CryptoUtils.nip44.v1.getSharedSecret(sender.privKey!!, receiver.pubKey)
        val sharedSecret2 = CryptoUtils.nip44.v1.getSharedSecret(receiver.privKey!!, sender.pubKey)

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

        val encrypted = CryptoUtils.nip44.v1.encrypt(msg, privateKey, publicKey)
        val decrypted = CryptoUtils.nip44.v1.decrypt(encrypted, privateKey, publicKey)

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
        val sharedSecret = CryptoUtils.nip44.v1.getSharedSecret(privateKey, publicKey)

        val encrypted = CryptoUtils.nip44.v1.encrypt(msg, sharedSecret)
        val decrypted = CryptoUtils.nip44.v1.decrypt(encrypted, sharedSecret)

        assertEquals(msg, decrypted)
    }

    @Test
    fun signString() {
        val random = "319cc5596fdd6cd767e5a59d976e8e059c61306af90dff1e6ee1067b3a1fdbc0".hexToByteArray()
        val message = "8e58c8251bb406b6ded69e9eb14f55282a9a53bdab16fc49a3218c2ad3abc887".hexToByteArray()
        val keyPair = KeyPair("a5ab474552c8f9c46c2eda5a0b68f27430ad81f96cb405e0cb4e34bf0c6494a2".hexToByteArray())

        val signedMessage = CryptoUtils.sign(message, keyPair.privKey!!, random).toHexKey()
        val expectedValue = "0f9be7e01ba53d5ee6874b9180c7956269fda7a5be424634c3d17b5cfcea6da001be89183876415ba08b7dafa6cff4555e393dc228fb8769b384344e9a27b77c"
        assertEquals(expectedValue, signedMessage)

        val message2 = "Hello"
        val signedMessage2 = CryptoUtils.signString(message2, keyPair.privKey!!, random).toHexKey()
        val expectedValue2 = "7ec8194a585bfb513564113b6b7bfeaafa0254c99d24eaf92280657c2291bab908b1b7bc553c83276a0254aef5041bbe6a50e93381edc4de3d859efa1c3a5a1e"
        assertEquals(expectedValue2, signedMessage2)
    }
}
