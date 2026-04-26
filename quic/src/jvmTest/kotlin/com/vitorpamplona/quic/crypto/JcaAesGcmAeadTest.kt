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
package com.vitorpamplona.quic.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Coverage for [JcaAesGcmAead] — the JVM/Android-platform AES-128-GCM
 * implementation that replaced per-packet `Cipher.getInstance` calls in the
 * audit-1 / audit-3 perf pass. Audit-4 #8/#9 flagged that the IV-reuse
 * fallback path was untested even though it's the per-packet send path on
 * JVM and the rebuild edge case fires once per Initial datagram during
 * handshake.
 *
 * Tests:
 *   1. Round-trip seal → open returns the original plaintext.
 *   2. Different nonces produce different ciphertexts (no nonce reuse).
 *   3. The IV-reuse fallback works: re-sealing with the same nonce twice
 *      yields a valid ciphertext that opens correctly. JCA's stateful
 *      AES-GCM cipher rejects literal IV reuse via the cached cipher; the
 *      fallback path uses a fresh `Cipher.getInstance` to honour the QUIC
 *      Initial-padding rebuild behaviour.
 *   4. Open with a corrupted ciphertext returns null (no exception).
 *   5. Open with a wrong AAD returns null.
 */
class JcaAesGcmAeadTest {
    private val key = ByteArray(16) { it.toByte() }
    private val nonce0 = ByteArray(12) { it.toByte() }
    private val nonce1 = ByteArray(12) { (it + 1).toByte() }
    private val aad = byteArrayOf(0xAA.toByte(), 0xBB.toByte())

    @Test
    fun seal_then_open_returns_original_plaintext() {
        val aead = JcaAesGcmAead(key)
        val plaintext = "hello aead".encodeToByteArray()
        val ciphertext = aead.seal(key, nonce0, aad, plaintext)
        assertNotEquals(
            plaintext.size,
            ciphertext.size,
            "ciphertext must include the 16-byte AEAD tag",
        )
        val recovered = aead.open(key, nonce0, aad, ciphertext)
        assertContentEquals(plaintext, recovered)
    }

    @Test
    fun two_different_nonces_produce_different_ciphertexts() {
        val aead = JcaAesGcmAead(key)
        val plaintext = ByteArray(32) { 0x42 }
        val c0 = aead.seal(key, nonce0, aad, plaintext)
        val c1 = aead.seal(key, nonce1, aad, plaintext)
        // Same plaintext + same key + same AAD but different IVs → different ciphertexts.
        var differs = false
        for (i in 0 until minOf(c0.size, c1.size)) {
            if (c0[i] != c1[i]) {
                differs = true
                break
            }
        }
        kotlin.test.assertTrue(differs, "ciphertexts under different nonces must differ")
    }

    @Test
    fun rebuild_with_same_nonce_uses_fallback_cipher_and_still_opens() {
        // Audit-4 #8: the rebuild edge case re-encrypts a packet with the
        // same packet number (the QUIC Initial-padding path). JcaAesGcmAead
        // detects nonce reuse against the most recent encrypt nonce and
        // falls back to a fresh `Cipher.getInstance`. Both ciphertexts must
        // be openable.
        val aead = JcaAesGcmAead(key)
        val plaintext = "rebuild me".encodeToByteArray()
        val first = aead.seal(key, nonce0, aad, plaintext)
        // Same nonce → triggers fallback path.
        val second = aead.seal(key, nonce0, aad, plaintext)
        // Both must decrypt back to the same plaintext.
        assertContentEquals(plaintext, aead.open(key, nonce0, aad, first))
        assertContentEquals(plaintext, aead.open(key, nonce0, aad, second))
        // GCM is deterministic given the same key/nonce/aad/plaintext, so
        // both ciphertexts will in fact be byte-identical — but the test
        // doesn't depend on that, only on both opening successfully.
    }

    @Test
    fun open_returns_null_on_corrupted_ciphertext() {
        val aead = JcaAesGcmAead(key)
        val ciphertext = aead.seal(key, nonce0, aad, byteArrayOf(0x10, 0x20, 0x30))
        // Flip a tag byte.
        ciphertext[ciphertext.size - 1] = (ciphertext[ciphertext.size - 1].toInt() xor 0x01).toByte()
        assertNull(aead.open(key, nonce0, aad, ciphertext))
    }

    @Test
    fun open_returns_null_on_wrong_aad() {
        val aead = JcaAesGcmAead(key)
        val ciphertext = aead.seal(key, nonce0, aad, byteArrayOf(0x10, 0x20, 0x30))
        val wrongAad = byteArrayOf(0xCC.toByte())
        assertNull(aead.open(key, nonce0, wrongAad, ciphertext))
    }

    @Test
    fun key_length_constants_match_aes_128_gcm() {
        val aead = JcaAesGcmAead(key)
        assertEquals(16, aead.keyLength)
        assertEquals(12, aead.nonceLength)
        assertEquals(16, aead.tagLength)
    }
}
