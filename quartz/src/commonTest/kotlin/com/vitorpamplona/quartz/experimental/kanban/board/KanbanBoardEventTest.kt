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
package com.vitorpamplona.quartz.experimental.kanban.board

import com.vitorpamplona.quartz.experimental.kanban.board.tags.ColumnTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KanbanBoardEventTest {
    private val owner = "e18a1d171a59d874edd336472afeb3a614d3dc83397dd097e922a99dcee02133"
    private val maintainer = "82341f882b6eabcd2ba7f1ef90aad961cf074af15b9ef44a09f9d2a8fbfbe6a2"

    /** The shape of the live boards (a bot mirroring an agent's task board), with synthetic text. */
    private val boardJson =
        """{"id":"${"0".repeat(64)}","pubkey":"$owner","created_at":1791364710,"kind":30301,"tags":[["title","Release Board"],["description","Tasks for the next release. Changes sync both ways."],["col","backlog","Backlog","1"],["col","inprogress","In Progress","2"],["col","review","Human Review","3"],["col","done","Done","5"],["p","$owner"],["p","$maintainer","wss://nos.lol/"],["d","release-board"]],"content":"","sig":"${"0".repeat(128)}"}"""

    private fun create(tags: TagArray): Event = EventFactory.create("0".repeat(64), owner, 1L, KanbanBoardEvent.KIND, tags, "", "")

    @Test
    fun factoryBuildsTheBoard() {
        assertTrue(EventFactory.isKnownKind(KanbanBoardEvent.KIND))
        assertIs<KanbanBoardEvent>(EventFactory.probe(KanbanBoardEvent.KIND))
        assertIs<KanbanBoardEvent>(Event.fromJson(boardJson))
    }

    @Test
    fun accessors() {
        val board = assertIs<KanbanBoardEvent>(Event.fromJson(boardJson))
        assertEquals("release-board", board.dTag())
        assertEquals("Release Board", board.title())
        assertEquals("Tasks for the next release. Changes sync both ways.", board.description())
        assertEquals(listOf("backlog", "inprogress", "review", "done"), board.columns().map { it.id })
        assertEquals(ColumnTag("inprogress", "In Progress", 2), board.columns()[1])
        assertEquals(listOf(owner, maintainer), board.maintainerKeys())
        assertTrue(board.canEditCards(owner))
        assertTrue(board.canEditCards(maintainer))
        assertFalse(board.canEditCards("f".repeat(64)))
        assertTrue(board is RootScope)
    }

    @Test
    fun withoutMaintainersOnlyTheOwnerEditsCards() {
        val board = assertIs<KanbanBoardEvent>(create(arrayOf(arrayOf("title", "Mine"))))
        assertTrue(board.canEditCards(owner))
        assertFalse(board.canEditCards(maintainer))
    }

    @Test
    fun sortedColumnsFollowTheOrder() {
        val board =
            assertIs<KanbanBoardEvent>(
                create(arrayOf(arrayOf("col", "c", "Done", "2"), arrayOf("col", "x", "Someday"), arrayOf("col", "a", "To Do", "0"), arrayOf("col", "b", "Doing", "1"))),
            )
        assertEquals(listOf("a", "b", "c", "x"), board.sortedColumns().map { it.id })
    }

    @Test
    fun maintainersAreTheEdges() {
        val board = assertIs<KanbanBoardEvent>(Event.fromJson(boardJson))
        assertEquals(listOf(owner, maintainer), board.linkedPubKeys())
        assertEquals(listOf(PubKeyHint(maintainer, RelayUrlNormalizer.normalizeOrNull("wss://nos.lol/")!!)), board.pubKeyHints())
    }

    @Test
    fun indexesTitleAndDescription() {
        val board = assertIs<KanbanBoardEvent>(Event.fromJson(boardJson))
        assertEquals("Release Board\nTasks for the next release. Changes sync both ways.", board.indexableContent())

        val visited = mutableListOf<String>()
        board.forEachIndexableField { field ->
            field?.let { visited.add(it) }
            true
        }
        assertEquals(board.indexableContent(), visited.joinToString(board.indexableSeparator()))

        val untitled = assertIs<KanbanBoardEvent>(create(arrayOf(arrayOf("col", "a", "To Do"))))
        assertEquals("", untitled.indexableContent())
    }

    @Test
    fun malformedTagsAreSkipped() {
        val board =
            assertIs<KanbanBoardEvent>(
                create(
                    arrayOf(
                        arrayOf("title", "Board"),
                        arrayOf("description", ""),
                        arrayOf("col", "day"),
                        arrayOf("col", "", "Nameless id"),
                        arrayOf("col", "a", ""),
                        arrayOf("col", "b", "Doing", "first"),
                        arrayOf("p", "not-a-key"),
                        arrayOf("p", "z".repeat(64)),
                    ),
                ),
            )
        assertNull(board.description())
        assertEquals(listOf(ColumnTag("b", "Doing", null)), board.columns())
        assertEquals(emptyList(), board.maintainerKeys())
        assertEquals(emptyList(), board.linkedPubKeys())
    }

    @Test
    fun buildRoundTrips() {
        val columns = listOf(ColumnTag("todo", "To Do", 0), ColumnTag("doing", "In Progress", 1), ColumnTag("done", "Done", 2))
        val template =
            KanbanBoardEvent.build(
                dTag = "my-board",
                title = "My Board",
                columns = columns,
                description = "Things to do",
                maintainers = listOf(maintainer),
                alt = "A board to track my work",
                createdAt = 5L,
            )
        assertEquals(KanbanBoardEvent.KIND, template.kind)
        val board = assertIs<KanbanBoardEvent>(create(template.tags))
        assertEquals("my-board", board.dTag())
        assertEquals("My Board", board.title())
        assertEquals("Things to do", board.description())
        assertEquals(columns, board.columns())
        assertEquals(listOf(maintainer), board.maintainerKeys())
        assertEquals("A board to track my work", board.alt())
    }
}
