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

import androidx.compose.runtime.Immutable

/**
 * Represents a chess position using 8x8 board array
 * Coordinates: file (a-h) = columns 0-7, rank (1-8) = rows 0-7
 * a1 = [0,0], h1 = [7,0], a8 = [0,7], h8 = [7,7]
 */
@Immutable
data class ChessPosition(
    private val board: Array<Array<ChessPiece?>>,
    val activeColor: Color,
    val moveNumber: Int,
    val castlingRights: CastlingRights = CastlingRights(),
    val enPassantSquare: String? = null,
    val halfMoveClock: Int = 0,
) {
    init {
        require(board.size == 8) { "Board must be 8x8, got ${board.size} ranks" }
        require(board.all { it.size == 8 }) { "Board must be 8x8, some ranks are not 8 files wide" }
    }

    /**
     * Get piece at square using algebraic notation (e.g., "e4")
     */
    operator fun get(square: String): ChessPiece? {
        val (file, rank) = parseSquare(square)
        return board[rank][file]
    }

    /**
     * Get piece at coordinates (file: 0-7, rank: 0-7)
     */
    fun pieceAt(
        file: Int,
        rank: Int,
    ): ChessPiece? = board.getOrNull(rank)?.getOrNull(file)

    /**
     * Create new position with a piece moved
     */
    fun makeMove(
        from: String,
        to: String,
        promotion: PieceType? = null,
    ): ChessPosition {
        val (fromFile, fromRank) = parseSquare(from)
        val (toFile, toRank) = parseSquare(to)

        val newBoard = board.map { it.copyOf() }.toTypedArray()
        val piece = newBoard[fromRank][fromFile] ?: throw IllegalArgumentException("No piece at $from")

        // Handle promotion
        val finalPiece =
            if (promotion != null && piece.type == PieceType.PAWN) {
                piece.copy(type = promotion)
            } else {
                piece
            }

        newBoard[toRank][toFile] = finalPiece
        newBoard[fromRank][fromFile] = null

        return copy(
            board = newBoard,
            activeColor = activeColor.opposite(),
            moveNumber = if (activeColor == Color.BLACK) moveNumber + 1 else moveNumber,
        )
    }

    /**
     * Create new position with castling
     */
    fun makeCastlingMove(kingSide: Boolean): ChessPosition {
        val rank = if (activeColor == Color.WHITE) 0 else 7
        val newBoard = board.map { it.copyOf() }.toTypedArray()

        if (kingSide) {
            // King-side castling (O-O)
            val king = newBoard[rank][4]
            val rook = newBoard[rank][7]
            newBoard[rank][6] = king
            newBoard[rank][5] = rook
            newBoard[rank][4] = null
            newBoard[rank][7] = null
        } else {
            // Queen-side castling (O-O-O)
            val king = newBoard[rank][4]
            val rook = newBoard[rank][0]
            newBoard[rank][2] = king
            newBoard[rank][3] = rook
            newBoard[rank][4] = null
            newBoard[rank][0] = null
        }

        return copy(
            board = newBoard,
            activeColor = activeColor.opposite(),
            moveNumber = if (activeColor == Color.BLACK) moveNumber + 1 else moveNumber,
        )
    }

    private fun parseSquare(square: String): Pair<Int, Int> {
        require(square.length == 2) { "Invalid square notation: $square" }
        val file = square[0].lowercaseChar() - 'a' // 0-7
        val rank = square[1] - '1' // 0-7
        require(file in 0..7 && rank in 0..7) { "Square out of bounds: $square" }
        return file to rank
    }

    companion object {
        /**
         * Standard starting position per FIDE rules
         */
        fun initial(): ChessPosition {
            val board = Array(8) { Array<ChessPiece?>(8) { null } }

            // White pieces (ranks 0-1)
            board[0] =
                arrayOf(
                    ChessPiece(PieceType.ROOK, Color.WHITE),
                    ChessPiece(PieceType.KNIGHT, Color.WHITE),
                    ChessPiece(PieceType.BISHOP, Color.WHITE),
                    ChessPiece(PieceType.QUEEN, Color.WHITE),
                    ChessPiece(PieceType.KING, Color.WHITE),
                    ChessPiece(PieceType.BISHOP, Color.WHITE),
                    ChessPiece(PieceType.KNIGHT, Color.WHITE),
                    ChessPiece(PieceType.ROOK, Color.WHITE),
                )
            board[1] = Array(8) { ChessPiece(PieceType.PAWN, Color.WHITE) }

            // Black pieces (ranks 6-7)
            board[6] = Array(8) { ChessPiece(PieceType.PAWN, Color.BLACK) }
            board[7] =
                arrayOf(
                    ChessPiece(PieceType.ROOK, Color.BLACK),
                    ChessPiece(PieceType.KNIGHT, Color.BLACK),
                    ChessPiece(PieceType.BISHOP, Color.BLACK),
                    ChessPiece(PieceType.QUEEN, Color.BLACK),
                    ChessPiece(PieceType.KING, Color.BLACK),
                    ChessPiece(PieceType.BISHOP, Color.BLACK),
                    ChessPiece(PieceType.KNIGHT, Color.BLACK),
                    ChessPiece(PieceType.ROOK, Color.BLACK),
                )

            return ChessPosition(
                board = board,
                activeColor = Color.WHITE,
                moveNumber = 1,
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChessPosition) return false
        return board.contentDeepEquals(other.board) &&
            activeColor == other.activeColor &&
            moveNumber == other.moveNumber &&
            castlingRights == other.castlingRights &&
            enPassantSquare == other.enPassantSquare &&
            halfMoveClock == other.halfMoveClock
    }

    override fun hashCode(): Int {
        var result = board.contentDeepHashCode()
        result = 31 * result + activeColor.hashCode()
        result = 31 * result + moveNumber
        result = 31 * result + castlingRights.hashCode()
        result = 31 * result + (enPassantSquare?.hashCode() ?: 0)
        result = 31 * result + halfMoveClock
        return result
    }
}

/**
 * Represents a chess piece
 */
@Immutable
data class ChessPiece(
    val type: PieceType,
    val color: Color,
)

/**
 * Castling availability
 */
@Immutable
data class CastlingRights(
    val whiteKingSide: Boolean = true,
    val whiteQueenSide: Boolean = true,
    val blackKingSide: Boolean = true,
    val blackQueenSide: Boolean = true,
)
