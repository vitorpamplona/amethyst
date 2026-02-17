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
package com.vitorpamplona.quartz.nip64Chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for JesterEvent (Jester Protocol kind 30 events)
 *
 * Verifies:
 * - Event kind is 30
 * - JSON content parsing (version, kind, fen, move, history)
 * - e-tag structure for event linking
 * - p-tag for opponent tagging
 * - Start vs Move event detection
 * - Compatibility with jesterui protocol
 *
 * Reference: https://github.com/jesterui/jesterui/blob/devel/FLOW.md
 */
class JesterEventTest {
    private val testPubkey = "abc123def456"
    private val opponentPubkey = "opponent789xyz"
    private val startEventId = "start-event-id-001"

    // ==========================================================================
    // PROTOCOL CONSTANTS
    // ==========================================================================

    @Test
    fun `verify event kind is 30`() {
        assertEquals(30, JesterProtocol.KIND, "Jester protocol uses kind 30")
        assertEquals(30, JesterEvent.KIND, "JesterEvent.KIND should be 30")
    }

    @Test
    fun `verify start position hash constant`() {
        assertEquals(
            "b1791d7fc9ae3d38966568c257ffb3a02cbf8394cdb4805bc70f64fc3c0b6879",
            JesterProtocol.START_POSITION_HASH,
            "Should match jesterui's START_POSITION_HASH",
        )
    }

    @Test
    fun `verify starting FEN constant`() {
        assertEquals(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            JesterProtocol.FEN_START,
            "Standard chess starting position",
        )
    }

    @Test
    fun `verify content kind constants`() {
        assertEquals(0, JesterProtocol.CONTENT_KIND_START, "Start event kind should be 0")
        assertEquals(1, JesterProtocol.CONTENT_KIND_MOVE, "Move event kind should be 1")
        assertEquals(2, JesterProtocol.CONTENT_KIND_CHAT, "Chat event kind should be 2")
    }

    // ==========================================================================
    // START EVENT TESTS
    // ==========================================================================

    @Test
    fun `parse start event - open challenge`() {
        val content = """{"version":"0","kind":0,"fen":"${JesterProtocol.FEN_START}","history":[],"nonce":"abc12345","playerColor":"white"}"""
        val event =
            JesterEvent(
                id = startEventId,
                pubKey = testPubkey,
                createdAt = 1000L,
                tags =
                    arrayOf(
                        arrayOf("e", JesterProtocol.START_POSITION_HASH),
                    ),
                content = content,
                sig = "test_sig",
            )

        assertEquals(30, event.kind)
        assertTrue(event.isStartEvent(), "Should be detected as start event")
        assertFalse(event.isMoveEvent(), "Should not be a move event")
        assertEquals(0, event.contentKind())
        assertEquals(JesterProtocol.FEN_START, event.fen())
        assertEquals(Color.WHITE, event.playerColor())
        assertEquals("abc12345", event.nonce())
        assertTrue(event.history().isEmpty(), "Start event has no history")
        assertNull(event.opponentPubkey(), "Open challenge has no opponent")
    }

    @Test
    fun `parse start event - private challenge`() {
        val content = """{"version":"0","kind":0,"fen":"${JesterProtocol.FEN_START}","history":[],"nonce":"xyz98765","playerColor":"black"}"""
        val event =
            JesterEvent(
                id = startEventId,
                pubKey = testPubkey,
                createdAt = 1000L,
                tags =
                    arrayOf(
                        arrayOf("e", JesterProtocol.START_POSITION_HASH),
                        arrayOf("p", opponentPubkey),
                    ),
                content = content,
                sig = "test_sig",
            )

        assertTrue(event.isStartEvent())
        assertEquals(Color.BLACK, event.playerColor())
        assertEquals(opponentPubkey, event.opponentPubkey(), "Private challenge should have opponent")
    }

    @Test
    fun `start event e-tag references START_POSITION_HASH`() {
        val content = """{"version":"0","kind":0,"fen":"${JesterProtocol.FEN_START}","history":[]}"""
        val event =
            JesterEvent(
                id = startEventId,
                pubKey = testPubkey,
                createdAt = 1000L,
                tags =
                    arrayOf(
                        arrayOf("e", JesterProtocol.START_POSITION_HASH),
                    ),
                content = content,
                sig = "test_sig",
            )

        val eTags = event.eTags()
        assertEquals(1, eTags.size)
        assertEquals(JesterProtocol.START_POSITION_HASH, eTags[0], "Start event should reference START_POSITION_HASH")
    }

