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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip51Lists.tags.RelayTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheNameTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheSize
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheSizeTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheType
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheTypeTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.DifficultyTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.FirstToFindWinnerTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.HintTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.MissionTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.TerrainTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.TypeModifier
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.TypeModifierTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.VerificationKeyTag

fun TagArrayBuilder<GeocacheListingEvent>.cacheName(name: String) = addUnique(CacheNameTag.assemble(name))

fun TagArrayBuilder<GeocacheListingEvent>.difficulty(rating: Int) = addUnique(DifficultyTag.assemble(rating))

fun TagArrayBuilder<GeocacheListingEvent>.terrain(rating: Int) = addUnique(TerrainTag.assemble(rating))

fun TagArrayBuilder<GeocacheListingEvent>.cacheSize(size: CacheSize) = addUnique(CacheSizeTag.assemble(size))

/**
 * The `g` ladder for a cache at [geohash], from 3 to 9 characters.
 *
 * Deliberately not [com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash], which publishes
 * every prefix from one character up.
 */
fun TagArrayBuilder<GeocacheListingEvent>.cacheLocation(geohash: String) = addAll(GeocacheGeohash.ladder(geohash).map(GeoHashTag::assembleSingle))

/**
 * The cache type. Kept off [addUnique] because `t` also carries the `archived` marker — replacing
 * every `t` here would un-retire a cache the owner had archived.
 */
fun TagArrayBuilder<GeocacheListingEvent>.cacheType(type: CacheType) = addUniqueValueIfNew(CacheTypeTag.assemble(type))

fun TagArrayBuilder<GeocacheListingEvent>.cacheType(code: String) = addUniqueValueIfNew(CacheTypeTag.assemble(code))

/** Retires the cache, preserving its history instead of deleting it. */
fun TagArrayBuilder<GeocacheListingEvent>.archived() = addUniqueValueIfNew(CacheTypeTag.assembleArchived())

fun TagArrayBuilder<GeocacheListingEvent>.typeModifier(modifier: TypeModifier) = addUniqueValueIfNew(TypeModifierTag.assemble(modifier))

fun TagArrayBuilder<GeocacheListingEvent>.typeModifiers(modifiers: Collection<TypeModifier>) = addAllUniqueValueIfNew(TypeModifierTag.assemble(modifiers))

fun TagArrayBuilder<GeocacheListingEvent>.hint(hint: String) = addUnique(HintTag.assemble(hint))

fun TagArrayBuilder<GeocacheListingEvent>.mission(mission: String) = addUnique(MissionTag.assemble(mission))

fun TagArrayBuilder<GeocacheListingEvent>.verificationKey(pubKey: HexKey) = addUnique(VerificationKeyTag.assemble(pubKey))

fun TagArrayBuilder<GeocacheListingEvent>.firstToFindWinner(winnerPubKey: HexKey) = addUnique(FirstToFindWinnerTag.assemble(winnerPubKey))

fun TagArrayBuilder<GeocacheListingEvent>.cacheImages(urls: List<String>) = addAllUniqueValueIfNew(urls.map(ImageTag::assemble))

fun TagArrayBuilder<GeocacheListingEvent>.logRelays(relays: List<NormalizedRelayUrl>) = addAllUniqueValueIfNew(relays.map(RelayTag::assemble))
