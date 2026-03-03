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

/**
 * Parse PGN (Portable Game Notation) format per NIP-64 specification
 *
 * Accepts "import format" (human-created, flexible) as per PGN spec
 * Handles:
 * - Tag pairs: [TagName "TagValue"]
 * - Move text in Standard Algebraic Notation (SAN)
 * - Comments: {...}
 * - Variations: (...)
 * - Result markers: 1-0, 0-1, 1/2-1/2, *
 */
object PGNParser {
    /**
     * Parse PGN content into ChessGame
     *
     * @param pgn PGN format string
     * @return Result containing ChessGame or error
     */
    fun parse(pgn: String): Result<ChessGame> =
        runCatching {
            val lines = pgn.lines().map { it.trim() }

            // Extract metadata tags [Key "Value"]
            val metadata = parseMetadata(lines)

            // Extract move text (everything after metadata)
            val moveText =
                lines
                    .dropWhile { it.startsWith("[") || it.isEmpty() }
                    .joinToString(" ")

            val (moves, result) = parseMoves(moveText)

            // Generate positions by replaying moves
            val positions = generatePositions(moves)

            ChessGame(
                metadata = metadata,
                moves = moves,
                positions = positions,
                result = result,
            )
        }

    /**
     * Extract PGN tag pairs from lines
     * Format: [TagName "TagValue"]
     */
    private fun parseMetadata(lines: List<String>): Map<String, String> {
        val tagRegex = """\[(\w+)\s+"([^"]*)"\]""".toRegex()
        return lines
            .mapNotNull { tagRegex.matchEntire(it) }
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    /**
     * Parse move text into list of moves and game result
     */
    private fun parseMoves(moveText: String): Pair<List<ChessMove>, GameResult> {
        // Remove comments {...} and variations (...)
        val cleaned =
            moveText
                .replace(Regex("""\{[^}]*\}"""), "") // Remove comments
                .replace(Regex("""\([^)]*\)"""), "") // Remove variations
                .replace(Regex("""\$\d+"""), "") // Remove NAG annotations
                .trim()

        // Extract result marker
        val result =
            when {
                cleaned.contains("1-0") -> GameResult.WHITE_WINS
                cleaned.contains("0-1") -> GameResult.BLACK_WINS
                cleaned.contains("1/2-1/2") -> GameResult.DRAW
                else -> GameResult.IN_PROGRESS
            }

        // Remove result marker from move text
        val movesOnly =
            cleaned
                .replace("1-0", "")
                .replace("0-1", "")
                .replace("1/2-1/2", "")
                .replace("*", "")
                .replace(Regex("""\s+"""), " ") // Normalize whitespace
                .trim()

        // Parse move pairs: "1. e4 e5 2. Nf3 Nc6"
        val moveRegex = """(\d+)\.\s*([^\s]+)(?:\s+([^\s]+))?""".toRegex()
        val moves = mutableListOf<ChessMove>()

        moveRegex.findAll(movesOnly).forEach { match ->
            val moveNum = match.groupValues[1].toIntOrNull() ?: return@forEach
            val whiteMove = match.groupValues[2]
            val blackMove = match.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }

            // Skip if this is just a result marker
            if (!isResultMarker(whiteMove)) {
                moves.add(parseMove(whiteMove, moveNum, Color.WHITE))
            }

            blackMove?.let {
                if (!isResultMarker(it)) {
                    moves.add(parseMove(it, moveNum, Color.BLACK))
                }
            }
        }

        return moves to result
    }

    /**
     * Check if text is a game result marker
     * Valid results: 1-0, 0-1, 1/2-1/2, *
     */
    private fun isResultMarker(text: String): Boolean = text == "1-0" || text == "0-1" || text == "1/2-1/2" || text == "*"

