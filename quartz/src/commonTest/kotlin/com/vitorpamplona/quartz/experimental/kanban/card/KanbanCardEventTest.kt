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
package com.vitorpamplona.quartz.experimental.kanban.card

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KanbanCardEventTest {
    private val owner = "e18a1d171a59d874edd336472afeb3a614d3dc83397dd097e922a99dcee02133"
    private val assignee = "82341f882b6eabcd2ba7f1ef90aad961cf074af15b9ef44a09f9d2a8fbfbe6a2"
    private val boardAddress = "30301:$owner:release-board"
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://nos.lol/")!!

    /** The shape of the live cards (a bot mirroring an agent's tasks), with synthetic text. */
    private val cardJson =
        """{"id":"${"0".repeat(64)}","pubkey":"$owner","created_at":1791419706,"kind":30302,"tags":[["title","Write the release notes"],["description","Source: release/t_1\nStatus: blocked\nAssignee: someone"],["a","$boardAddress","wss://nos.lol/"],["s","blocked"],["rank","10"],["u","https://example.com/spec.pdf"],["p","$assignee"],["d","card-t_1"]],"content":"","sig":"${"0".repeat(128)}"}"""

    private fun create(
        tags: TagArray,
        content: String = "",
    ): Event = EventFactory.create("0".repeat(64), owner, 1L, KanbanCardEvent.KIND, tags, content, "")

    @Test
    fun factoryBuildsTheCard() {
        assertTrue(EventFactory.isKnownKind(KanbanCardEvent.KIND))
        assertIs<KanbanCardEvent>(EventFactory.probe(KanbanCardEvent.KIND))
        assertIs<KanbanCardEvent>(Event.fromJson(cardJson))
    }

    @Test
    fun accessors() {
        val card = assertIs<KanbanCardEvent>(Event.fromJson(cardJson))
        assertEquals("card-t_1", card.dTag())
        assertEquals("Write the release notes", card.title())
        assertEquals("Source: release/t_1\nStatus: blocked\nAssignee: someone", card.description())
        assertEquals("blocked", card.status())
        assertEquals(10L, card.rank())
        assertEquals(listOf("https://example.com/spec.pdf"), card.attachments())
        assertEquals(listOf(assignee), card.assigneeKeys())
        assertEquals(boardAddress, card.board()?.toTag())
        assertFalse(card.isTracker())
        assertNull(card.trackedEvent())
        assertNull(card.trackedAddress())
        assertTrue(card is RootScope)
    }

    @Test
    fun edgesAreTheBoardAndTheAssignees() {
        val card = assertIs<KanbanCardEvent>(Event.fromJson(cardJson))
        assertEquals(listOf(boardAddress), card.linkedAddressIds())
        assertEquals(listOf(AddressHint(boardAddress, relay)), card.addressHints())
        assertEquals(listOf(assignee), card.linkedPubKeys())
        assertEquals(emptyList(), card.pubKeyHints())
        assertEquals(emptyList(), card.linkedEventIds())
    }

    @Test
    fun trackerCardsLinkWhatTheyTrack() {
        val tracked = "f".repeat(64)
        val eventTracker =
            assertIs<KanbanCardEvent>(
                create(arrayOf(arrayOf("d", "t1"), arrayOf("a", boardAddress), arrayOf("k", "1"), arrayOf("e", tracked, "wss://nos.lol/"))),
            )
        assertTrue(eventTracker.isTracker())
        assertEquals(1, eventTracker.trackedKind())
        assertEquals(listOf(tracked), eventTracker.linkedEventIds())
        assertEquals(listOf(EventIdHint(tracked, relay)), eventTracker.eventHints())
        assertEquals(listOf(boardAddress), eventTracker.linkedAddressIds())

        val issue = "1621:$assignee:issue-7"
        val addressTracker =
            assertIs<KanbanCardEvent>(create(arrayOf(arrayOf("d", "t2"), arrayOf("a", boardAddress), arrayOf("k", "1621"), arrayOf("a", issue))))
        assertEquals(boardAddress, addressTracker.board()?.toTag())
        assertEquals(issue, addressTracker.trackedAddress()?.toTag())
        assertEquals(listOf(boardAddress, issue), addressTracker.linkedAddressIds())

        val otherBoard = "30301:$assignee:their-board"
        val cardTracker =
            assertIs<KanbanCardEvent>(
                create(arrayOf(arrayOf("d", "t3"), arrayOf("a", boardAddress), arrayOf("k", "30302"), arrayOf("refs/board", otherBoard), arrayOf("refs/card", "their-card"))),
            )
        assertEquals(otherBoard, cardTracker.trackedCardBoard()?.toValue())
        assertEquals("their-card", cardTracker.trackedCardDTag())
        assertEquals(listOf(boardAddress, otherBoard), cardTracker.linkedAddressIds())
    }

    @Test
    fun indexesTitleAndDescription() {
        val card = assertIs<KanbanCardEvent>(Event.fromJson(cardJson))
        assertEquals("Write the release notes\nSource: release/t_1\nStatus: blocked\nAssignee: someone", card.indexableContent())

        val visited = mutableListOf<String>()
        card.forEachIndexableField { field ->
            field?.let { visited.add(it) }
            true
        }
        assertEquals(card.indexableContent(), visited.joinToString(card.indexableSeparator()))
    }

    @Test
    fun fieldbookMembershipsAreNotCards() {
        // Fieldbook's team membership: an `a` to a kind-30300 team, no title.
        val membership =
            create(
                arrayOf(arrayOf("d", "team_abc"), arrayOf("client", "fieldbook"), arrayOf("a", "30300:$assignee:team_abc")),
                """{"v":1,"status":"joined"}""",
            )
        assertEquals(UnrecognizedKind30302Event::class, membership::class)
        assertIs<AddressableEvent>(membership)
        assertFalse(membership is SearchableEvent)
        assertFalse(membership is AddressHintProvider)

        // A card with no title is still a card when it names its board.
        assertIs<KanbanCardEvent>(create(arrayOf(arrayOf("d", "x"), arrayOf("a", boardAddress))))
        assertEquals(UnrecognizedKind30302Event::class, create(emptyArray())::class)
    }

    @Test
    fun malformedTagsAreSkipped() {
        val card =
            assertIs<KanbanCardEvent>(
                create(
                    arrayOf(
                        arrayOf("title", "Card"),
                        arrayOf("a", "30301:not-a-key:board"),
                        arrayOf("a", "garbage"),
                        arrayOf("s", ""),
                        arrayOf("rank", "top"),
                        arrayOf("e", "short"),
                        arrayOf("e", "z".repeat(64)),
                        arrayOf("p", "nope"),
                        arrayOf("k", "one"),
                        arrayOf("refs/board", "30023:$owner:not-a-board"),
                        arrayOf("refs/card", ""),
                    ),
                ),
            )
        assertNull(card.board())
        assertNull(card.status())
        assertNull(card.rank())
        assertNull(card.trackedEvent())
        assertNull(card.trackedKind())
        assertNull(card.trackedCardBoard())
        assertNull(card.trackedCardDTag())
        assertEquals(emptyList(), card.assigneeKeys())
        assertEquals(emptyList(), card.linkedAddressIds())
        assertEquals(emptyList(), card.linkedEventIds())
        assertEquals(emptyList(), card.linkedPubKeys())
    }

    @Test
    fun buildRoundTrips() {
        val board = ATag(30301, owner, "release-board", relay)
        val tracked = ETag("f".repeat(64), relay, null)
        val template =
            KanbanCardEvent.build(
                dTag = "card-1",
                title = "Ship it",
                board = board,
                status = "todo",
                rank = 3,
                description = "Everything else is done",
                assignees = listOf(assignee),
                attachments = listOf("https://example.com/a"),
                alt = "A card representing a task",
                createdAt = 5L,
            ) {
                trackEvent(1, tracked)
            }
        assertEquals(KanbanCardEvent.KIND, template.kind)
        val card = assertIs<KanbanCardEvent>(create(template.tags))
        assertEquals("card-1", card.dTag())
        assertEquals("Ship it", card.title())
        assertEquals(board, card.board())
        assertEquals("todo", card.status())
        assertEquals(3L, card.rank())
        assertEquals("Everything else is done", card.description())
        assertEquals(listOf(assignee), card.assigneeKeys())
        assertEquals(listOf("https://example.com/a"), card.attachments())
        assertTrue(card.isTracker())
        assertEquals(1, card.trackedKind())
        assertEquals(tracked.eventId, card.trackedEvent()?.eventId)

        val cardTracker =
            KanbanCardEvent.build("card-2", "Mirror", board) {
                trackCard(Address(30301, assignee, "their-board"), "their-card")
            }
        val mirror = assertIs<KanbanCardEvent>(create(cardTracker.tags))
        assertEquals(30302, mirror.trackedKind())
        assertEquals("30301:$assignee:their-board", mirror.trackedCardBoard()?.toValue())
        assertEquals("their-card", mirror.trackedCardDTag())
    }
}
