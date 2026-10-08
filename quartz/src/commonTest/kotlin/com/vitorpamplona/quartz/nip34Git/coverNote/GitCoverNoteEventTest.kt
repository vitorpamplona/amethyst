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
package com.vitorpamplona.quartz.nip34Git.coverNote

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Kind 1624 against two shapes: the spec's (`root`-marked `e`, `k`, `alt`) and the one seen on relays
 * in 2026-10 (unmarked `e` with a relay hint, no `k`, quotes). The fixtures are synthetic — real
 * cover notes are free-text project notes — but carry the same tag shapes.
 */
class GitCoverNoteEventTest {
    private val issueId = "cbcf68a077a39bb82bc8042b9fce5ebd9a6671d157481fe31dc9548aa7f502e9"
    private val issueAuthor = "9cd14d9acdbab7ca162b772c91fa514da8e3248f61f924bf3441e6e72c5f0dee"
    private val quotedId = "6b4b5969a80043b038dd044f3a78dc9af7692af0d98fdb9db972d906585ce8e1"
    private val quotedAuthor = "d84afa5ba1239500e03ce4d6a2b1e5eef1696a814c3f9cc25b50721bedef0a0f"
    private val repoAddress = "30617:$quotedAuthor:wyrd"

    private fun event(
        tags: Array<Array<String>>,
        content: String = "**Status:** blocked on the relay-close fix.\n\nSee the linked PR.",
    ) = EventFactory.create<Event>("1".repeat(64), "2".repeat(64), 1_791_376_671L, GitCoverNoteEvent.KIND, tags, content, "0".repeat(128))

    // The spec's shape.
    private val specShape =
        arrayOf(
            arrayOf("e", issueId, "wss://relay.ngit.dev", "root"),
            arrayOf("p", issueAuthor),
            arrayOf("k", "1621"),
            arrayOf("alt", "Cover note for a git issue or PR"),
        )

    // The shape observed on relays: no marker, no `k`, a quote with a relay hint and author.
    private val observedShape =
        arrayOf(
            arrayOf("e", issueId, "wss://relay.ngit.dev"),
            arrayOf("p", issueAuthor),
            arrayOf("alt", "cover note for issue"),
            arrayOf("q", quotedId, "wss://grasp.t5.st", quotedAuthor),
            arrayOf("q", repoAddress, "wss://grasp.t5.st"),
            arrayOf("nonce", "0", "0", "ngit-created-at-tiebreak"),
        )

    @Test
    fun factoryBuildsTheCoverNote() {
        assertIs<GitCoverNoteEvent>(event(specShape))
        assertTrue(EventFactory.isKnownKind(GitCoverNoteEvent.KIND))
    }

    @Test
    fun readsTheSpecShape() {
        val note = assertIs<GitCoverNoteEvent>(event(specShape))
        assertEquals(issueId, note.rootEventId())
        assertEquals(MarkedETag.MARKER.ROOT, note.rootEvent()?.marker)
        assertEquals("wss://relay.ngit.dev/", note.rootEvent()?.relay?.url)
        assertEquals(issueAuthor, note.rootAuthor())
        assertEquals(1621, note.rootKind())
    }

    @Test
    fun readsTheObservedShapeWithAnUnmarkedRoot() {
        val note = assertIs<GitCoverNoteEvent>(event(observedShape))
        assertEquals(issueId, note.rootEventId())
        assertEquals(issueAuthor, note.rootAuthor())
        assertNull(note.rootKind())
        assertEquals(2, note.quotes().size)
    }

    @Test
    fun aMarkedRootWinsOverAnEarlierUnmarkedE() {
        val other = "f".repeat(64)
        val note = assertIs<GitCoverNoteEvent>(event(arrayOf(arrayOf("e", other, ""), arrayOf("e", issueId, "", "root"))))
        assertEquals(issueId, note.rootEventId())
    }