    /**
     * Parse a single move in Standard Algebraic Notation (SAN)
     *
     * Examples:
     * - e4 (pawn move)
     * - Nf3 (knight to f3)
     * - Bxe5 (bishop captures on e5)
     * - O-O (kingside castling)
     * - O-O-O (queenside castling)
     * - e8=Q (pawn promotion to queen)
     * - Nbd2 (knight from b-file to d2)
     * - R1a3 (rook from rank 1 to a3)
     * - Qh4+ (queen to h4 with check)
     * - Qh4# (queen to h4 with checkmate)
     */
    private fun parseMove(
        san: String,
        moveNumber: Int,
        color: Color,
    ): ChessMove {
        // Clean up annotations
        var text = san.replace(Regex("[!?]+"), "").trim()

        val isCheck = text.contains("+")
        val isCheckmate = text.contains("#")
        val isCapture = text.contains("x")

        // Remove check/checkmate markers
        text = text.replace("+", "").replace("#", "")

        // Castling
        if (text == "O-O" || text == "0-0") {
            return ChessMove(
                san = san,
                moveNumber = moveNumber,
                color = color,
                piece = PieceType.KING,
                toSquare = if (color == Color.WHITE) "g1" else "g8",
                isCheck = isCheck,
                isCheckmate = isCheckmate,
                isCastling = true,
            )
        }

        if (text == "O-O-O" || text == "0-0-0") {
            return ChessMove(
                san = san,
                moveNumber = moveNumber,
                color = color,
                piece = PieceType.KING,
                toSquare = if (color == Color.WHITE) "c1" else "c8",
                isCheck = isCheck,
                isCheckmate = isCheckmate,
                isCastling = true,
            )
        }

        // Remove capture marker
        text = text.replace("x", "")

        // Check for promotion (e.g., e8=Q)
        val promotionRegex = """([a-h][18])=([QRBN])""".toRegex()
        val promotionMatch = promotionRegex.find(text)
        val promotion = promotionMatch?.groupValues?.get(2)?.let { PieceType.fromSymbol(it[0]) }
        if (promotionMatch != null) {
            text = text.replace(promotionRegex, promotionMatch.groupValues[1])
        }

        // Determine piece type (first char if uppercase, otherwise pawn)
        val piece =
            if (text.isNotEmpty() && text[0].isUpperCase()) {
                PieceType.fromSymbol(text[0]) ?: PieceType.PAWN
            } else {
                PieceType.PAWN
            }

        // Remove piece symbol if present
        if (piece != PieceType.PAWN && text.isNotEmpty()) {
            text = text.substring(1)
        }

        // Extract destination square (last 2 chars should be file+rank)
        val squareRegex = """([a-h][1-8])$""".toRegex()
        val squareMatch = squareRegex.find(text)
        val toSquare = squareMatch?.value ?: ""

        // Extract disambiguation (file or rank between piece and destination)
        val fromSquare = text.replace(toSquare, "").takeIf { it.isNotEmpty() }

        return ChessMove(
            san = san,
            moveNumber = moveNumber,
            color = color,
            piece = piece,
            fromSquare = fromSquare,
            toSquare = toSquare,
            isCapture = isCapture,
            isCheck = isCheck,
            isCheckmate = isCheckmate,
            promotion = promotion,
        )
    }

    /**
     * Generate list of positions by replaying moves from starting position
     *
     * Note: This implementation uses simplified move application.
     * Full legal move validation would require complete chess engine logic.
     */
    private fun generatePositions(moves: List<ChessMove>): List<ChessPosition> {
        val positions = mutableListOf(ChessPosition.initial())
        var current = ChessPosition.initial()

        moves.forEach { move ->
            try {
                current =
                    when {
                        move.isCastling -> {
                            // Castling
                            val kingSide = move.toSquare.contains("g")
                            current.makeCastlingMove(kingSide)
                        }

                        move.fromSquare != null -> {
                            // Move with disambiguation (we can infer full source)
                            // For now, just use simplified move
                            makeSimplifiedMove(current, move)
                        }

                        else -> {
                            // Regular move
                            makeSimplifiedMove(current, move)
                        }
                    }
                positions.add(current)
            } catch (e: Exception) {
                // If move application fails, keep previous position
                positions.add(current)
            }
        }

        return positions
    }

    /**
     * Simplified move application without full legal move validation
     * Finds piece that can move to destination and creates new position
     */
    private fun makeSimplifiedMove(
        position: ChessPosition,
        move: ChessMove,
    ): ChessPosition {
        // For MVP: Try to find a piece of the right type that could move to destination
        // This is simplified and doesn't validate full chess rules
        val targetSquare = move.toSquare

        // Find all pieces of the moving color and type
        val candidates = mutableListOf<String>()
        for (rank in 0..7) {
            for (file in 0..7) {
                val piece = position.pieceAt(file, rank)
                if (piece != null &&
                    piece.color == move.color &&
                    piece.type == move.piece
                ) {
                    val square = "${'a' + file}${rank + 1}"
                    candidates.add(square)
                }
            }
        }

        // Use disambiguation if provided
        val fromSquare =
            if (move.fromSquare != null) {
                candidates.firstOrNull { it.contains(move.fromSquare) }
            } else {
                candidates.firstOrNull()
            } ?: ""

        return if (fromSquare.isNotEmpty()) {
            position.makeMove(fromSquare, targetSquare, move.promotion)
        } else {
            // If we can't find source, return current position unchanged
            position
        }
    }
}
