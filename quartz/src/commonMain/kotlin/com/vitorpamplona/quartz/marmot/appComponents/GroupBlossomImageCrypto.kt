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
package com.vitorpamplona.quartz.marmot.appComponents

import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20Poly1305
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.ciphers.NostrCipher
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * The current-profile group-avatar scheme for
 * `marmot.group.blossom.image.v1` (`0x8002`).
 *
 * ```text
 * aad            = "marmot-group-image-v1" || 0x00 || canonical_media_type
 * encrypted_blob = ChaCha20-Poly1305.encrypt(image_key, image_nonce, plaintext, aad)
 * image_hash     = SHA-256(encrypted_blob)
 * ```
 *
 * ## Three breaks from the MIP-01 scheme in `MarmotGroupImageEncryption`
 *
 * 1. `image_key` IS the AEAD key. MIP-01 treated it as an HKDF seed and derived
 *    the key from it.
 * 2. The AAD is domain-separated and binds the media type. MIP-01 used an empty
 *    AAD, so a blob could be replayed as a different type.
 * 3. `image_upload_key` IS the Blossom-auth secret key. MIP-01 derived that
 *    through HKDF too.
 *
 * None of these are negotiable at runtime: the component id is the version, so
 * a `0x8002` blob is always this scheme and a `marmot_group_data` blob is
 * always the old one. The legacy path stays in `MarmotGroupImageEncryption` for
 * groups already on disk; nothing falls back between them.
 *
 * ## The upload key is a shared capability
 *
 * `image_upload_key` travels inside the MLS-protected component, so every
 * current member — and any former member who kept a copy — can sign Blossom
 * write or delete authorizations for that blob as long as the server honours the
 * key. That is an accepted v1 limitation, not an oversight: the admin gate
 * protects group state, and it cannot revoke a capability already distributed.
 */
object GroupBlossomImageCrypto {
    const val KEY_LENGTH = GroupBlossomImageV1.KEY_SIZE
    const val NONCE_LENGTH = GroupBlossomImageV1.NONCE_SIZE

    /** An encrypted avatar plus the component state that describes it. */
    class Encrypted(
        /** The blob to upload to Blossom; its SHA-256 is the content id. */
        val ciphertext: ByteArray,
        /** Component state to commit, already carrying the canonical media type. */
        val component: GroupBlossomImageV1,
    )

    /**
     * Encrypt [plaintext] under a freshly generated key, nonce and upload
     * keypair.
     *
     * A producer MUST generate all three fresh for every new image and MUST NOT
     * reuse a key-nonce pair — ChaCha20-Poly1305 fails catastrophically on
     * nonce reuse, and here the "message" is a whole file.
     */
    fun encrypt(
        plaintext: ByteArray,
        mediaType: String,
    ): Encrypted {
        val canonical = MarmotMediaType.requireCanonical(mediaType)
        val imageKey = RandomInstance.bytes(KEY_LENGTH)
        val imageNonce = RandomInstance.bytes(NONCE_LENGTH)
        val uploadKey = Nip01Crypto.privKeyCreate()

        val ciphertext =
            ChaCha20Poly1305.encrypt(plaintext, GroupBlossomImageV1.aad(canonical), imageNonce, imageKey)

        return Encrypted(
            ciphertext = ciphertext,
            component =
                GroupBlossomImageV1(
                    imageHash = sha256(ciphertext),
                    imageKey = imageKey,
                    imageNonce = imageNonce,
                    imageUploadKey = uploadKey,
                    mediaType = canonical,
                ),
        )
    }

    /**
     * Decrypt a fetched blob against [component].
     *
     * Verifies the content hash first. A fetching client MUST do this before
     * decrypting: the blob is addressed by hash, so a store that returns
     * different bytes is either broken or hostile, and finding out via an AEAD
     * failure loses that distinction.
     */
    fun decrypt(
        ciphertext: ByteArray,
        component: GroupBlossomImageV1,
    ): ByteArray {
        require(component.hasImage) { "component carries no image" }
        require(sha256(ciphertext).contentEquals(component.imageHash)) {
            "fetched blob does not match image_hash"
        }
        val canonical = MarmotMediaType.requireCanonical(component.mediaType!!)
        return ChaCha20Poly1305.decrypt(
            ciphertext,
            GroupBlossomImageV1.aad(canonical),
            component.imageNonce!!,
            component.imageKey!!,
        )
    }

    fun decryptOrNull(
        ciphertext: ByteArray,
        component: GroupBlossomImageV1,
    ): ByteArray? =
        try {
            decrypt(ciphertext, component)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        }

    /**
     * A [NostrCipher] view for the display path, where the blob cache decrypts
     * a fetched URL transparently.
     */
    fun cipher(component: GroupBlossomImageV1): NostrCipher = ComponentCipher(component)

    private class ComponentCipher(
        private val component: GroupBlossomImageV1,
    ) : NostrCipher {
        override fun name(): String = "marmot-group-image-v1"

        override fun encrypt(bytesToEncrypt: ByteArray): ByteArray {
            require(component.hasImage) { "component carries no image" }
            val canonical = MarmotMediaType.requireCanonical(component.mediaType!!)
            return ChaCha20Poly1305.encrypt(
                bytesToEncrypt,
                GroupBlossomImageV1.aad(canonical),
                component.imageNonce!!,
                component.imageKey!!,
            )
        }

        // Qualified so these delegate to the object's two-argument helpers
        // rather than recursing into themselves.
        override fun decrypt(bytesToDecrypt: ByteArray): ByteArray = GroupBlossomImageCrypto.decrypt(bytesToDecrypt, component)

        override fun decryptOrNull(bytesToDecrypt: ByteArray): ByteArray? = GroupBlossomImageCrypto.decryptOrNull(bytesToDecrypt, component)
    }
}