    @Test
    fun malformedTagsAreSkippedNotThrown() {
        val note =
            assertIs<GitCoverNoteEvent>(
                event(
                    arrayOf(
                        arrayOf("e"),
                        arrayOf("e", "not-an-id", "wss://relay.ngit.dev", "root"),
                        arrayOf("p", "short"),
                        arrayOf("k", "issue"),
                        arrayOf("q", "30617:short:repo"),
                        arrayOf("q"),
                    ),
                ),
            )
        assertNull(note.rootEventId())
        assertNull(note.rootAuthor())
        assertNull(note.rootKind())
        assertEquals(emptyList(), note.linkedEventIds())
        assertEquals(emptyList(), note.linkedPubKeys())
        assertEquals(emptyList(), note.linkedAddressIds())
    }

    @Test
    fun graphEdgesAndHints() {
        val note = assertIs<GitCoverNoteEvent>(event(observedShape))
        assertEquals(listOf(issueId, quotedId), note.linkedEventIds())
        assertEquals(listOf(issueAuthor), note.linkedPubKeys())
        assertEquals(listOf(repoAddress), note.linkedAddressIds())

        assertEquals(listOf(issueId to "wss://relay.ngit.dev/", quotedId to "wss://grasp.t5.st/"), note.eventHints().map { it.eventId to it.relay.url })
        assertEquals(listOf(repoAddress to "wss://grasp.t5.st/"), note.addressHints().map { it.addressId to it.relay.url })
        // `p` carries no relay, so there is no pubkey hint.
        assertEquals(emptyList(), note.pubKeyHints())
    }

    @Test
    fun nostrUrisInTheBodyAreMentions() {
        val nevent = "nostr:nevent1qqsxkj6edx5qqsas8rwsgne60rwf4amf9tcdnr7mnkuh9kgxtpww3cgpz9mhxue69uhkwunpwdczuap49eehg7dvfc2"
        val note = assertIs<GitCoverNoteEvent>(event(specShape, "Implements $nevent."))
        assertTrue(note.linkedEventIds().contains(quotedId))
    }

    @Test
    fun indexesTheMarkdownBody() {
        val note = event(specShape)
        assertIs<SearchableEvent>(note)
        assertEquals(note.content, note.indexableContent())

        val visited = mutableListOf<String>()
        note.forEachIndexableField { f ->
            if (f != null) visited.add(f)
            true
        }
        assertEquals(note.indexableContent(), visited.joinToString(note.indexableSeparator()))
    }

    @Test
    fun builderRoundTrip() {
        val issue = EventFactory.create<GitIssueEvent>(issueId, issueAuthor, 1_791_373_000L, GitIssueEvent.KIND, arrayOf(arrayOf("subject", "Recurring events")), "body", "0".repeat(128))
        val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.ngit.dev")
        val template = GitCoverNoteEvent.build("Parked for now.", EventHintBundle(issue, relay), createdAt = 1_791_376_671L)

        assertEquals(
            listOf(
                listOf("e", issueId, "wss://relay.ngit.dev/", "root", issueAuthor),
                listOf("p", issueAuthor),
                listOf("k", "1621"),
            ),
            template.tags.map { it.toList() },
        )

        val note = assertIs<GitCoverNoteEvent>(EventFactory.create<Event>("1".repeat(64), issueAuthor, template.createdAt, template.kind, template.tags, template.content, "0".repeat(128)))
        assertEquals(issueId, note.rootEventId())
        assertEquals(MarkedETag.MARKER.ROOT, note.rootEvent()?.marker)
        assertEquals(issueAuthor, note.rootAuthor())
        assertEquals(GitIssueEvent.KIND, note.rootKind())
        assertEquals("Parked for now.", note.content)
    }

    @Test
    fun builderKeepsTheMarkerPositionalWithoutARelay() {
        val issue = EventFactory.create<GitIssueEvent>(issueId, issueAuthor, 1_791_373_000L, GitIssueEvent.KIND, emptyArray(), "body", "0".repeat(128))
        val template = GitCoverNoteEvent.build("note", EventHintBundle(issue), createdAt = 1L)
        assertEquals(listOf("e", issueId, "", "root", issueAuthor), template.tags.first().toList())
    }
}
