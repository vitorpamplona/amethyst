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
 * Represents a single chess move in Standard Algebraic Notation (SAN)
 * Per NIP-64, moves must comply with chess rules
 */
@Immutable
data class ChessMove(
    val san: String, // Standard Algebraic Notation, e.g., "Nf3", "e4", "O-O", "Qxe5+"
    val moveNumber: Int, // Which move in the game (1-based)
    val color: Color, // Who made the move
    val piece: PieceType = PieceType.PAWN,
    val fromSquare: String? = null, // Source square if known
    val toSquare: String, // Destination square
    val isCapture: Boolean = false,
    val isCheck: Boolean = false,
    val isCheckmate: Boolean = false,
    val isCastling: Boolean = false,
    val promotion: PieceType? = null,
) {
    override fun toString(): String = san
}

/**
 * Chess piece types per standard notation
 */
enum class PieceType(
    val symbol: Char,
) {
    KING('K'),
    QUEEN('Q'),
    ROOK('R'),
    BISHOP('B'),
    KNIGHT('N'),
    PAWN('P'),
    ;

    companion object {
        fun fromSymbol(c: Char): PieceType? = entries.find { it.symbol == c.uppercaseChar() }
    }
}

/**
 * Player color
 */
enum class Color {
    WHITE,
    BLACK,
    ;

    fun opposite(): Color =
        when (this) {
            WHITE -> BLACK
            BLACK -> WHITE
        }
}
