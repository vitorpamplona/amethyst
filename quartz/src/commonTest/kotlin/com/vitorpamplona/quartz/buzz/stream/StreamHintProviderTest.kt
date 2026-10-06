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
package com.vitorpamplona.quartz.buzz.stream

import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamHintProviderTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val other = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val root = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val reply = "a7a12e7bbd4b4c2ae6cd5ae1c8a5ab8a4e7f0f53bd21cd6f4b51f2cc1f6fe0e2"
    private val relay = "wss://relay.damus.io/"
    private val channel = "6f1a8a3c-9f1e-4d2b-8c1a-2b3c4d5e6f70"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    private fun SearchableEvent.rejoined(): String {
        val parts = mutableListOf<String>()
        forEachIndexableField { field ->
            if (field != null) parts.add(field)
            true
        }
        return parts.joinToString(indexableSeparator())
    }

    @Test
    fun v2MessageExposesMentionsAndThreadMarkers() {
        val event =
            StreamMessageV2Event(
                id,
                author,
                1700000000,
                arrayOf(
                    arrayOf("h", channel),
                    arrayOf("e", root, "", "root"),
                    arrayOf("e", reply, relay, "reply"),
                    arrayOf("p", other, relay),
                    arrayOf("p", author),
                ),
                "hello",
                sig,
            )

        assertEquals(listOf(other, author), event.linkedPubKeys())
        assertEquals(listOf(other), event.pubKeyHints().map { it.pubkey })
        assertEquals(listOf(relay), event.pubKeyHints().map { it.relay.url })
        assertEquals(listOf(root, reply), event.linkedEventIds())
        assertEquals(listOf(reply), event.eventHints().map { it.eventId })
    }

    @Test
    fun editIsSearchableAndPointsAtTheEditedMessage() {
        val template = StreamMessageEditEvent.build(channel, root, "fixed typo in the release notes")
        val event = StreamMessageEditEvent(id, author, template.createdAt, template.tags + arrayOf(arrayOf("p", other, relay)), template.content, sig)

        assertEquals("fixed typo in the release notes", event.indexableContent())
        assertEquals(event.indexableContent(), event.rejoined())
        assertEquals(listOf(root), event.linkedEventIds())
        assertTrue(event.eventHints().isEmpty())
        assertEquals(listOf(other), event.linkedPubKeys())
        assertEquals(listOf(other), event.pubKeyHints().map { it.pubkey })
    }

    @Test
    fun pinnedBookmarkedAndReminderLinkTheirTarget() {
        val pinned = StreamMessagePinnedEvent(id, author, 1, arrayOf(arrayOf("h", channel), arrayOf("e", root, relay)), "", sig)
        assertEquals(listOf(root), pinned.linkedEventIds())
        assertEquals(listOf(relay), pinned.eventHints().map { it.relay.url })

        val bookmarked = StreamMessageBookmarkedEvent(id, author, 1, arrayOf(arrayOf("h", channel), arrayOf("e", root)), "", sig)
        assertEquals(listOf(root), bookmarked.linkedEventIds())
        assertTrue(bookmarked.eventHints().isEmpty())

        val reminder = StreamReminderEvent(id, author, 1, arrayOf(arrayOf("h", channel), arrayOf("p", other), arrayOf("e", root)), "ship it friday", sig)
        assertEquals(listOf(other), reminder.linkedPubKeys())
        assertEquals(listOf(root), reminder.linkedEventIds())
        assertEquals("ship it friday", reminder.indexableContent())
    }

    @Test
    fun scheduledMessageIndexesItsBody() {
        val event = StreamMessageScheduledEvent(id, author, 1, arrayOf(arrayOf("h", channel)), "standup in five", sig)
        assertEquals("standup in five", event.indexableContent())
    }

    @Test
    fun canvasLinksTheExpectedHeadButNotTheNoneSentinel() {
        val withHead = CanvasEvent(id, author, 1, arrayOf(arrayOf("h", channel), arrayOf("expected-revision", root)), "# doc", sig)
        assertEquals(listOf(root), withHead.linkedEventIds())
        assertTrue(withHead.eventHints().isEmpty())

        val fresh = CanvasEvent(id, author, 1, arrayOf(arrayOf("h", channel), arrayOf("expected-revision", "none")), "# doc", sig)
        assertTrue(fresh.linkedEventIds().isEmpty())
    }

    @Test
    fun diffIndexesOnlyTheDescriptionNotTheCode() {
        val meta = DiffMeta(repoUrl = "https://github.com/x/y", commitSha = "abc123", description = "Fix the login race")
        val template = StreamMessageDiffEvent.build(channel, "--- a/f.kt\n+++ b/f.kt\n+val x = 1", meta)
        val event = StreamMessageDiffEvent(id, author, template.createdAt, template.tags, template.content, sig)

        assertEquals("Fix the login race", event.indexableContent())
        val fields = SearchFieldExtractor.extract(event) as IndexableFields.Tiered
        assertEquals(listOf("Fix the login race"), fields.secondary)
        assertEquals(null, fields.text)

        val bare = StreamMessageDiffEvent(id, author, 1, arrayOf(arrayOf("h", channel)), "diff", sig)
        assertEquals("", bare.indexableContent())
    }

    @Test
    fun systemMessageReadsReferencesAndTextFromItsPayload() {
        val payload =
            SystemMessagePayload(
                type = SystemMessagePayload.MESSAGE_DELETED,
                actor = author,
                target = other,
                topic = "Roadmap",
                purpose = "Plan the quarter",
                publicReason = "off-topic spam",
                targetEventId = root,
                participants = listOf(author, "not-a-key"),
            )
        val template = SystemMessageEvent.build(channel, payload)
        val event = SystemMessageEvent(id, author, template.createdAt, template.tags, template.content, sig)

        assertEquals(listOf(author, other), event.linkedPubKeys())
        assertTrue(event.pubKeyHints().isEmpty())
        assertEquals(listOf(root), event.linkedEventIds())
        assertTrue(event.eventHints().isEmpty())
        assertEquals("Roadmap\nPlan the quarter\noff-topic spam", event.indexableContent())
        assertEquals(event.indexableContent(), event.rejoined())

        val broken = SystemMessageEvent(id, author, 1, arrayOf(arrayOf("h", channel)), "{not json", sig)
        assertEquals("", broken.indexableContent())
        assertTrue(broken.linkedPubKeys().isEmpty())
        assertTrue(broken.linkedEventIds().isEmpty())
    }
}
