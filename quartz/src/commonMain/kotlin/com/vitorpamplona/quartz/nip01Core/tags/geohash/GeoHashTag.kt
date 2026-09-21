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
package com.vitorpamplona.quartz.nip01Core.tags.geohash

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

class GeoHashTag {
    companion object {
        const val TAG_NAME = "g"

        fun isTagged(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tags: Array<String>): String? {
            ensure(tags.has(1)) { return null }
            ensure(tags[0] == TAG_NAME) { return null }
            return tags[1]
        }

        fun assembleSingle(geohash: String) = arrayOf(TAG_NAME, geohash)

        fun geoMipMap(geohash: String): List<String> = geohash.indices.map { geohash.substring(0, it + 1) }.reversed()

        /**
         * The prefixes of [geohash] between [minPrecision] and [maxPrecision] characters,
         * coarse-to-fine.
         *
         * [geoMipMap] starts at one character and runs fine-to-coarse, which is what the geohash
         * chat channels want. Specs that pin a precision band instead — NIP-CC asks geocache
         * listings for 3 to 9 characters — want this: a 1- or 2-character geohash spans thousands
         * of kilometres, so tagging one is noise on the relay and useless for proximity search.
         *
         * Returns an empty list when [geohash] is shorter than [minPrecision].
         */
        fun geoMipMap(
            geohash: String,
            minPrecision: Int,
            maxPrecision: Int,
        ): List<String> {
            val finest = minOf(maxPrecision, geohash.length)
            if (minPrecision > finest) return emptyList()
            return (minPrecision..finest).map { geohash.substring(0, it) }
        }

        fun geohashMipMap(geohash: String): TagArray = geoMipMap(geohash).map { assembleSingle(it) }.toTypedArray()

        fun assemble(geohash: String) = geohashMipMap(geohash)
    }
}
