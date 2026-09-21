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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.containsAllTagNamesWithValues
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashes
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheNameTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheSize
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheSizeTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheType
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.DifficultyTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.TerrainTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.TypeModifier
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A geocache listing (kind 37516), as defined by
 * [NIP-CC](https://github.com/nostr-protocol/nips/blob/master/CC.md).
 *
 * Addressable, so the owner keeps editing the same cache — that is how a cache gets archived,
 * how its hint is corrected, and how a first-to-find winner is locked in. `content` is the cache
 * description.
 *
 * Community history about the cache does *not* live here: found logs are kind
 * [com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent] and everything else
 * (did-not-find, notes, maintenance) is a NIP-22 comment rooted on this listing — see
 * [com.vitorpamplona.quartz.nipCCGeocaching.comment.GeocacheLogComment].
 *
 * Required tags: `d`, `name`, `g`, `D`, `T`, `S`. See [isWellFormed].
 */
@Immutable
class GeocacheListingEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope,
    SearchableEvent {
    // The hint and the mission are deliberately absent: a cache is found by walking to it, and a
    // search that matches on "in the branches" hands out the answer to anyone who types it.
    override fun indexableContent() = listOfNotNull(cacheName(), content).joinToString("\n")

    // The read path: the same fields indexableContent() joins, without building the join a scan
    // would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(cacheName())) return
        visitor.visit(content)
    }

    fun cacheName() = tags.cacheName()

    /** Every `g` tag as published, coarse-to-fine. */
    fun geohashes() = tags.geohashes()

    /** The finest `g` tag — the one that actually points at the cache. */
    fun location() = tags.geohashes().maxByOrNull { it.length }

    fun difficulty() = tags.difficulty()

    fun terrain() = tags.terrain()

    fun cacheSize() = tags.cacheSize()

    fun cacheSizeCode() = tags.cacheSizeCode()

    /** The `t` cache type, defaulting to `traditional` when the listing does not say. */
    fun cacheTypeCode() = tags.cacheTypeCode()

    fun cacheType() = tags.cacheType()

    fun isArchived() = tags.isArchived()

    fun typeModifiers() = tags.typeModifiers()

    fun typeModifierCodes() = tags.typeModifierCodes()

    fun hasTypeModifier(modifier: TypeModifier) = tags.hasTypeModifier(modifier)

    fun isFirstToFind() = tags.isFirstToFind()

    /** The `hint` tag exactly as published, in whichever form its author chose. */
    fun hintOnWire() = tags.hint()

    /** The hint form safe to show before the reader asks. See [HintObfuscation]. */
    fun hintHidden() = tags.hintHidden()

    /** The hint form the reader gets when they ask. See [HintObfuscation]. */
    fun hintRevealed() = tags.hintRevealed()

    fun mission() = tags.mission()

    fun hasMission() = tags.mission() != null

    fun verificationKey() = tags.verificationKey()

    /** Whether finders can prove physical presence at this cache. */
    fun requiresVerification() = tags.verificationKey() != null

    /**
     * The locked-in first-to-find winner, or null.
     *
     * Only honoured on a listing that carries the `first-to-find` modifier: NIP-CC scopes `F` to
     * that modifier, and an `F` on any other cache would silently invent a claim nobody made.
     */
    fun firstToFindWinner() = if (isFirstToFind()) tags.firstToFindWinner() else null

    fun images() = tags.cacheImages()

    fun logRelays() = tags.logRelays()

    fun isWellFormed() = tags.containsAllTagNamesWithValues(REQUIRED_FIELDS)

    companion object {
        const val KIND = 37516

        /**
         * A kind NIP-CC references once — the curation list accepts "kind 37516 or 37515" — but
         * never defines. Readers accept it; nothing here ever writes it.
         */
        const val LEGACY_KIND = 37515

        val REQUIRED_FIELDS =
            setOf(
                DTag.TAG_NAME,
                CacheNameTag.TAG_NAME,
                GeoHashTag.TAG_NAME,
                DifficultyTag.TAG_NAME,
                TerrainTag.TAG_NAME,
                CacheSizeTag.TAG_NAME,
            )

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            name: String,
            description: String,
            geohash: String,
            difficulty: Int,
            terrain: Int,
            size: CacheSize,
            type: CacheType? = null,
            modifiers: Collection<TypeModifier> = emptySet(),
            hint: String? = null,
            mission: String? = null,
            verificationPubKey: HexKey? = null,
            images: List<String>? = null,
            relays: List<NormalizedRelayUrl>? = null,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GeocacheListingEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, description, createdAt) {
            // The `g` ladder starts at 3 characters, so a coarser geohash produces no `g` tag at
            // all — and `g` is required. Without this the builder happily signs a listing that
            // fails its own isWellFormed(), and the caller finds out after publishing it.
            // This is the floor for a *well-formed* event; NIP-CC's submission rule is stricter
            // (8+, 9+ for micro) and is [GeocacheGeohash.isPreciseEnough]'s job at the composer.
            require(geohash.length >= GeocacheGeohash.MIN_TAGGED) {
                "a geocache needs a geohash of at least ${GeocacheGeohash.MIN_TAGGED} characters, got \"$geohash\""
            }

            dTag(dTag)
            cacheName(name)
            cacheLocation(geohash)
            difficulty(difficulty)
            terrain(terrain)
            cacheSize(size)

            type?.let { cacheType(it) }
            if (modifiers.isNotEmpty()) typeModifiers(modifiers)
            // Written rot13'd: it is what the reference client does, and it is the choice that
            // degrades safely — a reader assuming plaintext sees noise rather than the answer.
            hint?.let { hint(rot13(it)) }
            mission?.let { mission(it) }
            verificationPubKey?.let { verificationKey(it) }
            images?.let { cacheImages(it) }
            relays?.let { logRelays(it) }

            initializer()
        }
    }
}
