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
package com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroRating

import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip40Expiration.expiration
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
 * Kind 38384 is Mostro's user rating, but Paygress heartbeats outnumber the ratings on it. The
 * factory must give a heartbeat no rating class at all.
 */
class MostroUserRatingEventTest {
    private fun parse(json: String): Event = Event.fromJson(json)

    private fun build(vararg tags: Array<String>): Event = EventFactory.create("0".repeat(64), "1".repeat(64), 1L, MostroUserRatingEvent.KIND, arrayOf(*tags), "", "")

    @Test
    fun kindIsKnownAndProbesAsTheRating() {
        assertTrue(EventFactory.isKnownKind(MostroUserRatingEvent.KIND))
        assertIs<MostroUserRatingEvent>(EventFactory.probe(MostroUserRatingEvent.KIND))
    }

    @Test
    fun realRatingAccessors() {
        val rating = assertIs<MostroUserRatingEvent>(parse(P2PKindFixtures.REAL_MOSTRO_RATING))
        assertEquals("217004c16c56ff07aa7709ae58e90ccc0d02ba1d2e096c35089055f654eba625", rating.ratedPubKey())
        assertEquals(11L, rating.totalReviews())
        assertEquals(4.7727272727272725, rating.totalRating())
        assertEquals(5L, rating.lastRating())
        assertEquals(5L, rating.maxRate())
        assertEquals(5L, rating.minRate())
        assertEquals(1766793600L, rating.since())
        assertEquals(284L, rating.days())
        assertEquals(1766793600L, rating.firstTradeDay())
        assertEquals(1799198814L, rating.expiration())
        // Live ratings carry no `y`.
        assertNull(rating.platform())
        assertNull(rating.instanceName())
        assertFalse(parse(P2PKindFixtures.REAL_MOSTRO_RATING) is SearchableEvent)
    }

    @Test
    fun ratedUserIsTheOnlyEdge() {
        val rating = assertIs<MostroUserRatingEvent>(parse(P2PKindFixtures.REAL_MOSTRO_RATING))
        assertEquals(listOf("217004c16c56ff07aa7709ae58e90ccc0d02ba1d2e096c35089055f654eba625"), rating.linkedPubKeys())
        assertEquals(emptyList(), rating.pubKeyHints())
    }

    @Test
    fun paygressHeartbeatsAreNeverRatings() {
        listOf(P2PKindFixtures.REAL_PAYGRESS_HEARTBEAT, P2PKindFixtures.SYNTHETIC_PAYGRESS_HEARTBEAT_WITH_P).forEach {
            val event = parse(it)
            assertIs<UnrecognizedKind38384Event>(event)
            assertFalse(event is MostroUserRatingEvent)
            // Still addressable, so replacement and `a`-tag deletion reach it.
            assertIs<AddressableEvent>(event)
            assertFalse(event is SearchableEvent)
            assertFalse(event is PubKeyHintProvider)
        }
    }

    @Test
    fun discriminatorNeedsTheRatingZOrTotalReviews() {
        assertIs<MostroUserRatingEvent>(build(arrayOf("z", "rating")))
        assertIs<MostroUserRatingEvent>(build(arrayOf("d", "x"), arrayOf("total_reviews", "3")))
        assertEquals(UnrecognizedKind38384Event::class, build(arrayOf("z", "order"))::class)
        assertEquals(UnrecognizedKind38384Event::class, build(arrayOf("total_reviews", ""))::class)
        assertEquals(UnrecognizedKind38384Event::class, build(arrayOf("z"))::class)
        assertEquals(UnrecognizedKind38384Event::class, build()::class)
    }

    @Test
    fun malformedTagsReadAsNull() {
        val rating =
            assertIs<MostroUserRatingEvent>(
                build(
                    arrayOf("d", "not-a-pubkey"),
                    arrayOf("z", "rating"),
                    arrayOf("total_reviews", "many"),
                    arrayOf("total_rating", "NaN"),
                    arrayOf("last_rating"),
                    arrayOf("max_rate", ""),
                    arrayOf("min_rate", "4.5"),
                    arrayOf("since", "yesterday"),
                ),
            )
        assertNull(rating.ratedPubKey())
        assertEquals(emptyList(), rating.linkedPubKeys())
        assertNull(rating.totalReviews())
        assertNull(rating.totalRating())
        assertNull(rating.lastRating())
        assertNull(rating.maxRate())
        assertNull(rating.minRate())
        assertNull(rating.since())
        assertNull(rating.firstTradeDay())
    }

    @Test
    fun firstTradeDayFallsBackToDays() {
        // Published an hour into day 10 with `days` = 3: the first trade was on day 7, and the
        // answer is that day's start, like `since`.
        val createdAt = 10L * 86400 + 3600
        val event =
            EventFactory.create<Event>(
                "0".repeat(64),
                "1".repeat(64),
                createdAt,
                MostroUserRatingEvent.KIND,
                arrayOf(arrayOf("z", "rating"), arrayOf("days", "3")),
                "",
                "",
            )
        val rating = assertIs<MostroUserRatingEvent>(event)
        assertEquals(7L * 86400, rating.firstTradeDay())
    }

    @Test
    fun buildRoundTrips() {
        val rated = "a".repeat(64)
        val template =
            MostroUserRatingEvent.build(
                ratedPubKey = rated,
                totalReviews = 4,
                totalRating = 4.5,
                lastRating = 5,
                maxRate = 5,
                minRate = 3,
                since = 1766793600L,
                expiration = 1799198814L,
                instanceName = "Test Instance",
                createdAt = 1791422814L,
            )
        assertEquals(MostroUserRatingEvent.KIND, template.kind)
        val rating = assertIs<MostroUserRatingEvent>(EventFactory.create<Event>("0".repeat(64), "1".repeat(64), template.createdAt, template.kind, template.tags, template.content, ""))
        assertEquals(rated, rating.ratedPubKey())
        assertEquals(4L, rating.totalReviews())
        assertEquals(4.5, rating.totalRating())
        assertEquals(5L, rating.lastRating())
        assertEquals(5L, rating.maxRate())
        assertEquals(3L, rating.minRate())
        assertEquals(1766793600L, rating.since())
        assertEquals(1799198814L, rating.expiration())
        assertEquals("mostro", rating.platform())
        assertEquals("Test Instance", rating.instanceName())
        assertEquals("rating", template.tags.first { it[0] == "z" }[1])
    }
}
