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
import com.vitorpamplona.quartz.utils.Log

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
 * Structured result of parsing a MIP-04 imeta tag.
 *
 * Lets callers distinguish "this isn't a MIP-04 tag" (skip), "looks like a
 * deprecated v1 blob with a nonce-reuse vulnerability" (show warning / block
 * decrypt), from valid v2 metadata.
 */
sealed class Mip04ParseResult {
    /** The tag is a well-formed MIP-04 v2 media descriptor. */
    data class Parsed(
        val meta: Mip04MediaMeta,
    ) : Mip04ParseResult()

    /**
     * The tag advertises `v=mip04-v1`. Per MIP-04 §"Deprecated Version 1",
     * clients MUST reject these blobs because v1 derived the ChaCha20
     * nonce deterministically and is vulnerable to nonce-reuse attacks.
     */
    data class DeprecatedV1(
        val url: String,
    ) : Mip04ParseResult()

    /** Missing required MIP-04 fields; treat as a non-MIP-04 imeta tag. */
    data object NotMip04 : Mip04ParseResult()

    /** Malformed MIP-04 tag (e.g. wrong nonce length or unknown version). */
    data class Invalid(
        val reason: String,
    ) : Mip04ParseResult()
}

private const val MIP04_LOG_TAG = "Mip04"

/**
 * Parse an IMetaTag into a structured [Mip04ParseResult]. The result captures
 * the deprecated-v1 case separately so callers can surface a warning instead
 * of silently dropping the media.
 */
fun IMetaTag.parseMip04(): Mip04ParseResult {
    val mimeType = properties[Mip04Fields.MIME_TYPE]?.firstOrNull()
    val filename = properties[Mip04Fields.FILENAME]?.firstOrNull()
    val fileHash = properties[Mip04Fields.FILE_HASH]?.firstOrNull()
    val nonce = properties[Mip04Fields.NONCE]?.firstOrNull()
    val version = properties[Mip04Fields.VERSION]?.firstOrNull()

    if (mimeType == null || filename == null || fileHash == null || nonce == null || version == null) {
        return Mip04ParseResult.NotMip04
    }

    if (version == Mip04MediaEncryption.LEGACY_VERSION_V1) {
        Log.w(MIP04_LOG_TAG) {
            "Rejecting MIP-04 v1 imeta for $url: v1 used deterministic nonces and is " +
                "vulnerable to nonce-reuse attacks. Re-upload with mip04-v2."
        }
        return Mip04ParseResult.DeprecatedV1(url)
    }

    if (version != Mip04MediaEncryption.VERSION) {
        return Mip04ParseResult.Invalid("Unknown MIP-04 version: $version")
    }

    if (nonce.length != 24) {
        return Mip04ParseResult.Invalid("nonce must be 24 hex chars (12 bytes), got ${nonce.length}")
    }

    return Mip04ParseResult.Parsed(
        Mip04MediaMeta(
            url = url,
            mimeType = mimeType,
            filename = filename,
            originalFileHash = fileHash,
            nonce = nonce,
            version = version,
            dimensions = properties[Mip04Fields.DIMENSIONS]?.firstOrNull(),
            blurhash = properties[Mip04Fields.BLURHASH]?.firstOrNull(),
            thumbhash = properties[Mip04Fields.THUMBHASH]?.firstOrNull(),
        ),
    )
}

/**
 * Parse an IMetaTag into MIP-04 media metadata.
 *
 * Returns null if the tag is not a valid MIP-04 v2 imeta. When a deprecated
 * `mip04-v1` tag is encountered, a security warning is logged before null is
 * returned — callers that need to distinguish that case should use
 * [parseMip04] instead.
 */
fun IMetaTag.toMip04MediaMeta(): Mip04MediaMeta? =
    when (val result = parseMip04()) {
        is Mip04ParseResult.Parsed -> result.meta
        is Mip04ParseResult.DeprecatedV1, Mip04ParseResult.NotMip04, is Mip04ParseResult.Invalid -> null
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
    thumbhash: String? = null,
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
            thumbhash?.let { add(Mip04Fields.THUMBHASH, it) }
        }.build()
