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
 * Pure Kotlin implementation of XChaCha20-Poly1305 AEAD (RFC 8439 §2.8 + XChaCha20 draft).
 *
 * All functions are stateless and thread-safe.
 *
 * Construction:
 * 1. Derive subkey via HChaCha20(key, nonce[0..15])
 * 2. Build 12-byte subnonce: 0x00000000 || nonce[16..23]
 * 3. Generate Poly1305 OTK from ChaCha20 block 0
 * 4. Encrypt with ChaCha20 starting at counter 1
 * 5. Compute Poly1305 tag over: pad16(ad) || pad16(ciphertext) || len(ad) || len(ct)
 */
object XChaCha20Poly1305 {
    private const val TAG_SIZE = 16

    /**
     * AEAD encrypt.
     *
     * @param plaintext message to encrypt
     * @param ad associated data (authenticated but not encrypted)
     * @param nonce 24-byte nonce
     * @param key 32-byte key
     * @return ciphertext || 16-byte tag
     */
    fun encrypt(
        plaintext: ByteArray,
        ad: ByteArray,
        nonce: ByteArray,
        key: ByteArray,
    ): ByteArray {
        // Step 1-2: Derive subkey and subnonce (no copyOfRange for nonce)
        val subKey = ChaCha20Core.hChaCha20FromNonce24(key, nonce)
        val subNonce = ByteArray(12)
        nonce.copyInto(subNonce, destinationOffset = 4, startIndex = 16, endIndex = 24)

        // Step 3: Generate Poly1305 one-time key (only 32 bytes, not full 64-byte block)
        val polyKey = ChaCha20Core.chaCha20PolyKey(subKey, subNonce)

        // Step 4: Encrypt plaintext with counter starting at 1
        val ciphertext = ChaCha20Core.chaCha20Xor(plaintext, subKey, subNonce, counter = 1)

        // Step 5: Compute tag
        val tag = computeTag(polyKey, ad, ciphertext)

        // Step 6: Return ciphertext || tag (single allocation)
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
     * @param nonce 24-byte nonce
     * @param key 32-byte key
     * @return plaintext, or throws on authentication failure
     */
    fun decrypt(
        ciphertextWithTag: ByteArray,
        ad: ByteArray,
        nonce: ByteArray,
        key: ByteArray,
    ): ByteArray {
        require(ciphertextWithTag.size >= TAG_SIZE) { "Ciphertext too short" }

        val ctLen = ciphertextWithTag.size - TAG_SIZE
        val ciphertext = ciphertextWithTag.copyOfRange(0, ctLen)
        val receivedTag = ciphertextWithTag.copyOfRange(ctLen, ciphertextWithTag.size)

        // Step 1-2: Derive subkey and subnonce (no copyOfRange for nonce)
        val subKey = ChaCha20Core.hChaCha20FromNonce24(key, nonce)
        val subNonce = ByteArray(12)
        nonce.copyInto(subNonce, destinationOffset = 4, startIndex = 16, endIndex = 24)

        // Step 3: Generate Poly1305 one-time key (only 32 bytes, not full 64-byte block)
        val polyKey = ChaCha20Core.chaCha20PolyKey(subKey, subNonce)

        // Step 4: Verify tag
        val expectedTag = computeTag(polyKey, ad, ciphertext)
        check(constantTimeEquals(receivedTag, expectedTag)) { "Authentication failed" }

        // Step 5: Decrypt
        return ChaCha20Core.chaCha20Xor(ciphertext, subKey, subNonce, counter = 1)
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

        // pad16(ad)
        ad.copyInto(macData, offset)
        offset += ad.size + adPadLen

        // pad16(ciphertext)
        ciphertext.copyInto(macData, offset)
        offset += ciphertext.size + ctPadLen

        // len(ad) as 8-byte little-endian
        longToLittleEndian(ad.size.toLong(), macData, offset)
        offset += 8

        // len(ciphertext) as 8-byte little-endian
        longToLittleEndian(ciphertext.size.toLong(), macData, offset)

        return Poly1305.mac(macData, polyKey)
    }

    /** Constant-time comparison to prevent timing attacks. */
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
