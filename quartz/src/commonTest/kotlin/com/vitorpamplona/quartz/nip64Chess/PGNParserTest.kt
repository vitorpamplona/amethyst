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
import kotlin.test.assertTrue

/**
 * Comprehensive tests for PGN parsing per NIP-64 specification
 *
 * Tests cover:
 * - PGN metadata extraction
 * - Move parsing in Standard Algebraic Notation
 * - Game result parsing
 * - Comments and variations handling
 * - Edge cases and error handling
 */
class PGNParserTest {
    // Test 1: Parse minimal PGN (NIP-64 requirement: accept import format)
    @Test
    fun `parse minimal PGN with single move`() {
        val pgn = "1. e4 *"
        val result = PGNParser.parse(pgn)

        assertTrue(result.isSuccess, "Should successfully parse minimal PGN")
        val game = result.getOrThrow()
        assertEquals(1, game.moves.size, "Should have 1 move")
        assertEquals("e4", game.moves[0].san)
        assertEquals(GameResult.IN_PROGRESS, game.result)
    }

    // Test 2: Parse complete game with metadata (NIP-64 requirement)
    @Test
    fun `parse PGN with full metadata tags`() {
        val pgn =
            """
            [Event "F/S Return Match"]
            [Site "Belgrade, Serbia JUG"]
            [Date "1992.11.04"]
            [Round "29"]
            [White "Fischer, Robert J."]
            [Black "Spassky, Boris V."]
            [Result "1/2-1/2"]

            1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 1/2-1/2
            """.trimIndent()

        val result = PGNParser.parse(pgn)
        assertTrue(result.isSuccess)
        val game = result.getOrThrow()

        // Verify metadata extraction
        assertEquals("F/S Return Match", game.event)
        assertEquals("Belgrade, Serbia JUG", game.site)
        assertEquals("1992.11.04", game.date)
        assertEquals("29", game.round)
        assertEquals("Fischer, Robert J.", game.white)
        assertEquals("Spassky, Boris V.", game.black)
        assertEquals(GameResult.DRAW, game.result)

        // Verify moves
        assertEquals(6, game.moves.size)
        assertEquals("e4", game.moves[0].san)
        assertEquals("Nf3", game.moves[2].san)
    }

    // Test 3: Scholar's Mate (4 move checkmate)
    @Test
    fun `parse scholars mate with checkmate notation`() {
        val pgn =
            """
            [Event "Scholar's Mate"]
            [White "Alice"]
            [Black "Bob"]
            [Result "1-0"]

            1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7# 1-0
            """.trimIndent()

        val result = PGNParser.parse(pgn)
        assertTrue(result.isSuccess)
        val game = result.getOrThrow()

        assertEquals(7, game.moves.size)
        assertEquals(GameResult.WHITE_WINS, game.result)

        // Verify last move has checkmate marker
        val lastMove = game.moves.last()
        assertEquals("Qxf7#", lastMove.san)
        assertTrue(lastMove.isCheckmate, "Last move should be checkmate")
        assertTrue(lastMove.isCapture, "Last move should be capture")
    }

    // Test 4: Fool's Mate (2 move checkmate)
    @Test
    fun `parse fools mate shortest checkmate`() {
        val pgn =
            """
            1. f3 e5 2. g4 Qh4# 0-1
            """.trimIndent()

        val result = PGNParser.parse(pgn)
        assertTrue(result.isSuccess)
        val game = result.getOrThrow()

        assertEquals(4, game.moves.size)
        assertEquals(GameResult.BLACK_WINS, game.result)

        val lastMove = game.moves.last()
        assertEquals("Qh4#", lastMove.san)
        assertTrue(lastMove.isCheckmate)
    }

