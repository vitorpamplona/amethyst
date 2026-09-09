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

import com.vitorpamplona.quartz.experimental.ratings.tags.RatingTag
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntityRatingEventTest {
    /**
     * A real five-star review of a kind-30040 publication, signed by the `imwald` client. Kept
     * verbatim (only the signature is genuine-but-irrelevant here) because every parsing decision
     * in [EntityRatingEvent] exists to handle exactly this shape.
     */
    private val wildEventJson =
        """
        {
          "id": "ea250acfc8863e3a4df3686eae1efa6f729a21b9f104bf71729b6d34c5de958d",
          "pubkey": "dd664d5e4016433a8cd69f005ae1480804351789b59de5af06276de65633d319",
          "created_at": 1788807940,
          "kind": 34259,
          "tags": [
            ["d", "books:30040:573634b648634cbad10f2451776089ea21090d9407f715e83c577b4611ae6edc:wuthering-heights"],
            ["m", "books"],
            ["rating", "1.000"],
            ["s", "5"],
            ["a", "30040:573634b648634cbad10f2451776089ea21090d9407f715e83c577b4611ae6edc:wuthering-heights"],
            ["A", "30040:573634b648634cbad10f2451776089ea21090d9407f715e83c577b4611ae6edc:wuthering-heights"],
            ["e", "aff2591ba5a445601da37714669da4b733dc0d2bc9ac476c3c9652cadfd11287", "", "573634b648634cbad10f2451776089ea21090d9407f715e83c577b4611ae6edc"],
            ["k", "30040"],
            ["p", "573634b648634cbad10f2451776089ea21090d9407f715e83c577b4611ae6edc"],
            ["c", "true"],
            ["client", "imwald"]
          ],
          "content": "This is my very favorite book.",
          "sig": "0c062160e8a965ffe66454216f40b0cfd3d5b4e2e87e8a39fb2f4b75ce227ac3034eac584aa64ba3deab9810f374bf4d702d45d8a6837f241df9c4deae243a4a"
        }
        """.trimIndent()

    private fun parseWild(): EntityRatingEvent {
        val event = Event.fromJson(wildEventJson)
        assertTrue(event is EntityRatingEvent, "EventFactory produced ${event::class.simpleName}, not EntityRatingEvent")
        return event
    }

    /** Address.parse() requires a real 64-hex pubkey, so the synthetic cases need one too. */
    private val otherPubKey = "a".repeat(64)

    private fun rating(vararg tags: Array<String>) = EntityRatingEvent("id", "pubkey", 0L, arrayOf(*tags), "", "sig")

    @Test
    fun kindIsRegisteredWithTheEventFactory() {
        assertTrue(EventFactory.isKnownKind(EntityRatingEvent.KIND))
    }

    @Test
    fun parsesTheWildEvent() {
        val event = parseWild()

        assertEquals("books", event.mark())
        assertEquals(5.0, event.stars())
        assertEquals(1.0, event.ratingFraction())
        assertEquals(5, event.starsTag())
        assertEquals(30040, event.targetKind())
        assertEquals("573634b648634cbad10f2451776089ea21090d9407f715e83c577b4611ae6edc", event.targetAuthor())
        assertEquals("aff2591ba5a445601da37714669da4b733dc0d2bc9ac476c3c9652cadfd11287", event.targetEventId())
        assertTrue(event.hasComment())
        assertTrue(event.hasTarget())
        assertTrue(event.isKnownMark())
    }

    @Test
    fun stripsTheMarkPrefixFromTheIdentifier() {
        val event = parseWild()

        assertEquals(
            "30040:573634b648634cbad10f2451776089ea21090d9407f715e83c577b4611ae6edc:wuthering-heights",
            event.targetIdentifier(),
        )

        val address = assertNotNull(event.targetAddress())
        assertEquals(30040, address.kind)
        assertEquals("wuthering-heights", address.dTag)
    }

    @Test
    fun resolvesTheTargetFromTheDTagWhenThereIsNoATag() {
        // The spec defines only `d`/`m`/`rating`; the a/A/e/k/p tags are an extension, so a
        // spec-only publisher must still resolve to a target.
        val event =
            rating(
                arrayOf("d", "books:30040:$otherPubKey:some-book"),
                arrayOf("m", "books"),
                arrayOf("rating", "0.600"),
            )

        val address = assertNotNull(event.targetAddress())
        assertEquals(30040, address.kind)
        assertEquals("some-book", address.dTag)
        assertEquals(3.0, event.stars())
    }

    @Test
    fun leavesAnIdentifierAloneWhenThePrefixIsNotTheMark() {
        // A `d` that merely contains colons is not prefixed; only a real `<mark>:` is stripped.
        val event =
            rating(
                arrayOf("d", "30040:$otherPubKey:some-book"),
                arrayOf("m", "books"),
                arrayOf("rating", "0.600"),
            )

        assertEquals("30040:$otherPubKey:some-book", event.targetIdentifier())
    }

    @Test
    fun anAbsentMarkMeansANostrEvent() {
        val event = rating(arrayOf("d", "abc"), arrayOf("rating", "0.5"))

        assertEquals(RatingMark.EVENT, event.mark())
    }

    // ---- the four branches of the stars() ladder -------------------------------------------

    @Test
    fun starsTagWinsOverTheRatingTag() {
        // Deliberately contradictory: `s` is the author's own count and must win.
        val event = rating(arrayOf("rating", "0.200"), arrayOf("s", "4"))

        assertEquals(4.0, event.stars())
    }

    @Test
    fun aFractionIsScaledToFiveStars() {
        assertEquals(4.0, rating(arrayOf("rating", "0.800")).stars())
        assertEquals(2.5, rating(arrayOf("rating", "0.5")).stars())
        assertEquals(0.0, rating(arrayOf("rating", "0")).stars())
    }

    @Test
    fun aFullScoreIsAcceptedEvenThoughTheSpecSaysLessThanOne() {
        // The spec's prose excludes 1, but every five-star review in the wild publishes it.
        assertEquals(5.0, rating(arrayOf("rating", "1.000")).stars())
        assertEquals(1.0, rating(arrayOf("rating", "1.000")).ratingFraction())
    }

    @Test
    fun aRawCountAboveOneIsReadAsStars() {
        assertEquals(4.0, rating(arrayOf("rating", "4")).stars())
        assertEquals(3.5, rating(arrayOf("rating", "3.5")).stars())
        // ...and is not a fraction, so ratingFraction() rejects it.
        assertNull(rating(arrayOf("rating", "4")).ratingFraction())
    }

    @Test
    fun anAmbiguousBareOneResolvesTowardsTheSpecScale() {
        // "1" is 1.0-of-1 to a spec publisher and 1-of-5 to a raw publisher, and the tag alone
        // cannot separate them. We read the documented scale; publishers who mean one star
        // should send `s`.
        assertEquals(5.0, rating(arrayOf("rating", "1")).stars())
        assertEquals(1.0, rating(arrayOf("rating", "1"), arrayOf("s", "1")).stars())
    }

    @Test
    fun anUnusableScoreIsNullRatherThanZero() {
        assertNull(rating(arrayOf("rating", "not-a-number")).stars())
        assertNull(rating(arrayOf("rating", "9")).stars())
        assertNull(rating(arrayOf("rating", "-1")).stars())
        assertNull(rating(arrayOf("d", "abc")).stars())
    }

    @Test
    fun anOutOfRangeStarsTagFallsThroughToTheRating() {
        // `s` only wins when it is a sane 1..5; a bogus one must not shadow a usable `rating`.
        assertEquals(4.0, rating(arrayOf("rating", "0.800"), arrayOf("s", "77")).stars())
    }

    // ---- content / comment ------------------------------------------------------------------

    @Test
    fun contentOutranksTheCommentFlag() {
        val withBody = EntityRatingEvent("id", "pk", 0L, arrayOf(arrayOf("c", "false")), "A review", "sig")
        assertTrue(withBody.hasComment())

        val flagOnly = rating(arrayOf("c", "true"))
        assertTrue(flagOnly.hasComment())

        val neither = rating(arrayOf("rating", "1.000"))
        assertTrue(!neither.hasComment())
    }

    @Test
    fun indexesTheReviewBodyForSearch() {
        assertEquals("This is my very favorite book.", parseWild().indexableContent())
    }

    // ---- linked ids (drive the fetch of the rated event) -------------------------------------

    @Test
    fun linksTheRatedAddressAndAuthorSoTheyGetFetched() {
        val event = parseWild()
        val coordinate = "30040:573634b648634cbad10f2451776089ea21090d9407f715e83c577b4611ae6edc:wuthering-heights"

        // a and A carry the same coordinate in the wild event; both are reported.
        assertEquals(listOf(coordinate, coordinate), event.linkedAddressIds())
        assertEquals(listOf("aff2591ba5a445601da37714669da4b733dc0d2bc9ac476c3c9652cadfd11287"), event.linkedEventIds())
        assertEquals(listOf("573634b648634cbad10f2451776089ea21090d9407f715e83c577b4611ae6edc"), event.linkedPubKeys())
    }

    @Test
    fun aRatingWithoutATargetIsRejected() {
        assertTrue(!rating(arrayOf("rating", "1.000")).hasTarget())
    }

    // ---- builder ----------------------------------------------------------------------------

    @Test
    fun buildsTheSameShapeThePublishersEmit() {
        val target =
            Address(
                30040,
                "573634b648634cbad10f2451776089ea21090d9407f715e83c577b4611ae6edc",
                "wuthering-heights",
            )

        val template = EntityRatingEvent.build(target, RatingMark.BOOKS, stars = 5, review = "Loved it")
        val tags = template.tags.associate { it[0] to it.getOrElse(1) { "" } }

        assertEquals("books:${target.toValue()}", tags["d"])
        assertEquals("books", tags["m"])
        assertEquals("1.000", tags["rating"])
        assertEquals("5", tags["s"])
        assertEquals(target.toValue(), tags["a"])
        assertEquals(target.toValue(), tags["A"])
        assertEquals("30040", tags["k"])
        assertEquals(target.pubKeyHex, tags["p"])
        assertEquals("true", tags["c"])
        assertEquals("Loved it", template.content)
    }

    @Test
    fun buildClampsStarsIntoRange() {
        val target = Address(30040, otherPubKey, "book")

        val tooHigh = EntityRatingEvent.build(target, RatingMark.BOOKS, stars = 9).tags.associate { it[0] to it[1] }
        assertEquals("5", tooHigh["s"])
        assertEquals("1.000", tooHigh["rating"])

        val tooLow = EntityRatingEvent.build(target, RatingMark.BOOKS, stars = 0).tags.associate { it[0] to it[1] }
        assertEquals("1", tooLow["s"])
        assertEquals("0.200", tooLow["rating"])
    }

    @Test
    fun buildOmitsTheCommentFlagWhenThereIsNoReview() {
        val target = Address(30040, otherPubKey, "book")
        val tags = EntityRatingEvent.build(target, RatingMark.BOOKS, stars = 3).tags.map { it[0] }

        assertTrue("c" !in tags)
    }

    @Test
    fun formatsFractionsWithThreeDecimals() {
        assertEquals("1.000", RatingTag.format3(1.0))
        assertEquals("0.800", RatingTag.format3(0.8))
        assertEquals("0.000", RatingTag.format3(0.0))
        assertEquals("0.200", RatingTag.format3(0.2))
        // Out-of-range input is clamped rather than emitted as a nonsense tag.
        assertEquals("1.000", RatingTag.format3(4.2))
    }
}