    // ==========================================================================
    // MOVE EVENT TESTS
    // ==========================================================================

    @Test
    fun `parse move event - first move e4`() {
        val content = """{"version":"0","kind":1,"fen":"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1","move":"e4","history":["e4"]}"""
        val event =
            JesterEvent(
                id = "move-001",
                pubKey = testPubkey,
                createdAt = 2000L,
                tags =
                    arrayOf(
                        arrayOf("e", startEventId),
                        arrayOf("e", startEventId), // For first move, head is also start
                        arrayOf("p", opponentPubkey),
                    ),
                content = content,
                sig = "test_sig",
            )

        assertFalse(event.isStartEvent(), "Should not be a start event")
        assertTrue(event.isMoveEvent(), "Should be detected as move event")
        assertEquals(1, event.contentKind())
        assertEquals("e4", event.move())
        assertEquals(listOf("e4"), event.history())
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", event.fen())
        assertEquals(opponentPubkey, event.opponentPubkey())
    }

    @Test
    fun `parse move event - multiple moves in history`() {
        val content = """{"version":"0","kind":1,"fen":"rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2","move":"Nf3","history":["e4","e5","Nf3"]}"""
        val event =
            JesterEvent(
                id = "move-003",
                pubKey = testPubkey,
                createdAt = 4000L,
                tags =
                    arrayOf(
                        arrayOf("e", startEventId),
                        arrayOf("e", "move-002"), // Previous move
                        arrayOf("p", opponentPubkey),
                    ),
                content = content,
                sig = "test_sig",
            )

        assertTrue(event.isMoveEvent())
        assertEquals("Nf3", event.move())
        assertEquals(listOf("e4", "e5", "Nf3"), event.history())
        assertEquals(3, event.history().size)
    }

    @Test
    fun `move event e-tags structure - startEventId and headEventId`() {
        val content = """{"version":"0","kind":1,"fen":"test","move":"e4","history":["e4"]}"""
        val headEventId = "move-002"
        val event =
            JesterEvent(
                id = "move-003",
                pubKey = testPubkey,
                createdAt = 3000L,
                tags =
                    arrayOf(
                        arrayOf("e", startEventId),
                        arrayOf("e", headEventId),
                        arrayOf("p", opponentPubkey),
                    ),
                content = content,
                sig = "test_sig",
            )

        assertEquals(startEventId, event.startEventId(), "First e-tag should be startEventId")
        assertEquals(headEventId, event.headEventId(), "Second e-tag should be headEventId")

        val eTags = event.eTags()
        assertEquals(2, eTags.size)
        assertEquals(startEventId, eTags[0])
        assertEquals(headEventId, eTags[1])
    }

    // ==========================================================================
    // GAME END EVENT TESTS
    // ==========================================================================

    @Test
    fun `parse game end event - checkmate`() {
        val content = """{"version":"0","kind":1,"fen":"r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4","move":"Qxf7#","history":["e4","e5","Qh5","Nc6","Bc4","Nf6","Qxf7#"],"result":"1-0","termination":"checkmate"}"""
        val event =
            JesterEvent(
                id = "move-007",
                pubKey = testPubkey,
                createdAt = 8000L,
                tags =
                    arrayOf(
                        arrayOf("e", startEventId),
                        arrayOf("e", "move-006"),
                        arrayOf("p", opponentPubkey),
                    ),
                content = content,
                sig = "test_sig",
            )

        assertTrue(event.isMoveEvent(), "End event is still a move event")
        assertEquals("1-0", event.result(), "Should have result")
        assertEquals("checkmate", event.termination(), "Should have termination reason")
        assertEquals(7, event.history().size)
    }

    @Test
    fun `parse game end event - resignation`() {
        val content = """{"version":"0","kind":1,"fen":"test","move":"e5","history":["e4","e5"],"result":"0-1","termination":"resignation"}"""
        val event =
            JesterEvent(
                id = "move-002",
                pubKey = opponentPubkey,
                createdAt = 3000L,
                tags =
                    arrayOf(
                        arrayOf("e", startEventId),
                        arrayOf("e", "move-001"),
                        arrayOf("p", testPubkey),
                    ),
                content = content,
                sig = "test_sig",
            )

        assertEquals("0-1", event.result(), "Black wins")
        assertEquals("resignation", event.termination())
    }