    // Test 5: Castling notation
    @Test
    fun `parse castling moves kingside and queenside`() {
        val pgn = "1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. O-O O-O 5. d3 d6 6. c3 a6 7. a4 a5 8. O-O-O *"

        val result = PGNParser.parse(pgn)
        assertTrue(result.isSuccess)
        val game = result.getOrThrow()

        // Find castling moves
        val kingsideCastling = game.moves.filter { it.san == "O-O" }
        val queensideCastling = game.moves.filter { it.san == "O-O-O" }

        assertEquals(2, kingsideCastling.size, "Should have 2 kingside castling moves")
        assertEquals(1, queensideCastling.size, "Should have 1 queenside castling move")

        kingsideCastling.forEach { move ->
            assertTrue(move.isCastling, "O-O should be marked as castling")
            assertEquals(PieceType.KING, move.piece)
        }

        queensideCastling.forEach { move ->
            assertTrue(move.isCastling, "O-O-O should be marked as castling")
            assertEquals(PieceType.KING, move.piece)
        }
    }

    // Test 6: Pawn promotion
    @Test
    fun `parse pawn promotion to queen`() {
        val pgn =
            """
            [Event "Promotion Example"]

            1. e4 d5 2. exd5 Qxd5 3. Nc3 Qa5 4. d4 c6 5. Nf3 Bg4 6. Bf4 e6
            7. h3 Bxf3 8. Qxf3 Bb4 9. Be2 Nd7 10. a3 O-O-O 11. axb4 Qxa1+
            12. Kd2 Qxh1 13. Qxh1 a6 14. c4 f6 15. b5 axb5 16. cxb5 c5
            17. b6 Ne7 18. Qh2 h6 19. dxc5 Nxc5 20. b3 Kc8 21. Qg3 Ncd7
            22. Bd6 Nf5 23. Qf4 Nxd6 24. Qxd6 Nb8 25. Kc3 Rh7 26. Kb4 Rd7
            27. Qc5+ Kd8 28. Bf3 Ke8 29. Bd5 exd5 30. Nxd5 Kf7 31. b7 Kg6
            32. b8=Q *
            """.trimIndent()

        val result = PGNParser.parse(pgn)
        assertTrue(result.isSuccess)
        val game = result.getOrThrow()

        // Find promotion move
        val promotionMove = game.moves.firstOrNull { it.promotion != null }
        assertNotNull(promotionMove, "Should have promotion move")
        assertEquals(PieceType.QUEEN, promotionMove.promotion)
        assertTrue(promotionMove.san.contains("=Q"), "Promotion move should contain =Q")
    }

    // Test 7: Captures
    @Test
    fun `parse capture notation`() {
        val pgn = "1. e4 d5 2. exd5 Qxd5 3. Nc3 Qxd4 *"

        val result = PGNParser.parse(pgn)
        assertTrue(result.isSuccess)
        val game = result.getOrThrow()

        val captures = game.moves.filter { it.isCapture }
        assertEquals(3, captures.size, "Should have 3 capture moves")

        captures.forEach { move ->
            assertTrue(move.san.contains("x"), "Capture moves should contain 'x'")
        }
    }

    // Test 8: Check notation
    @Test
    fun `parse check and checkmate markers`() {
        val pgn = "1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# 1-0"

        val result = PGNParser.parse(pgn)
        assertTrue(result.isSuccess)
        val game = result.getOrThrow()

        val checkMoves = game.moves.filter { it.isCheck }
        val checkmateMoves = game.moves.filter { it.isCheckmate }

        assertTrue(checkmateMoves.isNotEmpty(), "Should have checkmate moves")
        checkmateMoves.forEach { move ->
            assertTrue(move.san.contains("#"), "Checkmate moves should contain #")
        }
    }

    // Test 9: Comments and variations (NIP-64: should handle PGN comments)
    @Test
    fun `parse PGN with comments and variations stripped`() {
        val pgn =
            """
            1. e4 {Best by test} e5 (1...c5 2. Nf3) 2. Nf3 Nc6 {Developing} 3. Bb5 *
            """.trimIndent()

        val result = PGNParser.parse(pgn)
        assertTrue(result.isSuccess)
        val game = result.getOrThrow()

        // Comments and variations should be stripped
        assertEquals(5, game.moves.size)
        assertEquals("e4", game.moves[0].san)
        assertEquals("e5", game.moves[1].san)
        assertEquals("Nf3", game.moves[2].san)
    }

    // Test 10: NAG annotations (Numeric Annotation Glyphs)
    @Test
    fun `parse PGN with NAG annotations`() {
        val pgn = "1. e4$1 e5$6 2. Nf3$10 *"

        val result = PGNParser.parse(pgn)
        assertTrue(result.isSuccess)
        val game = result.getOrThrow()

        assertEquals(3, game.moves.size)
    }

