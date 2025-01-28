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
package com.vitorpamplona.quartz.nip92IMeta

import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip36SensitiveContent.CONTENT_WARNING
import com.vitorpamplona.quartz.nip94FileMetadata.Dimension
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent.Companion.ALT
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent.Companion.BLUR_HASH
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent.Companion.DIMENSION
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent.Companion.FILE_SIZE
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent.Companion.HASH
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent.Companion.MAGNET_URI
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent.Companion.MIME_TYPE
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent.Companion.ORIGINAL_HASH
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent.Companion.TORRENT_INFOHASH

class IMetaTagBuilder(
    val url: String,
) {
    val properties = mutableMapOf<String, String>()

    fun add(
        key: String,
        value: String,
    ): IMetaTagBuilder {
        properties.set(key, value)
        return this
    }

    fun magnet(uri: String) = add(MAGNET_URI, uri)

    fun mimeType(mime: String) = add(MIME_TYPE, mime)

    fun alt(alt: String) = add(ALT, alt)

    fun hash(hash: HexKey) = add(HASH, hash)

    fun size(size: Int) = add(FILE_SIZE, size.toString())

    fun dims(dims: Dimension) = add(DIMENSION, dims.toString())

    fun blurhash(blurhash: String) = add(BLUR_HASH, blurhash)

    fun originalHash(originalHash: String) = add(ORIGINAL_HASH, originalHash)

    fun torrent(uri: String) = add(TORRENT_INFOHASH, uri)

    fun sensitiveContent(reason: String) = add(CONTENT_WARNING, reason)

    fun build() = IMetaTag(url, properties)
}
