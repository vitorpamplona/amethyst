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
package com.vitorpamplona.quartz.nip44Encryption

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01
import com.vitorpamplona.quartz.nip01Core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.toHexKey
import com.vitorpamplona.quartz.utils.sha256Hash
import fr.acinq.secp256k1.Secp256k1
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class NIP44v2Test {
    private val vectors: VectorFile =
        jacksonObjectMapper()
            .readValue(
                getInstrumentation().context.assets.open("nip44.vectors.json"),
                VectorFile::class.java,
            )

    private val random = SecureRandom()
    private val nip44v2 = Nip44v2(Secp256k1.get(), random)
    private val nip01 = Nip01(Secp256k1.get(), random)

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

        val publicKeyA = nip01.pubkeyCreate(privateKeyA)
        val publicKeyB = nip01.pubkeyCreate(privateKeyB)

        assertEquals(
            nip44v2.getConversationKey(privateKeyA, publicKeyB).toHexKey(),
            nip44v2.getConversationKey(privateKeyB, publicKeyA).toHexKey(),
        )
    }

    @Test
    fun testCompressedWith03Keys() {
        val privateKeyA = "f410f88bcec6cbfda04d6a273c7b1dd8bba144cd45b71e87109cfa11dd7ed561".hexToByteArray()
        val privateKeyB = "e6159851715b4aa6190c22b899b0c792847de0a4435ac5b678f35738351c43b0".hexToByteArray()

        val publicKeyA = nip01.pubkeyCreate(privateKeyA)
        val publicKeyB = nip01.pubkeyCreate(privateKeyB)

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
    fun invalidMessageLengths() {
        for (v in vectors.v2?.invalid?.encryptMsgLengths!!) {
            val key = ByteArray(32)
            random.nextBytes(key)
            try {
                nip44v2.encrypt("a".repeat(v), key)
                fail("Should Throw for $v")
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

    private fun sha256Hex(data: ByteArray) = sha256Hash(data).toHexKey()
}
