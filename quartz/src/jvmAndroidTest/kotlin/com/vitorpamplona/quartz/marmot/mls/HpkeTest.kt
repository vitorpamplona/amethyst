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
package com.vitorpamplona.quartz.marmot.mls

import com.vitorpamplona.quartz.marmot.mls.crypto.Hpke
import com.vitorpamplona.quartz.marmot.mls.crypto.X25519
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for HPKE (RFC 9180) implementation used by MLS TreeKEM.
 *
 * Tests the seal/open (encrypt/decrypt) cycle which is critical
 * for path secret encryption in UpdatePath processing.
 */
class HpkeTest {
    @Test
    fun testSealOpenRoundTrip() {
        val recipient = X25519.generateKeyPair()
        val plaintext = "Secret MLS path data".encodeToByteArray()
        val info = "MLS 1.0 UpdatePathNode".encodeToByteArray()
        val aad = ByteArray(0)

        val ciphertext = Hpke.seal(recipient.publicKey, info, aad, plaintext)

        assertEquals(32, ciphertext.kemOutput.size, "KEM output should be 32 bytes (X25519 public key)")
        assertTrue(ciphertext.ciphertext.size > plaintext.size, "Ciphertext should include tag")

        val decrypted = Hpke.open(recipient.privateKey, ciphertext.kemOutput, info, aad, ciphertext.ciphertext)
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun testSealOpenEmptyPlaintext() {
        val recipient = X25519.generateKeyPair()
        val plaintext = ByteArray(0)
        val info = ByteArray(0)
        val aad = ByteArray(0)

        val ciphertext = Hpke.seal(recipient.publicKey, info, aad, plaintext)
        val decrypted = Hpke.open(recipient.privateKey, ciphertext.kemOutput, info, aad, ciphertext.ciphertext)

        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun testSealOpenLargePlaintext() {
        val recipient = X25519.generateKeyPair()
        val plaintext = ByteArray(10000) { it.toByte() }
        val info = "large test".encodeToByteArray()
        val aad = ByteArray(0)

        val ciphertext = Hpke.seal(recipient.publicKey, info, aad, plaintext)
        val decrypted = Hpke.open(recipient.privateKey, ciphertext.kemOutput, info, aad, ciphertext.ciphertext)

        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun testSealProducesDifferentCiphertexts() {
        val recipient = X25519.generateKeyPair()
        val plaintext = "Same plaintext".encodeToByteArray()
        val info = ByteArray(0)
        val aad = ByteArray(0)

        val ct1 = Hpke.seal(recipient.publicKey, info, aad, plaintext)
        val ct2 = Hpke.seal(recipient.publicKey, info, aad, plaintext)

        // Each seal generates a fresh ephemeral key, so KEM outputs differ
        assertFalse(
            ct1.kemOutput.contentEquals(ct2.kemOutput),
            "Each seal should use a different ephemeral key",
        )
    }

    @Test
    fun testSealOpenWithInfo() {
        val recipient = X25519.generateKeyPair()
        val plaintext = "test".encodeToByteArray()
        val info = "MLS 1.0 UpdatePathNode context data".encodeToByteArray()
        val aad = ByteArray(0)

        val ciphertext = Hpke.seal(recipient.publicKey, info, aad, plaintext)
        val decrypted = Hpke.open(recipient.privateKey, ciphertext.kemOutput, info, aad, ciphertext.ciphertext)

        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun testSealOpenWithMlsLabels() {
        // Simulate MLS EncryptWithLabel pattern
        val recipient = X25519.generateKeyPair()
        val pathSecret = ByteArray(32) { it.toByte() }

        val label = "MLS 1.0 UpdatePathNode"
        val fullInfo = label.encodeToByteArray()
        val aad = ByteArray(0)

        val ciphertext = Hpke.seal(recipient.publicKey, fullInfo, aad, pathSecret)
        val decrypted = Hpke.open(recipient.privateKey, ciphertext.kemOutput, fullInfo, aad, ciphertext.ciphertext)

        assertContentEquals(pathSecret, decrypted)
    }

    private fun assertContentEquals(
        expected: ByteArray,
        actual: ByteArray,
    ) {
        kotlin.test.assertContentEquals(expected, actual)
    }

    private fun assertFalse(
        condition: Boolean,
        message: String = "",
    ) {
        kotlin.test.assertFalse(condition, message)
    }
}
