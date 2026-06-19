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
package com.vitorpamplona.quartz.experimental.roadstr.tags

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag

/** Adds `lat`/`lon` tags (7-decimal strings) shared by Roadstr reports and confirmations. */
fun <T : Event> TagArrayBuilder<T>.coordinates(
    latitude: Double,
    longitude: Double,
) = apply {
    add(LatitudeTag.assemble(latitude))
    add(LongitudeTag.assemble(longitude))
}

/** Adds the three `g` geohash tags (precision 4, 5 and 6) derived from [latitude]/[longitude]. */
fun <T : Event> TagArrayBuilder<T>.roadGeohashes(
    latitude: Double,
    longitude: Double,
) = apply {
    val geohash6 = GeoHash.encode(latitude, longitude, 6).toString()
    add(GeoHashTag.assembleSingle(geohash6.substring(0, 4)))
    add(GeoHashTag.assembleSingle(geohash6.substring(0, 5)))
    add(GeoHashTag.assembleSingle(geohash6))
}
