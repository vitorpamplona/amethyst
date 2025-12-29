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

/*
 * Live chess game state and move events
 *
 * For real-time chess games, we use a different event structure than NIP-64.
 * NIP-64 is for completed games in PGN format. Live games need individual
 * move events that can be published and subscribed to in real-time.
 *
 * Event Structure:
 * - Game Challenge (Kind 30064): Challenge another player or open challenge
 * - Game Accept (Kind 30065): Accept a challenge
 * - Chess Move (Kind 30066): Individual move in a live game
 * - Game End (Kind 30067): Game result (checkmate, resignation, draw, etc.)
 *
 * All events use 'd' tag with game_id to group moves from the same game
 */

/**
 * Game challenge event - start a new game
 * Kind: 30064
 *
 * Tags:
 * - d: game_id (unique identifier)
 * - p: opponent pubkey (optional, if challenging specific player)
 * - player_color: white|black (color challenger wants to play)
 * - time_control: time control format (e.g., "10+0", "5+3")
 */
data class ChessGameChallenge(
    val gameId: String,
    val opponentPubkey: String? = null, // null = open challenge
    val playerColor: Color,
    val timeControl: String? = null,
)

/**
 * Game acceptance event - accept a challenge
 * Kind: 30065
 *
 * Tags:
 * - d: game_id (same as challenge)
 * - e: challenge event ID
 * - p: challenger pubkey
 */
data class ChessGameAccept(
    val gameId: String,
    val challengeEventId: String,
    val challengerPubkey: String,
)

/**
 * Chess move event - individual move in a live game
 * Kind: 30066
 *
 * Tags:
 * - d: game_id
 * - move_number: move number (1-based)
 * - san: move in Standard Algebraic Notation
 * - fen: resulting position in FEN notation
 * - p: opponent pubkey (for notifications)
 *
 * Content: Optional move comment or time remaining
 */
data class ChessMoveEvent(
    val gameId: String,
    val moveNumber: Int,
    val san: String,
    val fen: String,
    val opponentPubkey: String,
    val comment: String? = null,
)

/**
 * Game end event - final game result
 * Kind: 30067
 *
 * Tags:
 * - d: game_id
 * - result: 1-0|0-1|1/2-1/2 (white wins, black wins, draw)
 * - termination: checkmate|resignation|draw_agreement|stalemate|timeout|abandonment
 * - winner: pubkey of winner (if applicable)
 * - p: opponent pubkey
 *
 * Content: Optional game summary or final PGN
 */
data class ChessGameEnd(
    val gameId: String,
    val result: GameResult,
    val termination: GameTermination,
    val winnerPubkey: String? = null,
    val opponentPubkey: String,
    val pgn: String? = null,
)

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
 * Event kind constants for live chess
 */
object LiveChessEventKinds {
    const val GAME_CHALLENGE = 30064
    const val GAME_ACCEPT = 30065
    const val CHESS_MOVE = 30066
    const val GAME_END = 30067
}
