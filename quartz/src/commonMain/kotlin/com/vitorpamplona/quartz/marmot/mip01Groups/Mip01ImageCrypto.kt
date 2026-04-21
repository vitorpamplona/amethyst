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

import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider

/**
 * MIP-01 image & Blossom upload key derivations.
 *
 * Per MIP-01 v2, the `image_key` and `image_upload_key` seeds stored in the
 * Marmot Group Data Extension are HKDF IKMs used to derive:
 *
 * - The ChaCha20-Poly1305 key that decrypts the encrypted image blob in Blossom.
 * - The 32-byte seed used to deterministically derive the Blossom upload keypair
 *   (so admins can delete old images when updating group metadata).
 *
 * Both derivations use HKDF-SHA256 as: `HKDF-Extract(salt=∅, IKM=seed)` followed
 * by `HKDF-Expand(prk, info, 32)` where `info` is the MIP-01 versioned label.
 */
object Mip01ImageCrypto {
    /** HKDF-Expand info label for the group image encryption key (MIP-01 v2). */
    const val IMAGE_ENCRYPTION_LABEL = "mip01-image-encryption-v2"

    /** HKDF-Expand info label for the Blossom upload keypair seed (MIP-01 v2). */
    const val BLOSSOM_UPLOAD_LABEL = "mip01-blossom-upload-v2"

    /** Required length of every derived output (bytes). */
    const val OUTPUT_LENGTH = 32

    private val EMPTY_SALT = ByteArray(0)

    /**
     * Derive the ChaCha20-Poly1305 key used to decrypt/encrypt the group image blob.
     *
     * @param imageKey 32-byte HKDF seed from `MarmotGroupData.imageKey`.
     * @return 32-byte symmetric key.
     */
    fun deriveImageEncryptionKey(imageKey: ByteArray): ByteArray {
        require(imageKey.size == OUTPUT_LENGTH) {
            "image_key must be $OUTPUT_LENGTH bytes, got ${imageKey.size}"
        }
        val prk = MlsCryptoProvider.hkdfExtract(EMPTY_SALT, imageKey)
        return MlsCryptoProvider.hkdfExpand(prk, IMAGE_ENCRYPTION_LABEL.encodeToByteArray(), OUTPUT_LENGTH)
    }

    /**
     * Derive the deterministic seed for the Blossom upload keypair.
     *
     * The returned 32-byte value is the input to whatever keypair generation
     * scheme the caller uses for Blossom (typically a Nostr-style secp256k1
     * private key). Because the seed is deterministic, any admin that holds
     * `image_upload_key` can recreate the keypair later to delete old blobs.
     *
     * @param imageUploadKey 32-byte HKDF seed from `MarmotGroupData.imageUploadKey`.
     * @return 32-byte private-key seed.
     */
    fun deriveBlossomUploadSeed(imageUploadKey: ByteArray): ByteArray {
        require(imageUploadKey.size == OUTPUT_LENGTH) {
            "image_upload_key must be $OUTPUT_LENGTH bytes, got ${imageUploadKey.size}"
        }
        val prk = MlsCryptoProvider.hkdfExtract(EMPTY_SALT, imageUploadKey)
        return MlsCryptoProvider.hkdfExpand(prk, BLOSSOM_UPLOAD_LABEL.encodeToByteArray(), OUTPUT_LENGTH)
    }
}
