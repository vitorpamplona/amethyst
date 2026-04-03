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
package com.vitorpamplona.quartz.nip44Encryption.crypto

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests for standard ChaCha20-Poly1305 AEAD (RFC 8439) with 12-byte nonces.
 * Includes the official RFC 8439 §2.8.2 test vector and round-trip tests.
 */
class ChaCha20Poly1305Test {
    private fun hex(s: String): ByteArray = s.replace(" ", "").hexToByteArray()

    // ===== RFC 8439 §2.8.2: AEAD_CHACHA20_POLY1305 Test Vector =====
    @Test
    fun testEncrypt_RFC8439() {
        val key = hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f")
        val nonce = hex("070000004041424344454647")
        val ad = hex("50515253c0c1c2c3c4c5c6c7")
        val plaintext =
            "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it."
                .encodeToByteArray()

        val result = ChaCha20Poly1305.encrypt(plaintext, ad, nonce, key)

        val expectedCiphertext =
            hex(
                "d31a8d34648e60db7b86afbc53ef7ec2" +
                    "a4aded51296e08fea9e2b5a736ee62d6" +
                    "3dbea45e8ca9671282fafb69da92728b" +
                    "1a71de0a9e060b2905d6a5b67ecd3b36" +
                    "92ddbd7f2d778b8c9803aee328091b58" +
                    "fab324e4fad675945585808b4831d7bc" +
                    "3ff4def08e4b7a9de576d26586cec64b" +
                    "6116",
            )
        val expectedTag = hex("1ae10b594f09e26a7e902ecbd0600691")

        val ciphertext = result.copyOfRange(0, result.size - 16)
        val tag = result.copyOfRange(result.size - 16, result.size)

        assertContentEquals(expectedCiphertext, ciphertext)
        assertContentEquals(expectedTag, tag)
    }

    @Test
    fun testDecrypt_RFC8439() {
        val key = hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f")
        val nonce = hex("070000004041424344454647")
        val ad = hex("50515253c0c1c2c3c4c5c6c7")
        val ciphertextWithTag =
            hex(
                "d31a8d34648e60db7b86afbc53ef7ec2" +
                    "a4aded51296e08fea9e2b5a736ee62d6" +
                    "3dbea45e8ca9671282fafb69da92728b" +
                    "1a71de0a9e060b2905d6a5b67ecd3b36" +
                    "92ddbd7f2d778b8c9803aee328091b58" +
                    "fab324e4fad675945585808b4831d7bc" +
                    "3ff4def08e4b7a9de576d26586cec64b" +
                    "6116" +
                    "1ae10b594f09e26a7e902ecbd0600691",
            )

        val plaintext = ChaCha20Poly1305.decrypt(ciphertextWithTag, ad, nonce, key)

        val expected =
            "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it."
        assertEquals(expected, plaintext.decodeToString())
    }

    @Test
    fun testRoundTrip() {
        val key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = hex("000000000000004a00000000")
        val ad = hex("feedfacedeadbeef")
        val plaintext = "Hello, Nostr! Round-trip test with 12-byte nonce.".encodeToByteArray()

        val encrypted = ChaCha20Poly1305.encrypt(plaintext, ad, nonce, key)
        val decrypted = ChaCha20Poly1305.decrypt(encrypted, ad, nonce, key)

        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun testRoundTrip_EmptyAD() {
        val key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = hex("000102030405060708090a0b")
        val ad = ByteArray(0)
        val plaintext = "Empty AAD test - used by Marmot GroupEvents".encodeToByteArray()

        val encrypted = ChaCha20Poly1305.encrypt(plaintext, ad, nonce, key)
        val decrypted = ChaCha20Poly1305.decrypt(encrypted, ad, nonce, key)

        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun testRoundTrip_EmptyPlaintext() {
        val key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = hex("000102030405060708090a0b")
        val ad = ByteArray(0)
        val plaintext = ByteArray(0)

        val encrypted = ChaCha20Poly1305.encrypt(plaintext, ad, nonce, key)
        assertEquals(16, encrypted.size) // Just the 16-byte tag
        val decrypted = ChaCha20Poly1305.decrypt(encrypted, ad, nonce, key)
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun testTamperedCiphertext() {
        val key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = hex("000102030405060708090a0b")
        val ad = ByteArray(0)
        val plaintext = "Tamper detection test".encodeToByteArray()

        val encrypted = ChaCha20Poly1305.encrypt(plaintext, ad, nonce, key)
        encrypted[0] = (encrypted[0].toInt() xor 1).toByte()

        assertFailsWith<IllegalStateException> {
            ChaCha20Poly1305.decrypt(encrypted, ad, nonce, key)
        }
    }

    @Test
    fun testTamperedTag() {
        val key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = hex("000102030405060708090a0b")
        val ad = ByteArray(0)
        val plaintext = "Tag tamper test".encodeToByteArray()

        val encrypted = ChaCha20Poly1305.encrypt(plaintext, ad, nonce, key)
        // Flip a bit in the last byte (part of the tag)
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 1).toByte()

        assertFailsWith<IllegalStateException> {
            ChaCha20Poly1305.decrypt(encrypted, ad, nonce, key)
        }
    }

    @Test
    fun testWrongKey() {
        val key1 = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val key2 = hex("ff0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = hex("000102030405060708090a0b")
        val ad = ByteArray(0)
        val plaintext = "Wrong key test".encodeToByteArray()

        val encrypted = ChaCha20Poly1305.encrypt(plaintext, ad, nonce, key1)

        assertFailsWith<IllegalStateException> {
            ChaCha20Poly1305.decrypt(encrypted, ad, nonce, key2)
        }
    }

    @Test
    fun testInvalidNonceLength() {
        val key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val badNonce = hex("0102030405060708") // 8 bytes instead of 12
        val plaintext = "test".encodeToByteArray()

        assertFailsWith<IllegalArgumentException> {
            ChaCha20Poly1305.encrypt(plaintext, ByteArray(0), badNonce, key)
        }
    }

    @Test
    fun testInvalidKeyLength() {
        val badKey = hex("0102030405060708090a0b0c0d0e0f10") // 16 bytes instead of 32
        val nonce = hex("000102030405060708090a0b")
        val plaintext = "test".encodeToByteArray()

        assertFailsWith<IllegalArgumentException> {
            ChaCha20Poly1305.encrypt(plaintext, ByteArray(0), nonce, badKey)
        }
    }

    @Test
    fun testLargeMessage() {
        val key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = hex("000102030405060708090a0b")
        val ad = ByteArray(0)
        // Test with a message larger than a single ChaCha20 block (64 bytes)
        val plaintext = ByteArray(1000) { (it % 256).toByte() }

        val encrypted = ChaCha20Poly1305.encrypt(plaintext, ad, nonce, key)
        assertEquals(1000 + 16, encrypted.size)
        val decrypted = ChaCha20Poly1305.decrypt(encrypted, ad, nonce, key)

        assertContentEquals(plaintext, decrypted)
    }
}
