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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Coverage for [ChaCha20Poly1305Aead] — used when nests/aioquic negotiate
 * `TLS_CHACHA20_POLY1305_SHA256` instead of AES-128-GCM. Pre-audit-4 only
 * the `open` side was tested via Rfc9001ChaCha20InteropTest; the `seal`
 * path and the `require` failure paths were untested even though `seal`
 * runs on every outbound 1-RTT packet.
 */
class ChaCha20Poly1305AeadTest {
    private val key = ByteArray(32) { it.toByte() }
    private val nonce = ByteArray(12) { (it + 1).toByte() }
    private val aad = byteArrayOf(0xCA.toByte(), 0xFE.toByte())

    @Test
    fun seal_then_open_round_trips() {
        val plaintext = "hello chacha20-poly1305".encodeToByteArray()
        val ciphertext = ChaCha20Poly1305Aead.seal(key, nonce, aad, plaintext)
        // Tag is the last 16 bytes; ciphertext = stream-cipher output + tag.
        assertEquals(plaintext.size + 16, ciphertext.size)
        val recovered = ChaCha20Poly1305Aead.open(key, nonce, aad, ciphertext)
        assertContentEquals(plaintext, recovered)
    }

    @Test
    fun open_rejects_bad_tag() {
        val plaintext = byteArrayOf(0x01, 0x02, 0x03)
        val ciphertext = ChaCha20Poly1305Aead.seal(key, nonce, aad, plaintext)
        ciphertext[ciphertext.size - 1] = (ciphertext[ciphertext.size - 1].toInt() xor 0x01).toByte()
        assertNull(ChaCha20Poly1305Aead.open(key, nonce, aad, ciphertext))
    }

    @Test
    fun open_rejects_wrong_aad() {
        val plaintext = byteArrayOf(0x01, 0x02, 0x03)
        val ciphertext = ChaCha20Poly1305Aead.seal(key, nonce, aad, plaintext)
        val wrongAad = byteArrayOf(0xDE.toByte(), 0xAD.toByte())
        assertNull(ChaCha20Poly1305Aead.open(key, nonce, wrongAad, ciphertext))
    }

    @Test
    fun seal_rejects_wrong_size_key() {
        assertFailsWith<IllegalArgumentException> {
            ChaCha20Poly1305Aead.seal(ByteArray(16), nonce, aad, byteArrayOf(0x01))
        }
    }

    @Test
    fun seal_rejects_wrong_size_nonce() {
        assertFailsWith<IllegalArgumentException> {
            ChaCha20Poly1305Aead.seal(key, ByteArray(8), aad, byteArrayOf(0x01))
        }
    }

    @Test
    fun open_rejects_wrong_size_key() {
        assertFailsWith<IllegalArgumentException> {
            ChaCha20Poly1305Aead.open(ByteArray(16), nonce, aad, byteArrayOf(0x01))
        }
    }

    @Test
    fun key_length_constants_match_chacha20_poly1305() {
        assertEquals(32, ChaCha20Poly1305Aead.keyLength)
        assertEquals(12, ChaCha20Poly1305Aead.nonceLength)
        assertEquals(16, ChaCha20Poly1305Aead.tagLength)
    }
}
