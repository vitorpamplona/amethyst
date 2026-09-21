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
package com.vitorpamplona.quartz.cordn.appEncryptedMedia

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * How a cordn message points at an encrypted file.
 *
 * A NIP-92 `imeta` tag, which is what every Nostr client already parses, with
 * the fields a recipient needs to fetch and open the blob. It rides **inside**
 * the MLS-encrypted envelope, so the coordinator and the blob host see none of
 * it — the host sees an opaque upload, the coordinator sees a sealed payload.
 *
 * The hash is of the **plaintext**, not of the blob. That is deliberate: it is
 * what the AAD binds ([CordnMediaEncryption]), so a recipient can tell an
 * altered file from a re-encoded one, and it is useless to the blob host,
 * which only ever holds the ciphertext.
 *
 * Marmot's `Mip04IMetaTag` carries a `v` version field and a rule about
 * rejecting `mip04-v1`. There is no cordn equivalent, and inventing one would
 * put a field on the wire that no other cordn client writes or reads.
 */
object CordnMediaTag {
    const val TAG_NAME = "imeta"

    const val URL = "url"
    const val MIME_TYPE = "m"
    const val FILENAME = "filename"
    const val HASH = "x"
    const val NONCE = "n"
    const val DIMENSIONS = "dim"
    const val BLURHASH = "blurhash"

    /** Builds the `imeta` tag for an uploaded [media] at [url]. */
    fun build(
        media: CordnEncryptedMedia,
        url: String,
        dimensions: String? = null,
        blurhash: String? = null,
    ): Array<String> =
        buildList {
            add(TAG_NAME)
            add("$URL $url")
            add("$MIME_TYPE ${media.mimeType}")
            add("$FILENAME ${media.filename}")
            add("$HASH ${media.plaintextHash.toHexKey()}")
            add("$NONCE ${media.nonce.toHexKey()}")
            dimensions?.let { add("$DIMENSIONS $it") }
            blurhash?.let { add("$BLURHASH $it") }
        }.toTypedArray()

    /**
     * Reads every attachment in [tags], skipping any that is not usable.
     *
     * A tag missing a field, or carrying a nonce or hash of the wrong length,
     * is dropped rather than returned half-formed: the only thing a caller
     * could do with a partial descriptor is attempt a decrypt that must fail,
     * and a message with one broken attachment should still show its other
     * attachments and its text.
     */
    fun parseAll(tags: TagArray): List<CordnMediaAttachment> =
        tags.mapNotNull { tag ->
            if (tag.getOrNull(0) != TAG_NAME) return@mapNotNull null

            val fields =
                tag
                    .drop(1)
                    .mapNotNull { field ->
                        val at = field.indexOf(' ')
                        if (at <= 0) null else field.substring(0, at) to field.substring(at + 1)
                    }.toMap()

            val url = fields[URL] ?: return@mapNotNull null
            val mime = fields[MIME_TYPE] ?: return@mapNotNull null
            val filename = fields[FILENAME] ?: return@mapNotNull null
            val hash = fields[HASH]?.takeIf { it.length == HASH_HEX_LENGTH } ?: return@mapNotNull null
            val nonce = fields[NONCE]?.takeIf { it.length == NONCE_HEX_LENGTH } ?: return@mapNotNull null

            CordnMediaAttachment(
                url = url,
                mimeType = mime,
                filename = filename,
                plaintextHash = hash,
                nonce = nonce,
                dimensions = fields[DIMENSIONS],
                blurhash = fields[BLURHASH],
            )
        }

    private const val HASH_HEX_LENGTH = 64
    private const val NONCE_HEX_LENGTH = 24
}

/** One encrypted attachment, as a message advertises it. */
data class CordnMediaAttachment(
    val url: String,
    val mimeType: String,
    val filename: String,
    val plaintextHash: String,
    val nonce: String,
    val dimensions: String? = null,
    val blurhash: String? = null,
) {
    val hashBytes: ByteArray get() = plaintextHash.hexToByteArray()
    val nonceBytes: ByteArray get() = nonce.hexToByteArray()

    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
}
