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

import com.vitorpamplona.quartz.events.FileHeaderEvent

class Nip92MediaAttachments {
    companion object {
        const val IMETA = "imeta"
    }

    fun convertFromFileHeader(header: FileHeaderEvent): Array<String>? {
        val myUrl = header.url() ?: return null
        return createTag(
            myUrl,
            header.tags,
        )
    }

    fun createTag(
        imageUrl: String,
        tags: Array<Array<String>>,
    ): Array<String> =
        arrayOf(
            IMETA,
            "url $imageUrl",
        ) +
            tags.mapNotNull {
                if (it.isNotEmpty() && it[0] != "url") {
                    if (it.size > 1) {
                        "${it[0]} ${it[1]}"
                    } else {
                        "${it[0]}}"
                    }
                } else {
                    null
                }
            }

    fun parse(
        imageUrl: String,
        tags: Array<Array<String>>,
    ): Map<String, String> =
        tags
            .firstOrNull {
                it.size > 1 && it[0] == IMETA && it[1] == "url $imageUrl"
            }?.let { tagList ->
                parseIMeta(tagList)
            } ?: emptyMap()

    fun parse(tags: Array<Array<String>>): Map<String, Map<String, String>> =
        tags.filter { it.size > 1 && it[0] == IMETA }.associate {
            val allTags = parseIMeta(it)
            (allTags.get("url") ?: "") to allTags
        }

    fun parseIMeta(tags: Array<String>): Map<String, String> =
        tags.associate { tag ->
            val parts = tag.split(" ", limit = 2)
            when (parts.size) {
                2 -> parts[0] to parts[1]
                1 -> parts[0] to ""
                else -> "" to ""
            }
        }
}
