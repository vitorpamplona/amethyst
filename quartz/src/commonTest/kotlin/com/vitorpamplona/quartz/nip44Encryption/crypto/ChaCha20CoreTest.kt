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
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Test vectors from RFC 8439, XChaCha20 draft (draft-irtf-cfrg-xchacha-03),
 * and libsodium test suite.
 */
class ChaCha20CoreTest {
    private fun hex(s: String): ByteArray = s.replace(" ", "").hexToByteArray()

    private fun ByteArray.toHex(): String = toHexKey()

    // ===== RFC 8439 §2.3.2: ChaCha20 Block Function Test Vector =====
    @Test
    fun testChaCha20Block_RFC8439() {
        val key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = hex("000000090000004a00000000")
        val counter = 1

        val block = ChaCha20Core.chaCha20Block(key, counter, nonce)

        val expected =
            hex(
                "10f1e7e4d13b5915500fdd1fa32071c4" +
                    "c7d1f4c733c068030422aa9ac3d46c4e" +
                    "d2826446079faa0914c2d705d98b02a2" +
                    "b5129cd1de164eb9cbd083e8a2503c4e",
            )
        assertContentEquals(expected, block)
    }

    // ===== RFC 8439 §2.4.2: ChaCha20 Encryption Test Vector =====
    @Test
    fun testChaCha20Encrypt_RFC8439() {
        val key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = hex("000000000000004a00000000")
        val counter = 1
        val plaintext =
            "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it."
                .encodeToByteArray()

        val ciphertext = ChaCha20Core.chaCha20Xor(plaintext, key, nonce, counter)

        val expected =
            hex(
                "6e2e359a2568f98041ba0728dd0d6981" +
                    "e97e7aec1d4360c20a27afccfd9fae0b" +
                    "f91b65c5524733ab8f593dabcd62b357" +
                    "1639d624e65152ab8f530c359f0861d8" +
                    "07ca0dbf500d6a6156a38e088a22b65e" +
                    "52bc514d16ccf806818ce91ab7793736" +
                    "5af90bbf74a35be6b40b8eedf2785e42" +
                    "874d",
            )
        assertContentEquals(expected, ciphertext)
    }

    // ===== RFC 8439 §2.5.2: Poly1305 Test Vector =====
    @Test
    fun testPoly1305_RFC8439() {
        val key = hex("85d6be7857556d337f4452fe42d506a80103808afb0db2fd4abff6af4149f51b")
        val message =
            "Cryptographic Forum Research Group".encodeToByteArray()

        val tag = Poly1305.mac(message, key)

        val expected = hex("a8061dc1305136c6c22b8baf0c0127a9")
        assertContentEquals(expected, tag)
    }

    // ===== RFC 8439 §2.8.2: AEAD ChaCha20-Poly1305 Test Vector =====
    // Adapted for XChaCha20 via the XChaCha20 draft test vector
    @Test
    fun testPoly1305KeyGeneration_RFC8439() {
        // RFC 8439 §2.6.2 Poly1305 Key Generation
        val key = hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f")
        val nonce = hex("000000000001020304050607")

        val block = ChaCha20Core.chaCha20Block(key, 0, nonce)
        val polyKey = block.copyOfRange(0, 32)

        val expected = hex("8ad5a08b905f81cc815040274ab29471a833b637e3fd0da508dbb8e2fdd1a646")
        assertContentEquals(expected, polyKey)
    }

    // ===== XChaCha20 draft §2.2.1: HChaCha20 Test Vector =====
    @Test
    fun testHChaCha20_Draft() {
        val key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val input = hex("000000090000004a0000000031415927")

        val subKey = ChaCha20Core.hChaCha20(key, input)

        val expected = hex("82413b4227b27bfed30e42508a877d73a0f9e4d58a74a853c12ec41326d3ecdc")
        assertContentEquals(expected, subKey)
    }

    // ===== XChaCha20 draft §A.3.1: XChaCha20-Poly1305 AEAD Test Vector =====
    @Test
    fun testXChaCha20Poly1305Encrypt_Draft() {
        val key = hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f")
        val nonce = hex("404142434445464748494a4b4c4d4e4f5051525354555657")
        val ad = hex("50515253c0c1c2c3c4c5c6c7")
        val plaintext =
            "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it."
                .encodeToByteArray()

        val result = XChaCha20Poly1305.encrypt(plaintext, ad, nonce, key)

        // Ciphertext portion
        val expectedCiphertext =
            hex(
                "bd6d179d3e83d43b9576579493c0e939572a1700252bfaccbed2902c21396cbb" +
                    "731c7f1b0b4aa6440bf3a82f4eda7e39ae64c6708c54c216cb96b72e1213b452" +
                    "2f8c9ba40db5d945b11b69b982c1bb9e3f3fac2bc369488f76b2383565d3fff9" +
                    "21f9664c97637da9768812f615c68b13b52e",
            )
        // Tag portion
        val expectedTag = hex("c0875924c1c7987947deafd8780acf49")

        val ciphertext = result.copyOfRange(0, result.size - 16)
        val tag = result.copyOfRange(result.size - 16, result.size)

        assertContentEquals(expectedCiphertext, ciphertext)
        assertContentEquals(expectedTag, tag)
    }

