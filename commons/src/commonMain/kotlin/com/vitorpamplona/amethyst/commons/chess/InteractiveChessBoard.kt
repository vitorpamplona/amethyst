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
package com.vitorpamplona.amethyst.commons.chess

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * @param isSpectator If true, disables all interaction (spectator/watch mode)
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
    isSpectator: Boolean = false,
    positionVersion: Int = 0, // External trigger for position refresh (e.g. moveHistory.size)
    onMoveMade: (from: String, to: String, san: String) -> Unit = { _, _, _ -> },
) {
    // Track local move count + external version to trigger recomposition when board changes
    var localMoveCount by remember { mutableStateOf(0) }
    val effectiveVersion = localMoveCount + positionVersion
    val position = remember(engine, effectiveVersion) { engine.getPosition() }
    val sideToMove = remember(engine, effectiveVersion) { engine.getSideToMove() }
    var selectedSquare by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var legalMoves by remember { mutableStateOf<List<String>>(emptyList()) }

    // Pawn promotion state
    var pendingPromotion by remember { mutableStateOf<PendingPromotion?>(null) }

    // Clear stale selection when position changes externally (e.g. opponent move)
    LaunchedEffect(positionVersion) {
        selectedSquare = null
        legalMoves = emptyList()
        pendingPromotion = null
    }

    // Can only interact if not spectating and it's your turn (or playerColor is null for local play)
    val canInteract = !isSpectator && (playerColor == null || sideToMove == playerColor)

    val squareSize = boardSize / 8

    // Rank iteration order based on perspective
    val rankRange = if (flipped) (0..7) else (7 downTo 0)
    val fileRange = if (flipped) (7 downTo 0) else (0..7)

    Box(modifier = modifier.size(boardSize)) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                                if (!canInteract || pendingPromotion != null) return@InteractiveChessSquare

                                if (selectedSquare != null) {
                                    // Attempt to make move - validate first, don't actually make it
                                    val from = fileRankToSquare(selectedSquare!!.first, selectedSquare!!.second)
                                    val to = square

                                    if (legalMoves.contains(to)) {
                                        // Check if this is a pawn promotion move
                                        val fromRank = selectedSquare!!.second
                                        val toRank = rank
                                        val movingPiece = position.pieceAt(selectedSquare!!.first, fromRank)
                                        val isPromotion =
                                            movingPiece?.type == PieceType.PAWN &&
                                                (toRank == 7 || toRank == 0)

                                        if (isPromotion) {
                                            // Show promotion dialog
                                            pendingPromotion =
                                                PendingPromotion(
                                                    from = from,
                                                    to = to,
                                                    file = file,
                                                    rank = toRank,
                                                    color = sideToMove,
                                                )
                                        } else {
                                            // Valid move - callback will make the actual move
                                            selectedSquare = null
                                            legalMoves = emptyList()
                                            // Pass empty san - callback will get it from makeMove result
                                            onMoveMade(from, to, "")
                                            localMoveCount++ // Trigger recomposition after move is made
                                        }
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

        // Promotion dialog overlay
        pendingPromotion?.let { promo ->
            PromotionPicker(
                color = promo.color,
                squareSize = squareSize,
                onPieceSelected = { pieceType ->
                    // Make the move with promotion
                    val promotionSuffix =
                        when (pieceType) {
                            PieceType.QUEEN -> "q"
                            PieceType.ROOK -> "r"
                            PieceType.BISHOP -> "b"
                            PieceType.KNIGHT -> "n"
                            else -> "q"
                        }
                    selectedSquare = null
                    legalMoves = emptyList()
                    pendingPromotion = null
                    // Include promotion in the 'to' square for the callback
                    onMoveMade(promo.from, promo.to + promotionSuffix, "")
                    localMoveCount++
                },
                onDismiss = {
                    pendingPromotion = null
                    selectedSquare = null
                    legalMoves = emptyList()
                },
            )
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

/**
 * Data class for pending pawn promotion
 */
private data class PendingPromotion(
    val from: String,
    val to: String,
    val file: Int,
    val rank: Int,
    val color: ChessColor,
)

/**
 * Promotion piece picker overlay
 */
@Composable
private fun PromotionPicker(
    color: ChessColor,
    squareSize: Dp,
    onPieceSelected: (PieceType) -> Unit,
    onDismiss: () -> Unit,
) {
    val isWhite = color == ChessColor.WHITE
    val promotionPieces =
        listOf(
            PieceType.QUEEN to if (isWhite) ChessPieceVectors.WhiteQueen else ChessPieceVectors.BlackQueen,
            PieceType.ROOK to if (isWhite) ChessPieceVectors.WhiteRook else ChessPieceVectors.BlackRook,
            PieceType.BISHOP to if (isWhite) ChessPieceVectors.WhiteBishop else ChessPieceVectors.BlackBishop,
            PieceType.KNIGHT to if (isWhite) ChessPieceVectors.WhiteKnight else ChessPieceVectors.BlackKnight,
        )

    // Semi-transparent overlay
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.clickable { /* prevent dismiss */ },
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                promotionPieces.forEach { (pieceType, icon) ->
                    Box(
                        modifier =
                            Modifier
                                .size(squareSize)
                                .background(Color(0xFFF0D9B5), RoundedCornerShape(4.dp))
                                .clickable { onPieceSelected(pieceType) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            imageVector = icon,
                            contentDescription = pieceType.name,
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}
