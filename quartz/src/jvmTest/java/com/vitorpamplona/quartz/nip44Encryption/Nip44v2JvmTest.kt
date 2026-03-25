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
package com.vitorpamplona.quartz.nip44Encryption

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * NIP-44v2 tests verifying conversation key derivation, ECDH symmetry,
 * and encrypt/decrypt round-trips on JVM Desktop.
 */
class Nip44v2JvmTest {
    @Test
    fun conversationKeyFromSpecVector1() {
        val nip44v2 = Nip44v2()
        val convKey =
            nip44v2.getConversationKey(
                "315e59ff51cb9209768cf7da80791ddcaae56ac9775eb25b6dee1234bc5d2268".hexToByteArray(),
                "c2f9d9948dc8c7c38321e4b85c8558872eafa0641cd269db76848a6073e69133".hexToByteArray(),
            )
        assertEquals(
            "3dfef0ce2a4d80a25e7a328accf73448ef67096f65f79588e358d9a0eb9013f1",
            convKey.toHexKey(),
        )
    }

    @Test
    fun conversationKeyFromSpecVector2() {
        val nip44v2 = Nip44v2()
        val convKey =
            nip44v2.getConversationKey(
                "a1e37752c9fdc1273be53f68c5f74be7c8905728e8de75800b94262f9497c86e".hexToByteArray(),
                "03bb7947065dde12ba991ea045132581d0954f042c84e06d8c00066e23c1a800".hexToByteArray(),
            )
        assertEquals(
            "4d14f36e81b8452128da64fe6f1eae873baae2f444b02c950b90e43553f2178b",
            convKey.toHexKey(),
        )
    }

    @Test
    fun conversationKeyIsSymmetric() {
        val nip44v2 = Nip44v2()
        val privA = "f410f88bcec6cbfda04d6a273c7b1dd8bba144cd45b71e87109cfa11dd7ed561".hexToByteArray()
        val privB = "65f039136f8da8d3e87b4818746b53318d5481e24b2673f162815144223a0b5a".hexToByteArray()
        val pubA = KeyPair(privA).pubKey
        val pubB = KeyPair(privB).pubKey

        val convAB = nip44v2.getConversationKey(privA, pubB)
        val convBA = nip44v2.getConversationKey(privB, pubA)

        assertEquals(convAB.toHexKey(), convBA.toHexKey())
    }

    @Test
    fun encryptDecryptRoundTrip() {
        val nip44v2 = Nip44v2()
        val privA = "0000000000000000000000000000000000000000000000000000000000000001".hexToByteArray()
        val privB = "0000000000000000000000000000000000000000000000000000000000000002".hexToByteArray()
        val pubA = KeyPair(privA).pubKey
        val pubB = KeyPair(privB).pubKey

        val encrypted = nip44v2.encrypt("hello world", privA, pubB)
        val decrypted = nip44v2.decrypt(encrypted, privB, pubA)
        assertEquals("hello world", decrypted)
    }

    @Test
    fun crossKeyPairDecrypt() {
        val nip44v2 = Nip44v2()
        val ephemeralPriv = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".hexToByteArray()
        val signerPriv = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".hexToByteArray()
        val ephemeralPub = KeyPair(ephemeralPriv).pubKey
        val signerPub = KeyPair(signerPriv).pubKey

        val request = """{"id":"test","method":"connect","params":["deadbeef"]}"""
        val encryptedRequest = nip44v2.encrypt(request, ephemeralPriv, signerPub)

        val decryptedRequest = nip44v2.decrypt(encryptedRequest, signerPriv, ephemeralPub)
        assertEquals(request, decryptedRequest)
    }
}
