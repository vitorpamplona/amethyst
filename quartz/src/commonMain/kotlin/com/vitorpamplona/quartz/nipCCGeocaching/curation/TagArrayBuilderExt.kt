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
package com.vitorpamplona.quartz.nipCCGeocaching.curation

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip51Lists.tags.DescriptionTag
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag
import com.vitorpamplona.quartz.nipCCGeocaching.curation.tags.ListTheme
import com.vitorpamplona.quartz.nipCCGeocaching.curation.tags.ListThemeTag
import com.vitorpamplona.quartz.nipCCGeocaching.curation.tags.MapStyle
import com.vitorpamplona.quartz.nipCCGeocaching.curation.tags.MapStyleTag

fun TagArrayBuilder<GeocacheCurationListEvent>.listTitle(title: String) = addUnique(TitleTag.assemble(title))

fun TagArrayBuilder<GeocacheCurationListEvent>.listDescription(description: String) = addUnique(DescriptionTag.assemble(description))

fun TagArrayBuilder<GeocacheCurationListEvent>.listImage(url: String) = addUnique(ImageTag.assemble(url))

fun TagArrayBuilder<GeocacheCurationListEvent>.listTheme(theme: ListTheme) = addUnique(ListThemeTag.assemble(theme))

fun TagArrayBuilder<GeocacheCurationListEvent>.listMapStyle(style: MapStyle) = addUnique(MapStyleTag.assemble(style))

/**
 * The `g` ladder for a list centred on [geohash], from 3 to 6 characters.
 *
 * Coarser than a listing's 3..9 on purpose: NIP-CC asks a list for discovery precision, and a
 * trail's centre point is not a place anybody walks to.
 */
fun TagArrayBuilder<GeocacheCurationListEvent>.listLocation(geohash: String) =
    addAll(
        GeoHashTag.geoMipMap(geohash, GeocacheCurationListEvent.MIN_TAGGED_PRECISION, GeocacheCurationListEvent.MAX_TAGGED_PRECISION).map(GeoHashTag::assembleSingle),
    )

/** Appends one cache. Call order is the list order NIP-CC says to preserve. */
fun TagArrayBuilder<GeocacheCurationListEvent>.geocache(
    cache: Address,
    relayHint: NormalizedRelayUrl? = null,
) = addUniqueValueIfNew(ATag.assemble(cache, relayHint))

fun TagArrayBuilder<GeocacheCurationListEvent>.geocaches(caches: List<Address>) = addAllUniqueValueIfNew(caches.map { ATag.assemble(it, null) })
