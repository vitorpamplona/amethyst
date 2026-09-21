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

import com.vitorpamplona.quartz.TestResourceLoader
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.HintObfuscation
import com.vitorpamplona.quartz.nipCCGeocaching.listing.rot13
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.TypeModifier
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Our parsers against events other people actually published.
 *
 * The corpus in `nipcc.interop.json` was captured from relay.damus.io, nos.lol,
 * relay.primal.net and nostr.wine, and is pinned rather than re-fetched so the suite stays
 * offline and deterministic. It was picked to cover what the wild does and the spec's examples
 * do not: an owner-archived cache, a locked-in `F` winner, an `art` modifier, a cache type no
 * version of this code knows, tags we ignore entirely, both hint conventions, and a found log
 * carrying a real embedded kind 7517.
 *
 * The reason this file exists rather than more hand-written fixtures: reading NIP-CC's tag table
 * literally produced a `hint` implementation that was backwards against every publisher on the
 * network, and no amount of testing against the spec's own examples would have caught it.
 */
class NipCCInteropTest {
    private val corpus = Json.parseToJsonElement(TestResourceLoader().loadString("nipcc.interop.json")).jsonObject

    private fun eventsIn(field: String) =
        corpus[field]!!.jsonArray.map {
            Event.fromJson(it.toString()) to it.jsonObject["_why"]!!.toString().trim('"')
        }

    private val listings get() = eventsIn("listings")
    private val foundLogs get() = eventsIn("foundLogs")

    @Test
    fun everyCapturedListingParsesAsAGeocache() {
        listings.forEach { (event, why) ->
            assertTrue(event is GeocacheListingEvent, "$why: kind ${event.kind} did not parse as a listing")
            assertTrue(event.isWellFormed(), "$why: a real, published cache failed isWellFormed()")
            assertNotNull(event.cacheName(), "$why: no name")
            assertNotNull(event.location(), "$why: no geohash")
            assertNotNull(event.difficulty(), "$why: D did not parse")
            assertNotNull(event.terrain(), "$why: T did not parse")
        }
    }

    @Test
    fun theGeohashLadderInTheWildIsExactlyTheBandWePublish() {
        // Every listing sampled carried `g` at precisions 3 through 9 — the same band
        // GeocacheGeohash.ladder emits. A reader that only looked at one precision, or a writer
        // that emitted 1..9, would be the odd one out.
        listings.forEach { (event, why) ->
            val lengths = (event as GeocacheListingEvent).geohashes().map { it.length }.sorted()
            assertEquals((3..9).toList(), lengths, "$why: unexpected geohash ladder")
        }
    }

    @Test
    fun noCapturedCacheShowsItsHintBeforeTheReaderAsks() {
        // The whole point of the hint mechanism, checked against real publishers rather than
        // against an assumed convention. The corpus deliberately contains both: this NIP is
        // self-contradictory about whether `hint` is plaintext or ROT13, and the network split
        // roughly in half along author lines. Either fixed choice spoils about half of these.
        val hinted =
            listings.mapNotNull { (event, why) ->
                (event as GeocacheListingEvent).hintOnWire()?.let { Triple(event, it, why) }
            }
        assertTrue(hinted.isNotEmpty(), "the corpus should carry at least one hinted cache")

        hinted.forEach { (event, onWire, why) ->
            val hidden = event.hintHidden()!!
            val revealed = event.hintRevealed()!!

            // Only ever the two rotations of what was published — never invented text.
            assertEquals(setOf(onWire, rot13(onWire)), setOf(hidden, revealed), "$why")

            // And the one shown unasked is the one that reads less like prose.
            assertTrue(
                HintObfuscation.englishScore(hidden) <= HintObfuscation.englishScore(revealed),
                "$why: the hidden form <$hidden> reads more like English than the revealed one <$revealed>",
            )
        }
    }

    @Test
    fun theCorpusReallyDoesContainBothHintConventions() {
        // Guards the test above from quietly becoming vacuous: if a future corpus refresh
        // happened to capture only one convention, the interesting case would stop being
        // covered and nothing else would say so.
        val onWire = listings.mapNotNull { (event, _) -> (event as GeocacheListingEvent).hintOnWire() }
        val prose = onWire.count { HintObfuscation.isProse(it) }

        assertTrue(prose > 0, "no plaintext-on-wire hint in the corpus — refresh it from relays")
        assertTrue(prose < onWire.size, "no rot13-on-wire hint in the corpus — refresh it from relays")
    }

