/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
abstract class VideoEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    fun url() = tags.firstOrNull { it.size > 1 && it[0] == URL }?.get(1)

    fun urls() = tags.filter { it.size > 1 && it[0] == URL }.map { it[1] }

    fun mimeType() = tags.firstOrNull { it.size > 1 && it[0] == MIME_TYPE }?.get(1)

    fun hash() = tags.firstOrNull { it.size > 1 && it[0] == HASH }?.get(1)

    fun size() = tags.firstOrNull { it.size > 1 && it[0] == FILE_SIZE }?.get(1)

    fun alt() = tags.firstOrNull { it.size > 1 && it[0] == ALT }?.get(1)

    fun dimensions() = tags.firstOrNull { it.size > 1 && it[0] == DIMENSION }?.get(1)

    fun magnetURI() = tags.firstOrNull { it.size > 1 && it[0] == MAGNET_URI }?.get(1)

    fun torrentInfoHash() = tags.firstOrNull { it.size > 1 && it[0] == TORRENT_INFOHASH }?.get(1)

    fun blurhash() = tags.firstOrNull { it.size > 1 && it[0] == BLUR_HASH }?.get(1)

    fun title() = tags.firstOrNull { it.size > 1 && it[0] == TITLE }?.get(1)

    fun summary() = tags.firstOrNull { it.size > 1 && it[0] == SUMMARY }?.get(1)

    fun image() = tags.firstOrNull { it.size > 1 && it[0] == IMAGE }?.get(1)

    fun thumb() = tags.firstOrNull { it.size > 1 && it[0] == THUMB }?.get(1)

    fun hasUrl() = tags.any { it.size > 1 && it[0] == URL }

    fun isOneOf(mimeTypes: Set<String>) = tags.any { it.size > 1 && it[0] == FileHeaderEvent.MIME_TYPE && mimeTypes.contains(it[1]) }

    companion object {
        private const val URL = "url"
        private const val ENCRYPTION_KEY = "aes-256-gcm"
        private const val MIME_TYPE = "m"
        private const val FILE_SIZE = "size"
        private const val DIMENSION = "dim"
        private const val HASH = "x"
        private const val MAGNET_URI = "magnet"
        private const val TORRENT_INFOHASH = "i"
        private const val BLUR_HASH = "blurhash"
        private const val ORIGINAL_HASH = "ox"
        private const val ALT = "alt"
        private const val TITLE = "title"
        private const val PUBLISHED_AT = "published_at"
        private const val SUMMARY = "summary"
        private const val DURATION = "duration"
        private const val IMAGE = "image"
        private const val THUMB = "thumb"

        fun <T : VideoEvent> create(
            kind: Int,
            url: String,
            magnetUri: String? = null,
            mimeType: String? = null,
            alt: String? = null,
            hash: String? = null,
            size: String? = null,
            dimensions: String? = null,
            blurhash: String? = null,
            originalHash: String? = null,
            magnetURI: String? = null,
            torrentInfoHash: String? = null,
            encryptionKey: AESGCM? = null,
            sensitiveContent: Boolean? = null,
            altDescription: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (T) -> Unit,
        ) {
            val tags =
                listOfNotNull(
                    arrayOf(URL, url),
                    magnetUri?.let { arrayOf(MAGNET_URI, it) },
                    mimeType?.let { arrayOf(MIME_TYPE, it) },
                    alt?.ifBlank { null }?.let { arrayOf(ALT, it) } ?: arrayOf("alt", altDescription),
                    hash?.let { arrayOf(HASH, it) },
                    size?.let { arrayOf(FILE_SIZE, it) },
                    dimensions?.let { arrayOf(DIMENSION, it) },
                    blurhash?.let { arrayOf(BLUR_HASH, it) },
                    originalHash?.let { arrayOf(ORIGINAL_HASH, it) },
                    magnetURI?.let { arrayOf(MAGNET_URI, it) },
                    torrentInfoHash?.let { arrayOf(TORRENT_INFOHASH, it) },
                    encryptionKey?.let { arrayOf(ENCRYPTION_KEY, it.key, it.nonce) },
                    sensitiveContent?.let {
                        if (it) {
                            arrayOf("content-warning", "")
                        } else {
                            null
                        }
                    },
                )

            val content = alt ?: ""
            signer.sign<T>(createdAt, kind, tags.toTypedArray(), content, onReady)
        }
    }
}
