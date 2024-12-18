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
package com.vitorpamplona.quartz.encoders

import com.vitorpamplona.quartz.events.FileHeaderEvent.Companion.ALT
import com.vitorpamplona.quartz.events.FileHeaderEvent.Companion.BLUR_HASH
import com.vitorpamplona.quartz.events.FileHeaderEvent.Companion.DIMENSION
import com.vitorpamplona.quartz.events.FileHeaderEvent.Companion.FILE_SIZE
import com.vitorpamplona.quartz.events.FileHeaderEvent.Companion.HASH
import com.vitorpamplona.quartz.events.FileHeaderEvent.Companion.MAGNET_URI
import com.vitorpamplona.quartz.events.FileHeaderEvent.Companion.MIME_TYPE
import com.vitorpamplona.quartz.events.FileHeaderEvent.Companion.ORIGINAL_HASH
import com.vitorpamplona.quartz.events.FileHeaderEvent.Companion.TORRENT_INFOHASH

class IMetaTag(
    val url: String,
    val properties: Map<String, String>,
)

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

    fun sensitiveContent(reason: String) = add("content-warning", reason)

    fun build() = IMetaTag(url, properties)
}

class Nip92MediaAttachments {
    companion object {
        const val IMETA = "imeta"

        fun createTag(header: IMetaTag): Array<String> =
            createTag(
                header.url,
                header.properties,
            )

        fun createTag(
            url: String,
            tags: Map<String, String>,
        ): Array<String> =
            arrayOf(
                IMETA,
                "url $url",
            ) +
                tags.mapNotNull {
                    if (it.key != "url") {
                        "${it.key} ${it.value}"
                    } else {
                        null
                    }
                }

        fun parse(
            url: String,
            tags: Array<Array<String>>,
        ): Map<String, String> =
            tags
                .firstOrNull {
                    it.size > 1 && it[0] == IMETA && it[1] == "url $url"
                }?.let { tagList ->
                    parseIMeta(tagList)
                } ?: emptyMap()

        fun parse(tags: Array<Array<String>>): Map<String, Map<String, String>> =
            tags.filter { it.size > 1 && it[0] == IMETA }.associate {
                val allTags = parseIMeta(it)
                (allTags.get("url") ?: "") to allTags
            }

        private fun parseIMeta(tags: Array<String>): Map<String, String> =
            tags.associate { tag ->
                val parts = tag.split(" ", limit = 2)
                when (parts.size) {
                    2 -> parts[0] to parts[1]
                    1 -> parts[0] to ""
                    else -> "" to ""
                }
            }
    }
}
