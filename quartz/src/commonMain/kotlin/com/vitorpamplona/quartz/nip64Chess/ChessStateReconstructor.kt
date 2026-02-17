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
 * Deterministic chess state reconstruction from Jester protocol events.
 *
 * This is the single source of truth for converting a collection of Jester events
 * into a consistent game state. Both Android and Desktop platforms must use this
 * algorithm to ensure identical state from the same events.
 *
 * Jester Protocol Reconstruction:
 * 1. Find start event (content.kind=0) → establishes startEventId, challenger color, players
 * 2. Find move with longest history → this is the current state
 * 3. Replay moves from history to build engine state
 * 4. Check for result/termination in latest move → mark finished if present
 *
 * Key Jester differences:
 * - No separate accept event (acceptance is implicit)
 * - Full move history in every move event
 * - Can reconstruct from any single move (has complete history)
 * - startEventId is the game identifier (event ID of start event)
 *
 * Guarantees:
 * - Same events always produce same state (deterministic)
 * - Uses longest history for authoritative state
 * - Invalid moves are skipped without error
 */
object ChessStateReconstructor {
    /**
     * Reconstruct game state from a collection of Jester events.
     *
     * @param events The collected Jester events for a single game
     * @param viewerPubkey The pubkey of the user viewing the game (determines perspective)
     * @return ReconstructionResult or error if reconstruction fails
     */
    fun reconstruct(
        events: JesterGameEvents,
        viewerPubkey: String,
    ): ReconstructionResult {
        // Step 1: Get start event (required for player info)
        val startEvent =
            events.startEvent
                ?: return ReconstructionResult.Error("No start event found")

        val startEventId = startEvent.id
        val challengerPubkey = startEvent.pubKey
        val challengerColor = startEvent.playerColor() ?: Color.WHITE
        val challengedPubkey = startEvent.opponentPubkey()

        // In Jester, we determine opponent from the p-tag or from move events
        // For open challenges, we need to find who made the first move
        val opponentFromMoves =
            events.moves
                .firstOrNull { it.pubKey != challengerPubkey }
                ?.pubKey

        val actualOpponent = challengedPubkey ?: opponentFromMoves

        // Determine players based on challenger's color choice
        val (whitePubkey, blackPubkey) =
            if (challengerColor == Color.WHITE) {
                challengerPubkey to actualOpponent
            } else {
                actualOpponent to challengerPubkey
            }

        // Determine viewer's role
        val viewerRole =
            when (viewerPubkey) {
                whitePubkey -> ViewerRole.WHITE_PLAYER
                blackPubkey -> ViewerRole.BLACK_PLAYER
                else -> ViewerRole.SPECTATOR
            }

        val playerColor =
            when (viewerRole) {
                ViewerRole.WHITE_PLAYER -> Color.WHITE
                ViewerRole.BLACK_PLAYER -> Color.BLACK
                ViewerRole.SPECTATOR -> Color.WHITE // Spectators see from white's perspective
            }

        val opponentPubkey =
            when (viewerRole) {
                ViewerRole.WHITE_PLAYER -> blackPubkey ?: ""
                ViewerRole.BLACK_PLAYER -> whitePubkey ?: ""
                ViewerRole.SPECTATOR -> blackPubkey ?: "" // For spectators, "opponent" is black
            }

        // Check if game is pending (no moves yet AND viewer is the challenger)
        // If viewer accepted someone else's challenge, the game is NOT pending
        val isChallenger = viewerPubkey == startEvent.pubKey
        val isPendingChallenge = events.moves.isEmpty() && isChallenger

        // Step 2: Create engine and apply moves from the longest history
        val engine = ChessEngine()
        val latestMove = events.latestMove()
        val history = latestMove?.history() ?: emptyList()

        // Track applied moves
        val appliedMoveNumbers = mutableSetOf<Int>()
        var isDesynced = false

        // Apply moves from history
        for ((index, san) in history.withIndex()) {
            val moveNumber = index + 1
            val result = engine.makeMove(san)
            if (result.success) {
                appliedMoveNumbers.add(moveNumber)
            } else {
                // Move failed - game might be desynced
                isDesynced = true
                // Try to recover by loading the FEN from latest move if available
                latestMove?.fen()?.let { fen ->
                    engine.loadFen(fen)
                }
                break
            }
        }

        // Verify final position matches if we have FEN
        latestMove?.fen()?.let { expectedFen ->
            val currentFen = engine.getFen()
            if (!fenPositionsMatch(currentFen, expectedFen)) {
                isDesynced = true
                engine.loadFen(expectedFen)
            }
        }

        // Step 3: Check game end status
        val gameResult = latestMove?.result()
        val gameStatus =
            when {
                gameResult != null -> {
                    val result = parseGameResult(gameResult)
                    GameStatus.Finished(result)
                }

                engine.isCheckmate() -> {
                    val winner = engine.getSideToMove().opposite()
                    GameStatus.Finished(
                        if (winner == Color.WHITE) GameResult.WHITE_WINS else GameResult.BLACK_WINS,
                    )
                }

                engine.isStalemate() -> {
                    GameStatus.Finished(GameResult.DRAW)
                }

                else -> {
                    GameStatus.InProgress
                }
            }

        // Get the head event ID (for linking next move)
        val headEventId = latestMove?.id ?: startEventId

        // Build the reconstructed state
        val state =
            ReconstructedGameState(
                startEventId = startEventId,
                headEventId = headEventId,
                whitePubkey = whitePubkey,
                blackPubkey = blackPubkey,
                viewerRole = viewerRole,
                playerColor = playerColor,
                opponentPubkey = opponentPubkey,
                isPendingChallenge = isPendingChallenge,
                currentPosition = engine.getPosition(),
                moveHistory = engine.getMoveHistory(),
                gameStatus = gameStatus,
                isDesynced = isDesynced,
                pendingDrawOffer = null, // Jester doesn't have explicit draw offers
                appliedMoveNumbers = appliedMoveNumbers,
                challengeCreatedAt = startEvent.createdAt,
                timeControl = null, // Not supported in Jester
            )

        return ReconstructionResult.Success(state, engine)
    }

