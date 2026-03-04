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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.quartz.nip64Chess.ChessPiece
import com.vitorpamplona.quartz.nip64Chess.ChessPosition
import com.vitorpamplona.quartz.nip64Chess.PieceType
import com.vitorpamplona.quartz.nip64Chess.Color as ChessColor

/**
 * Renders an 8x8 chess board with pieces
 * Shared composable for Android and Desktop platforms
 *
 * @param position The chess position to display
 * @param modifier Modifier for the board
 * @param boardSize Total size of the board in dp
 * @param showCoordinates Whether to show file labels (a-h) on bottom rank
 */
@Composable
fun ChessBoard(
    position: ChessPosition,
    modifier: Modifier = Modifier,
    boardSize: Dp = 320.dp,
    showCoordinates: Boolean = true,
) {
    val squareSize = boardSize / 8

    Column(modifier = modifier.size(boardSize)) {
        // Render ranks 8 down to 1 (from white's perspective)
        for (rank in 7 downTo 0) {
            Row {
                for (file in 0..7) {
                    val piece = position.pieceAt(file, rank)
                    val isLightSquare = (rank + file) % 2 == 0

                    ChessSquare(
                        piece = piece,
                        isLight = isLightSquare,
                        size = squareSize,
                        coordinate =
                            if (showCoordinates && rank == 0) {
                                ('a' + file).toString()
                            } else {
                                null
                            },
                    )
                }
            }
        }
    }
}

/**
 * Renders a single square on the chess board
 */
@Composable
private fun ChessSquare(
    piece: com.vitorpamplona.quartz.nip64Chess.ChessPiece?,
    isLight: Boolean,
    size: Dp,
    coordinate: String?,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .background(
                    if (isLight) Color(0xFFF0D9B5) else Color(0xFFB58863),
                ),
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

        // Show file coordinate (a-h) on bottom rank
        coordinate?.let {
            Text(
                text = it,
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