    @Test
    fun testXChaCha20Poly1305Decrypt_Draft() {
        val key = hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f")
        val nonce = hex("404142434445464748494a4b4c4d4e4f5051525354555657")
        val ad = hex("50515253c0c1c2c3c4c5c6c7")
        val ciphertextWithTag =
            hex(
                "bd6d179d3e83d43b9576579493c0e939572a1700252bfaccbed2902c21396cbb" +
                    "731c7f1b0b4aa6440bf3a82f4eda7e39ae64c6708c54c216cb96b72e1213b452" +
                    "2f8c9ba40db5d945b11b69b982c1bb9e3f3fac2bc369488f76b2383565d3fff9" +
                    "21f9664c97637da9768812f615c68b13b52e" +
                    "c0875924c1c7987947deafd8780acf49",
            )

        val plaintext = XChaCha20Poly1305.decrypt(ciphertextWithTag, ad, nonce, key)

        val expected =
            "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it."
        assertEquals(expected, plaintext.decodeToString())
    }

    @Test
    fun testXChaCha20Poly1305_TamperedCiphertext() {
        val key = hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f")
        val nonce = hex("404142434445464748494a4b4c4d4e4f5051525354555657")
        val ad = hex("50515253c0c1c2c3c4c5c6c7")
        val plaintext = "Test message".encodeToByteArray()

        val encrypted = XChaCha20Poly1305.encrypt(plaintext, ad, nonce, key)
        // Flip a bit in the ciphertext
        encrypted[0] = (encrypted[0].toInt() xor 1).toByte()

        assertFailsWith<IllegalStateException> {
            XChaCha20Poly1305.decrypt(encrypted, ad, nonce, key)
        }
    }

    @Test
    fun testXChaCha20Poly1305_RoundTrip() {
        val key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = hex("000102030405060708090a0b0c0d0e0f1011121314151617")
        val ad = hex("feedfacedeadbeef")
        val plaintext = "Hello, Nostr! This is a round-trip test.".encodeToByteArray()

        val encrypted = XChaCha20Poly1305.encrypt(plaintext, ad, nonce, key)
        val decrypted = XChaCha20Poly1305.decrypt(encrypted, ad, nonce, key)

        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun testXChaCha20Poly1305_EmptyPlaintext() {
        val key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = hex("000102030405060708090a0b0c0d0e0f1011121314151617")
        val ad = hex("feedface")
        val plaintext = ByteArray(0)

        val encrypted = XChaCha20Poly1305.encrypt(plaintext, ad, nonce, key)
        assertEquals(16, encrypted.size) // Just the tag
        val decrypted = XChaCha20Poly1305.decrypt(encrypted, ad, nonce, key)
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun testXChaCha20Poly1305_EmptyAD() {
        val key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = hex("000102030405060708090a0b0c0d0e0f1011121314151617")
        val ad = ByteArray(0)
        val plaintext = "No additional data".encodeToByteArray()

        val encrypted = XChaCha20Poly1305.encrypt(plaintext, ad, nonce, key)
        val decrypted = XChaCha20Poly1305.decrypt(encrypted, ad, nonce, key)
        assertContentEquals(plaintext, decrypted)
    }

    // ===== ChaCha20 stream cipher (counter=0) for NIP-44v2 compatibility =====
    @Test
    fun testChaCha20Xor_SymmetricEncryptDecrypt() {
        val key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = hex("000000090000004a00000000")
        val plaintext = "Symmetric cipher test".encodeToByteArray()

        val encrypted = ChaCha20Core.chaCha20Xor(plaintext, key, nonce, counter = 0)
        val decrypted = ChaCha20Core.chaCha20Xor(encrypted, key, nonce, counter = 0)

        assertContentEquals(plaintext, decrypted)
    }

    // ===== XChaCha20 stream cipher symmetry =====
    @Test
    fun testXChaCha20Xor_SymmetricEncryptDecrypt() {
        val key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = hex("000102030405060708090a0b0c0d0e0f1011121314151617")
        val plaintext = "XChaCha20 symmetric test".encodeToByteArray()

        val encrypted = ChaCha20Core.xChaCha20Xor(plaintext, nonce, key)
        val decrypted = ChaCha20Core.xChaCha20Xor(encrypted, nonce, key)

        assertContentEquals(plaintext, decrypted)
    }
}
