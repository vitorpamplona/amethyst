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
package com.vitorpamplona.quartz.marmot.mip01Groups

import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20Poly1305
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.ciphers.NostrCipher

/**
 * [NostrCipher] for a Marmot group avatar, implementing the MIP-01 v2 scheme (see
 * [MarmotGroupImageEncryption]) — byte-for-byte interoperable with mdk/whitenoise.
 *
 * The same instance serves two paths:
 * - **Upload** — the file-upload pipeline calls [encrypt] over the (compressed)
 *   image bytes; the resulting blob is stored on Blossom and addressed by
 *   `SHA-256(ciphertext)`. The [imageKey] seed / [imageNonce] are generated up front
 *   so the caller can persist them into the group's [MarmotGroupData].
 * - **Display** — registered in the encrypted-blob HTTP cache keyed by the blob
 *   URL, so a fetched avatar is transparently decrypted via [decryptOrNull]
 *   (v2 first, then the v1 raw-key fallback).
 */
class MarmotGroupImageCipher(
    /** 32-byte HKDF seed stored as `image_key` (the AEAD key is derived from it). */
    val imageKey: ByteArray,
    /** 12-byte nonce. */
    val imageNonce: ByteArray,
) : NostrCipher {
    override fun name(): String = "mip01-image-encryption-v2"

    override fun encrypt(bytesToEncrypt: ByteArray): ByteArray {
        val aeadKey = Mip01ImageCrypto.deriveImageEncryptionKey(imageKey)
        return ChaCha20Poly1305.encrypt(bytesToEncrypt, EMPTY_AAD, imageNonce, aeadKey)
    }

    override fun decrypt(bytesToDecrypt: ByteArray): ByteArray = decryptOrNull(bytesToDecrypt) ?: throw IllegalStateException("Failed to decrypt Marmot group image")

    override fun decryptOrNull(bytesToDecrypt: ByteArray): ByteArray? = MarmotGroupImageEncryption.decryptAny(bytesToDecrypt, imageKey, imageNonce)

    companion object {
        private val EMPTY_AAD = ByteArray(0)

        /**
         * Build a cipher with a freshly-generated seed + nonce, ready to encrypt a new
         * avatar. The generated [imageKey]/[imageNonce] are exposed on the returned
         * instance so the caller can persist them into [MarmotGroupData].
         */
        fun forNewImage(): MarmotGroupImageCipher =
            MarmotGroupImageCipher(
                imageKey = RandomInstance.bytes(MarmotGroupImageEncryption.KEY_LENGTH),
                imageNonce = RandomInstance.bytes(MarmotGroupImageEncryption.NONCE_LENGTH),
            )
    }
}
