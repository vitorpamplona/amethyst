/**
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

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move

/**
 * JVM/Android implementation of ChessEngine using kchesslib
 */
actual class ChessEngine {
    private val board = Board()

    actual fun getFen(): String = board.fen

    actual fun loadFen(fen: String) {
        board.loadFromFen(fen)
    }

    actual fun reset() {
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
    }

    actual fun makeMove(san: String): MoveResult =
        try {
            val move = board.doMove(san)
            if (move != null) {
                MoveResult(
                    success = true,
                    san = san,
                    position = boardToPosition(),
                )
            } else {
                board.undoMove()
                MoveResult(
                    success = false,
                    error = "Invalid move: $san",
                )
            }
        } catch (e: Exception) {
            MoveResult(
                success = false,
                error = e.message ?: "Unknown error making move $san",
            )
        }

    actual fun makeMove(
        from: String,
        to: String,
        promotion: PieceType?,
    ): MoveResult =
        try {
            val fromSquare = Square.fromValue(from.uppercase())
            val toSquare = Square.fromValue(to.uppercase())

            val promotionPiece =
                when (promotion) {
                    PieceType.QUEEN -> if (board.sideToMove == Side.WHITE) Piece.WHITE_QUEEN else Piece.BLACK_QUEEN
                    PieceType.ROOK -> if (board.sideToMove == Side.WHITE) Piece.WHITE_ROOK else Piece.BLACK_ROOK
                    PieceType.BISHOP -> if (board.sideToMove == Side.WHITE) Piece.WHITE_BISHOP else Piece.BLACK_BISHOP
                    PieceType.KNIGHT -> if (board.sideToMove == Side.WHITE) Piece.WHITE_KNIGHT else Piece.BLACK_KNIGHT
                    else -> Piece.NONE
                }

            val move = Move(fromSquare, toSquare, promotionPiece)

            if (board.legalMoves().contains(move)) {
                board.doMove(move)
                val san = move.toString() // kchesslib converts to SAN
                MoveResult(
                    success = true,
                    san = san,
                    position = boardToPosition(),
                )
            } else {
                MoveResult(
                    success = false,
                    error = "Illegal move: $from to $to",
                )
            }
        } catch (e: Exception) {
            MoveResult(
                success = false,
                error = e.message ?: "Unknown error making move $from to $to",
            )
        }

    actual fun getLegalMoves(): List<String> = board.legalMoves().map { it.toString() }

    actual fun getLegalMovesFrom(square: String): List<String> {
        val sq = Square.fromValue(square.uppercase())
        return board
            .legalMoves()
            .filter { it.from == sq }
            .map { it.to.toString().lowercase() }
    }

    actual fun isLegalMove(san: String): Boolean =
        try {
            val move = board.doMove(san)
            if (move != null) {
                board.undoMove()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }

    actual fun isLegalMove(
        from: String,
        to: String,
        promotion: PieceType?,
    ): Boolean =
        try {
            val fromSquare = Square.fromValue(from.uppercase())
            val toSquare = Square.fromValue(to.uppercase())

            val promotionPiece =
                when (promotion) {
                    PieceType.QUEEN -> if (board.sideToMove == Side.WHITE) Piece.WHITE_QUEEN else Piece.BLACK_QUEEN
                    PieceType.ROOK -> if (board.sideToMove == Side.WHITE) Piece.WHITE_ROOK else Piece.BLACK_ROOK
                    PieceType.BISHOP -> if (board.sideToMove == Side.WHITE) Piece.WHITE_BISHOP else Piece.BLACK_BISHOP
                    PieceType.KNIGHT -> if (board.sideToMove == Side.WHITE) Piece.WHITE_KNIGHT else Piece.BLACK_KNIGHT
                    else -> Piece.NONE
                }

            val move = Move(fromSquare, toSquare, promotionPiece)
            board.legalMoves().contains(move)
        } catch (e: Exception) {
            false
        }

    actual fun isCheckmate(): Boolean = board.isMated

    actual fun isStalemate(): Boolean = board.isStaleMate

    actual fun isInCheck(): Boolean = board.isKingAttacked

    actual fun undoMove() {
        board.undoMove()
    }

    actual fun getPosition(): ChessPosition = boardToPosition()

    actual fun getSideToMove(): Color =
        when (board.sideToMove) {
            Side.WHITE -> Color.WHITE
            Side.BLACK -> Color.BLACK
        }

    actual fun getMoveHistory(): List<String> {
        // kchesslib stores move history
        return board.backup.map { it.move.toString() }
    }

    /**
     * Convert kchesslib Board to our ChessPosition model
     */
    private fun boardToPosition(): ChessPosition {
        val positionArray = Array(8) { Array<ChessPiece?>(8) { null } }

        for (rank in 0..7) {
            for (file in 0..7) {
                val square = Square.squareAt(rank * 8 + file)
                val piece = board.getPiece(square)

                if (piece != Piece.NONE) {
                    val pieceType =
                        when (piece.pieceType) {
                            com.github.bhlangonijr.chesslib.PieceType.KING -> PieceType.KING
                            com.github.bhlangonijr.chesslib.PieceType.QUEEN -> PieceType.QUEEN
                            com.github.bhlangonijr.chesslib.PieceType.ROOK -> PieceType.ROOK
                            com.github.bhlangonijr.chesslib.PieceType.BISHOP -> PieceType.BISHOP
                            com.github.bhlangonijr.chesslib.PieceType.KNIGHT -> PieceType.KNIGHT
                            com.github.bhlangonijr.chesslib.PieceType.PAWN -> PieceType.PAWN
                            else -> PieceType.PAWN
                        }

                    val color =
                        when (piece.pieceSide) {
                            Side.WHITE -> Color.WHITE
                            Side.BLACK -> Color.BLACK
                            else -> Color.WHITE
                        }

                    positionArray[rank][file] = ChessPiece(pieceType, color)
                }
            }
        }

        return ChessPosition(
            board = positionArray,
            activeColor = getSideToMove(),
            moveNumber = board.moveCounter,
            castlingRights =
                CastlingRights(
                    whiteKingSide = board.castleRight.toString().contains("K"),
                    whiteQueenSide = board.castleRight.toString().contains("Q"),
                    blackKingSide = board.castleRight.toString().contains("k"),
                    blackQueenSide = board.castleRight.toString().contains("q"),
                ),
            enPassantSquare = board.enPassantTarget?.let { it.toString().lowercase() },
            halfMoveClock = board.halfMoveCounter,
        )
    }
}
