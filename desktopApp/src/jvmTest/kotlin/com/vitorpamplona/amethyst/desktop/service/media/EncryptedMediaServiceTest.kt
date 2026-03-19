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
package com.vitorpamplona.amethyst.desktop.service.media

import com.vitorpamplona.quartz.utils.ciphers.AESGCM
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the AESGCM encryption used by EncryptedMediaService.
 * These test the crypto primitives without requiring network access.
 */
class EncryptedMediaServiceTest {
    @Test
    fun aesgcmEncryptDecryptRoundTrip() {
        val plaintext = "Hello, encrypted media!".toByteArray()
        val cipher = AESGCM()

        val encrypted = cipher.encrypt(plaintext)
        val decrypted = cipher.decrypt(encrypted)

        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun aesgcmEncryptedDataDiffersFromPlaintext() {
        val plaintext = "Secret data".toByteArray()
        val cipher = AESGCM()

        val encrypted = cipher.encrypt(plaintext)

        assertFalse(plaintext.contentEquals(encrypted))
        assertTrue(encrypted.size > plaintext.size) // Includes auth tag
    }

    @Test
    fun aesgcmDecryptWithExplicitKeyAndNonce() {
        val cipher1 = AESGCM()
        val plaintext = "Roundtrip test data".toByteArray()

        val encrypted = cipher1.encrypt(plaintext)

        // Reconstruct cipher with same key and nonce
        val cipher2 = AESGCM(cipher1.keyBytes, cipher1.nonce)
        val decrypted = cipher2.decrypt(encrypted)

        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun aesgcmKeyAndNonceAreGenerated() {
        val cipher = AESGCM()

        assertNotNull(cipher.keyBytes)
        assertNotNull(cipher.nonce)
        assertTrue(cipher.keyBytes.size == 32) // AES-256
        assertTrue(cipher.nonce.size == 16)
    }

    @Test
    fun aesgcmDifferentCiphersProduceDifferentOutput() {
        val plaintext = "Same message".toByteArray()
        val cipher1 = AESGCM()
        val cipher2 = AESGCM()

        val encrypted1 = cipher1.encrypt(plaintext)
        val encrypted2 = cipher2.encrypt(plaintext)

        // Different keys should produce different ciphertext
        assertFalse(encrypted1.contentEquals(encrypted2))
    }

    @Test
    fun aesgcmHandlesEmptyData() {
        val cipher = AESGCM()
        val plaintext = byteArrayOf()

        val encrypted = cipher.encrypt(plaintext)
        val decrypted = cipher.decrypt(encrypted)

        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun aesgcmHandlesLargeData() {
        val cipher = AESGCM()
        // Simulate a small "file" - 64KB
        val plaintext = ByteArray(65536) { (it % 256).toByte() }

        val encrypted = cipher.encrypt(plaintext)
        val decrypted = cipher.decrypt(encrypted)

        assertContentEquals(plaintext, decrypted)
    }
}