    @Test
    fun anOwnerArchivedCacheReadsAsArchivedWithItsTypeIntact() {
        val (event, why) = listings.first { it.second.contains("archived") }
        val cache = event as GeocacheListingEvent

        assertTrue(cache.isArchived(), "$why: an archived cache did not read as archived")
        // `t` carries both meanings; `archived` must not have eaten the cache type.
        assertTrue(cache.cacheTypeCode() != "archived", "$why: the archived marker was read as the cache type")
    }

    @Test
    fun aRealFirstToFindLockInIsAttributedToItsFTag() {
        val (event, why) = listings.first { it.second.contains("first-to-find") }
        val cache = event as GeocacheListingEvent

        assertTrue(cache.isFirstToFind(), "$why: not read as first-to-find")
        assertNotNull(cache.firstToFindWinner(), "$why: the published F winner did not parse")
        assertEquals(64, cache.firstToFindWinner()!!.length)
    }

    @Test
    fun anArtCacheKeepsBothOfItsModifiers() {
        val (event, why) = listings.first { it.second.contains("art") }

        assertTrue((event as GeocacheListingEvent).hasTypeModifier(TypeModifier.ART), "$why: the art modifier was dropped")
    }

    @Test
    fun aCacheTypeWeDoNotKnowSurvivesAsItsCode() {
        // Forward compatibility, checked against a type a real client invented rather than one
        // this test made up.
        val (event, why) = listings.first { it.second.contains("client-defined") }
        val cache = event as GeocacheListingEvent

        assertNull(cache.cacheType(), "$why: an unknown type resolved to a known one")
        assertTrue(cache.cacheTypeCode().isNotEmpty(), "$why: the raw type code was lost")
        assertTrue(cache.isWellFormed(), "$why: an unknown type should not make the cache unreadable")
    }

    @Test
    fun tagsWeDoNotModelDoNotDisturbTheOnesWeDo() {
        // Real listings carry `client`, `expiration`, NIP-32 `L`/`l` labels, `content-warning`,
        // and payout hints this package knows nothing about.
        val (event, why) = listings.first { it.second.contains("ignore") }
        val cache = event as GeocacheListingEvent

        assertTrue(cache.isWellFormed(), "$why: unmodelled tags broke the cache")
        assertNotNull(cache.cacheSize(), "$why: size did not parse")
    }

    @Test
    fun aListingWithNoTypeTagDefaultsToTraditional() {
        val (event, why) = listings.first { it.second.contains("no t tag") }

        assertEquals("traditional", (event as GeocacheListingEvent).cacheTypeCode(), "$why")
    }

    @Test
    fun everyCapturedFoundLogResolvesToTheCacheItNames() {
        foundLogs.forEach { (event, why) ->
            assertTrue(event is GeocacheFoundLogEvent, "$why: did not parse as a found log")
            val cache = event.geocache()
            assertNotNull(cache, "$why: the `a` tag did not resolve")
            assertEquals(GeocacheListingEvent.KIND, cache.kind, "$why")
        }
    }

    @Test
    fun aRealEmbeddedVerificationParsesAndNamesItsFinder() {
        // The composite `a` tag — `<finder-hex>:<naddr>` — is the shape no generic NIP-01 reader
        // handles, so this is the one worth checking against a real payload rather than one we
        // assembled ourselves.
        val (event, why) = foundLogs.first { it.second.contains("embedded") }
        val log = event as GeocacheFoundLogEvent

        assertTrue(log.hasVerificationAttached(), "$why: the cheap gate missed a real payload")

        val verification = log.embeddedVerification()
        assertNotNull(verification, "$why: a real embedded 7517 failed to parse")
        assertEquals(GeocacheVerificationEvent.KIND, verification.kind)

        val claim = verification.finderCache()
        assertNotNull(claim, "$why: the composite `a` tag failed to parse")
        assertEquals(log.pubKey, claim.finderPubKey, "$why: the verification names a different finder than the log's author")
        assertEquals(log.geocache(), claim.cache, "$why: the verification names a different cache than the log")
        assertTrue(verification.hasExpectedContent(), "$why: content did not match the static format")
    }
}
