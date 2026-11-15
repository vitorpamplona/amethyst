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
package com.vitorpamplona.amethyst.commons.ui.note.creators.location

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geohash_area_block
import com.vitorpamplona.amethyst.commons.resources.geohash_area_building
import com.vitorpamplona.amethyst.commons.resources.geohash_area_city
import com.vitorpamplona.amethyst.commons.resources.geohash_area_continent
import com.vitorpamplona.amethyst.commons.resources.geohash_area_neighborhood
import com.vitorpamplona.amethyst.commons.resources.geohash_area_province
import com.vitorpamplona.amethyst.commons.resources.geohash_area_region
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChannelLevel
import kotlin.math.abs

/**
 * What the platforms' geohash location pickers share, so the Android (osmdroid) and desktop (tile
 * map) pickers frame a place and name its size the same way.
 */
object GeohashPickerDefaults {
    /** How far out a picker starts with nothing chosen yet: the Americas, Europe and Africa. */
    const val WORLD_ZOOM = 2.5

    /**
     * Where a picker starts with nothing chosen: a mid-Atlantic point with no place name. Not
     * 20N/0E, which is inland Mali (it reverse-geocodes to Tessalit) and sits on the prime
     * meridian, where a sub-kilometre pan flips the geohash between the `e…` and `s…` halves of
     * the world.
     */
    const val WORLD_CENTER_LAT = 20.0
    const val WORLD_CENTER_LON = -30.0

    /**
     * A zoom at which a cell of [length] characters roughly fills the view: a coarse cell opens
     * zoomed out, a fine one zoomed in.
     */
    fun zoomForGeohashLength(length: Int): Double =
        when {
            length <= 1 -> 2.0
            length == 2 -> 4.0
            length == 3 -> 6.5
            length == 4 -> 9.0
            length == 5 -> 12.0
            length == 6 -> 14.0
            length == 7 -> 16.0
            else -> 17.5
        }

    /**
     * The area level closest to a geohash of [chars] characters, for a seed that matches none
     * exactly (3 characters, or 9 from a geocache): the precision a picker shows instead of a
     * default that has nothing to do with the seed.
     */
    fun nearestLevel(chars: Int): GeohashChannelLevel = GeohashChannelLevel.ordered.minBy { abs(it.chars - chars) }
}

/** A rough physical size for a geohash cell at this precision, for the location pickers' chips. */
@Composable
fun GeohashChannelLevel.areaSize(): String =
    stringRes(
        when (this) {
            GeohashChannelLevel.CONTINENT -> Res.string.geohash_area_continent
            GeohashChannelLevel.REGION -> Res.string.geohash_area_region
            GeohashChannelLevel.PROVINCE -> Res.string.geohash_area_province
            GeohashChannelLevel.CITY -> Res.string.geohash_area_city
            GeohashChannelLevel.NEIGHBORHOOD -> Res.string.geohash_area_neighborhood
            GeohashChannelLevel.BLOCK -> Res.string.geohash_area_block
            GeohashChannelLevel.BUILDING -> Res.string.geohash_area_building
        },
    )
