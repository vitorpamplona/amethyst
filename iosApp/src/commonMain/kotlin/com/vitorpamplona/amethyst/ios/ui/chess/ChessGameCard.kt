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
package com.vitorpamplona.amethyst.ios.ui.chess

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.quartz.nip64Chess.ChessPosition
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.PGNParser

/**
 * Display data for a chess game note (NIP-64).
 */
data class ChessGameDisplayData(
    val noteId: String,
    val white: String?,
    val black: String?,
    val result: String?,
    val eventName: String?,
    val moveCount: Int,
    val pgn: String,
    val lastPosition: ChessPosition?,
)

/**
 * Converts PGN content into display data.
 */
fun pgnToChessDisplayData(
    noteId: String,
    pgnContent: String,
): ChessGameDisplayData? {
    val game =
        try {
            PGNParser.parse(pgnContent).getOrNull() ?: return null
        } catch (e: Exception) {
            return null
        }
    return ChessGameDisplayData(
        noteId = noteId,
        white = game.metadata["White"],
        black = game.metadata["Black"],
        result = game.result.notation,
        eventName = game.metadata["Event"],
        moveCount = game.moves.size,
        pgn = pgnContent,
        lastPosition = game.positions.lastOrNull(),
    )
}

/**
 * Card displaying a chess game with a text board preview.
 */
@Composable
fun ChessGameCard(
    data: ChessGameDisplayData,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "♟ Chess Game",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                data.result?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))

            data.white?.let { Text("⬜ $it", style = MaterialTheme.typography.bodySmall) }
            data.black?.let { Text("⬛ $it", style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.height(8.dp))

            // Mini board
            data.lastPosition?.let { position ->
                MiniBoardDisplay(position)
            }

            Spacer(Modifier.height(4.dp))

            Row {
                Text(
                    "${data.moveCount} moves",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                data.eventName?.let {
                    Text(
                        " · $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Renders a mini chess board from a ChessPosition using unicode chess pieces.
 */
@Composable
fun MiniBoardDisplay(
    position: ChessPosition,
    modifier: Modifier = Modifier,
) {
    val boardText =
        remember(position) {
            buildString {
                for (rank in 7 downTo 0) {
                    for (file in 0..7) {
                        val piece = position.pieceAt(file, rank)
                        append(
                            if (piece != null) {
                                pieceToUnicode(piece.type.symbol, piece.color == Color.WHITE)
                            } else {
                                '·'
                            },
                        )
                    }
                    if (rank > 0) append('\n')
                }
            }
        }

    Text(
        text = boardText,
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 16.sp,
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(4.dp),
    )
}

private fun pieceToUnicode(
    symbol: Char,
    isWhite: Boolean,
): Char =
    when (symbol) {
        'K' -> if (isWhite) '♔' else '♚'
        'Q' -> if (isWhite) '♕' else '♛'
        'R' -> if (isWhite) '♖' else '♜'
        'B' -> if (isWhite) '♗' else '♝'
        'N' -> if (isWhite) '♘' else '♞'
        'P' -> if (isWhite) '♙' else '♟'
        else -> '·'
    }
