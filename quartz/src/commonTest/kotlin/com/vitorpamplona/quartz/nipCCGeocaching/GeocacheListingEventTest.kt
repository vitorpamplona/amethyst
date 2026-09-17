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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheSize
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheType
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.TypeModifier
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.TypeModifierCategory
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kind 37516, checked against the examples in NIP-CC itself. */
class GeocacheListingEventTest {
    private val owner = "0461fcbecc4c3374439932d6b8f11269ccdb7cc973ad7a50ae362db135a474dd"
    private val verificationKey = "6805d4e5c0df48b4f76e2fdcb67a2acb1d97567b01c6fe17a236dc32f34f1c07"

    private fun listing(vararg tags: Array<String>) = GeocacheListingEvent("id", owner, 1_748_619_568L, arrayOf(*tags), "a cache", "sig")

    /** The "Basic Cache" example from NIP-CC, tag for tag. */
    private fun basicCacheTags(): TagArray =
        arrayOf(
            arrayOf("d", "first-treasure-1748619568668"),
            arrayOf("name", "First Treasure"),
            arrayOf("g", "u4x"),
            arrayOf("g", "u4xs"),
            arrayOf("g", "u4xsu"),
            arrayOf("g", "u4xsu6"),
            arrayOf("g", "u4xsu6r"),
            arrayOf("g", "u4xsu6ry"),
            arrayOf("g", "u4xsu6ryb"),
            arrayOf("D", "1"),
            arrayOf("T", "1"),
            arrayOf("S", "small"),
            arrayOf("t", "traditional"),
            arrayOf("hint", "In the branches"),
            arrayOf("image", "https://blossom.primal.net/74efe.jpg"),
        )

    @Test
    fun theKindIsRegisteredWithTheEventFactory() {
        // Without this the class above is dead code: 37516 parses as a plain Event and no
        // accessor on it is ever reached.
        assertTrue(EventFactory.isKnownKind(GeocacheListingEvent.KIND))
        assertTrue(EventFactory.create<Event>("id", owner, 1L, GeocacheListingEvent.KIND, emptyArray(), "", "") is GeocacheListingEvent)
    }

    @Test
    fun parsesTheSpecsBasicCacheExample() {
        val cache = listing(*basicCacheTags())

        assertEquals("first-treasure-1748619568668", cache.dTag())
        assertEquals("First Treasure", cache.cacheName())
        assertEquals(1, cache.difficulty())
        assertEquals(1, cache.terrain())
        assertEquals(CacheSize.SMALL, cache.cacheSize())
        assertEquals(CacheType.TRADITIONAL, cache.cacheType())
        assertEquals("In the branches", cache.hint())
        assertEquals(listOf("https://blossom.primal.net/74efe.jpg"), cache.images())
        assertEquals("u4xsu6ryb", cache.location())
        assertEquals(7, cache.geohashes().size)
        assertTrue(cache.isWellFormed())
    }

    @Test
    fun parsesTheSpecsVerifiedCacheExample() {
        val cache =
            listing(
                arrayOf("d", "verified-treasure-1748619568669"),
                arrayOf("name", "Verified Treasure"),
                arrayOf("g", "u4xsu6ry"),
                arrayOf("D", "3"),
                arrayOf("T", "2"),
                arrayOf("S", "small"),
                arrayOf("t", "traditional"),
                arrayOf("hint", "Look for the secret code"),
                arrayOf("verification", verificationKey),
            )

        assertEquals(verificationKey, cache.verificationKey())
        assertTrue(cache.requiresVerification())
    }

    @Test
    fun parsesTheSpecsFirstToFindArtExample() {
        val cache =
            listing(
                arrayOf("d", "linocut-aftermath-1748619568671"),
                arrayOf("name", "Aftermath (Linocut #1)"),
                arrayOf("g", "u4xsu6ry"),
                arrayOf("D", "2"),
                arrayOf("T", "2"),
                arrayOf("S", "small"),
                arrayOf("t", "traditional"),
                arrayOf("n", "first-to-find"),
                arrayOf("n", "art"),
                arrayOf("verification", verificationKey),
            )

        assertTrue(cache.isFirstToFind())
        assertTrue(cache.hasTypeModifier(TypeModifier.ART))
        assertEquals(
            mapOf(
                TypeModifierCategory.CLAIM_SEMANTICS to TypeModifier.FIRST_TO_FIND,
                TypeModifierCategory.PRIZE_NATURE to TypeModifier.ART,
            ),
            cache.typeModifiers(),
        )
    }

    @Test
    fun parsesTheSpecsKeyQuestExample() {
        val cache =
            listing(
                arrayOf("d", "key-quest-treasure-1748619568670"),
                arrayOf("name", "Riddle of the Old Oak"),
                arrayOf("g", "u4xsu6ry"),
                arrayOf("D", "4"),
                arrayOf("T", "2"),
                arrayOf("S", "small"),
                arrayOf("t", "mystery"),
                arrayOf("mission", "Bring a token of nature you found along the way"),
                arrayOf("verification", verificationKey),
            )

        assertEquals(CacheType.MYSTERY, cache.cacheType())
        assertTrue(cache.hasMission())
        assertEquals("Bring a token of nature you found along the way", cache.mission())
    }

