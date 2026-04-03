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
package com.vitorpamplona.quartz.marmot.mip03GroupMessages

import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20Poly1305
import com.vitorpamplona.quartz.utils.RandomInstance
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Handles the outer ChaCha20-Poly1305 encryption layer for Marmot GroupEvents (MIP-03).
 *
 * The encryption flow:
 *   Encrypt: content = base64(randomNonce(12) || ChaCha20-Poly1305.encrypt(key, nonce, mlsMessageBytes, aad=""))
 *   Decrypt: decode base64, split nonce (first 12 bytes) from ciphertext+tag, decrypt with empty AAD
 *
 * The key is derived from MLS-Exporter("marmot", "group-event", 32) by the MLS engine.
 * Since the MLS engine is not yet integrated, this helper accepts the 32-byte key as a parameter.
 */
object GroupEventEncryption {
    private val EMPTY_AAD = ByteArray(0)

    /**
     * Encrypts an MLS message for a GroupEvent.
     *
     * @param mlsMessageBytes the raw MLS message bytes to encrypt
     * @param groupKey 32-byte key derived from MLS-Exporter("marmot", "group-event", 32)
     * @return base64-encoded string containing nonce(12) || ciphertext || tag(16)
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun encrypt(
        mlsMessageBytes: ByteArray,
        groupKey: ByteArray,
    ): String {
        require(groupKey.size == GroupEvent.EXPORTER_KEY_LENGTH) {
            "Group key must be ${GroupEvent.EXPORTER_KEY_LENGTH} bytes"
        }

        val nonce = RandomInstance.bytes(GroupEvent.NONCE_LENGTH)
        val ciphertextWithTag = ChaCha20Poly1305.encrypt(mlsMessageBytes, EMPTY_AAD, nonce, groupKey)

        // Prepend nonce to ciphertext+tag
        val payload = ByteArray(nonce.size + ciphertextWithTag.size)
        nonce.copyInto(payload)
        ciphertextWithTag.copyInto(payload, nonce.size)

        return Base64.encode(payload)
    }

    /**
     * Decrypts a GroupEvent's encrypted content.
     *
     * @param encryptedContentBase64 base64-encoded content from the GroupEvent
     * @param groupKey 32-byte key derived from MLS-Exporter("marmot", "group-event", 32)
     * @return decrypted MLS message bytes
     * @throws IllegalStateException if authentication fails
     * @throws IllegalArgumentException if content is malformed
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun decrypt(
        encryptedContentBase64: String,
        groupKey: ByteArray,
    ): ByteArray {
        require(groupKey.size == GroupEvent.EXPORTER_KEY_LENGTH) {
            "Group key must be ${GroupEvent.EXPORTER_KEY_LENGTH} bytes"
        }

        val payload = Base64.decode(encryptedContentBase64)
        require(payload.size >= GroupEvent.MIN_CONTENT_LENGTH) {
            "Payload too short: ${payload.size} bytes, minimum ${GroupEvent.MIN_CONTENT_LENGTH}"
        }

        val nonce = payload.copyOfRange(0, GroupEvent.NONCE_LENGTH)
        val ciphertextWithTag = payload.copyOfRange(GroupEvent.NONCE_LENGTH, payload.size)

        return ChaCha20Poly1305.decrypt(ciphertextWithTag, EMPTY_AAD, nonce, groupKey)
    }
}
