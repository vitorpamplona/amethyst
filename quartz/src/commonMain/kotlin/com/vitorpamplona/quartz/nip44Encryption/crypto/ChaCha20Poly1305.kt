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

/**
 * Standard ChaCha20-Poly1305 AEAD (RFC 8439) with 12-byte nonces.
 *
 * Used by the Marmot protocol (MIP-03) for outer encryption of GroupEvents.
 * Unlike [XChaCha20Poly1305] which uses 24-byte nonces via HChaCha20,
 * this operates directly with the IETF ChaCha20 variant.
 *
 * Construction:
 * 1. Generate Poly1305 OTK from ChaCha20 block 0
 * 2. Encrypt with ChaCha20 starting at counter 1
 * 3. Compute Poly1305 tag over: pad16(ad) || pad16(ciphertext) || len(ad) || len(ct)
 */
object ChaCha20Poly1305 {
    private const val TAG_SIZE = 16

    /**
     * AEAD encrypt.
     *
     * @param plaintext message to encrypt
     * @param ad associated data (authenticated but not encrypted)
     * @param nonce 12-byte nonce
     * @param key 32-byte key
     * @return ciphertext || 16-byte tag
     */
    fun encrypt(
        plaintext: ByteArray,
        ad: ByteArray,
        nonce: ByteArray,
        key: ByteArray,
    ): ByteArray {
        require(nonce.size == 12) { "Nonce must be 12 bytes" }
        require(key.size == 32) { "Key must be 32 bytes" }

        // Step 1: Generate Poly1305 one-time key
        val polyKey = ChaCha20Core.chaCha20PolyKey(key, nonce)

        // Step 2: Encrypt plaintext with counter starting at 1
        val ciphertext = ChaCha20Core.chaCha20Xor(plaintext, key, nonce, counter = 1)

        // Step 3: Compute tag
        val tag = computeTag(polyKey, ad, ciphertext)

        // Step 4: Return ciphertext || tag
        val result = ByteArray(ciphertext.size + TAG_SIZE)
        ciphertext.copyInto(result)
        tag.copyInto(result, ciphertext.size)
        return result
    }

    /**
     * AEAD decrypt.
     *
     * @param ciphertextWithTag ciphertext || 16-byte tag
     * @param ad associated data
     * @param nonce 12-byte nonce
     * @param key 32-byte key
     * @return plaintext, or throws on authentication failure
     */
    fun decrypt(
        ciphertextWithTag: ByteArray,
        ad: ByteArray,
        nonce: ByteArray,
        key: ByteArray,
    ): ByteArray {
        require(nonce.size == 12) { "Nonce must be 12 bytes" }
        require(key.size == 32) { "Key must be 32 bytes" }
        require(ciphertextWithTag.size >= TAG_SIZE) { "Ciphertext too short" }

        val ctLen = ciphertextWithTag.size - TAG_SIZE
        val ciphertext = ciphertextWithTag.copyOfRange(0, ctLen)
        val receivedTag = ciphertextWithTag.copyOfRange(ctLen, ciphertextWithTag.size)

        // Step 1: Generate Poly1305 one-time key
        val polyKey = ChaCha20Core.chaCha20PolyKey(key, nonce)

        // Step 2: Verify tag
        val expectedTag = computeTag(polyKey, ad, ciphertext)
        check(constantTimeEquals(receivedTag, expectedTag)) { "Authentication failed" }

        // Step 3: Decrypt
        return ChaCha20Core.chaCha20Xor(ciphertext, key, nonce, counter = 1)
    }

    /**
     * Compute Poly1305 tag over the AEAD construction:
     * pad16(ad) || pad16(ciphertext) || len(ad) as 8-byte LE || len(ct) as 8-byte LE
     */
    private fun computeTag(
        polyKey: ByteArray,
        ad: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        val adPadLen = if (ad.size % 16 == 0) 0 else 16 - (ad.size % 16)
        val ctPadLen = if (ciphertext.size % 16 == 0) 0 else 16 - (ciphertext.size % 16)

        val macData = ByteArray(ad.size + adPadLen + ciphertext.size + ctPadLen + 16)
        var offset = 0

        ad.copyInto(macData, offset)
        offset += ad.size + adPadLen

        ciphertext.copyInto(macData, offset)
        offset += ciphertext.size + ctPadLen

        longToLittleEndian(ad.size.toLong(), macData, offset)
        offset += 8
        longToLittleEndian(ciphertext.size.toLong(), macData, offset)

        return Poly1305.mac(macData, polyKey)
    }

    private fun constantTimeEquals(
        a: ByteArray,
        b: ByteArray,
    ): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }

    private fun longToLittleEndian(
        value: Long,
        output: ByteArray,
        offset: Int,
    ) {
        output[offset] = (value and 0xFF).toByte()
        output[offset + 1] = (value ushr 8 and 0xFF).toByte()
        output[offset + 2] = (value ushr 16 and 0xFF).toByte()
        output[offset + 3] = (value ushr 24 and 0xFF).toByte()
        output[offset + 4] = (value ushr 32 and 0xFF).toByte()
        output[offset + 5] = (value ushr 40 and 0xFF).toByte()
        output[offset + 6] = (value ushr 48 and 0xFF).toByte()
        output[offset + 7] = (value ushr 56 and 0xFF).toByte()
    }
}
