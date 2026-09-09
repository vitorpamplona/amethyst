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
package com.vitorpamplona.quartz.experimental.citations

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CitationEventTest {
    private fun external(vararg tags: Array<String>) = ExternalCitationEvent("id", "pk", 0L, arrayOf(*tags), "why I cited it", "sig")

    private fun hardcopy(vararg tags: Array<String>) = HardcopyCitationEvent("id", "pk", 0L, arrayOf(*tags), "", "sig")

    private fun prompt(vararg tags: Array<String>) = PromptCitationEvent("id", "pk", 0L, arrayOf(*tags), "Summarize the fable.", "sig")

    @Test
    fun kindsAreRegisteredWithTheEventFactory() {
        assertTrue(EventFactory.isKnownKind(ExternalCitationEvent.KIND))
        assertTrue(EventFactory.isKnownKind(HardcopyCitationEvent.KIND))
        assertTrue(EventFactory.isKnownKind(PromptCitationEvent.KIND))
    }

    @Test
    fun kind30IsNotModelledBecauseItCollidesWithChess() {
        // Quartz already registers kind 30 as a Jester chess move, so a kind-30 "internal
        // citation" parses as chess here. This test states the collision rather than hiding it.
        assertEquals(30, JesterEvent.KIND)
        assertTrue(30 !in CitationEvent.KINDS)
        assertEquals(setOf(31, 32, 33), CitationEvent.KINDS)
    }

    // ---- kind 31, external -------------------------------------------------------------------

    @Test
    fun parsesAnExternalCitation() {
        val event =
            external(
                arrayOf("u", "https://example.com/article"),
                arrayOf("accessed_on", "2026-09-08"),
                arrayOf("title", "An Article"),
                arrayOf("author", "A. Writer"),
                arrayOf("published_on", "2026-01-02"),
                arrayOf("open_timestamp", "f".repeat(64)),
            )

        assertEquals("https://example.com/article", event.url())
        assertEquals("2026-09-08", event.accessedOn())
        assertEquals("An Article", event.title())
        assertEquals("A. Writer", event.author())
        assertEquals("2026-01-02", event.publishedOn())
        assertEquals("f".repeat(64), event.openTimestamp())
        assertTrue(event.hasSource())
    }

    @Test
    fun acceptsTheManifestsUrlTagAsAFallback() {
        // The reference client's manifest names the tag `url` while its card and draft builder
        // use `u`. `u` is the wire truth; `url` is accepted so a publisher who followed the
        // manifest is not dropped.
        assertEquals("https://a.example", external(arrayOf("u", "https://a.example")).url())
        assertEquals("https://b.example", external(arrayOf("url", "https://b.example")).url())
    }

    @Test
    fun anExternalCitationFallsBackToItsUrlForADisplayTitle() {
        assertEquals("https://a.example", external(arrayOf("u", "https://a.example")).displayTitle())
        assertEquals("Titled", external(arrayOf("u", "https://a.example"), arrayOf("title", "Titled")).displayTitle())
    }

    // ---- kind 32, hardcopy -------------------------------------------------------------------

    @Test
    fun parsesAHardcopyCitation() {
        val event =
            hardcopy(
                arrayOf("title", "The Farmer and The Snake"),
                arrayOf("author", "Aesop"),
                arrayOf("page_range", "14-15"),
                arrayOf("chapter_title", "Winter"),
                arrayOf("editor", "An Editor"),
                arrayOf("doi", "10.1000/xyz"),
            )

        assertEquals("The Farmer and The Snake", event.title())
        assertEquals("Aesop", event.author())
        assertEquals("14-15", event.pageRange())
        assertEquals("Winter", event.chapterTitle())
        assertEquals("An Editor", event.editor())
        assertEquals("10.1000/xyz", event.doi())
    }

    @Test
    fun readsTheVolumeFromTheSecondSlotOfPublishedIn() {
        // The volume has no tag of its own; it rides in `published_in`'s second value.
        val event = hardcopy(arrayOf("published_in", "Aesop's Fables", "3rd ed."))

        assertEquals("Aesop's Fables", event.publishedIn())
        assertEquals("3rd ed.", event.volume())
    }

    @Test
    fun aPublishedInWithNoVolumeHasNoVolume() {
        val event = hardcopy(arrayOf("published_in", "Aesop's Fables"))

        assertEquals("Aesop's Fables", event.publishedIn())
        assertNull(event.volume())
    }

    @Test
    fun anEmptyVolumeSlotIsNotAVolume() {
        // The builder writes "" when there is no volume, so an empty slot must not become one.
        assertNull(hardcopy(arrayOf("published_in", "Work", "")).volume())
    }

    // ---- kind 33, prompt ---------------------------------------------------------------------

    @Test
    fun parsesAPromptCitation() {
        val event = prompt(arrayOf("llm", "Claude"), arrayOf("accessed_on", "2026-09-08"), arrayOf("u", "https://chat.example"))

        assertEquals("Claude", event.llm())
        assertEquals("https://chat.example", event.url())
        assertEquals("Claude", event.displayTitle())
        assertEquals("Summarize the fable.", event.content)
    }

    // ---- shared ------------------------------------------------------------------------------

    @Test
    fun parsesThroughTheFactoryIntoTheRightSubclass() {
        fun json(kind: Int) =
            """
            {"id":"${"a".repeat(64)}","pubkey":"${"b".repeat(64)}","created_at":1788807940,"kind":$kind,
             "tags":[["title","T"],["accessed_on","2026-09-08"]],"content":"c","sig":"${"c".repeat(128)}"}
            """.trimIndent()

        assertTrue(Event.fromJson(json(31)) is ExternalCitationEvent)
        assertTrue(Event.fromJson(json(32)) is HardcopyCitationEvent)
        assertTrue(Event.fromJson(json(33)) is PromptCitationEvent)
    }

    @Test
    fun indexesTitleSummaryAndBodyForSearch() {
        val event = external(arrayOf("title", "An Article"), arrayOf("summary", "A summary"))

        assertEquals("An Article\nA summary\nwhy I cited it", event.indexableContent())
    }

    @Test
    fun aCitationThatNamesNothingHasNoSource() {
        assertTrue(!HardcopyCitationEvent("id", "pk", 0L, emptyArray(), "", "sig").hasSource())
    }
}