    @Test
    fun theCacheTypeDefaultsToTraditional() {
        // "The type of cache (`t`) is optional and defaults to `traditional` if not specified."
        val cache = listing(arrayOf("d", "x"), arrayOf("name", "n"), arrayOf("g", "u4xsu6ryb"), arrayOf("D", "1"), arrayOf("T", "1"), arrayOf("S", "micro"))

        assertEquals(CacheType.TRADITIONAL, cache.cacheType())
        assertEquals("traditional", cache.cacheTypeCode())
    }

    @Test
    fun aClientDefinedCacheTypeSurvivesAsItsCode() {
        // "Cache types are determined by individual clients" — an unknown type must not read as
        // `traditional`, which would hide a puzzle cache behind a walk-up label.
        val cache = listing(arrayOf("t", "earthcache"))

        assertNull(cache.cacheType())
        assertEquals("earthcache", cache.cacheTypeCode())
    }

    @Test
    fun archivedIsALifecycleMarkerNotACacheType() {
        // `t` carries both on this kind. Reading `archived` as the type would make every retired
        // cache an unknown type, and reading the type as a lifecycle state would un-retire it.
        val cache = listing(arrayOf("t", "mystery"), arrayOf("t", "archived"))

        assertTrue(cache.isArchived())
        assertEquals(CacheType.MYSTERY, cache.cacheType())
    }

    @Test
    fun aCacheWithNoArchivedTagIsNotArchived() {
        assertFalse(listing(arrayOf("t", "traditional")).isArchived())
    }

    @Test
    fun difficultyAndTerrainRejectValuesOutsideOneToFive() {
        assertNull(listing(arrayOf("D", "0")).difficulty())
        assertNull(listing(arrayOf("D", "6")).difficulty())
        assertNull(listing(arrayOf("D", "")).difficulty())
        assertNull(listing(arrayOf("D", "hard")).difficulty())
        assertNull(listing(arrayOf("T", "-1")).terrain())
        assertEquals(5, listing(arrayOf("D", "5")).difficulty())
        assertEquals(1, listing(arrayOf("T", "1")).terrain())
    }

    @Test
    fun anUnknownSizeIsNotSilentlyACategory() {
        val cache = listing(arrayOf("S", "nano"))

        assertNull(cache.cacheSize())
        assertEquals("nano", cache.cacheSizeCode())
    }

    @Test
    fun onlyOneModifierPerCategoryCountsAndTheFirstWins() {
        // NIP-CC rule 2. Both values here are CLAIM_SEMANTICS-shaped in a future where more
        // exist; today only one category has two candidates, so the rule is exercised with
        // a repeat.
        val cache = listing(arrayOf("n", "first-to-find"), arrayOf("n", "first-to-find"), arrayOf("n", "art"))

        assertEquals(2, cache.typeModifiers().size)
    }

    @Test
    fun anUnknownModifierIsIgnoredRatherThanFatal() {
        // NIP-CC rule 4, the forward-compatibility rule: a listing using a modifier from a future
        // revision must still parse as a cache.
        val cache = listing(*basicCacheTags(), arrayOf("n", "time-limited"), arrayOf("n", "art"))

        assertTrue(cache.isWellFormed())
        assertEquals(mapOf(TypeModifierCategory.PRIZE_NATURE to TypeModifier.ART), cache.typeModifiers())
        assertEquals(listOf("time-limited", "art"), cache.typeModifierCodes())
    }

    @Test
    fun theFTagIsOnlyHonouredOnAFirstToFindCache() {
        // "Only valid when the treasure carries the `first-to-find` `n` modifier." Honouring it
        // anywhere else invents an exclusive claim on a cache that never had one.
        val winner = "b".repeat(64)

        assertNull(listing(arrayOf("F", winner)).firstToFindWinner())
        assertEquals(winner, listing(arrayOf("n", "first-to-find"), arrayOf("F", winner)).firstToFindWinner())
    }

    @Test
    fun aMalformedPubKeyIsNotAVerificationKey() {
        assertNull(listing(arrayOf("verification", "not-hex")).verificationKey())
        assertNull(listing(arrayOf("verification", "")).verificationKey())
        // isHex64 does not check the length itself; a longer string must still be rejected.
        assertNull(listing(arrayOf("verification", "a".repeat(70))).verificationKey())
        assertEquals(verificationKey, listing(arrayOf("verification", verificationKey)).verificationKey())
    }

    @Test
    fun aListingMissingARequiredTagIsNotWellFormed() {
        GeocacheListingEvent.REQUIRED_FIELDS.forEach { missing ->
            val tags = basicCacheTags().filterNot { it[0] == missing }.toTypedArray()
            assertFalse(listing(*tags).isWellFormed(), "a listing without `$missing` should not be well formed")
        }
    }

    @Test
    fun theAddressIsTheKindPubkeyAndDTag() {
        val cache = listing(*basicCacheTags())

        assertEquals("37516:$owner:first-treasure-1748619568668", cache.address().toValue())
    }

    @Test
    fun aListingRoundTripsThroughJson() {
        val cache = listing(*basicCacheTags())
        val reparsed = Event.fromJson(cache.toJson())

        assertTrue(reparsed is GeocacheListingEvent)
        assertEquals("First Treasure", reparsed.cacheName())
        assertEquals(CacheSize.SMALL, reparsed.cacheSize())
    }
}
