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

import com.vitorpamplona.quartz.marmot.mip04EncryptedMedia.Mip04MediaEncryption
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20Poly1305
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * Marmot group image (avatar) encryption.
 *
 * Implements the canonical `marmot.group.blossom.image.v1` app component scheme
 * (the successor to the deprecated MIP-01 image scheme). The plaintext avatar is
 * encrypted with ChaCha20-Poly1305 and the ciphertext is uploaded as an opaque
 * blob to a Blossom server, addressed by the SHA-256 of the *ciphertext*.
 *
 * The encryption parameters live inside the group's [MarmotGroupData] extension:
 * - `image_key`         — the raw 32-byte ChaCha20-Poly1305 key (NOT an HKDF seed).
 * - `image_nonce`       — the 12-byte nonce.
 * - `image_hash`        — SHA-256 of the encrypted blob (= the Blossom hash).
 * - `image_upload_key`  — the raw 32-byte secret key of a fresh Nostr keypair used
 *                         to authorize Blossom writes (see [MarmotGroupData.imageUploadKey]).
 * - `media_type`        — the canonical MIME type of the plaintext image.
 *
 * ```
 * aad            = "marmot-group-image-v1" || 0x00 || media_type
 * encrypted_blob = ChaCha20-Poly1305.encrypt(image_key, image_nonce, plaintext, aad)
 * image_hash     = SHA-256(encrypted_blob)
 * ```
 *
 * A fetching client MUST verify that the fetched bytes hash to `image_hash`
 * before decrypting.
 *
 * ### Backward compatibility (parse-both)
 * Amethyst never shipped the deprecated MIP-01 image scheme (no client code ever
 * populated the image fields), but other clients might have. For robustness,
 * [decryptAny] first tries the canonical raw-key scheme and, on authentication
 * failure, falls back to the deprecated scheme where `image_key` is an HKDF seed
 * ([Mip01ImageCrypto.deriveImageEncryptionKey]) and the AEAD carries no AAD.
 */
object MarmotGroupImageEncryption {
    /** ASCII label mixed into the AEAD associated data. */
    const val AAD_LABEL = "marmot-group-image-v1"

    const val KEY_LENGTH = 32
    const val NONCE_LENGTH = 12

    private val NULL_SEPARATOR = byteArrayOf(0x00)
    private val EMPTY_AAD = ByteArray(0)

    /**
     * Build the AEAD associated data: `"marmot-group-image-v1" || 0x00 || media_type`.
     * The media type is canonicalized the same way MIP-04 canonicalizes it
     * (lowercased, trimmed, parameters stripped) so both peers derive identical bytes.
     */
    fun buildAad(mediaType: String): ByteArray {
        val label = AAD_LABEL.encodeToByteArray()
        val mime = Mip04MediaEncryption.canonicalizeMimeType(mediaType).encodeToByteArray()
        val out = ByteArray(label.size + 1 + mime.size)
        label.copyInto(out, 0)
        NULL_SEPARATOR.copyInto(out, label.size)
        mime.copyInto(out, label.size + 1)
        return out
    }

    /**
     * Result of encrypting a group image, ready to be uploaded to Blossom and
     * folded into a [MarmotGroupData].
     */
    class Encrypted(
        /** The encrypted blob to upload to Blossom (ciphertext || 16-byte tag). */
        val ciphertext: ByteArray,
        /** Random 32-byte ChaCha20-Poly1305 key — store as `image_key`. */
        val imageKey: ByteArray,
        /** Random 12-byte nonce — store as `image_nonce`. */
        val imageNonce: ByteArray,
        /** SHA-256 of [ciphertext] (hex) — store as `image_hash`; also the Blossom hash. */
        val imageHash: HexKey,
    )

    /**
     * Encrypt a plaintext image with a freshly-generated key + nonce, per the
     * canonical scheme. Returns the ciphertext to upload plus the parameters to
     * persist in [MarmotGroupData].
     */
    fun encrypt(
        plaintext: ByteArray,
        mediaType: String,
    ): Encrypted {
        val imageKey = RandomInstance.bytes(KEY_LENGTH)
        val imageNonce = RandomInstance.bytes(NONCE_LENGTH)
        val ciphertext = ChaCha20Poly1305.encrypt(plaintext, buildAad(mediaType), imageNonce, imageKey)
        return Encrypted(
            ciphertext = ciphertext,
            imageKey = imageKey,
            imageNonce = imageNonce,
            imageHash = sha256(ciphertext).toHexKey(),
        )
    }

    /**
     * Decrypt a group image blob using the canonical raw-key scheme.
     *
     * @throws IllegalStateException on authentication failure.
     */
    fun decrypt(
        ciphertext: ByteArray,
        imageKey: ByteArray,
        imageNonce: ByteArray,
        mediaType: String,
    ): ByteArray = ChaCha20Poly1305.decrypt(ciphertext, buildAad(mediaType), imageNonce, imageKey)

    /**
     * Decrypt a group image blob, trying the canonical raw-key scheme first and
     * falling back to the deprecated MIP-01 HKDF-seed scheme.
     *
     * Returns null if neither scheme authenticates (wrong key, corrupt blob, or an
     * unknown future scheme).
     *
     * @param mediaType canonical MIME type from [MarmotGroupData.imageMediaType];
     *   may be null for legacy groups that predate the `media_type` field, in which
     *   case only the legacy fallback is attempted.
     */
    fun decryptAny(
        ciphertext: ByteArray,
        imageKey: ByteArray,
        imageNonce: ByteArray,
        mediaType: String?,
    ): ByteArray? {
        if (imageKey.size == KEY_LENGTH && imageNonce.size == NONCE_LENGTH && mediaType != null) {
            try {
                return decrypt(ciphertext, imageKey, imageNonce, mediaType)
            } catch (_: Exception) {
                // fall through to the deprecated scheme
            }
        }
        return decryptLegacyOrNull(ciphertext, imageKey, imageNonce)
    }

    /**
     * Deprecated MIP-01 image scheme: `image_key` is an HKDF seed rather than the
     * raw AEAD key, and the AEAD carries no associated data. Kept only so we can
     * still open avatars produced by pre-canonical clients.
     */
    private fun decryptLegacyOrNull(
        ciphertext: ByteArray,
        imageKeySeed: ByteArray,
        imageNonce: ByteArray,
    ): ByteArray? =
        try {
            if (imageKeySeed.size != Mip01ImageCrypto.OUTPUT_LENGTH || imageNonce.size != NONCE_LENGTH) {
                null
            } else {
                val key = Mip01ImageCrypto.deriveImageEncryptionKey(imageKeySeed)
                ChaCha20Poly1305.decrypt(ciphertext, EMPTY_AAD, imageNonce, key)
            }
        } catch (_: Exception) {
            null
        }

    /**
     * Generate the raw 32-byte secret key of a fresh Nostr keypair to authorize
     * Blossom writes — store as [MarmotGroupData.imageUploadKey]. Any admin that
     * later holds this value can re-sign uploads/deletions for the blob.
     */
    fun generateUploadKey(): ByteArray = RandomInstance.bytes(KEY_LENGTH)
}
