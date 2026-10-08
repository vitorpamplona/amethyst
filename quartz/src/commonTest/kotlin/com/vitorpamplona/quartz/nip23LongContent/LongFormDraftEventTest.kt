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
package com.vitorpamplona.quartz.nip23LongContent

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip23LongContent.draft.LongFormDraftEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * NIP-23 kind 30024 drafts. A draft has "the same structure" as kind 30023, so the same synthetic
 * article is read as both kinds and every shared accessor, hint and search field must agree.
 */
class LongFormDraftEventTest {
    private val author = "7".repeat(64)
    private val mentioned = "8".repeat(64)
    private val quoted = "9".repeat(64)
    private val quotedAuthor = "a".repeat(64)

    private val tags =
        arrayOf(
            arrayOf("d", "my-article"),
            arrayOf("title", "On Drafts"),
            arrayOf("summary", "Why drafts matter"),
            arrayOf("image", "https://example.com/cover.png"),
            arrayOf("published_at", "1700000000"),
            arrayOf("t", "writing"),
            arrayOf("p", mentioned, "wss://relay.example.com", "mention"),
            arrayOf("q", quoted, "wss://relay.example.com"),
            arrayOf("q", "30023:$quotedAuthor:other-article", "wss://relay.example.com"),
            arrayOf("client", "YakiHonne", "31990:${"b".repeat(64)}:1700732875747"),
        )

    private val body = "# On Drafts\n\nSee nostr:npub1enxvenxvenxvenxvenxvenxvenxvenxvenxvenxvenxvenxvenxqn2pktz for more."

    private fun asKind(kind: Int): Event = EventFactory.create("1".repeat(64), author, 1_700_000_100L, kind, tags, body, "00".repeat(64))

    private fun <T : Event> EventTemplate<T>.toEvent(): T = EventFactory.create("2".repeat(64), author, createdAt, kind, tags, content, "")

    @Test
    fun factoryBuildsDraftForKind30024() {
        assertIs<LongFormDraftEvent>(asKind(LongFormDraftEvent.KIND))
        assertTrue(EventFactory.isKnownKind(LongFormDraftEvent.KIND))
    }

    @Test
    fun readsTheSameFieldsAsAPublishedArticle() {
        val draft = assertIs<LongFormDraftEvent>(asKind(LongFormDraftEvent.KIND))
        val article = assertIs<LongFormContentEvent>(asKind(LongFormContentEvent.KIND))

        assertEquals("On Drafts", draft.title())
        assertEquals("Why drafts matter", draft.summary())
        assertEquals("https://example.com/cover.png", draft.image())
        assertEquals(1_700_000_000L, draft.publishedAt())
        assertEquals(listOf("writing"), draft.topics())
        assertEquals("30024:$author:my-article", draft.addressTag())

        assertEquals(article.title(), draft.title())
        assertEquals(article.summary(), draft.summary())
        assertEquals(article.image(), draft.image())
        assertEquals(article.publishedAt(), draft.publishedAt())
        assertEquals(article.topics(), draft.topics())
        assertEquals(article.dTag(), draft.dTag())
    }

    @Test
    fun hintsAndLinksMatchKind30023() {
        val draft = assertIs<LongFormDraftEvent>(asKind(LongFormDraftEvent.KIND))
        val article = assertIs<LongFormContentEvent>(asKind(LongFormContentEvent.KIND))

        assertTrue(draft.linkedPubKeys().contains(mentioned))
        // Cited as a nostr: URI in the markdown (NIP-27), not tagged.
        assertTrue(draft.linkedPubKeys().contains("c".repeat(64)))
        assertTrue(draft.linkedEventIds().contains(quoted))
        assertEquals(article.linkedPubKeys(), draft.linkedPubKeys())
        assertEquals(article.pubKeyHints(), draft.pubKeyHints())
        assertEquals(article.linkedEventIds(), draft.linkedEventIds())
        assertEquals(article.eventHints(), draft.eventHints())
        assertEquals(article.linkedAddressIds(), draft.linkedAddressIds())
        assertEquals(article.addressHints(), draft.addressHints())
    }

    @Test
    fun isNeitherAThreadNorACommentRoot() {
        val draft: Event = asKind(LongFormDraftEvent.KIND)
        assertFalse(draft is BaseThreadedEvent)
        assertFalse(draft is RootScope)
    }

    @Test
    fun publishedAtInTheFutureIsDropped() {
        val draft =
            assertIs<LongFormDraftEvent>(
                EventFactory.create<Event>("1".repeat(64), author, 100L, LongFormDraftEvent.KIND, arrayOf(arrayOf("published_at", "200")), "", ""),
            )
        assertNull(draft.publishedAt())
    }

    @Test
    fun malformedTagsReadAsNull() {
        val draft =
            assertIs<LongFormDraftEvent>(
                EventFactory.create<Event>(
                    "1".repeat(64),
                    author,
                    100L,
                    LongFormDraftEvent.KIND,
                    arrayOf(arrayOf("title"), arrayOf("summary", ""), arrayOf("published_at", "soon"), arrayOf("p", "short"), arrayOf("q")),
                    "",
                    "",
                ),
            )
        assertNull(draft.title())
        assertNull(draft.summary())
        assertNull(draft.publishedAt())
        assertEquals("", draft.dTag())
        assertEquals(emptyList(), draft.linkedPubKeys())
        assertEquals(emptyList(), draft.linkedEventIds())
        assertEquals(emptyList(), draft.linkedAddressIds())
    }

    @Test
    fun indexesLikeKind30023AndTheVisitorAgrees() {
        val draft = assertIs<LongFormDraftEvent>(asKind(LongFormDraftEvent.KIND))
        val article = assertIs<LongFormContentEvent>(asKind(LongFormContentEvent.KIND))

        assertEquals("On Drafts\nWhy drafts matter\n$body\nwriting", draft.indexableContent())
        assertEquals(article.indexableContent(), draft.indexableContent())

        val visited = mutableListOf<String>()
        draft.forEachIndexableField { field ->
            field?.let { visited.add(it) }
            true
        }
        assertEquals(draft.indexableContent(), visited.joinToString(draft.indexableSeparator()))
    }

    @Test
    fun buildRoundTrips() {
        val draft =
            LongFormDraftEvent
                .build(
                    description = "Body text",
                    title = "Title",
                    summary = "Summary",
                    image = "https://example.com/i.png",
                    publishedAt = 1_600_000_000L,
                    dTag = "slug",
                    createdAt = 1_700_000_000L,
                ).toEvent()

        assertEquals(LongFormDraftEvent.KIND, draft.kind)
        assertEquals("slug", draft.dTag())
        assertEquals("Title", draft.title())
        assertEquals("Summary", draft.summary())
        assertEquals("https://example.com/i.png", draft.image())
        assertEquals(1_600_000_000L, draft.publishedAt())
        assertEquals("Body text", draft.content)
    }
}
