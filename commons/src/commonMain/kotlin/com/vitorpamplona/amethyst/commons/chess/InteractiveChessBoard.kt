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
package com.vitorpamplona.amethyst.commons.chess

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.quartz.nip64Chess.ChessEngine
import com.vitorpamplona.quartz.nip64Chess.ChessPiece
import com.vitorpamplona.quartz.nip64Chess.PieceType
import com.vitorpamplona.quartz.nip64Chess.Color as ChessColor

/**
 * Interactive chess board that allows piece selection and moves
 *
 * @param engine Chess engine for move validation and legal moves
 * @param modifier Modifier for the board
 * @param boardSize Total size of the board in dp
 * @param flipped If true, renders from black's perspective (rank 1 at top)
 * @param playerColor The color the player is playing as (only allows moving these pieces)
 * @param onMoveMade Callback when a valid move is made, receives (from, to, san)
 *                   NOTE: This callback should make the actual move - this component only validates
 */
@Composable
fun InteractiveChessBoard(
    engine: ChessEngine,
    modifier: Modifier = Modifier,
    boardSize: Dp = 400.dp,
    flipped: Boolean = false,
    playerColor: ChessColor? = null, // null = allow both (for local play)
    onMoveMade: (from: String, to: String, san: String) -> Unit = { _, _, _ -> },
) {
    // Track move count to trigger recomposition when board changes
    var moveCount by remember { mutableStateOf(0) }
    val position = remember(engine, moveCount) { engine.getPosition() }
    val sideToMove = remember(engine, moveCount) { engine.getSideToMove() }
    var selectedSquare by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var legalMoves by remember { mutableStateOf<List<String>>(emptyList()) }

    // Can only interact if it's your turn (or playerColor is null for local play)
    val canInteract = playerColor == null || sideToMove == playerColor

    val squareSize = boardSize / 8

    // Rank iteration order based on perspective
    val rankRange = if (flipped) (0..7) else (7 downTo 0)
    val fileRange = if (flipped) (7 downTo 0) else (0..7)

    Column(modifier = modifier.size(boardSize)) {
        for (rank in rankRange) {
            Row {
                for (file in fileRange) {
                    val piece = position.pieceAt(file, rank)
                    val isLightSquare = (rank + file) % 2 == 0
                    val square = fileRankToSquare(file, rank)
                    val isSelected = selectedSquare == (file to rank)
                    val isLegalMove = legalMoves.contains(square)

                    val showCoord = if (flipped) rank == 7 else rank == 0

                    InteractiveChessSquare(
                        piece = piece,
                        isLight = isLightSquare,
                        size = squareSize,
                        isSelected = isSelected,
                        isLegalMove = isLegalMove,
                        showCoordinate = showCoord,
                        file = file,
                        rank = rank,
                        onClick = {
                            if (!canInteract) return@InteractiveChessSquare

                            if (selectedSquare != null) {
                                // Attempt to make move - validate first, don't actually make it
                                val from = fileRankToSquare(selectedSquare!!.first, selectedSquare!!.second)
                                val to = square

                                if (legalMoves.contains(to)) {
                                    // Valid move - callback will make the actual move
                                    selectedSquare = null
                                    legalMoves = emptyList()
                                    // Pass empty san - callback will get it from makeMove result
                                    onMoveMade(from, to, "")
                                    moveCount++ // Trigger recomposition after move is made
                                } else {
                                    // Not a legal move target - try selecting this square instead
                                    val validColor = playerColor ?: sideToMove
                                    if (piece != null && piece.color == validColor) {
                                        selectedSquare = file to rank
                                        legalMoves = engine.getLegalMovesFrom(square)
                                    } else {
                                        selectedSquare = null
                                        legalMoves = emptyList()
                                    }
                                }
                            } else {
                                // Select piece - only allow selecting player's pieces
                                val validColor = playerColor ?: sideToMove
                                if (piece != null && piece.color == validColor) {
                                    selectedSquare = file to rank
                                    legalMoves = engine.getLegalMovesFrom(square)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Single interactive chess square
 */
@Composable
private fun InteractiveChessSquare(
    piece: com.vitorpamplona.quartz.nip64Chess.ChessPiece?,
    isLight: Boolean,
    size: Dp,
    isSelected: Boolean,
    isLegalMove: Boolean,
    showCoordinate: Boolean,
    file: Int,
    rank: Int,
    onClick: () -> Unit,
) {
    val backgroundColor =
        when {
            isSelected -> Color(0xFF7FA650)
            isLegalMove -> Color(0xFFAAC75B).copy(alpha = 0.6f)
            isLight -> Color(0xFFF0D9B5)
            else -> Color(0xFFB58863)
        }

    Box(
        modifier =
            Modifier
                .size(size)
                .background(backgroundColor)
                .clickable { onClick() }
                .let {
                    if (isSelected) {
                        it.border(2.dp, Color(0xFF4A7536))
                    } else {
                        it
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        // Display piece using CBurnett ImageVector icons
        piece?.let {
            Image(
                imageVector = it.toImageVector(),
                contentDescription = "${it.color} ${it.type}",
                modifier = Modifier.fillMaxSize().padding(2.dp),
            )
        }

        // Show legal move indicator
        if (isLegalMove && piece == null) {
            Box(
                modifier =
                    Modifier
                        .size(size * 0.25f)
                        .background(Color(0xFF7FA650), CircleShape)
                        .alpha(0.5f),
            )
        } else if (isLegalMove && piece != null) {
            // Capture indicator - ring around piece
            Box(
                modifier =
                    Modifier
                        .size(size * 0.8f)
                        .border(3.dp, Color(0xFF7FA650).copy(alpha = 0.5f), CircleShape),
            )
        }

        // Show file coordinate (a-h) on bottom rank
        if (showCoordinate) {
            Text(
                text = ('a' + file).toString(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isLight) Color(0xFFB58863) else Color(0xFFF0D9B5),
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp),
            )
        }
    }
}

/**
 * Convert file/rank to algebraic notation (e.g., 0,0 -> "a1")
 */
private fun fileRankToSquare(
    file: Int,
    rank: Int,
): String = "${('a' + file)}${rank + 1}"

/**
 * Extension function to convert ChessPiece to CBurnett ImageVector
 */
private fun ChessPiece.toImageVector(): ImageVector {
    val white = color == ChessColor.WHITE
    return when (type) {
        PieceType.KING -> if (white) ChessPieceVectors.WhiteKing else ChessPieceVectors.BlackKing
        PieceType.QUEEN -> if (white) ChessPieceVectors.WhiteQueen else ChessPieceVectors.BlackQueen
        PieceType.ROOK -> if (white) ChessPieceVectors.WhiteRook else ChessPieceVectors.BlackRook
        PieceType.BISHOP -> if (white) ChessPieceVectors.WhiteBishop else ChessPieceVectors.BlackBishop
        PieceType.KNIGHT -> if (white) ChessPieceVectors.WhiteKnight else ChessPieceVectors.BlackKnight
        PieceType.PAWN -> if (white) ChessPieceVectors.WhitePawn else ChessPieceVectors.BlackPawn
    }
}