    @Test
    fun `parse game end event - draw`() {
        val content = """{"version":"0","kind":1,"fen":"test","move":"Kf1","history":["e4","e5","Kf1"],"result":"1/2-1/2","termination":"draw_agreement"}"""
        val event =
            JesterEvent(
                id = "move-003",
                pubKey = testPubkey,
                createdAt = 4000L,
                tags =
                    arrayOf(
                        arrayOf("e", startEventId),
                        arrayOf("e", "move-002"),
                        arrayOf("p", opponentPubkey),
                    ),
                content = content,
                sig = "test_sig",
            )

        assertEquals("1/2-1/2", event.result(), "Draw")
        assertEquals("draw_agreement", event.termination())
    }

    // ==========================================================================
    // EDGE CASES AND ERROR HANDLING
    // ==========================================================================

    @Test
    fun `handle malformed JSON content gracefully`() {
        val event =
            JesterEvent(
                id = "test",
                pubKey = testPubkey,
                createdAt = 1000L,
                tags = emptyArray(),
                content = "invalid json {{{}",
                sig = "test_sig",
            )

        assertNull(event.contentKind(), "Should return null for invalid JSON")
        assertFalse(event.isStartEvent(), "Should not crash on invalid content")
        assertFalse(event.isMoveEvent(), "Should not crash on invalid content")
        assertNull(event.fen())
        assertNull(event.move())
        assertTrue(event.history().isEmpty())
    }

    @Test
    fun `handle empty content`() {
        val event =
            JesterEvent(
                id = "test",
                pubKey = testPubkey,
                createdAt = 1000L,
                tags = emptyArray(),
                content = "",
                sig = "test_sig",
            )

        assertNull(event.contentKind())
        assertFalse(event.isStartEvent())
        assertFalse(event.isMoveEvent())
    }

    @Test
    fun `handle missing optional fields`() {
        // Minimal valid content with only required fields
        val content = """{"kind":1}"""
        val event =
            JesterEvent(
                id = "test",
                pubKey = testPubkey,
                createdAt = 1000L,
                tags = emptyArray(),
                content = content,
                sig = "test_sig",
            )

        assertEquals(1, event.contentKind())
        assertNull(event.move())
        assertTrue(event.history().isEmpty())
        assertNull(event.result())
        assertNull(event.termination())
        assertNull(event.playerColor())
    }

    @Test
    fun `handle event with no e-tags`() {
        val content = """{"version":"0","kind":0}"""
        val event =
            JesterEvent(
                id = "test",
                pubKey = testPubkey,
                createdAt = 1000L,
                tags = emptyArray(),
                content = content,
                sig = "test_sig",
            )

        assertNull(event.startEventId(), "No e-tags means no startEventId")
        assertNull(event.headEventId(), "No e-tags means no headEventId")
        assertTrue(event.eTags().isEmpty())
    }

    @Test
    fun `handle event with only one e-tag`() {
        val content = """{"version":"0","kind":1}"""
        val event =
            JesterEvent(
                id = "test",
                pubKey = testPubkey,
                createdAt = 1000L,
                tags =
                    arrayOf(
                        arrayOf("e", startEventId),
                    ),
                content = content,
                sig = "test_sig",
            )

        assertEquals(startEventId, event.startEventId())
        assertNull(event.headEventId(), "Only one e-tag means no headEventId")
    }

    // ==========================================================================
    // JESTER GAME EVENTS CONTAINER TESTS
    // ==========================================================================

    @Test
    fun `JesterGameEvents - empty returns correct values`() {
        val events = JesterGameEvents.empty()

        assertNull(events.startEvent)
        assertTrue(events.moves.isEmpty())
        assertNull(events.latestMove())
        assertEquals(JesterProtocol.FEN_START, events.currentFen())
        assertTrue(events.fullHistory().isEmpty())
        assertFalse(events.isEnded())
        assertNull(events.result())
    }

