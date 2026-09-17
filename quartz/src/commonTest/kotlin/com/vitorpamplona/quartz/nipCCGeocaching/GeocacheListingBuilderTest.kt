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
package com.vitorpamplona.quartz.nipCCGeocaching

import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheGeohash
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.rot13
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheSize
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheType
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.TypeModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The write path for kind 37516, plus the two helpers NIP-CC asks clients for. */
class GeocacheListingBuilderTest {
    private val verificationKey = "6805d4e5c0df48b4f76e2fdcb67a2acb1d97567b01c6fe17a236dc32f34f1c07"

    private fun Array<Array<String>>.values(name: String) = filter { it.isNotEmpty() && it[0] == name }.map { it[1] }

    @Test
    fun buildProducesTheTagsTheSpecRequires() {
        val template =
            GeocacheListingEvent.build(
                name = "First Treasure",
                description = "The first Nostr treasure",
                geohash = "u4xsu6ryb",
                difficulty = 1,
                terrain = 1,
                size = CacheSize.SMALL,
                type = CacheType.TRADITIONAL,
                hint = "In the branches",
                dTag = "first-treasure-1748619568668",
                createdAt = 1_748_619_568L,
            )

        assertEquals(GeocacheListingEvent.KIND, template.kind)
        assertEquals("The first Nostr treasure", template.content)
        assertEquals(listOf("first-treasure-1748619568668"), template.tags.values("d"))
        assertEquals(listOf("First Treasure"), template.tags.values("name"))
        assertEquals(listOf("1"), template.tags.values("D"))
        assertEquals(listOf("1"), template.tags.values("T"))
        assertEquals(listOf("small"), template.tags.values("S"))
        assertEquals(listOf("traditional"), template.tags.values("t"))
        assertEquals(listOf("In the branches"), template.tags.values("hint"))
    }

    @Test
    fun theGeohashLadderRunsThreeToNineCoarseToFine() {
        // The spec's own example publishes u4x .. u4xsu6ryb. A 1- or 2-character geohash spans
        // thousands of kilometres and is noise on the relay, so the ladder starts at 3.
        val template =
            GeocacheListingEvent.build(
                name = "n",
                description = "",
                geohash = "u4xsu6ryb",
                difficulty = 1,
                terrain = 1,
                size = CacheSize.SMALL,
            )

        assertEquals(
            listOf("u4x", "u4xs", "u4xsu", "u4xsu6", "u4xsu6r", "u4xsu6ry", "u4xsu6ryb"),
            template.tags.values("g"),
        )
    }

    @Test
    fun theLadderStopsAtNineEvenForAFinerGeohash() {
        // Seven rungs, 3 through 9 — the three extra characters of precision are dropped rather
        // than published, so the finest `g` tag is never finer than the band NIP-CC defines.
        assertEquals(7, GeocacheGeohash.ladder("u4xsu6rybxyz").size)
        assertEquals("u4x", GeocacheGeohash.ladder("u4xsu6rybxyz").first())
        assertEquals("u4xsu6ryb", GeocacheGeohash.ladder("u4xsu6rybxyz").last())
    }

    @Test
    fun aGeohashTooCoarseForTheBandProducesNoTags() {
        assertEquals(emptyList(), GeocacheGeohash.ladder("u4"))
        assertEquals(emptyList(), GeoHashTag.geoMipMap("u4", 3, 9))
    }

    @Test
    fun theBoundedMipMapLeavesTheOriginalOneAlone() {
        // The unbounded form feeds the geohash chat channels and must keep running
        // fine-to-coarse from one character.
        assertEquals(listOf("u4x", "u4", "u"), GeoHashTag.geoMipMap("u4x"))
    }

    @Test
    fun microCachesNeedOneMoreCharacterThanEveryoneElse() {
        // "Validate geohash precision meets minimum requirements (8+ characters, 9+ for micro
        // caches)". A 38m box does not find a film canister.
        assertTrue(GeocacheGeohash.isPreciseEnough("u4xsu6ry", CacheSize.REGULAR))
        assertFalse(GeocacheGeohash.isPreciseEnough("u4xsu6ry", CacheSize.MICRO))
        assertTrue(GeocacheGeohash.isPreciseEnough("u4xsu6ryb", CacheSize.MICRO))
        assertFalse(GeocacheGeohash.isPreciseEnough("u4xsu6r", CacheSize.REGULAR))
    }

    @Test
    fun anOutOfRangeRatingIsClampedRatherThanThrown() {
        val template =
            GeocacheListingEvent.build(
                name = "n",
                description = "",
                geohash = "u4xsu6ryb",
                difficulty = 9,
                terrain = 0,
                size = CacheSize.SMALL,
            )

        assertEquals(listOf("5"), template.tags.values("D"))
        assertEquals(listOf("1"), template.tags.values("T"))
    }

    @Test
    fun modifiersAndVerificationRoundTripThroughBuild() {
        val template =
            GeocacheListingEvent.build(
                name = "Aftermath",
                description = "linocut",
                geohash = "u4xsu6ryb",
                difficulty = 2,
                terrain = 2,
                size = CacheSize.SMALL,
                modifiers = listOf(TypeModifier.FIRST_TO_FIND, TypeModifier.ART),
                verificationPubKey = verificationKey,
            )

        val cache = GeocacheListingEvent("id", "a".repeat(64), 1L, template.tags, template.content, "sig")

        assertTrue(cache.isFirstToFind())
        assertTrue(cache.hasTypeModifier(TypeModifier.ART))
        assertEquals(verificationKey, cache.verificationKey())
        assertTrue(cache.isWellFormed())
    }

    @Test
    fun rot13IsItsOwnInverseAndTouchesOnlyAsciiLetters() {
        // The hint travels as plaintext; ROT13 is the display transform that stops a reader
        // spoiling themselves by scrolling past it.
        assertEquals("Va gur oenapurf", rot13("In the branches"))
        assertEquals("In the branches", rot13(rot13("In the branches")))
        assertEquals("123 !?-é中", rot13("123 !?-é中"))
        assertEquals("nomAZ", rot13("abzNM"))
    }

    @Test
    fun theHintAccessorOffersBothForms() {
        val cache = GeocacheListingEvent("id", "a".repeat(64), 1L, arrayOf(arrayOf("hint", "In the branches")), "", "sig")

        assertEquals("In the branches", cache.hint())
        assertEquals("Va gur oenapurf", cache.hintRot13())
        assertNull(GeocacheListingEvent("id", "a".repeat(64), 1L, emptyArray(), "", "sig").hintRot13())
    }
}
