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

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move

/**
 * JVM/Android implementation of ChessEngine using kchesslib.
 *
 * Maintains its own SAN history because chesslib's Move.toString() returns
 * UCI/coordinate notation (e.g. "e2e4") rather than proper SAN (e.g. "e4").
 */
actual class ChessEngine {
    private val board = Board()
    private val sanHistory = mutableListOf<String>()

    actual fun getFen(): String = board.fen

    actual fun loadFen(fen: String) {
        board.loadFromFen(fen)
        sanHistory.clear()
    }

    actual fun reset() {
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        sanHistory.clear()
    }

    actual fun makeMove(san: String): MoveResult =
        try {
            if (board.doMove(san)) {
                // Input is already SAN from Nostr events, store directly
                sanHistory.add(san)
                MoveResult(
                    success = true,
                    san = san,
                    position = boardToPosition(),
                )
            } else {
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
                val san = computeSan(move)
                board.doMove(move)
                sanHistory.add(san)
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
            .filterNotNull()
            .filter { it.from == sq }
            .map { it.to.toString().lowercase() }
    }

    actual fun isLegalMove(san: String): Boolean =
        try {
            if (board.doMove(san)) {
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
        if (sanHistory.isNotEmpty()) {
            sanHistory.removeAt(sanHistory.lastIndex)
        }
    }

    actual fun getPosition(): ChessPosition = boardToPosition()

    actual fun getSideToMove(): Color =
        when (board.sideToMove) {
            Side.WHITE -> Color.WHITE
            Side.BLACK -> Color.BLACK
        }

    actual fun getMoveHistory(): List<String> = sanHistory.toList()

    /**
     * Compute proper SAN notation for a move BEFORE it is applied to the board.
     * Handles pieces, pawns, castling, captures, promotion, disambiguation, check/checkmate.
     */
    private fun computeSan(move: Move): String {
        val fromSquare = move.from
        val toSquare = move.to
        val piece = board.getPiece(fromSquare)
        val pt = piece.pieceType ?: return move.toString()
        val promotionPiece = move.promotion ?: Piece.NONE

        // Castling
        if (pt == com.github.bhlangonijr.chesslib.PieceType.KING) {
            val fileDiff = toSquare.file.ordinal - fromSquare.file.ordinal
            if (fileDiff == 2) {
                val suffix = checkSuffixAfterMove(move)
                return "O-O$suffix"
            }
            if (fileDiff == -2) {
                val suffix = checkSuffixAfterMove(move)
                return "O-O-O$suffix"
            }
        }

        val sb = StringBuilder()
        val epTarget = board.enPassantTarget
        val isCapture =
            board.getPiece(toSquare) != Piece.NONE ||
                (
                    pt == com.github.bhlangonijr.chesslib.PieceType.PAWN &&
                        epTarget != null && epTarget != Square.NONE && toSquare == epTarget
                )

        if (pt != com.github.bhlangonijr.chesslib.PieceType.PAWN) {
            sb.append(sanSymbol(pt))

            // Disambiguation: check if other pieces of same type can reach the same square
            val ambiguous =
                board.legalMoves().filterNotNull().filter {
                    it.to == toSquare &&
                        board.getPiece(it.from).pieceType == pt &&
                        it.from != fromSquare
                }

            if (ambiguous.isNotEmpty()) {
                val sameFile = ambiguous.any { it.from.file == fromSquare.file }
                val sameRank = ambiguous.any { it.from.rank == fromSquare.rank }
                when {
                    !sameFile -> {
                        sb.append(fileChar(fromSquare))
                    }

                    !sameRank -> {
                        sb.append(rankChar(fromSquare))
                    }

                    else -> {
                        sb.append(fileChar(fromSquare))
                        sb.append(rankChar(fromSquare))
                    }
                }
            }
        } else if (isCapture) {
            // Pawn captures include the source file
            sb.append(fileChar(fromSquare))
        }

        if (isCapture) sb.append('x')

        sb.append(toSquare.toString().lowercase())

        // Promotion
        if (promotionPiece != Piece.NONE) {
            sb.append('=')
            val promType = promotionPiece.pieceType
            if (promType != null) sb.append(sanSymbol(promType))
        }

        // Check/checkmate suffix
        sb.append(checkSuffixAfterMove(move))

        return sb.toString()
    }

    /**
     * Temporarily apply a move to check for check/checkmate, then undo.
     */
    private fun checkSuffixAfterMove(move: Move): String {
        board.doMove(move)
        val suffix =
            when {
                board.isMated -> "#"
                board.isKingAttacked -> "+"
                else -> ""
            }
        board.undoMove()
        return suffix
    }

    private fun sanSymbol(pt: com.github.bhlangonijr.chesslib.PieceType): String =
        when (pt) {
            com.github.bhlangonijr.chesslib.PieceType.KING -> "K"
            com.github.bhlangonijr.chesslib.PieceType.QUEEN -> "Q"
            com.github.bhlangonijr.chesslib.PieceType.ROOK -> "R"
            com.github.bhlangonijr.chesslib.PieceType.BISHOP -> "B"
            com.github.bhlangonijr.chesslib.PieceType.KNIGHT -> "N"
            else -> ""
        }

    private fun fileChar(square: Square): Char = 'a' + square.file.ordinal

    private fun rankChar(square: Square): Char = '1' + square.rank.ordinal

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