    /**
     * Compare FEN positions, ignoring halfmove clock and fullmove number.
     * This provides a more lenient comparison that focuses on the actual board state.
     */
    private fun fenPositionsMatch(
        fen1: String,
        fen2: String,
    ): Boolean {
        // FEN format: position activeColor castling enPassant halfmove fullmove
        // Compare only first 4 parts (position, activeColor, castling, enPassant)
        val parts1 = fen1.split(" ")
        val parts2 = fen2.split(" ")

        if (parts1.size < 4 || parts2.size < 4) {
            return fen1 == fen2 // Fallback to exact match
        }

        return parts1[0] == parts2[0] && // Board position
            parts1[1] == parts2[1] && // Active color
            parts1[2] == parts2[2] && // Castling rights
            parts1[3] == parts2[3] // En passant
    }

    private fun parseGameResult(result: String?): GameResult =
        when (result) {
            "1-0" -> GameResult.WHITE_WINS
            "0-1" -> GameResult.BLACK_WINS
            "1/2-1/2" -> GameResult.DRAW
            else -> GameResult.IN_PROGRESS
        }
}

/**
 * The viewer's role in the game.
 */
enum class ViewerRole {
    WHITE_PLAYER,
    BLACK_PLAYER,
    SPECTATOR,
}

/**
 * Result of game state reconstruction.
 */
sealed class ReconstructionResult {
    data class Success(
        val state: ReconstructedGameState,
        val engine: ChessEngine,
    ) : ReconstructionResult()

    data class Error(
        val message: String,
    ) : ReconstructionResult()

    fun isSuccess(): Boolean = this is Success

    fun getOrNull(): ReconstructedGameState? = (this as? Success)?.state

    fun getEngineOrNull(): ChessEngine? = (this as? Success)?.engine
}

/**
 * Reconstructed game state from Jester events.
 * This is an immutable snapshot of the game at the time of reconstruction.
 */
@Immutable
data class ReconstructedGameState(
    /** The start event ID - this is the game identifier in Jester protocol */
    val startEventId: String,
    /** The current head event ID - used for linking the next move */
    val headEventId: String,
    val whitePubkey: String?,
    val blackPubkey: String?,
    val viewerRole: ViewerRole,
    val playerColor: Color,
    val opponentPubkey: String,
    val isPendingChallenge: Boolean,
    val currentPosition: ChessPosition,
    val moveHistory: List<String>,
    val gameStatus: GameStatus,
    val isDesynced: Boolean,
    val pendingDrawOffer: String?,
    val appliedMoveNumbers: Set<Int>,
    val challengeCreatedAt: Long,
    val timeControl: String?,
) {
    /** Legacy alias for startEventId */
    @Deprecated("Use startEventId instead", ReplaceWith("startEventId"))
    val gameId: String get() = startEventId

    fun isPlayerTurn(): Boolean =
        when (viewerRole) {
            ViewerRole.SPECTATOR -> false
            ViewerRole.WHITE_PLAYER -> currentPosition.activeColor == Color.WHITE
            ViewerRole.BLACK_PLAYER -> currentPosition.activeColor == Color.BLACK
        }

    fun isFinished(): Boolean = gameStatus is GameStatus.Finished

    fun hasOpponentDrawOffer(playerPubkey: String): Boolean = pendingDrawOffer != null && pendingDrawOffer != playerPubkey

    fun hasOurDrawOffer(playerPubkey: String): Boolean = pendingDrawOffer == playerPubkey
}
