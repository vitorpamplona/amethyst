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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.quartz.nip64Chess.ChessEngine
import com.vitorpamplona.quartz.nip64Chess.PieceType
import com.vitorpamplona.quartz.nip64Chess.Color as ChessColor

/**
 * Interactive chess board that allows piece selection and moves
 *
 * @param engine Chess engine for move validation and legal moves
 * @param modifier Modifier for the board
 * @param boardSize Total size of the board in dp
 * @param onMoveMade Callback when a valid move is made, receives (from, to, san)
 */
@Composable
fun InteractiveChessBoard(
    engine: ChessEngine,
    modifier: Modifier = Modifier,
    boardSize: Dp = 400.dp,
    onMoveMade: (from: String, to: String, san: String) -> Unit = { _, _, _ -> },
) {
    val position = remember(engine) { engine.getPosition() }
    var selectedSquare by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var legalMoves by remember { mutableStateOf<List<String>>(emptyList()) }

    val squareSize = boardSize / 8

    Column(modifier = modifier.size(boardSize)) {
        // Render ranks 8 down to 1 (from white's perspective)
        for (rank in 7 downTo 0) {
            Row {
                for (file in 0..7) {
                    val piece = position.pieceAt(file, rank)
                    val isLightSquare = (rank + file) % 2 == 0
                    val square = fileRankToSquare(file, rank)
                    val isSelected = selectedSquare == (file to rank)
                    val isLegalMove = legalMoves.contains(square)

                    InteractiveChessSquare(
                        piece = piece,
                        isLight = isLightSquare,
                        size = squareSize,
                        isSelected = isSelected,
                        isLegalMove = isLegalMove,
                        showCoordinate = rank == 0,
                        file = file,
                        rank = rank,
                        onClick = {
                            if (selectedSquare != null) {
                                // Attempt to make move
                                val from = fileRankToSquare(selectedSquare!!.first, selectedSquare!!.second)
                                val to = square
                                val result = engine.makeMove(from, to)

                                if (result.success) {
                                    onMoveMade(from, to, result.san ?: "$from$to")
                                    selectedSquare = null
                                    legalMoves = emptyList()
                                } else {
                                    // If move failed, try selecting this square instead
                                    if (piece != null && piece.color == engine.getSideToMove()) {
                                        selectedSquare = file to rank
                                        legalMoves = engine.getLegalMovesFrom(square)
                                    } else {
                                        selectedSquare = null
                                        legalMoves = emptyList()
                                    }
                                }
                            } else {
                                // Select piece
                                if (piece != null && piece.color == engine.getSideToMove()) {
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
        // Display piece using Unicode chess symbols
        piece?.let {
            Text(
                text = it.toUnicode(),
                fontSize = (size.value * 0.6).sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
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
 * Extension function to convert ChessPiece to Unicode chess symbol
 */
private fun com.vitorpamplona.quartz.nip64Chess.ChessPiece.toUnicode(): String {
    val white = color == ChessColor.WHITE
    return when (type) {
        PieceType.KING -> if (white) "♔" else "♚"
        PieceType.QUEEN -> if (white) "♕" else "♛"
        PieceType.ROOK -> if (white) "♖" else "♜"
        PieceType.BISHOP -> if (white) "♗" else "♝"
        PieceType.KNIGHT -> if (white) "♘" else "♞"
        PieceType.PAWN -> if (white) "♙" else "♟"
    }
}
