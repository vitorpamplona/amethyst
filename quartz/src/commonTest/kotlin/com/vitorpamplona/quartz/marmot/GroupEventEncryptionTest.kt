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
package com.vitorpamplona.quartz.marmot

import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEventEncryption
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for Marmot GroupEvent encryption/decryption (MIP-03).
 */
class GroupEventEncryptionTest {
    private fun hex(s: String): ByteArray = s.replace(" ", "").hexToByteArray()

    // Simulate a 32-byte MLS exporter-derived key
    private val testGroupKey = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")

    @Test
    fun testRoundTrip() {
        val mlsMessage = "Hello MLS Group!".encodeToByteArray()

        val encrypted = GroupEventEncryption.encrypt(mlsMessage, testGroupKey)
        val decrypted = GroupEventEncryption.decrypt(encrypted, testGroupKey)

        assertContentEquals(mlsMessage, decrypted)
    }

    @Test
    fun testRoundTrip_EmptyMessage() {
        val mlsMessage = ByteArray(0)

        val encrypted = GroupEventEncryption.encrypt(mlsMessage, testGroupKey)
        val decrypted = GroupEventEncryption.decrypt(encrypted, testGroupKey)

        assertContentEquals(mlsMessage, decrypted)
    }

    @Test
    fun testRoundTrip_LargeMessage() {
        // Simulate a large MLS commit message
        val mlsMessage = ByteArray(4096) { (it % 256).toByte() }

        val encrypted = GroupEventEncryption.encrypt(mlsMessage, testGroupKey)
        val decrypted = GroupEventEncryption.decrypt(encrypted, testGroupKey)

        assertContentEquals(mlsMessage, decrypted)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testEncryptedFormat() {
        val mlsMessage = "test content".encodeToByteArray()

        val encryptedBase64 = GroupEventEncryption.encrypt(mlsMessage, testGroupKey)
        val decoded = Base64.decode(encryptedBase64)

        // Should be: nonce(12) + ciphertext(messageLen) + tag(16)
        val expectedSize = GroupEvent.NONCE_LENGTH + mlsMessage.size + GroupEvent.AUTH_TAG_LENGTH
        assertEquals(expectedSize, decoded.size)
    }

    @Test
    fun testDifferentEncryptionsProduceDifferentCiphertext() {
        val mlsMessage = "same plaintext".encodeToByteArray()

        val encrypted1 = GroupEventEncryption.encrypt(mlsMessage, testGroupKey)
        val encrypted2 = GroupEventEncryption.encrypt(mlsMessage, testGroupKey)

        // Each encryption uses a random nonce, so outputs must differ
        assertTrue(encrypted1 != encrypted2)
    }

    @Test
    fun testWrongKeyFails() {
        val mlsMessage = "secret message".encodeToByteArray()
        val wrongKey = hex("ff0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")

        val encrypted = GroupEventEncryption.encrypt(mlsMessage, testGroupKey)

        assertFailsWith<IllegalStateException> {
            GroupEventEncryption.decrypt(encrypted, wrongKey)
        }
    }

    @Test
    fun testInvalidKeyLength() {
        val mlsMessage = "test".encodeToByteArray()
        val shortKey = ByteArray(16)

        assertFailsWith<IllegalArgumentException> {
            GroupEventEncryption.encrypt(mlsMessage, shortKey)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testTruncatedPayloadFails() {
        val tooShort = Base64.encode(ByteArray(10)) // Less than MIN_CONTENT_LENGTH

        assertFailsWith<IllegalArgumentException> {
            GroupEventEncryption.decrypt(tooShort, testGroupKey)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testTamperedContentFails() {
        val mlsMessage = "tamper test".encodeToByteArray()

        val encryptedBase64 = GroupEventEncryption.encrypt(mlsMessage, testGroupKey)
        val decoded = Base64.decode(encryptedBase64)

        // Tamper with a ciphertext byte (after the 12-byte nonce)
        decoded[GroupEvent.NONCE_LENGTH] = (decoded[GroupEvent.NONCE_LENGTH].toInt() xor 0xFF).toByte()
        val tamperedBase64 = Base64.encode(decoded)

        assertFailsWith<IllegalStateException> {
            GroupEventEncryption.decrypt(tamperedBase64, testGroupKey)
        }
    }

    @Test
    fun testIntegrationWithGroupEventBuild() {
        val mlsMessage = "MLS application message".encodeToByteArray()
        val groupId = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"

        val encryptedContent = GroupEventEncryption.encrypt(mlsMessage, testGroupKey)
        val groupEvent = GroupEvent.build(encryptedContent, groupId)

        assertEquals(GroupEvent.KIND, groupEvent.kind)
        assertEquals(encryptedContent, groupEvent.content)

        // Decrypt from the event content
        val decrypted = GroupEventEncryption.decrypt(groupEvent.content, testGroupKey)
        assertContentEquals(mlsMessage, decrypted)
    }
}
