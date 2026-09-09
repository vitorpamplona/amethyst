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
package com.vitorpamplona.quartz.experimental.ratings

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelayReviewEventTest {
    private fun review(vararg tags: Array<String>) = RelayReviewEvent("id", "pk", 0L, arrayOf(*tags), "", "sig")

    @Test
    fun kindIsRegisteredWithTheEventFactory() {
        assertTrue(EventFactory.isKnownKind(RelayReviewEvent.KIND))
    }

    @Test
    fun parsesAnOverallScoreAndPerCategoryScores() {
        val event =
            review(
                arrayOf("d", "wss://relay.example.com/"),
                arrayOf("rating", "0.8"),
                arrayOf("rating", "1", "speed"),
                arrayOf("rating", "0.2", "content"),
            )

        assertEquals(0.8, event.overallRating())
        assertEquals(4.0, event.stars())
        assertEquals(2, event.categoryRatings().size)
        assertEquals(listOf("speed", "content"), event.categoryRatings().map { it.category })
        assertEquals(listOf(1.0, 0.2), event.categoryRatings().map { it.value })
    }

    @Test
    fun theOverallScoreIsTheTagWithNoCategory() {
        // A review that only scores aspects has no overall number to show.
        val event = review(arrayOf("d", "wss://r.example/"), arrayOf("rating", "1", "speed"))

        assertNull(event.overallRating())
        assertNull(event.stars())
        assertEquals(1, event.categoryRatings().size)
    }

    @Test
    fun rejectsScoresOutsideTheNormalizedRange() {
        // Unlike kind 34259 there is no raw 1..5 convention here, so 4 is malformed, not four stars.
        assertNull(review(arrayOf("d", "wss://r.example/"), arrayOf("rating", "4")).overallRating())
        assertNull(review(arrayOf("d", "wss://r.example/"), arrayOf("rating", "-1")).overallRating())
        assertNull(review(arrayOf("d", "wss://r.example/"), arrayOf("rating", "nope")).overallRating())
    }

    @Test
    fun acceptsBothBoundaries() {
        assertEquals(0.0, review(arrayOf("rating", "0")).overallRating())
        assertEquals(1.0, review(arrayOf("rating", "1")).overallRating())
        assertEquals(5.0, review(arrayOf("rating", "1")).stars())
    }

    @Test
    fun readsTheRelayFromDAndFallsBackToARelayTag() {
        assertEquals("wss://relay.example.com/", review(arrayOf("d", "wss://relay.example.com/")).relayUrl())
        assertEquals("wss://other.example/", review(arrayOf("relay", "wss://other.example/")).relayUrl())
        // `d` is the addressable identity, so it wins when both are present.
        assertEquals(
            "wss://a.example/",
            review(arrayOf("d", "wss://a.example/"), arrayOf("relay", "wss://b.example/")).relayUrl(),
        )
        assertNull(review(arrayOf("rating", "0.5")).relayUrl())
    }

    @Test
    fun normalizesTheRelayUrl() {
        assertEquals("wss://relay.example.com/", review(arrayOf("d", "relay.example.com")).relay()?.url)
    }

    @Test
    fun parsesAWholeEventThroughTheFactory() {
        val json =
            """
            {"id":"${"a".repeat(64)}","pubkey":"${"b".repeat(64)}","created_at":1788807940,"kind":31987,
             "tags":[["d","wss://relay.example.com/"],["rating","0.6"],["rating","1","speed"]],
             "content":"Fast, but drops old events.","sig":"${"c".repeat(128)}"}
            """.trimIndent()

        val event = Event.fromJson(json)
        assertTrue(event is RelayReviewEvent, "EventFactory produced ${event::class.simpleName}")
        assertEquals(3.0, event.stars())
        assertEquals("Fast, but drops old events.", event.indexableContent())
    }

    @Test
    fun buildsTheShapeThePublishersEmit() {
        val template =
            RelayReviewEvent.build(
                relayUrl = "wss://relay.example.com/",
                stars = 4,
                review = "Solid",
                categories = mapOf("speed" to 5),
            )

        val ratings = template.tags.filter { it[0] == "rating" }
        assertEquals("wss://relay.example.com/", template.tags.first { it[0] == "d" }[1])
        assertEquals("0.800", ratings[0][1])
        assertEquals(2, ratings[0].size, "the overall score carries no category")
        assertEquals(listOf("1.000", "speed"), ratings[1].drop(1))
        assertEquals("Solid", template.content)
    }
}
