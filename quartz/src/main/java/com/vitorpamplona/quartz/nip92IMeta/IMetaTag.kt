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

class IMetaTag(
    val url: String,
    val properties: Map<String, List<String>>,
) {
    fun toTagArray() =
        arrayOf(TAG_NAME, "$ANCHOR_PROPERTY $url") +
            properties
                .mapNotNull { (key, value) ->
                    if (key != ANCHOR_PROPERTY) {
                        value.map { "$key $it" }
                    } else {
                        null
                    }
                }.flatten()

    companion object {
        const val TAG_NAME = "imeta"
        const val ANCHOR_PROPERTY = "url"
        const val TAG_SIZE = 2

        fun parse(tag: Array<String>): IMetaTag? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null

            val allTags = parseIMeta(tag)
            val url = allTags.get(ANCHOR_PROPERTY)?.firstOrNull()

            return if (url != null) {
                IMetaTag(url, allTags.minus(ANCHOR_PROPERTY))
            } else {
                null
            }
        }

        private fun parseIMeta(tags: Array<String>): Map<String, List<String>> {
            val propertiesByKey = mutableMapOf<String, MutableList<String>>()

            tags.forEach { tag ->
                val parts = tag.split(" ", limit = 2)
                when (parts.size) {
                    2 -> propertiesByKey.getOrPut(parts[0], { mutableListOf() }).add(parts[1])
                    1 -> propertiesByKey.getOrPut(parts[0], { mutableListOf() }).add("")
                }
            }

            return propertiesByKey
        }

        fun assemble(iMetaTag: IMetaTag) = iMetaTag.toTagArray()
    }
}
