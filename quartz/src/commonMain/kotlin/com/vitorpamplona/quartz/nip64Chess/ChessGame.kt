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
 * Represents a complete chess game parsed from PGN
 * Per NIP-64, content must be valid PGN format
 */
@Immutable
data class ChessGame(
    val metadata: Map<String, String>,
    val moves: List<ChessMove>,
    val positions: List<ChessPosition>,
    val result: GameResult,
) {
    init {
        require(positions.isNotEmpty()) { "Game must have at least starting position" }
        require(positions.size == moves.size + 1) { "Position count must be moves + 1" }
    }

    /**
     * Standard PGN tag accessors
     */
    val event: String? get() = metadata["Event"]
    val site: String? get() = metadata["Site"]
    val date: String? get() = metadata["Date"]
    val round: String? get() = metadata["Round"]
    val white: String? get() = metadata["White"]
    val black: String? get() = metadata["Black"]

    /**
     * Get position after move N (0 = starting position)
     */
    fun positionAt(moveIndex: Int): ChessPosition = positions.getOrElse(moveIndex) { positions.last() }

    /**
     * Check if game has required metadata
     */
    fun hasRequiredMetadata(): Boolean =
        metadata.containsKey("Event") &&
            metadata.containsKey("Site") &&
            metadata.containsKey("Date") &&
            metadata.containsKey("Round") &&
            metadata.containsKey("White") &&
            metadata.containsKey("Black") &&
            metadata.containsKey("Result")
}

/**
 * Game result per PGN specification
 */
enum class GameResult(
    val notation: String,
) {
    WHITE_WINS("1-0"),
    BLACK_WINS("0-1"),
    DRAW("1/2-1/2"),
    IN_PROGRESS("*"),
    ;

    companion object {
        fun parse(notation: String): GameResult = entries.find { it.notation == notation } ?: IN_PROGRESS
    }
}
