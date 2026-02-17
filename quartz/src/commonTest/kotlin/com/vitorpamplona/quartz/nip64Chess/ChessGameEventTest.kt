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
import kotlin.test.assertTrue

/**
 * Tests for ChessGameEvent (NIP-64 Kind 64 event)
 *
 * Verifies:
 * - Event kind is 64
 * - PGN content storage
 * - Alt text support (NIP-31)
 * - Event structure compliance
 */
class ChessGameEventTest {
    private val samplePGN =
        """
        [Event "Test Game"]
        [Site "Internet"]
        [Date "2024.12.28"]
        [Round "1"]
        [White "Alice"]
        [Black "Bob"]
        [Result "1-0"]

        1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# 1-0
        """.trimIndent()

    @Test
    fun `verify event kind is 64`() {
        assertEquals(64, ChessGameEvent.KIND, "Chess game event should be kind 64")
    }

    @Test
    fun `pgn content is accessible`() {
        // Create a mock event manually for testing
        val testEvent =
            ChessGameEvent(
                id = "test_id",
                pubKey = "test_pubkey",
                createdAt = 1000L,
                tags = arrayOf(arrayOf("alt", "Chess Game")),
                content = samplePGN,
                sig = "test_sig",
            )

        assertEquals(samplePGN, testEvent.pgn(), "PGN content should be accessible via pgn()")
        assertEquals(samplePGN, testEvent.content, "PGN should be in content field")
    }

    @Test
    fun `alt text is accessible when present`() {
        val customAltText = "Scholar's Mate Example"
        val testEvent =
            ChessGameEvent(
                id = "test_id",
                pubKey = "test_pubkey",
                createdAt = 1000L,
                tags = arrayOf(arrayOf("alt", customAltText)),
                content = samplePGN,
                sig = "test_sig",
            )

        assertEquals(customAltText, testEvent.altText(), "Alt text should be extractable from tags")
    }

    @Test
    fun `alt text returns null when not present`() {
        val testEvent =
            ChessGameEvent(
                id = "test_id",
                pubKey = "test_pubkey",
                createdAt = 1000L,
                tags = emptyArray(),
                content = samplePGN,
                sig = "test_sig",
            )

        assertEquals(null, testEvent.altText(), "Should return null when no alt tag present")
    }

    @Test
    fun `event can store complete game with metadata`() {
        val testEvent =
            ChessGameEvent(
                id = "test_id",
                pubKey = "test_pubkey",
                createdAt = 1000L,
                tags = arrayOf(arrayOf("alt", "Chess Game")),
                content = samplePGN,
                sig = "test_sig",
            )

        // Parse the PGN to verify it's valid
        val gameResult = PGNParser.parse(testEvent.pgn())
        assertTrue(gameResult.isSuccess, "Event should contain valid PGN")

        val game = gameResult.getOrThrow()
        assertEquals("Test Game", game.event)
        assertEquals("Alice", game.white)
        assertEquals("Bob", game.black)
        assertEquals(GameResult.WHITE_WINS, game.result)
    }

    @Test
    fun `event can store minimal PGN`() {
        val minimalPGN = "1. e4 *"
        val testEvent =
            ChessGameEvent(
                id = "test_id",
                pubKey = "test_pubkey",
                createdAt = 1000L,
                tags = arrayOf(arrayOf("alt", "Chess Game")),
                content = minimalPGN,
                sig = "test_sig",
            )

        val gameResult = PGNParser.parse(testEvent.pgn())
        assertTrue(gameResult.isSuccess, "Should handle minimal PGN")

        val game = gameResult.getOrThrow()
        assertEquals(1, game.moves.size)
        assertEquals(GameResult.IN_PROGRESS, game.result)
    }

    @Test
    fun `event can store game in progress`() {
        val inProgressPGN =
            """
            [Event "Live Game"]
            [Result "*"]

            1. e4 e5 2. Nf3 Nc6 3. Bb5 *
            """.trimIndent()

        val testEvent =
            ChessGameEvent(
                id = "test_id",
                pubKey = "test_pubkey",
                createdAt = 1000L,
                tags = arrayOf(arrayOf("alt", "Live Chess Game")),
                content = inProgressPGN,
                sig = "test_sig",
            )

        val gameResult = PGNParser.parse(testEvent.pgn())
        assertTrue(gameResult.isSuccess)

        val game = gameResult.getOrThrow()
        assertEquals(GameResult.IN_PROGRESS, game.result)
        assertEquals("*", game.metadata["Result"])
    }

