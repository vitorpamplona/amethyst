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
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip51Lists.tags.DescriptionTag
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag
import com.vitorpamplona.quartz.nipCCGeocaching.curation.tags.ListThemeTag
import com.vitorpamplona.quartz.nipCCGeocaching.curation.tags.MapStyleTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent

fun TagArray.listTitle() = firstNotNullOfOrNull(TitleTag::parse)

fun TagArray.listDescription() = firstNotNullOfOrNull(DescriptionTag::parse)

fun TagArray.listImage() = firstNotNullOfOrNull(ImageTag::parse)

fun TagArray.listTheme() = firstNotNullOfOrNull(ListThemeTag::parse)

fun TagArray.listThemeCode() = firstNotNullOfOrNull(ListThemeTag::parseCode)

fun TagArray.listMapStyle() = firstNotNullOfOrNull(MapStyleTag::parse)

fun TagArray.listMapStyleCode() = firstNotNullOfOrNull(MapStyleTag::parseCode)

/** Every `a` tag, in publication order — which NIP-CC says is meaningful. */
fun TagArray.curatedAddresses() = mapNotNull(ATag::parseAddress)

/**
 * The curated geocaches, in order, filtered to the kinds NIP-CC allows a list to reference.
 *
 * A list can point at anything; only 37516 (and the referenced-but-undefined 37515) are caches.
 * Dropping the rest keeps an unrelated address out of a treasure-hunt itinerary.
 */
fun TagArray.curatedGeocaches(): List<Address> =
    curatedAddresses().filter {
        it.kind == GeocacheListingEvent.KIND || it.kind == GeocacheListingEvent.LEGACY_KIND
    }
