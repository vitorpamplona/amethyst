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
package com.vitorpamplona.quartz.marmot.mip04EncryptedMedia

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.IMetaTagBuilder

/**
 * MIP-04 imeta tag field names per the spec.
 */
object Mip04Fields {
    const val URL = "url"
    const val MIME_TYPE = "m"
    const val FILENAME = "filename"
    const val DIMENSIONS = "dim"
    const val BLURHASH = "blurhash"
    const val THUMBHASH = "thumbhash"
    const val FILE_HASH = "x"
    const val NONCE = "n"
    const val VERSION = "v"
}

/**
 * Parsed MIP-04 encrypted media metadata from an imeta tag.
 */
data class Mip04MediaMeta(
    val url: String,
    val mimeType: String,
    val filename: String,
    val originalFileHash: String,
    val nonce: String,
    val version: String,
    val dimensions: String? = null,
    val blurhash: String? = null,
    val thumbhash: String? = null,
) {
    val nonceBytes: ByteArray get() = nonce.hexToByteArray()
    val originalFileHashBytes: ByteArray get() = originalFileHash.hexToByteArray()

    val isV2: Boolean get() = version == Mip04MediaEncryption.VERSION
}

/**
 * Parse an IMetaTag into MIP-04 media metadata.
 * Returns null if the tag is not a valid MIP-04 v2 imeta.
 */
fun IMetaTag.toMip04MediaMeta(): Mip04MediaMeta? {
    val mimeType = properties[Mip04Fields.MIME_TYPE]?.firstOrNull() ?: return null
    val filename = properties[Mip04Fields.FILENAME]?.firstOrNull() ?: return null
    val fileHash = properties[Mip04Fields.FILE_HASH]?.firstOrNull() ?: return null
    val nonce = properties[Mip04Fields.NONCE]?.firstOrNull() ?: return null
    val version = properties[Mip04Fields.VERSION]?.firstOrNull() ?: return null

    if (version != Mip04MediaEncryption.VERSION) return null
    if (nonce.length != 24) return null // 12 bytes = 24 hex chars

    return Mip04MediaMeta(
        url = url,
        mimeType = mimeType,
        filename = filename,
        originalFileHash = fileHash,
        nonce = nonce,
        version = version,
        dimensions = properties[Mip04Fields.DIMENSIONS]?.firstOrNull(),
        blurhash = properties[Mip04Fields.BLURHASH]?.firstOrNull(),
        thumbhash = properties[Mip04Fields.THUMBHASH]?.firstOrNull(),
    )
}

/**
 * Build an MIP-04 imeta tag from encryption results and file metadata.
 */
fun buildMip04IMetaTag(
    url: String,
    mimeType: String,
    filename: String,
    originalFileHash: ByteArray,
    nonce: ByteArray,
    dimensions: String? = null,
    blurhash: String? = null,
): IMetaTag =
    IMetaTagBuilder(url)
        .apply {
            add(Mip04Fields.MIME_TYPE, Mip04MediaEncryption.canonicalizeMimeType(mimeType))
            add(Mip04Fields.FILENAME, filename)
            add(Mip04Fields.FILE_HASH, originalFileHash.toHexKey())
            add(Mip04Fields.NONCE, nonce.toHexKey())
            add(Mip04Fields.VERSION, Mip04MediaEncryption.VERSION)
            dimensions?.let { add(Mip04Fields.DIMENSIONS, it) }
            blurhash?.let { add(Mip04Fields.BLURHASH, it) }
        }.build()