    @Test
    fun `event preserves PGN formatting`() {
        val formattedPGN =
            """
            [Event "Formatted Game"]
            [White "Player 1"]
            [Black "Player 2"]

            1. e4 e5
            2. Nf3 Nc6
            3. Bb5 a6
            *
            """.trimIndent()

        val testEvent =
            ChessGameEvent(
                id = "test_id",
                pubKey = "test_pubkey",
                createdAt = 1000L,
                tags = arrayOf(arrayOf("alt", "Chess Game")),
                content = formattedPGN,
                sig = "test_sig",
            )

        // Content should be preserved exactly as provided
        assertEquals(formattedPGN, testEvent.pgn())
    }

    @Test
    fun `default alt text is Chess Game`() {
        assertEquals("Chess Game", ChessGameEvent.ALT_DESCRIPTION)
    }

    @Test
    fun `event inherits from Event base class`() {
        val testEvent =
            ChessGameEvent(
                id = "test_id",
                pubKey = "test_pubkey",
                createdAt = 1000L,
                tags = emptyArray(),
                content = "1. e4 *",
                sig = "test_sig",
            )

        // Verify base Event properties
        assertEquals("test_id", testEvent.id)
        assertEquals("test_pubkey", testEvent.pubKey)
        assertEquals(1000L, testEvent.createdAt)
        assertEquals(64, testEvent.kind)
        assertEquals("test_sig", testEvent.sig)
    }

    @Test
    fun `event can contain long tournament game`() {
        val longPGN =
            """
            [Event "Tournament Game"]
            [Site "Online"]
            [Date "2024.12.28"]
            [Round "5"]
            [White "GM Player"]
            [Black "IM Player"]
            [Result "1/2-1/2"]

            1. d4 Nf6 2. c4 g6 3. Nc3 Bg7 4. e4 d6 5. Nf3 O-O 6. Be2 e5
            7. O-O Nc6 8. d5 Ne7 9. Ne1 Nd7 10. Nd3 f5 11. Bd2 Nf6 12. f3 f4
            13. Rc1 g5 14. Nb5 Ng6 15. c5 Rf7 16. Qa4 h5 17. Rfe1 Bf8
            18. cxd6 cxd6 19. Rc6 Bd7 20. Rec1 Bxc6 21. Rxc6 Qd7 22. Rc1 Rc8
            23. Rxc8 Qxc8 24. Qa6 Qc2 25. Qxb7 Qxb2 26. Qxa7 Qxa2 27. Qb7 Qa1+
            28. Kf2 Qb2 29. Qa7 Ra7 30. Qa4 Qa2 31. Qa8 Qb2 32. Qa4 Qa2 1/2-1/2
            """.trimIndent()

        val testEvent =
            ChessGameEvent(
                id = "test_id",
                pubKey = "test_pubkey",
                createdAt = 1000L,
                tags = arrayOf(arrayOf("alt", "Long Tournament Game")),
                content = longPGN,
                sig = "test_sig",
            )

        val gameResult = PGNParser.parse(testEvent.pgn())
        assertTrue(gameResult.isSuccess)

        val game = gameResult.getOrThrow()
        assertTrue(game.moves.size > 50, "Should handle long games")
        assertEquals(GameResult.DRAW, game.result)
    }

    @Test
    fun `verify tags array structure`() {
        val testEvent =
            ChessGameEvent(
                id = "test_id",
                pubKey = "test_pubkey",
                createdAt = 1000L,
                tags =
                    arrayOf(
                        arrayOf("alt", "Test Alt Text"),
                        arrayOf("t", "chess"),
                        arrayOf("t", "game"),
                    ),
                content = "1. e4 *",
                sig = "test_sig",
            )

        // Verify tags structure
        assertTrue(testEvent.tags.size >= 1)
        assertEquals("alt", testEvent.tags[0][0])
        assertEquals("Test Alt Text", testEvent.tags[0][1])
    }
}
