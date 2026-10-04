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
package com.vitorpamplona.amethyst.commons.observer

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObserverEditorTest {
    private val reader = key(0)
    private val alice = key(1)
    private val bob = key(2)
    private val carol = key(3)
    private val dave = key(4)

    private var nextId = 100

    private fun key(n: Int) = n.toString(16).padStart(64, '0')

    private fun note(
        author: String,
        content: String,
        createdAt: Long = 1_000,
        kind: Int = 1,
        tags: Array<Array<String>> = emptyArray(),
    ) = Event(key(nextId++), author, createdAt, kind, tags, content, "")

    private fun corpus(
        ranked: Map<ObserverDesk, List<Event>>,
        engagement: Map<String, ObserverEngagement> = emptyMap(),
        names: Map<String, String> = emptyMap(),
    ) = ObserverCorpus(reader, 0, 86_400, ranked, engagement, names, dayNotes = 11_800)

    @Test
    fun leadIsWhatTheWebOfTrustEngagedWithMost() {
        val quiet = note(alice, "A perfectly reasonable post about gardening in the autumn rain.", createdAt = 5_000)
        val loud = note(bob, "Bitcoin core developers met in Lisbon today. Here is what they decided.", createdAt = 1_000)
        val edition =
            ObserverEditor.edit(
                corpus(
                    mapOf(ObserverDesk.NOTES to listOf(quiet, loud)),
                    engagement = mapOf(loud.id to ObserverEngagement(reactions = 3, replies = 2)),
                    names = mapOf(bob to "Bob"),
                ),
            )

        assertEquals(loud.id, edition.lead?.event?.id)
        assertEquals("Bitcoin core developers met in Lisbon today.", edition.lead?.headline)
        assertEquals("Here is what they decided.", edition.lead?.body)
        assertEquals("Bob", edition.lead?.byline)
        assertEquals(listOf(quiet.id), edition.topStories.map { it.event.id })
    }

    @Test
    fun withNoSignalsTheRelaysOrderStands() {
        val first = note(alice, "The first thing the lens returned, which is long enough to run.", createdAt = 9_000)
        val second = note(bob, "The second thing the lens returned, also long enough to run.", createdAt = 8_000)
        val edition = ObserverEditor.edit(corpus(mapOf(ObserverDesk.NOTES to listOf(first, second))))

        assertEquals(first.id, edition.lead?.event?.id)
    }

    @Test
    fun greetingsAndRepliesDoNotRunAboveTheFold() {
        val gm = note(alice, "GM ☕", createdAt = 9_000)
        val reply =
            note(
                bob,
                "I disagree with this entirely and here is a long reason why that matters.",
                tags = arrayOf(arrayOf("e", key(99), "", "reply")),
            )
        val story = note(carol, "The relay operators agreed on a new spam policy this week.")
        val edition =
            ObserverEditor.edit(
                corpus(
                    mapOf(ObserverDesk.NOTES to listOf(gm, reply, story)),
                    engagement = mapOf(gm.id to ObserverEngagement(reactions = 50), reply.id to ObserverEngagement(reactions = 40)),
                ),
            )

        assertEquals(story.id, edition.lead?.event?.id)
        // The greeting still makes the wire, so nothing the lens chose goes unprinted; the reply does not.
        val wire = edition.sections.single { it.kind == ObserverSectionKind.WIRE }
        assertEquals(listOf(gm.id), wire.stories.map { it.event.id })
    }

    @Test
    fun oneStoryPerAuthorAboveTheFold() {
        val a1 = note(alice, "Alice's first story of the day, with plenty to say about it.")
        val a2 = note(alice, "Alice's second story of the day, also with plenty to say.")
        val b1 = note(bob, "Bob's only story of the day, which is about the weather today.")
        val edition =
            ObserverEditor.edit(
                corpus(
                    mapOf(ObserverDesk.NOTES to listOf(a1, a2, b1)),
                    engagement = mapOf(a1.id to ObserverEngagement(reactions = 9), a2.id to ObserverEngagement(reactions = 8)),
                ),
            )

        assertEquals(a1.id, edition.lead?.event?.id)
        assertEquals(listOf(b1.id), edition.topStories.map { it.event.id })
        assertEquals(
            listOf(a2.id),
            edition.sections
                .single { it.kind == ObserverSectionKind.WIRE }
                .stories
                .map { it.event.id },
        )
    }

    @Test
    fun articlesRunUnderTheirOwnTitleAndCountSignalsByAddress() {
        val article =
            LongFormContentEvent(
                key(nextId++),
                dave,
                2_000,
                arrayOf(arrayOf("d", "essay"), arrayOf("title", "On Relays"), arrayOf("summary", "Why outboxes won.")),
                "Long body text that is not the summary.",
                "",
            )
        val note = note(alice, "A note that would otherwise lead the paper, being long enough.")
        val edition =
            ObserverEditor.edit(
                corpus(
                    mapOf(ObserverDesk.NOTES to listOf(note), ObserverDesk.ARTICLES to listOf(article)),
                    engagement = mapOf("30023:$dave:essay" to ObserverEngagement(reposts = 4)),
                ),
            )

        assertEquals(article.id, edition.lead?.event?.id)
        assertEquals("On Relays", edition.lead?.headline)
        assertEquals("Why outboxes won.", edition.lead?.body)
        assertEquals(4, edition.lead?.reposts)
    }

    @Test
    fun emptyDesksPrintNoSection() {
        val edition = ObserverEditor.edit(corpus(mapOf(ObserverDesk.NOTES to listOf(note(alice, "Just one story on a very quiet day, nothing else.")))))
        assertTrue(edition.sections.isEmpty())
        assertNotNull(edition.lead)
    }

    @Test
    fun anEmptyCorpusIsAnEmptyEdition() {
        assertTrue(ObserverEditor.edit(corpus(emptyMap())).isEmpty)
    }

    @Test
    fun perAuthorCapAndDuplicateCollapse() {
        val dupes = (1..3).map { note(alice, "Same photo of a tiger, posted again https://x.example/$it.jpg") }
        val many = (1..10).map { note(bob, "Long-form archive entry number $it of many", kind = 30023) }

        assertEquals(1, ObserverEditor.prune(ObserverDesk.NOTES, dupes).size)
        assertEquals(ObserverDesk.ARTICLES.perAuthor, ObserverEditor.prune(ObserverDesk.ARTICLES, many).size)
    }

    @Test
    fun soldClassifiedsAreNotAdvertised() {
        val sold = note(alice, "Vintage radio", kind = 30402, tags = arrayOf(arrayOf("title", "Radio"), arrayOf("status", "sold")))
        val active =
            note(bob, "A bike", kind = 30402, tags = arrayOf(arrayOf("title", "Bike"), arrayOf("price", "210000", "SATS")))
        val edition = ObserverEditor.edit(corpus(mapOf(ObserverDesk.CLASSIFIEDS to listOf(sold, active))))

        val classifieds = edition.sections.single { it.kind == ObserverSectionKind.CLASSIFIEDS }
        assertEquals(listOf(active.id), classifieds.stories.map { it.event.id })
        assertEquals(
            ObserverDetail.Price("210000", "SATS", null),
            classifieds.stories
                .single()
                .details
                .single(),
        )
    }

    @Test
    fun calendarRunsInDateOrderAcrossBothShapes() {
        val later = note(alice, "", kind = 31923, tags = arrayOf(arrayOf("title", "Meetup"), arrayOf("start", "1791500000"), arrayOf("start_tzid", "America/New_York")))
        val sooner = note(bob, "", kind = 31922, tags = arrayOf(arrayOf("title", "Conference"), arrayOf("start", "2026-10-05")))
        val undated = note(carol, "", kind = 31923, tags = arrayOf(arrayOf("title", "Someday")))
        val edition = ObserverEditor.edit(corpus(mapOf(ObserverDesk.CALENDAR to listOf(undated, later, sooner))))

        val calendar = edition.sections.single { it.kind == ObserverSectionKind.CALENDAR }
        assertEquals(listOf("Conference", "Meetup", "Someday"), calendar.stories.map { it.headline })
        assertEquals(ObserverDetail.Starts(1791500000, null, "America/New_York"), calendar.stories[1].details.single())
    }

    @Test
    fun highlightsCreditTheQuotedAuthorNotTheHighlighter() {
        val highlight =
            note(alice, "Human code review has very nearly run its course.", kind = 9802, tags = arrayOf(arrayOf("p", carol), arrayOf("r", "https://example.com/essay")))
        val story = ObserverEditor.story(highlight, ObserverDesk.HIGHLIGHTS, ObserverEngagement.NONE, mapOf(alice to "Alice", carol to "Carol"), 400)

        assertEquals("Alice", story.byline)
        assertEquals("Human code review has very nearly run its course.", story.headline)
        assertTrue(ObserverDetail.QuotedAuthor("Carol") in story.details)
    }

    @Test
    fun mentionsBecomeNamesAndLinksAreRemoved() {
        val npub = carol.hexToByteArray().toNpub()
        val text = ObserverEditor.cleanText("Thanks nostr:$npub, see https://example.com/x  and   nostr:note1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq", mapOf(carol to "Carol"))
        assertEquals("Thanks @Carol, see and", text)
    }

    @Test
    fun picturesNeedAnImageAndVideoIsNotOne() {
        val picture = note(alice, "Sunset", kind = 20, tags = arrayOf(arrayOf("imeta", "url https://cdn.example/a.jpg", "m image/jpeg")))
        val clip = note(bob, "Clip", kind = 20, tags = arrayOf(arrayOf("imeta", "url https://cdn.example/a.mp4", "m video/mp4")))

        assertEquals("https://cdn.example/a.jpg", ObserverEditor.imageOf(picture))
        assertNull(ObserverEditor.imageOf(clip))

        val edition = ObserverEditor.edit(corpus(mapOf(ObserverDesk.PICTURES to listOf(picture, clip))))
        assertEquals(
            listOf(picture.id),
            edition.sections
                .single { it.kind == ObserverSectionKind.PHOTOS }
                .stories
                .map { it.event.id },
        )
    }

    @Test
    fun headlinesAreClippedAtAWord() {
        val (headline, rest) = ObserverEditor.firstSentence("word ".repeat(60).trim())
        assertTrue(headline.length <= ObserverEditor.HEADLINE_CHARS + 1)
        assertTrue(headline.endsWith("…"))
        assertTrue(rest.startsWith("…"))
        assertFalse(headline.contains("  "))
    }

    @Test
    fun trendingNeedsTwoPeople() {
        val events =
            listOf(
                note(alice, "a", tags = arrayOf(arrayOf("t", "Bitcoin"), arrayOf("t", "solo"))),
                note(bob, "b", tags = arrayOf(arrayOf("t", "bitcoin"))),
                note(alice, "c", tags = arrayOf(arrayOf("t", "solo"))),
            )
        assertEquals(listOf(ObserverTrend("bitcoin", 2)), ObserverEditor.trending(events))
    }

    @Test
    fun theCodeIsAPrintRunNotAFile() {
        val a = note(alice, "x")
        val b = note(bob, "y")
        val one = ObserverEditor.code(corpus(mapOf(ObserverDesk.NOTES to listOf(a, b))))
        val sameMaterialOtherOrder = ObserverEditor.code(corpus(mapOf(ObserverDesk.NOTES to listOf(b, a))))
        val otherMaterial = ObserverEditor.code(corpus(mapOf(ObserverDesk.NOTES to listOf(a))))

        assertEquals(6, one.length)
        assertEquals(one, sameMaterialOtherOrder)
        assertTrue(one != otherMaterial)
    }

    @Test
    fun epochDayIsTheCivilDate() {
        assertEquals("1970-01-01", ObserverEditor.epochDay(0))
        assertEquals("2026-10-04", ObserverEditor.epochDay(1_791_072_000))
        assertEquals("2000-02-29", ObserverEditor.epochDay(951_782_400))
    }

    @Test
    fun tallyCountsEachPersonOncePerSignal() {
        val story = key(500)
        val events =
            listOf(
                note(alice, "+", kind = 7, tags = arrayOf(arrayOf("e", story))),
                note(alice, "🔥", kind = 7, tags = arrayOf(arrayOf("e", story))),
                note(bob, "+", kind = 7, tags = arrayOf(arrayOf("e", story))),
                note(carol, "", kind = 6, tags = arrayOf(arrayOf("e", story))),
                note(dave, "Agreed", kind = 1, tags = arrayOf(arrayOf("e", story, "", "reply"))),
                note(dave, "cf.", kind = 1, tags = arrayOf(arrayOf("e", story, "", "mention"))),
                note(dave, "+", kind = 7, tags = arrayOf(arrayOf("e", key(501)))),
            )
        assertEquals(mapOf(story to ObserverEngagement(reactions = 2, reposts = 1, replies = 1)), ObserverPull.tally(events, setOf(story), emptySet()))
    }

    @Test
    fun zapsCountWhoPaidNotTheServerThatSigned() {
        val story = key(600)
        val server = key(700)
        val request = """{"pubkey":"$carol","kind":9734,"tags":[["e","$story"]]}"""
        val events =
            listOf(
                Event(key(nextId++), server, 1, 9735, arrayOf(arrayOf("e", story), arrayOf("P", alice)), "", ""),
                Event(key(nextId++), server, 2, 9735, arrayOf(arrayOf("e", story), arrayOf("P", alice)), "", ""),
                Event(key(nextId++), server, 3, 9735, arrayOf(arrayOf("e", story), arrayOf("description", request)), "", ""),
            )
        assertEquals(mapOf(story to ObserverEngagement(zaps = 2)), ObserverPull.tally(events, setOf(story), emptySet()))
    }

    @Test
    fun readinessFirstUnmetLinkWins() {
        fun assess(
            seen: Boolean,
            rank: String?,
            lensed: Int,
            anonymous: Int,
        ) = ObserverReadiness.assess(ObserverReadiness.Facts(seen, rank, lensed, anonymous))

        assertEquals(ObserverReadiness.State.RELAY_SILENT, assess(false, null, 0, 0))
        // An unresolvable lens silently becomes the anonymous ranking, so both probes answer.
        assertEquals(ObserverReadiness.State.NO_SCORE_LIST, assess(false, null, 12, 12))
        assertEquals(ObserverReadiness.State.NO_RANK_SERVICE, assess(true, null, 12, 12))
        assertEquals(ObserverReadiness.State.NOT_PROJECTED, assess(true, alice, 0, 12))
        assertEquals(ObserverReadiness.State.READY, assess(true, alice, 12, 12))
    }

    @Test
    fun bylinesIncludeTheReaderQuotedAuthorsAndMentions() {
        val npub = dave.hexToByteArray().toNpub()
        val events =
            listOf(
                note(alice, "hi nostr:$npub"),
                note(bob, "quote", kind = 9802, tags = arrayOf(arrayOf("p", carol))),
            )
        assertEquals(setOf(reader, alice, bob, carol, dave), ObserverPress.bylinesFor(reader, events))
    }
}
