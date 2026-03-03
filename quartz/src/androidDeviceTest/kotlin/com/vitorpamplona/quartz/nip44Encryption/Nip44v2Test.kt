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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.sha256.sha256
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.fail
import kotlinx.serialization.json.decodeFromStream
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Nip44v2Test {
    private val vectors: VectorFile =
        JsonMapper.jsonInstance.decodeFromStream<VectorFile>(
            javaClass.classLoader.getResourceAsStream("nip44.vectors.json"),
        )

    private val nip44v2 = Nip44v2()

    @Test
    fun conversationKeyTest() {
        for (v in vectors.v2?.valid?.getConversationKey!!) {
            val conversationKey =
                nip44v2.getConversationKey(v.sec1!!.hexToByteArray(), v.pub2!!.hexToByteArray())

            assertEquals(v.conversationKey, conversationKey.toHexKey())
        }
    }

    @Test
    fun paddingTest() {
        for (v in vectors.v2?.valid?.calcPaddedLen!!) {
            val actual = nip44v2.calcPaddedLen(v[0])
            assertEquals(v[1], actual)
        }
    }

    @Test
    fun testCompressedWith02Keys() {
        val privateKeyA = "f410f88bcec6cbfda04d6a273c7b1dd8bba144cd45b71e87109cfa11dd7ed561".hexToByteArray()
        val privateKeyB = "65f039136f8da8d3e87b4818746b53318d5481e24b2673f162815144223a0b5a".hexToByteArray()

        val publicKeyA = Nip01Crypto.pubKeyCreate(privateKeyA)
        val publicKeyB = Nip01Crypto.pubKeyCreate(privateKeyB)

        assertEquals(
            nip44v2.getConversationKey(privateKeyA, publicKeyB).toHexKey(),
            nip44v2.getConversationKey(privateKeyB, publicKeyA).toHexKey(),
        )
    }

    @Test
    fun testCompressedWith03Keys() {
        val privateKeyA = "f410f88bcec6cbfda04d6a273c7b1dd8bba144cd45b71e87109cfa11dd7ed561".hexToByteArray()
        val privateKeyB = "e6159851715b4aa6190c22b899b0c792847de0a4435ac5b678f35738351c43b0".hexToByteArray()

        val publicKeyA = Nip01Crypto.pubKeyCreate(privateKeyA)
        val publicKeyB = Nip01Crypto.pubKeyCreate(privateKeyB)

        assertEquals(
            nip44v2.getConversationKey(privateKeyA, publicKeyB).toHexKey(),
            nip44v2.getConversationKey(privateKeyB, publicKeyA).toHexKey(),
        )
    }

    @Test
    fun encryptDecryptTest() {
        for (v in vectors.v2?.valid?.encryptDecrypt!!) {
            val pub2 = KeyPair(v.sec2!!.hexToByteArray())
            val conversationKey1 = nip44v2.getConversationKey(v.sec1!!.hexToByteArray(), pub2.pubKey)
            assertEquals(v.conversationKey, conversationKey1.toHexKey())

            val ciphertext =
                nip44v2
                    .encryptWithNonce(
                        v.plaintext!!,
                        conversationKey1,
                        v.nonce!!.hexToByteArray(),
                    ).encodePayload()

            assertEquals(v.payload, ciphertext)

            val pub1 = KeyPair(v.sec1.hexToByteArray())
            val conversationKey2 = nip44v2.getConversationKey(v.sec2.hexToByteArray(), pub1.pubKey)
            assertEquals(v.conversationKey, conversationKey2.toHexKey())

            val decrypted = nip44v2.decrypt(v.payload!!, conversationKey2)
            assertEquals(v.plaintext, decrypted)
        }
    }

    @Test
    fun encryptDecryptLongTest() {
        for (v in vectors.v2?.valid?.encryptDecryptLongMsg!!) {
            val conversationKey = v.conversationKey!!.hexToByteArray()
            val plaintext = v.pattern!!.repeat(v.repeat!!)

            assertEquals(v.plaintextSha256, sha256Hex(plaintext.toByteArray(Charsets.UTF_8)))

            val ciphertext =
                nip44v2
                    .encryptWithNonce(
                        plaintext,
                        conversationKey,
                        v.nonce!!.hexToByteArray(),
                    ).encodePayload()

            assertEquals(v.payloadSha256, sha256Hex(ciphertext.toByteArray(Charsets.UTF_8)))

            val decrypted = nip44v2.decrypt(ciphertext, conversationKey)

            assertEquals(plaintext, decrypted)
        }
    }

    @Test
    fun extendedMessageLengths() {
        for (v in vectors.v2?.invalid?.encryptMsgLengths!!) {
            val key = RandomInstance.bytes(32)
            try {
                val input = "a".repeat(v)
                val result = nip44v2.encrypt(input, key)
                val decrypted = nip44v2.decrypt(result, key)
                assertEquals(input, decrypted)
            } catch (e: Exception) {
                assertNotNull(e)
            }
        }
    }

    @Test
    fun invalidDecrypt() {
        for (v in vectors.v2?.invalid?.decrypt!!) {
            try {
                val result = nip44v2.decrypt(v.payload!!, v.conversationKey!!.hexToByteArray())
                assertNull(result)
                // fail("Should Throw for ${v.note}")
            } catch (e: Exception) {
                assertNotNull(e)
            }
        }
    }

    @Test
    fun invalidConversationKey() {
        for (v in vectors.v2?.invalid?.getConversationKey!!) {
            try {
                nip44v2.getConversationKey(v.sec1!!.hexToByteArray(), v.pub2!!.hexToByteArray())
                fail("Should Throw for ${v.note}")
            } catch (e: Exception) {
                assertNotNull(e)
            }
        }
    }

    private fun sha256Hex(data: ByteArray) = sha256(data).toHexKey()
}