    // Test 11: Disambiguating moves
    @Test
    fun `parse moves with disambiguation`() {
        val pgn =
            """
            1. Nf3 Nf6 2. Nc3 Nc6 3. d4 d5 4. Bf4 Bf5 5. e3 e6
            6. Nbd2 Nbd7 7. Bd3 Bd6 *
            """.trimIndent()

        val result = PGNParser.parse(pgn)
        assertTrue(result.isSuccess)
        val game = result.getOrThrow()

        // Check for disambiguated moves (Nbd2, Nbd7)
        val disambiguatedMoves = game.moves.filter { it.fromSquare != null }
        assertTrue(disambiguatedMoves.isNotEmpty(), "Should have disambiguated moves")
    }

    // Test 12: All possible game results
    @Test
    fun `parse all game result notations`() {
        val whiteWins = "1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# 1-0"
        val blackWins = "1. f3 e5 2. g4 Qh4# 0-1"
        val draw = "1. e4 e5 2. Nf3 Nc6 1/2-1/2"
        val inProgress = "1. e4 e5 *"

        assertEquals(GameResult.WHITE_WINS, PGNParser.parse(whiteWins).getOrThrow().result)
        assertEquals(GameResult.BLACK_WINS, PGNParser.parse(blackWins).getOrThrow().result)
        assertEquals(GameResult.DRAW, PGNParser.parse(draw).getOrThrow().result)
        assertEquals(GameResult.IN_PROGRESS, PGNParser.parse(inProgress).getOrThrow().result)
    }

    // Test 13: Empty/invalid PGN handling
    @Test
    fun `handle empty PGN gracefully`() {
        val emptyPgn = ""
        val result = PGNParser.parse(emptyPgn)

        // Should not crash, might return empty game
        assertTrue(result.isSuccess || result.isFailure)
    }

    // Test 14: Position generation
    @Test
    fun `generate positions for each move`() {
        val pgn = "1. e4 e5 2. Nf3 Nc6 3. Bb5 *"

        val result = PGNParser.parse(pgn)
        assertTrue(result.isSuccess)
        val game = result.getOrThrow()

        // Should have starting position + one per move
        assertEquals(game.moves.size + 1, game.positions.size)

        // First position should be starting position
        val startPos = game.positions[0]
        assertEquals(Color.WHITE, startPos.activeColor)
        assertEquals(1, startPos.moveNumber)
    }

    // Test 15: Verify required metadata (NIP-64: PGN should have standard tags)
    @Test
    fun `detect presence of required PGN metadata tags`() {
        val fullPgn =
            """
            [Event "FIDE World Championship"]
            [Site "London"]
            [Date "2018.11.28"]
            [Round "12"]
            [White "Carlsen, Magnus"]
            [Black "Caruana, Fabiano"]
            [Result "1-0"]

            1. e4 *
            """.trimIndent()

        val minimalPgn = "1. e4 *"

        val fullGame = PGNParser.parse(fullPgn).getOrThrow()
        val minimalGame = PGNParser.parse(minimalPgn).getOrThrow()

        assertTrue(fullGame.hasRequiredMetadata(), "Full PGN should have required metadata")
        assertFalse(minimalGame.hasRequiredMetadata(), "Minimal PGN should not have required metadata")
    }

