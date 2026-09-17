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

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
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
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.TypeModifierCategory
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.TypeModifierTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.VerificationKeyTag

fun TagArray.cacheName() = firstNotNullOfOrNull(CacheNameTag::parse)

fun TagArray.difficulty() = firstNotNullOfOrNull(DifficultyTag::parse)

fun TagArray.terrain() = firstNotNullOfOrNull(TerrainTag::parse)

fun TagArray.cacheSize() = firstNotNullOfOrNull(CacheSizeTag::parse)

/** The raw `S` value, which may be outside the [CacheSize] vocabulary. */
fun TagArray.cacheSizeCode() = firstNotNullOfOrNull(CacheSizeTag::parseCode)

/** The `t` cache type as published, defaulting to `traditional` per NIP-CC when absent. */
fun TagArray.cacheTypeCode() = firstNotNullOfOrNull(CacheTypeTag::parseTypeCode) ?: CacheType.TRADITIONAL.code

/** The parsed [CacheType], or null when the listing names a client-defined type. */
fun TagArray.cacheType() = CacheType.fromCode(cacheTypeCode())

/** Whether the owner retired this cache with `["t", "archived"]`. */
fun TagArray.isArchived() = fastAny(CacheTypeTag::isArchived)

/**
 * The known `n` modifiers, at most one per category.
 *
 * NIP-CC rule 2: where a listing carries several values from one category, the first occurrence
 * wins and the rest are ignored. Unknown values are dropped (rule 4) — see [typeModifierCodes]
 * to see them anyway.
 */
fun TagArray.typeModifiers(): Map<TypeModifierCategory, TypeModifier> {
    val byCategory = mutableMapOf<TypeModifierCategory, TypeModifier>()
    fastForEach { tag ->
        TypeModifierTag.parse(tag)?.let {
            if (!byCategory.containsKey(it.category)) byCategory[it.category] = it
        }
    }
    return byCategory
}

/** Every raw `n` value in publication order, unknown modifiers included. */
fun TagArray.typeModifierCodes() = mapNotNull(TypeModifierTag::parseCode)

fun TagArray.hasTypeModifier(modifier: TypeModifier) = typeModifiers()[modifier.category] == modifier

fun TagArray.isFirstToFind() = hasTypeModifier(TypeModifier.FIRST_TO_FIND)

fun TagArray.hint() = firstNotNullOfOrNull(HintTag::parse)

/** The first `mission` tag. A listing must not carry more than one; extras are ignored. */
fun TagArray.mission() = firstNotNullOfOrNull(MissionTag::parse)

fun TagArray.verificationKey() = firstNotNullOfOrNull(VerificationKeyTag::parse)

/** The first `F` tag. Only meaningful on a `first-to-find` listing; extras are ignored. */
fun TagArray.firstToFindWinner() = firstNotNullOfOrNull(FirstToFindWinnerTag::parse)

fun TagArray.cacheImages() = mapNotNull(ImageTag::parse)

/** The `r` tags: relays the owner prefers logs to be published to. */
fun TagArray.logRelays() = mapNotNull(RelayTag::parse)
