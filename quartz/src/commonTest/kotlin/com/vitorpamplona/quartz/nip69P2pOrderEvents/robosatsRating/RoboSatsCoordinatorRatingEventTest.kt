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
package com.vitorpamplona.quartz.nip69P2pOrderEvents.robosatsRating

import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.P2PKindFixtures
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Kind 31986 is RoboSats' coordinator rating, which has no written spec; Borkstr publishes NIP
 * compatibility reports on the same number. Only the RoboSats shape, with its coordinator token,
 * becomes a [RoboSatsCoordinatorRatingEvent].
 */
class RoboSatsCoordinatorRatingEventTest {
    private fun parse(json: String): Event = Event.fromJson(json)

    private fun build(vararg tags: Array<String>): Event = EventFactory.create("0".repeat(64), "1".repeat(64), 1L, RoboSatsCoordinatorRatingEvent.KIND, arrayOf(*tags), "", "")

    private val token = "ab".repeat(64)
    private val coordinator = "c".repeat(64)

    @Test
    fun kindIsKnownAndProbesAsTheRating() {
        assertTrue(EventFactory.isKnownKind(RoboSatsCoordinatorRatingEvent.KIND))
        assertIs<RoboSatsCoordinatorRatingEvent>(EventFactory.probe(RoboSatsCoordinatorRatingEvent.KIND))
    }

    @Test
    fun realRatingAccessors() {
        val rating = assertIs<RoboSatsCoordinatorRatingEvent>(parse(P2PKindFixtures.REAL_ROBOSATS_RATING))
        assertEquals("f2d4855df39a7db6196666e8469a07a131cddc08dcaa744a344343ffcf54a10c", rating.coordinator())
        assertEquals(1.0, rating.rating())
        assertEquals("lake", rating.coordinatorAlias())
        assertEquals("138621", rating.orderId())
        assertEquals(
            "578f47cb33da9bf538db09c6eef357d777523b640898e5f95b28afaa3f5c3701309b743243669cf695dc4e8640af0df59cadd04e2bf08903e9009a405de223b0",
            rating.coordinatorToken(),
        )
        assertEquals("96342850bbc9cb0a9ac67d6880c4745971aa19881a3403a297b7330c332ba3cb138621", rating.coordinatorTokenMessage())
        assertFalse(parse(P2PKindFixtures.REAL_ROBOSATS_RATING) is SearchableEvent)
    }

    @Test
    fun theTokenIsTheCoordinatorsSignatureOverTheMessage() {
        // What RoboSats' verifyCoordinatorToken checks: BIP-340 over the unhashed UTF-8 message.
        val rating = assertIs<RoboSatsCoordinatorRatingEvent>(parse(P2PKindFixtures.REAL_ROBOSATS_RATING))
        assertTrue(rating.verifyCoordinatorToken())
    }

    @Test
    fun aTokenForAnotherRobotOrOrderDoesNotVerify() {
        val real = assertIs<RoboSatsCoordinatorRatingEvent>(parse(P2PKindFixtures.REAL_ROBOSATS_RATING))
        val realToken = real.coordinatorToken()!!
        val realCoordinator = real.coordinator()!!

        // The same token on another order of the same robot.
        val otherOrder =
            EventFactory.create<Event>(
                real.id,
                real.pubKey,
                real.createdAt,
                RoboSatsCoordinatorRatingEvent.KIND,
                arrayOf(arrayOf("sig", realToken), arrayOf("d", "lake:138622"), arrayOf("p", realCoordinator), arrayOf("rating", "1")),
                "",
                "",
            )
        assertFalse(assertIs<RoboSatsCoordinatorRatingEvent>(otherOrder).verifyCoordinatorToken())

        // The same token copied by another robot.
        assertFalse(assertIs<RoboSatsCoordinatorRatingEvent>(build(arrayOf("sig", realToken), arrayOf("d", "lake:138621"), arrayOf("p", realCoordinator))).verifyCoordinatorToken())

        // No order id: nothing to vouch for.
        assertFalse(assertIs<RoboSatsCoordinatorRatingEvent>(build(arrayOf("sig", token), arrayOf("p", coordinator))).verifyCoordinatorToken())
    }

    @Test
    fun theCoordinatorIsTheOnlyEdge() {
        val rating = assertIs<RoboSatsCoordinatorRatingEvent>(parse(P2PKindFixtures.REAL_ROBOSATS_RATING))
        assertEquals(listOf("f2d4855df39a7db6196666e8469a07a131cddc08dcaa744a344343ffcf54a10c"), rating.linkedPubKeys())
        assertEquals(emptyList(), rating.pubKeyHints())
    }

    @Test
    fun borkstrReportsAreNotCoordinatorRatings() {
        val report = parse(P2PKindFixtures.REAL_BORKSTR_REPORT)
        assertIs<UnrecognizedKind31986Event>(report)
        assertIs<AddressableEvent>(report)
        assertFalse(report is SearchableEvent)
    }

    @Test
    fun discriminatorNeedsAWellFormedTokenAndACoordinator() {
        assertIs<RoboSatsCoordinatorRatingEvent>(build(arrayOf("sig", token), arrayOf("p", coordinator)))
        assertEquals(UnrecognizedKind31986Event::class, build(arrayOf("p", coordinator), arrayOf("rating", "1"))::class)
        assertEquals(UnrecognizedKind31986Event::class, build(arrayOf("sig", token))::class)
        assertEquals(UnrecognizedKind31986Event::class, build(arrayOf("sig", "abc"), arrayOf("p", coordinator))::class)
        assertEquals(UnrecognizedKind31986Event::class, build(arrayOf("sig", "zz".repeat(64)), arrayOf("p", coordinator))::class)
        assertEquals(UnrecognizedKind31986Event::class, build(arrayOf("sig", token), arrayOf("p", "short"))::class)
    }

    @Test
    fun malformedTagsReadAsNull() {
        val rating =
            assertIs<RoboSatsCoordinatorRatingEvent>(
                build(arrayOf("sig", token), arrayOf("p", coordinator), arrayOf("d", "no-separator"), arrayOf("rating", "5")),
            )
        // A 5 is a star count, not RoboSats' 0..1 fraction: rejected, not clamped.
        assertNull(rating.rating())
        assertNull(rating.coordinatorAlias())
        assertNull(rating.orderId())
        assertEquals("1".repeat(64), rating.coordinatorTokenMessage())

        val noD = assertIs<RoboSatsCoordinatorRatingEvent>(build(arrayOf("sig", token), arrayOf("p", coordinator), arrayOf("rating", "x")))
        assertNull(noD.rating())
        assertNull(noD.coordinatorAlias())
    }

    @Test
    fun buildRoundTrips() {
        val template =
            RoboSatsCoordinatorRatingEvent.build(
                coordinatorAlias = "temple",
                orderId = "96770",
                coordinator = coordinator,
                rating = 0.8,
                coordinatorToken = token,
                createdAt = 1791399469L,
            )
        val rating = assertIs<RoboSatsCoordinatorRatingEvent>(EventFactory.create<Event>("0".repeat(64), "1".repeat(64), template.createdAt, template.kind, template.tags, template.content, ""))
        assertEquals("temple", rating.coordinatorAlias())
        assertEquals("96770", rating.orderId())
        assertEquals(coordinator, rating.coordinator())
        assertEquals(0.8, rating.rating())
        assertEquals(token, rating.coordinatorToken())
        assertEquals("temple:96770", rating.dTag())
    }
}
