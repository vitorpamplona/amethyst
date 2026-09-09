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
package com.vitorpamplona.quartz.experimental.library

import com.vitorpamplona.quartz.experimental.publications.PublicationContentEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryEventsTest {
    private val author = "a".repeat(64)

    @Test
    fun kindsAreRegisteredWithTheEventFactory() {
        assertTrue(EventFactory.isKnownKind(LearningResourceEvent.KIND))
        assertTrue(EventFactory.isKnownKind(BookshelfDirectoryEvent.KIND))
        assertTrue(EventFactory.isKnownKind(BlossomPieceIndexEvent.KIND))
    }

    // ---- 30142 learning resource -------------------------------------------------------------

    @Test
    fun parsesALearningResource() {
        val event =
            LearningResourceEvent(
                "id",
                author,
                0L,
                arrayOf(
                    arrayOf("d", "intro-to-nostr"),
                    arrayOf("title", "Intro to Nostr"),
                    arrayOf("summary", "The basics."),
                    arrayOf("image", "https://img.example/cover.jpg"),
                    arrayOf("t", "nostr"),
                ),
                "Lesson one.",
                "sig",
            )

        assertEquals("Intro to Nostr", event.title())
        assertEquals("The basics.", event.summary())
        assertEquals("https://img.example/cover.jpg", event.image())
        assertEquals(listOf("nostr"), event.topics())
        assertEquals("Intro to Nostr\nThe basics.\nLesson one.", event.indexableContent())
    }

    @Test
    fun aLearningResourceReadsTheSchemaOrgNameAndDescription() {
        // The `type: LearningResource` publishers emit schema.org's spelling. Shape taken from a
        // real event (`4fa5d1c4...`, d=17xu8qb7), which rendered as its slug before this.
        val event =
            LearningResourceEvent(
                "id",
                author,
                0L,
                arrayOf(
                    arrayOf("d", "17xu8qb7"),
                    arrayOf("type", "LearningResource"),
                    arrayOf("name", "Caesar-Scheibe"),
                    arrayOf("description", "Eine Anleitung."),
                ),
                "Body.",
                "sig",
            )

        assertEquals("Caesar-Scheibe", event.title())
        assertEquals("Eine Anleitung.", event.summary())
        assertEquals("Caesar-Scheibe", event.titleOrIdentifier())
        assertEquals("Caesar-Scheibe\nEine Anleitung.\nBody.", event.indexableContent())
    }

    @Test
    fun titleAndSummaryWinOverNameAndDescription() {
        val event =
            LearningResourceEvent(
                "id",
                author,
                0L,
                arrayOf(
                    arrayOf("d", "both"),
                    arrayOf("title", "Preferred"),
                    arrayOf("name", "Ignored"),
                    arrayOf("summary", "Preferred summary."),
                    arrayOf("description", "Ignored description."),
                ),
                "",
                "sig",
            )

        assertEquals("Preferred", event.title())
        assertEquals("Preferred summary.", event.summary())
    }

    @Test
    fun aLearningResourceReadsTheBookPublishersByline() {
        // The Codices shape: a title, an author, a year, a cover, and no body at all. Reading only
        // title + summary rendered these as a bare line of text.
        val event =
            LearningResourceEvent(
                "id",
                author,
                0L,
                arrayOf(
                    arrayOf("d", "50078fd7-f337-40c2-852a-56f3751a8132"),
                    arrayOf("title", "The End of Faith"),
                    arrayOf("author", "Sam Harris"),
                    arrayOf("published", "2004"),
                    arrayOf("image", "https://covers.openlibrary.org/b/id/1469127-M.jpg"),
                    arrayOf("t", "codices"),
                ),
                "",
                "sig",
            )

        assertEquals("Sam Harris", event.author())
        assertEquals("2004", event.published())
        assertEquals(emptyList<String>(), event.facetLabels())
    }

    @Test
    fun aLearningResourceReadsTheSchemaOrgFacets() {
        // Shape taken from a real Edufeed event (`1f3208b1…`, an RWTH Aachen workshop).
        val event =
            LearningResourceEvent(
                "id",
                author,
                0L,
                arrayOf(
                    arrayOf("d", "workshop-schatzsuche"),
                    arrayOf("type", "LearningResource"),
                    arrayOf("name", "Schatzsuche"),
                    arrayOf("inLanguage", "de"),
                    arrayOf("creator:name", "Michaela Wehling"),
                    arrayOf("creator:type", "Person"),
                    arrayOf("creator:name", "Kerstin Kamphausen"),
                    arrayOf("license:id", "https://creativecommons.org/licenses/by-sa/4.0/"),
                    arrayOf("isAccessibleForFree", "true"),
                    arrayOf("about:id", "http://w3id.org/kim/schulfaecher/s1013"),
                    arrayOf("about:prefLabel:de", "Informatik"),
                    arrayOf("educationalLevel:id", "https://w3id.org/kim/educationalLevel/level_1"),
                    arrayOf("educationalLevel:prefLabel:de", "Primarbereich"),
                    arrayOf("educationalLevel:id", "https://w3id.org/kim/educationalLevel/level_2"),
                    arrayOf("educationalLevel:prefLabel:de", "Sekundarbereich I"),
                    arrayOf("learningResourceType:id", "https://w3id.org/kim/hcrt/educational_game"),
                    arrayOf("learningResourceType:prefLabel:de", "Lernspiel"),
                ),
                "Body.",
                "sig",
            )

        // Only the first creator: the byline is one line, and seven names would push the title off it.
        assertEquals("Michaela Wehling", event.author())
        assertEquals("de", event.language())
        assertEquals(true, event.isFreeToAccess())
        assertEquals("https://creativecommons.org/licenses/by-sa/4.0/", event.license())
        assertEquals(listOf("Informatik"), event.subjects())
        assertEquals(listOf("Primarbereich", "Sekundarbereich I"), event.educationalLevels())
        assertEquals(listOf("Lernspiel"), event.resourceTypes())
        // Type, then level, then subject — what it is before who it is for before what it is about.
        assertEquals(listOf("Lernspiel", "Primarbereich", "Sekundarbereich I", "Informatik"), event.facetLabels())
    }

    @Test
    fun aFacetIsShownInOneLanguageAndNeverRepeated() {
        // A real event carries `learningResourceType` in six languages at once and repeats the same
        // subject under several ids; concatenating either would read as gibberish.
        val event =
            LearningResourceEvent(
                "id",
                author,
                0L,
                arrayOf(
                    arrayOf("d", "multi"),
                    arrayOf("inLanguage", "de"),
                    arrayOf("learningResourceType:prefLabel:en", "Worksheet"),
                    arrayOf("learningResourceType:prefLabel:de", "Arbeitsmaterial"),
                    arrayOf("learningResourceType:prefLabel:fr", "Materiel"),
                    arrayOf("about:prefLabel:de", "Informatik"),
                    arrayOf("about:prefLabel:de", "Mathematik"),
                    arrayOf("about:prefLabel:de", "Informatik"),
                ),
                "",
                "sig",
            )

        assertEquals(listOf("Arbeitsmaterial"), event.resourceTypes())
        assertEquals(listOf("Worksheet"), event.resourceTypes("en"))
        assertEquals(listOf("Informatik", "Mathematik"), event.subjects())
    }

    @Test
    fun aFacetWithoutAMatchingLanguageFallsBackToTheFirstPublished() {
        val event =
            LearningResourceEvent(
                "id",
                author,
                0L,
                arrayOf(
                    arrayOf("d", "no-lang"),
                    arrayOf("learningResourceType:prefLabel:nl", "Werkblad"),
                    arrayOf("learningResourceType:prefLabel:cs", "Pracovni list"),
                ),
                "",
                "sig",
            )

        assertEquals(listOf("Werkblad"), event.resourceTypes())
        assertEquals(listOf("Werkblad"), event.resourceTypes("de"))
    }

    @Test
    fun aLearningResourceReadsTheAttachedPdf() {
        // Shape taken from a real Edufeed event (`a192fee3…`, the Caesar-Scheibe worksheet). The
        // material is a PDF hanging off `encoding:*` and named nowhere in the body, so a reader
        // who cannot see it cannot get at the thing the event is about.
        val event =
            LearningResourceEvent(
                "id",
                author,
                0L,
                arrayOf(
                    arrayOf("d", "17xu8qb7"),
                    arrayOf("type", "LearningResource"),
                    arrayOf("name", "Caesar-Scheibe"),
                    arrayOf("datePublished", "2026-09-03"),
                    arrayOf("dateCreated", "2026-09-03"),
                    arrayOf("image", "https://blossom.edufeed.org/5b8a701b.jpeg"),
                    arrayOf("encoding:contentUrl", "https://blossom.edufeed.org/b84841a0.pdf"),
                    arrayOf("encoding:encodingFormat", "application/pdf"),
                    arrayOf("encoding:sha256", "b84841a0d6b44d25120ab166fd5689c342b763f0a24c4aab54354912c6fcb6d7"),
                    arrayOf("encoding:contentSize", "54137"),
                ),
                "Eine Anleitung.",
                "sig",
            )

        assertEquals("https://blossom.edufeed.org/b84841a0.pdf", event.contentUrl())
        assertEquals("application/pdf", event.contentFormat())
        assertEquals("b84841a0d6b44d25120ab166fd5689c342b763f0a24c4aab54354912c6fcb6d7", event.contentHash())
        assertEquals(54137L, event.contentSize())
        // `datePublished` is a fourth spelling of the same fact; without it the byline was empty.
        assertEquals("2026-09-03", event.published())
        // The `image` is the cover, not the material — they must not be confused for each other.
        assertEquals("https://blossom.edufeed.org/5b8a701b.jpeg", event.image())
    }

    @Test
    fun aLearningResourceReadsTheFileItShipsAs() {
        val event =
            LearningResourceEvent(
                "id",
                author,
                0L,
                arrayOf(
                    arrayOf("d", "6sah9tsh"),
                    arrayOf("name", "H5P Multiple Choice"),
                    arrayOf("encoding:contentUrl", "https://haven.laoc.xyz/982eaf3e.zip"),
                    arrayOf("encoding:encodingFormat", "application/x-webxdc"),
                    arrayOf("encoding:contentSize", "2805254"),
                ),
                "",
                "sig",
            )

        assertEquals("https://haven.laoc.xyz/982eaf3e.zip", event.contentUrl())
        assertEquals("application/x-webxdc", event.contentFormat())
        assertEquals(2805254L, event.contentSize())
        // Not stated is not "no": a resource that never mentions price must not read as paid.
        assertNull(event.isFreeToAccess())
    }

    @Test
    fun aLearningResourceFallsBackToItsIdentifier() {
        val event = LearningResourceEvent("id", author, 0L, arrayOf(arrayOf("d", "untitled-course")), "", "sig")

        assertEquals("untitled-course", event.titleOrIdentifier())
    }

    // ---- 30045 directory ---------------------------------------------------------------------

    @Test
    fun parsesADirectoryAndItsShelf() {
        val coord = "${PublicationContentEvent.KIND}:$author:chapter-one"
        val event =
            BookshelfDirectoryEvent(
                "id",
                author,
                0L,
                arrayOf(
                    arrayOf("d", "my-shelf"),
                    arrayOf("title", "My Shelf"),
                    arrayOf("a", coord, "Chapter One"),
                    arrayOf("e", "f".repeat(64)),
                ),
                "",
                "sig",
            )

        assertEquals("My Shelf", event.title())
        assertEquals(2, event.itemCount())
        // Reuses the publication table-of-contents parser, so inline titles come through.
        assertEquals("Chapter One", event.items()[0].title)
        assertEquals("f".repeat(64), event.items()[1].eventId)
    }

    @Test
    fun aDirectoryExcludesUppercaseSourceTagsFromItsShelf() {
        // Same rule as a publication index: `A`/`E` name a source, not contents.
        val coord = "${PublicationContentEvent.KIND}:$author:ch"
        val event =
            BookshelfDirectoryEvent(
                "id",
                author,
                0L,
                arrayOf(arrayOf("d", "s"), arrayOf("A", coord), arrayOf("a", coord)),
                "",
                "sig",
            )

        assertEquals(1, event.itemCount())
    }

    @Test
    fun anEmptyShelfIsEmpty() {
        val event = BookshelfDirectoryEvent("id", author, 0L, arrayOf(arrayOf("d", "s")), "", "sig")

        assertEquals(0, event.itemCount())
    }

    // ---- 32176 blossom piece index -----------------------------------------------------------

    @Test
    fun parsesABlossomPieceIndex() {
        val event =
            BlossomPieceIndexEvent(
                "id",
                author,
                0L,
                arrayOf(
                    arrayOf("d", "piece-1"),
                    arrayOf("title", "A File"),
                    arrayOf("size", "1048576"),
                    arrayOf("type", "application/pdf"),
                ),
                "",
                "sig",
            )

        assertEquals("A File", event.title())
        assertEquals("1048576", event.size())
        assertEquals("application/pdf", event.type())
        assertEquals(1_048_576L, event.sizeInBytes())
    }

    @Test
    fun aNonNumericSizeStaysAStringAndYieldsNoByteCount() {
        // `size` is published as text; parsing it would force a guess about units.
        val event =
            BlossomPieceIndexEvent(
                "id",
                author,
                0L,
                arrayOf(arrayOf("d", "p"), arrayOf("size", "1.2 MB")),
                "",
                "sig",
            )

        assertEquals("1.2 MB", event.size())
        assertNull(event.sizeInBytes())
    }

    @Test
    fun parsesAllThreeThroughTheFactory() {
        fun json(kind: Int) =
            """
            {"id":"${"a".repeat(64)}","pubkey":"$author","created_at":1788807940,"kind":$kind,
             "tags":[["d","x"],["title","T"]],"content":"","sig":"${"c".repeat(128)}"}
            """.trimIndent()

        assertTrue(Event.fromJson(json(30142)) is LearningResourceEvent)
        assertTrue(Event.fromJson(json(30045)) is BookshelfDirectoryEvent)
        assertTrue(Event.fromJson(json(32176)) is BlossomPieceIndexEvent)
    }
}
