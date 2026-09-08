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
