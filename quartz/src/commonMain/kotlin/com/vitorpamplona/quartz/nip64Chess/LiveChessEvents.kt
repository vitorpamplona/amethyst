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

/*
 * Live chess game state and move events
 *
 * Uses Jester protocol (kind 30) for compatibility with jesterui.
 * See: https://github.com/jesterui/jesterui/blob/devel/FLOW.md
 *
 * Key features of Jester protocol:
 * - Single event kind (30) for all chess messages
 * - Content is JSON with: version, kind (0=start, 1=move), fen, move, history
 * - Full move history included in every move event
 * - Event linking via e-tags: [startId] or [startId, headId]
 *
 * This enables:
 * - Easy game reconstruction from any single move event
 * - Tolerance for missing intermediate moves
 * - Compatibility with jesterui and other Jester clients
 */

/**
 * Game start/challenge data for publishing
 *
 * Jester protocol (kind 30) start event:
 * - e-tag: reference to standard start position hash
 * - p-tag: opponent pubkey (for private/direct challenges)
 * - Content JSON with: version, kind=0, fen, history=[], nonce, playerColor
 *
 * @param opponentPubkey Opponent's pubkey for direct challenge (null = open challenge)
 * @param playerColor Color the challenger wants to play
 * @param nonce Unique identifier for this game
 */
data class ChessGameChallenge(
    val opponentPubkey: String? = null, // null = open challenge
    val playerColor: Color,
    val nonce: String = generateNonce(),
) {
    companion object {
        private fun generateNonce(): String {
            val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
            return (1..8).map { chars.random() }.joinToString("")
        }
    }

    // Legacy compatibility - gameId is now derived from start event ID after signing
    @Deprecated("gameId is determined after event creation", ReplaceWith("startEventId"))
    val gameId: String get() = nonce
}

/**
 * Game acceptance data
 *
 * In Jester protocol, accepting a game is implicit - the opponent
 * simply makes the first move (if challenger chose white) or waits
 * for challenger's first move (if challenger chose black).
 *
 * This data class is kept for internal state tracking.
 *
 * @param startEventId ID of the game start event to accept
 * @param challengerPubkey Pubkey of the challenger
 */
data class ChessGameAccept(
    val startEventId: String,
    val challengerPubkey: String,
) {
    // Legacy compatibility
    @Deprecated("Use startEventId instead", ReplaceWith("startEventId"))
    val gameId: String get() = startEventId

    @Deprecated("Use startEventId instead", ReplaceWith("startEventId"))
    val challengeEventId: String get() = startEventId
}

/**
 * Chess move data for publishing
 *
 * Jester protocol (kind 30) requires:
 * - Full move history in every move event
 * - e-tags linking: [startEventId, headEventId]
 * - Content JSON with: version, kind=1, fen, move, history
 *
 * @param startEventId ID of the game start event
 * @param headEventId ID of the previous move (or startEventId for first move)
 * @param san Move in Standard Algebraic Notation (e.g., "e4", "Nf3")
 * @param fen Resulting board position in FEN notation
 * @param history Complete move history including this move
 * @param opponentPubkey Opponent's pubkey for p-tag notification
 */
data class ChessMoveEvent(
    val startEventId: String,
    val headEventId: String,
    val san: String,
    val fen: String,
    val history: List<String>,
    val opponentPubkey: String,
) {
    /** Move number (1-based, derived from history length) */
    val moveNumber: Int get() = history.size

    // Legacy compatibility - some code still uses gameId
    @Deprecated("Use startEventId instead", ReplaceWith("startEventId"))
    val gameId: String get() = startEventId
}

/**
 * Game end data for publishing
 *
 * In Jester protocol, game end is a move event with result/termination
 * fields in the content JSON.
 *
 * @param startEventId ID of the game start event
 * @param headEventId ID of the previous move
 * @param lastMove The final move (null for resignation without move)
 * @param fen Final board position
 * @param history Complete move history
 * @param result Game result (1-0, 0-1, 1/2-1/2)
 * @param termination How the game ended
 * @param opponentPubkey Opponent's pubkey for notification
 */
data class ChessGameEnd(
    val startEventId: String,
    val headEventId: String,
    val lastMove: String?,
    val fen: String,
    val history: List<String>,
    val result: GameResult,
    val termination: GameTermination,
    val opponentPubkey: String,
) {
    // Legacy compatibility
    @Deprecated("Use startEventId instead", ReplaceWith("startEventId"))
    val gameId: String get() = startEventId

    val winnerPubkey: String? get() = null // Determined from result
}

/**
 * Draw offer data
 *
 * In Jester protocol, draw offers can be communicated via:
 * - Chat message (kind=2 in content)
 * - Special field in move content
 *
 * Opponent can:
 * - Accept by sending a game end with DRAW_AGREEMENT
 * - Decline implicitly by making their next move
 *
 * @param startEventId ID of the game start event
 * @param headEventId ID of the current head move
 * @param opponentPubkey Opponent's pubkey for notification
 * @param message Optional message with the draw offer
 */
data class ChessDrawOffer(
    val startEventId: String,
    val headEventId: String,
    val opponentPubkey: String,
    val message: String? = null,
) {
    // Legacy compatibility
    @Deprecated("Use startEventId instead", ReplaceWith("startEventId"))
    val gameId: String get() = startEventId
}

/**
 * Game termination reason
 */
enum class GameTermination {
    CHECKMATE,
    RESIGNATION,
    DRAW_AGREEMENT,
    STALEMATE,
    TIMEOUT,
    ABANDONMENT,
}

/**
 * Event kind constants for live chess (Jester protocol)
 *
 * Jester uses a single kind (30) for all chess events.
 * The event type is determined by the content.kind field:
 * - 0: Game start/challenge
 * - 1: Move
 * - 2: Chat
 */
object LiveChessEventKinds {
    /** Jester protocol uses kind 30 for all chess events */
    const val JESTER = 30

    // Legacy constants - deprecated, use JESTER instead
    @Deprecated("Jester uses single kind 30", ReplaceWith("JESTER"))
    const val GAME_CHALLENGE = 30

    @Deprecated("Jester uses single kind 30", ReplaceWith("JESTER"))
    const val GAME_ACCEPT = 30

    @Deprecated("Jester uses single kind 30", ReplaceWith("JESTER"))
    const val CHESS_MOVE = 30

    @Deprecated("Jester uses single kind 30", ReplaceWith("JESTER"))
    const val GAME_END = 30

    @Deprecated("Jester uses single kind 30", ReplaceWith("JESTER"))
    const val DRAW_OFFER = 30
}