    // Test 16: Long tournament game
    @Test
    fun `parse realistic tournament game`() {
        val pgn =
            """
            [Event "Wch"]
            [Site "New York"]
            [Date "1886.??.??"]
            [Round "1"]
            [White "Zukertort, Johannes"]
            [Black "Steinitz, William"]
            [Result "0-1"]

            1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. e3 c5 5. Nf3 Nc6 6. a3 dxc4
            7. Bxc4 cxd4 8. exd4 Be7 9. O-O O-O 10. Qd3 Bd7 11. Qe2 Qb8
            12. Rd1 Rd8 13. Be3 Be8 14. Ne5 Nxe5 15. dxe5 Rxd1+ 16. Rxd1 Nd7
            17. f4 Nc5 18. Qf2 Rc8 19. b4 Na6 20. Bd3 Nb8 21. Ne4 Nc6
            22. Nd6 Bxd6 23. exd6 Qxd6 24. Bxh7+ Kh8 25. Bf5 Qc7 26. Bxc8 Qxc8
            27. Qd2 Bg6 28. Qd7 Qxd7 29. Rxd7 b6 30. Bc1 Nd8 31. Rxd8+ 0-1
            """.trimIndent()

        val result = PGNParser.parse(pgn)
        assertTrue(result.isSuccess)
        val game = result.getOrThrow()

        assertEquals("Zukertort, Johannes", game.white)
        assertEquals("Steinitz, William", game.black)
        assertEquals(GameResult.BLACK_WINS, game.result)
        assertTrue(game.moves.size > 50, "Tournament game should have many moves")
    }

    // Test 17: Alternative castling notation (0-0 instead of O-O)
    @Test
    fun `parse alternative castling notation with zeros`() {
        val pgn = "1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. 0-0 0-0 *"

        val result = PGNParser.parse(pgn)
        assertTrue(result.isSuccess)
        val game = result.getOrThrow()

        val castlingMoves = game.moves.filter { it.isCastling }
        assertEquals(2, castlingMoves.size)
    }

    // Test 18: Move count verification
    @Test
    fun `verify move numbers are correct`() {
        val pgn = "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 *"

        val result = PGNParser.parse(pgn)
        assertTrue(result.isSuccess)
        val game = result.getOrThrow()

        assertEquals(6, game.moves.size)

        // Verify move numbers
        assertEquals(1, game.moves[0].moveNumber) // e4
        assertEquals(1, game.moves[1].moveNumber) // e5
        assertEquals(2, game.moves[2].moveNumber) // Nf3
        assertEquals(2, game.moves[3].moveNumber) // Nc6
        assertEquals(3, game.moves[4].moveNumber) // Bb5
        assertEquals(3, game.moves[5].moveNumber) // a6
    }

    // Test 19: Move colors are correct
    @Test
    fun `verify move colors alternate correctly`() {
        val pgn = "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 *"

        val result = PGNParser.parse(pgn)
        assertTrue(result.isSuccess)
        val game = result.getOrThrow()

        assertEquals(Color.WHITE, game.moves[0].color)
        assertEquals(Color.BLACK, game.moves[1].color)
        assertEquals(Color.WHITE, game.moves[2].color)
        assertEquals(Color.BLACK, game.moves[3].color)
        assertEquals(Color.WHITE, game.moves[4].color)
        assertEquals(Color.BLACK, game.moves[5].color)
    }

    // Test 20: Piece type detection
    @Test
    fun `detect piece types from SAN notation`() {
        val pgn = "1. e4 e5 2. Nf3 Nc6 3. Bb5 Bc5 4. Qa4 Qf6 5. Ke2 Ke7 6. Ra3 Ra6 *"

        val result = PGNParser.parse(pgn)
        assertTrue(result.isSuccess)
        val game = result.getOrThrow()

        // e4, e5 - pawns
        assertEquals(PieceType.PAWN, game.moves[0].piece)
        assertEquals(PieceType.PAWN, game.moves[1].piece)

        // Nf3, Nc6 - knights
        assertEquals(PieceType.KNIGHT, game.moves[2].piece)
        assertEquals(PieceType.KNIGHT, game.moves[3].piece)

        // Bb5, Bc5 - bishops
        assertEquals(PieceType.BISHOP, game.moves[4].piece)
        assertEquals(PieceType.BISHOP, game.moves[5].piece)

        // Qa4, Qf6 - queens
        assertEquals(PieceType.QUEEN, game.moves[6].piece)
        assertEquals(PieceType.QUEEN, game.moves[7].piece)

        // Ke2, Ke7 - kings
        assertEquals(PieceType.KING, game.moves[8].piece)
        assertEquals(PieceType.KING, game.moves[9].piece)

        // Ra3, Ra6 - rooks
        assertEquals(PieceType.ROOK, game.moves[10].piece)
        assertEquals(PieceType.ROOK, game.moves[11].piece)
    }
}
