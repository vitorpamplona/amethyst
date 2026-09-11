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

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * `marmot.group.blossom.image.v1`, component `0x8002` — an encrypted group
 * avatar stored on Blossom.
 *
 * ```text
 * struct {
 *   opaque image_hash<0..32>;
 *   opaque image_key<0..32>;
 *   opaque image_nonce<0..12>;
 *   opaque image_upload_key<0..32>;
 *   opaque media_type<0..128>;
 * } MarmotGroupBlossomImageV1;
 * ```
 *
 * An absent image is five EMPTY fields, not a zero-length component: the
 * component is present and says "no image".
 *
 * ## Two changes from what MIP-01 packed into `marmot_group_data`
 *
 * 1. `image_key` and `image_upload_key` are the keys themselves, not HKDF
 *    seeds. `image_key` is the ChaCha20-Poly1305 content key; `image_upload_key`
 *    is the secret key of a freshly generated Nostr keypair whose public half is
 *    the blob's server-side write credential.
 * 2. There is a `media_type` field, and it is bound into the AEAD:
 *    `aad = "marmot-group-image-v1" || 0x00 || canonical_media_type`. MIP-01
 *    used an empty AAD and had nowhere to put the type.
 *
 * Both are wire-visible breaks from the MIP-era scheme. This type carries the
 * component bytes; the encryption itself is a separate concern.
 *
 * ## An accepted v1 limitation
 *
 * `image_upload_key` travels inside the MLS-protected component, so every
 * current member — and any former member that kept a copy — can sign write or
 * delete authorizations for that blob for as long as the server honours the
 * key. The admin gate protects updates to group state; it cannot revoke a
 * capability already handed out. An admin restores a deleted image by uploading
 * under a fresh key and committing a full replacement.
 */
data class GroupBlossomImageV1(
    /** SHA-256 of the ENCRYPTED blob — its Blossom content id. */
    val imageHash: ByteArray?,
    /** The ChaCha20-Poly1305 content key, not a seed. */
    val imageKey: ByteArray?,
    val imageNonce: ByteArray?,
    /** Secret key of a per-image Nostr keypair used for Blossom auth, not a seed. */
    val imageUploadKey: ByteArray?,
    /** Media type of the DECRYPTED image; also bound into the AEAD's AAD. */
    val mediaType: String?,
) {
    init {
        val present = listOf(imageHash, imageKey, imageNonce, imageUploadKey).count { it != null }
        require(present == 0 || present == 4) {
            "group image fields must be all present or all absent"
        }
        if (present == 4) {
            require(imageHash!!.size == HASH_SIZE) { "image_hash must be $HASH_SIZE bytes" }
            require(imageKey!!.size == KEY_SIZE) { "image_key must be $KEY_SIZE bytes" }
            require(imageNonce!!.size == NONCE_SIZE) { "image_nonce must be $NONCE_SIZE bytes" }
            require(imageUploadKey!!.size == KEY_SIZE) { "image_upload_key must be $KEY_SIZE bytes" }
            val type = requireNotNull(mediaType) { "a present image must carry a media_type" }
            require(type.isNotEmpty()) { "media_type must not be empty when an image is present" }
            require(type.encodeToByteArray().size <= MEDIA_TYPE_MAX_BYTES) {
                "media_type exceeds $MEDIA_TYPE_MAX_BYTES bytes"
            }
        } else {
            require(mediaType.isNullOrEmpty()) { "media_type must be empty when no image is present" }
        }
    }

    val hasImage: Boolean get() = imageHash != null

    val imageHashHex: HexKey? get() = imageHash?.toHexKey()

    fun encode(): ByteArray {
        val writer = TlsWriter()
        writer.putOpaqueVarInt(imageHash ?: ByteArray(0))
        writer.putOpaqueVarInt(imageKey ?: ByteArray(0))
        writer.putOpaqueVarInt(imageNonce ?: ByteArray(0))
        writer.putOpaqueVarInt(imageUploadKey ?: ByteArray(0))
        writer.putOpaqueVarInt(mediaType?.encodeToByteArray() ?: ByteArray(0))
        return writer.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GroupBlossomImageV1) return false
        return imageHash.contentEquals(other.imageHash) &&
            imageKey.contentEquals(other.imageKey) &&
            imageNonce.contentEquals(other.imageNonce) &&
            imageUploadKey.contentEquals(other.imageUploadKey) &&
            mediaType == other.mediaType
    }

    override fun hashCode(): Int {
        var result = imageHash?.contentHashCode() ?: 0
        result = 31 * result + (imageKey?.contentHashCode() ?: 0)
        result = 31 * result + (imageNonce?.contentHashCode() ?: 0)
        result = 31 * result + (imageUploadKey?.contentHashCode() ?: 0)
        result = 31 * result + (mediaType?.hashCode() ?: 0)
        return result
    }

    companion object {
        const val COMPONENT_ID = AppComponentIds.GROUP_BLOSSOM_IMAGE_V1
        const val HASH_SIZE = 32
        const val KEY_SIZE = 32
        const val NONCE_SIZE = 12
        const val MEDIA_TYPE_MAX_BYTES = 128

        /** The canonical "this group has no image" state: five empty fields. */
        val ABSENT = GroupBlossomImageV1(null, null, null, null, null)

        /** ASCII domain label bound into the image AEAD, before the 0x00 separator. */
        const val AAD_LABEL = "marmot-group-image-v1"

        /**
         * `"marmot-group-image-v1" || 0x00 || canonical_media_type`, with no
         * length prefixes anywhere.
         */
        fun aad(canonicalMediaType: String): ByteArray = AAD_LABEL.encodeToByteArray() + byteArrayOf(0) + canonicalMediaType.encodeToByteArray()

        fun decode(bytes: ByteArray): GroupBlossomImageV1 {
            val reader = TlsReader(bytes)
            val hash = reader.readOpaqueVarInt()
            val key = reader.readOpaqueVarInt()
            val nonce = reader.readOpaqueVarInt()
            val uploadKey = reader.readOpaqueVarInt()
            val mediaType = reader.readOpaqueVarInt()
            require(!reader.hasRemaining) { "group image component has trailing bytes" }

            return GroupBlossomImageV1(
                imageHash = hash.takeIf { it.isNotEmpty() },
                imageKey = key.takeIf { it.isNotEmpty() },
                imageNonce = nonce.takeIf { it.isNotEmpty() },
                imageUploadKey = uploadKey.takeIf { it.isNotEmpty() },
                mediaType = mediaType.takeIf { it.isNotEmpty() }?.decodeToString(),
            )
        }
    }
}
