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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20Poly1305
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * Marmot group image (avatar) encryption — MIP-01 v2.
 *
 * This is byte-for-byte interoperable with the reference implementation
 * (`mdk-core`'s `extension/group_image.rs`, used by whitenoise): a group avatar
 * is encrypted with ChaCha20-Poly1305 and the ciphertext is uploaded to Blossom,
 * addressed by the SHA-256 of the *ciphertext*.
 *
 * The parameters live inside the group's [MarmotGroupData] extension:
 * - `image_key`        — a 32-byte HKDF **seed**. The AEAD key is
 *   `HKDF-SHA256(salt=∅, ikm=image_key, info="mip01-image-encryption-v2", 32)`
 *   (see [Mip01ImageCrypto.deriveImageEncryptionKey]).
 * - `image_nonce`      — the 12-byte ChaCha20-Poly1305 nonce (used verbatim).
 * - `image_hash`       — SHA-256 of the encrypted blob (= the Blossom hash).
 * - `image_upload_key` — a 32-byte HKDF **seed**. The Blossom-auth secp256k1
 *   secret is `HKDF-SHA256(salt=∅, ikm=image_upload_key, info="mip01-blossom-upload-v2", 32)`
 *   (see [Mip01ImageCrypto.deriveBlossomUploadSeed]).
 *
 * The AEAD uses **no associated data** (empty AAD), matching MIP-01. The plaintext
 * MIME type is descriptive metadata only — it is deliberately NOT bound into the
 * crypto and NOT stored on the wire, because mdk's `NostrGroupDataExtension` parser
 * rejects any trailing bytes at a known version and has no media_type field, so
 * storing it would break group parsing for whitenoise/mdk members.
 *
 * A fetching client MUST verify that the fetched bytes hash to `image_hash` before
 * decrypting.
 *
 * ### Version fallback (parse both), mirroring mdk
 * [decryptAny] first tries v2 (HKDF-derived key); on failure it falls back to v1,
 * where `image_key` is used directly as the AEAD key. New images are always v2.
 */
object MarmotGroupImageEncryption {
    const val KEY_LENGTH = 32
    const val NONCE_LENGTH = 12

    private val EMPTY_AAD = ByteArray(0)

    /**
     * Result of encrypting a group image, ready to be uploaded to Blossom and
     * folded into a [MarmotGroupData].
     */
    class Encrypted(
        /** The encrypted blob to upload to Blossom (ciphertext || 16-byte tag). */
        val ciphertext: ByteArray,
        /** Random 32-byte HKDF seed — store as `image_key`. */
        val imageKey: ByteArray,
        /** Random 12-byte nonce — store as `image_nonce`. */
        val imageNonce: ByteArray,
        /** SHA-256 of [ciphertext] (hex) — store as `image_hash`; also the Blossom hash. */
        val imageHash: HexKey,
    )

    /**
     * Encrypt a plaintext image with a freshly-generated seed + nonce (MIP-01 v2).
     * Returns the ciphertext to upload plus the parameters to persist in
     * [MarmotGroupData].
     */
    fun encrypt(plaintext: ByteArray): Encrypted {
        val imageKeySeed = RandomInstance.bytes(KEY_LENGTH)
        val imageNonce = RandomInstance.bytes(NONCE_LENGTH)
        val aeadKey = Mip01ImageCrypto.deriveImageEncryptionKey(imageKeySeed)
        val ciphertext = ChaCha20Poly1305.encrypt(plaintext, EMPTY_AAD, imageNonce, aeadKey)
        return Encrypted(
            ciphertext = ciphertext,
            imageKey = imageKeySeed,
            imageNonce = imageNonce,
            imageHash = sha256(ciphertext).toHexKey(),
        )
    }

    /**
     * Decrypt a group image blob (MIP-01 v2): `image_key` is an HKDF seed.
     *
     * @throws IllegalStateException on authentication failure.
     */
    fun decrypt(
        ciphertext: ByteArray,
        imageKey: ByteArray,
        imageNonce: ByteArray,
    ): ByteArray {
        val aeadKey = Mip01ImageCrypto.deriveImageEncryptionKey(imageKey)
        return ChaCha20Poly1305.decrypt(ciphertext, EMPTY_AAD, imageNonce, aeadKey)
    }

    /**
     * Decrypt a group image blob, trying v2 (HKDF-derived key) first and falling
     * back to v1 (raw `image_key`), exactly like mdk. Returns null if neither
     * authenticates.
     */
    fun decryptAny(
        ciphertext: ByteArray,
        imageKey: ByteArray,
        imageNonce: ByteArray,
    ): ByteArray? {
        if (imageKey.size != KEY_LENGTH || imageNonce.size != NONCE_LENGTH) return null

        // v2: image_key is an HKDF seed.
        try {
            return decrypt(ciphertext, imageKey, imageNonce)
        } catch (_: Exception) {
            // fall through to v1
        }

        // v1: image_key is the AEAD key directly.
        return try {
            ChaCha20Poly1305.decrypt(ciphertext, EMPTY_AAD, imageNonce, imageKey)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generate the 32-byte HKDF **seed** stored as [MarmotGroupData.imageUploadKey].
     * The actual Blossom-auth secp256k1 secret is derived from it via
     * [deriveUploadKeypairSecret].
     */
    fun generateUploadKey(): ByteArray = RandomInstance.bytes(KEY_LENGTH)

    /**
     * Derive the 32-byte secp256k1 secret used to authorize Blossom writes for the
     * avatar from the stored `image_upload_key` seed (MIP-01 v2).
     */
    fun deriveUploadKeypairSecret(imageUploadKey: ByteArray): ByteArray = Mip01ImageCrypto.deriveBlossomUploadSeed(imageUploadKey)
}