    @Test
    fun `JesterGameEvents - latestMove returns move with longest history`() {
        val move1 = createTestMoveEvent("move-001", listOf("e4"))
        val move2 = createTestMoveEvent("move-002", listOf("e4", "e5"))
        val move3 = createTestMoveEvent("move-003", listOf("e4", "e5", "Nf3"))

        // Provide moves in random order
        val events =
            JesterGameEvents(
                startEvent = null,
                moves = listOf(move2, move3, move1),
            )

        val latest = events.latestMove()
        assertNotNull(latest)
        assertEquals("move-003", latest.id, "Should return move with longest history")
        assertEquals(3, latest.history().size)
    }

    @Test
    fun `JesterGameEvents - currentFen returns FEN from latest move`() {
        val move1 = createTestMoveEvent("move-001", listOf("e4"), fen = "fen-after-e4")
        val move2 = createTestMoveEvent("move-002", listOf("e4", "e5"), fen = "fen-after-e5")

        val events =
            JesterGameEvents(
                startEvent = null,
                moves = listOf(move1, move2),
            )

        assertEquals("fen-after-e5", events.currentFen())
    }

    @Test
    fun `JesterGameEvents - fullHistory returns history from latest move`() {
        val move1 = createTestMoveEvent("move-001", listOf("e4"))
        val move2 = createTestMoveEvent("move-002", listOf("e4", "e5"))

        val events =
            JesterGameEvents(
                startEvent = null,
                moves = listOf(move1, move2),
            )

        assertEquals(listOf("e4", "e5"), events.fullHistory())
    }

    @Test
    fun `JesterGameEvents - isEnded detects result in latest move`() {
        val normalMove = createTestMoveEvent("move-001", listOf("e4"))
        val endMove = createTestMoveEventWithResult("move-002", listOf("e4", "Qxf7#"), result = "1-0")

        val ongoingGame = JesterGameEvents(startEvent = null, moves = listOf(normalMove))
        val finishedGame = JesterGameEvents(startEvent = null, moves = listOf(normalMove, endMove))

        assertFalse(ongoingGame.isEnded())
        assertTrue(finishedGame.isEnded())
        assertEquals("1-0", finishedGame.result())
    }

    // ==========================================================================
    // EXTENSION FUNCTION TESTS
    // ==========================================================================

    @Test
    fun `isJesterEvent extension detects kind 30`() {
        val jesterEvent =
            JesterEvent(
                id = "test",
                pubKey = testPubkey,
                createdAt = 1000L,
                tags = emptyArray(),
                content = "{}",
                sig = "sig",
            )

        assertTrue(jesterEvent.isJesterEvent())
    }

    @Test
    fun `toJesterEvent converts valid Event`() {
        val event =
            JesterEvent(
                id = "test",
                pubKey = testPubkey,
                createdAt = 1000L,
                tags = emptyArray(),
                content = """{"kind":0}""",
                sig = "sig",
            )

        val jesterEvent = event.toJesterEvent()
        assertNotNull(jesterEvent)
        assertEquals("test", jesterEvent.id)
    }

    // ==========================================================================
    // HELPER FUNCTIONS
    // ==========================================================================

    private fun createTestMoveEvent(
        id: String,
        history: List<String>,
        fen: String = "test-fen",
    ): JesterEvent {
        val historyJson = history.joinToString(",") { "\"$it\"" }
        val content = """{"version":"0","kind":1,"fen":"$fen","move":"${history.last()}","history":[$historyJson]}"""
        return JesterEvent(
            id = id,
            pubKey = testPubkey,
            createdAt = 1000L + history.size * 1000,
            tags =
                arrayOf(
                    arrayOf("e", startEventId),
                    arrayOf("e", "prev-move"),
                    arrayOf("p", opponentPubkey),
                ),
            content = content,
            sig = "sig",
        )
    }

    private fun createTestMoveEventWithResult(
        id: String,
        history: List<String>,
        result: String,
    ): JesterEvent {
        val historyJson = history.joinToString(",") { "\"$it\"" }
        val content = """{"version":"0","kind":1,"fen":"test-fen","move":"${history.last()}","history":[$historyJson],"result":"$result","termination":"checkmate"}"""
        return JesterEvent(
            id = id,
            pubKey = testPubkey,
            createdAt = 1000L + history.size * 1000,
            tags =
                arrayOf(
                    arrayOf("e", startEventId),
                    arrayOf("e", "prev-move"),
                    arrayOf("p", opponentPubkey),
                ),
            content = content,
            sig = "sig",
        )
    }
}
