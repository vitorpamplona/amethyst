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
package com.vitorpamplona.quartz.experimental.bitchat.geohash

/**
 * The named geohash precision levels Bitchat exposes as location channels, each a
 * fixed geohash character length. Coarser (fewer chars) = larger area.
 *
 * Because a geohash is a prefix code, the channel for any level is just the first
 * [chars] characters of a finer cell — so one precise fix yields every level via
 * [cellFor].
 */
enum class GeohashChannelLevel(
    val chars: Int,
) {
    REGION(2),
    PROVINCE(4),
    CITY(5),
    NEIGHBORHOOD(6),
    BLOCK(7),
    BUILDING(8),
    ;

    /**
     * The channel cell for this level, i.e. [fullGeohash] truncated to [chars].
     * Returns null when [fullGeohash] is shorter than this level's precision.
     */
    fun cellFor(fullGeohash: String): String? = if (fullGeohash.length >= chars) fullGeohash.substring(0, chars) else null

    companion object {
        /** Coarsest → finest, the order a location-channel picker should list them. */
        val ordered = listOf(REGION, PROVINCE, CITY, NEIGHBORHOOD, BLOCK, BUILDING)

        /** The named level whose precision matches [chars], if any. */
        fun forChars(chars: Int): GeohashChannelLevel? = entries.firstOrNull { it.chars == chars }
    }
}
