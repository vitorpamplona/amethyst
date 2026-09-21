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
import com.vitorpamplona.quartz.nipCCGeocaching.listing.HintObfuscation
import com.vitorpamplona.quartz.nipCCGeocaching.listing.rot13
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheSize
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheType
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.TypeModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        // Published rot13'd: the reference client does it, and it is the choice that fails
        // safe — a reader assuming plaintext sees noise rather than the answer.
        assertEquals(listOf("Va gur oenapurf"), template.tags.values("hint"))
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
    fun aGeohashTooCoarseToTagIsRefusedRatherThanSignedWithoutALocation() {
        // The ladder starts at 3 characters, so a coarser geohash yields no `g` tag at all — and
        // `g` is required. Silently signing that produces a listing that fails its own
        // isWellFormed(), which the caller only discovers after publishing it.
        listOf("", "u", "u4").forEach { tooCoarse ->
            assertFailsWith<IllegalArgumentException>("<$tooCoarse> should not build") {
                GeocacheListingEvent.build(
                    name = "n",
                    description = "",
                    geohash = tooCoarse,
                    difficulty = 1,
                    terrain = 1,
                    size = CacheSize.SMALL,
                )
            }
        }
    }

    @Test
    fun theCoarsestBuildableCacheIsStillWellFormed() {
        val template =
            GeocacheListingEvent.build(
                name = "n",
                description = "",
                geohash = "u4x",
                difficulty = 1,
                terrain = 1,
                size = CacheSize.SMALL,
            )
        val cache = GeocacheListingEvent("id", "a".repeat(64), 1L, template.tags, template.content, "sig")

        assertEquals(listOf("u4x"), template.tags.values("g"))
        assertTrue(cache.isWellFormed())
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
    fun theHiddenHintIsTheFormThatReadsLessLikeProse() {
        // Publishers disagree about which form goes on the wire, so the reader must not be
        // handed the readable one either way round. Both of these carry the same hint; only
        // the encoding differs.
        val encoded = GeocacheListingEvent("id", "a".repeat(64), 1L, arrayOf(arrayOf("hint", "Va gur oenapurf")), "", "sig")
        val plain = GeocacheListingEvent("id", "a".repeat(64), 1L, arrayOf(arrayOf("hint", "In the branches")), "", "sig")

        assertEquals("Va gur oenapurf", encoded.hintOnWire())
        assertEquals("Va gur oenapurf", encoded.hintHidden())
        assertEquals("In the branches", encoded.hintRevealed())

        assertEquals("In the branches", plain.hintOnWire())
        assertEquals("Va gur oenapurf", plain.hintHidden())
        assertEquals("In the branches", plain.hintRevealed())

        assertNull(GeocacheListingEvent("id", "a".repeat(64), 1L, emptyArray(), "", "sig").hintRevealed())
    }

    @Test
    fun theProseTestSurvivesTheVowelTrap() {
        // The obvious heuristic — count vowels — is backwards here, and quietly so. ROT13 maps
        // n→a, r→e, h→u and b→o, all common in English, so ciphertext usually has MORE vowels
        // than its plaintext: "In the branches" has 4 of 13, "Va gur oenapurf" has 6 of 13.
        // These are the real hints that broke it, taken off the relays.
        listOf(
            "In the branches",
            "Under the bench.",
            "Third tree",
            "Look up",
            "Small",
            "Hiding in the top of the wall",
            "Between the tree and the lathe cactus",
        ).forEach { plain ->
            assertTrue(HintObfuscation.isProse(plain), "<$plain> should read as English")
            assertTrue(!HintObfuscation.isProse(rot13(plain)), "<${rot13(plain)}> should not read as English")
            assertEquals(plain, HintObfuscation.revealed(rot13(plain)), "encoded <$plain> should decode")
            assertEquals(plain, HintObfuscation.revealed(plain), "plain <$plain> should stay put")
        }
    }

    @Test
    fun theHeuristicOnlySpeaksEnglishAndBothReadingsStayReachable() {
        // The guess is English-only, so a plaintext hint in another language scores as
        // ciphertext and "reveals" to noise. That is not fixable with a bigger table — it is why
        // hidden() and revealed() are always the same two strings in some order, so a UI that
        // offers both can still get a reader to their own hint.
        listOf("Bajo el banco", "Sob o banco de madeira", "Unter der Bank").forEach { plain ->
            assertEquals(
                setOf(plain, rot13(plain)),
                setOf(HintObfuscation.hidden(plain), HintObfuscation.revealed(plain)),
                "<$plain>: both readings must stay reachable even when the guess is wrong",
            )
        }
    }

    @Test
    fun theRarestLettersStillScoreApart() {
        // A frequency floor would flatten the bottom of the table together, and the comparison
        // between a form and its rotation stops meaning anything for hints built from rare
        // letters. `q`, `z` and `x` are the three the table puts lowest.
        val q = HintObfuscation.englishScore("q")
        val z = HintObfuscation.englishScore("z")
        val x = HintObfuscation.englishScore("x")

        assertTrue(q != z, "q and z score identically — a floor is swallowing them")
        assertTrue(z != x, "z and x score identically — a floor is swallowing them")
        assertTrue(z < HintObfuscation.englishScore("e"), "the rarest letter should score below the commonest")
    }

    @Test
    fun theTwoHintFormsAreAlwaysEachOthersRotation() {
        // Whatever the heuristic decides, it only ever chooses between these two — it never
        // invents or drops text.
        listOf("In the branches", "Va gur oenapurf", "Small", "\u00dfaum", "123 !?").forEach { onWire ->
            assertEquals(onWire, rot13(rot13(onWire)))
            assertEquals(
                setOf(onWire, rot13(onWire)),
                setOf(HintObfuscation.hidden(onWire), HintObfuscation.revealed(onWire)),
                "<$onWire>",
            )
        }
    }

    @Test
    fun buildTakesPlaintextAndRoundTripsThroughTheWireForm() {
        val template =
            GeocacheListingEvent.build(
                name = "n",
                description = "",
                geohash = "u4xsu6ryb",
                difficulty = 1,
                terrain = 1,
                size = CacheSize.SMALL,
                hint = "In the branches",
            )
        val cache = GeocacheListingEvent("id", "a".repeat(64), 1L, template.tags, template.content, "sig")

        assertEquals("In the branches", cache.hintRevealed())
    }
}
