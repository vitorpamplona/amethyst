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
package com.vitorpamplona.quartz.nipCCGeocaching.listing

import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheSize

/**
 * The geohash precision rules NIP-CC puts on geocache listings (kind 37516).
 *
 * Two different numbers are involved and they are easy to confuse:
 *
 * - [MIN_TAGGED]..[MAX_TAGGED] is the band of `g` tags a listing publishes, so that relays can
 *   answer proximity queries at several zoom levels.
 * - [minPublishPrecision] is how precise the *location the owner picked* must be before a client
 *   accepts the submission at all. A cache you cannot walk up to is not a cache.
 */
object GeocacheGeohash {
    /** Coarsest `g` tag worth publishing (~156km). */
    const val MIN_TAGGED = 3

    /** Finest `g` tag NIP-CC asks for (~5m). */
    const val MAX_TAGGED = 9

    /** NIP-CC: "Validate geohash precision meets minimum requirements (8+ characters …)". */
    const val MIN_PUBLISH = 8

    /** "… 9+ for micro caches" — a 38m box does not find a film canister. */
    const val MIN_PUBLISH_MICRO = 9

    /** The precision a submission of [size] must reach before a client accepts it. */
    fun minPublishPrecision(size: CacheSize?) = if (size == CacheSize.MICRO) MIN_PUBLISH_MICRO else MIN_PUBLISH

    /** Whether [geohash] is precise enough to publish as a cache of [size]. */
    fun isPreciseEnough(
        geohash: String,
        size: CacheSize?,
    ) = geohash.length >= minPublishPrecision(size)

    /** The `g` values a listing at [geohash] should publish, coarse-to-fine. */
    fun ladder(geohash: String) = GeoHashTag.geoMipMap(geohash, MIN_TAGGED, MAX_TAGGED)
}
