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

import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages state for a live chess game
 *
 * This class coordinates between:
 * - ChessEngine (move validation, board state)
 * - Nostr events (publishing moves, receiving opponent moves)
 * - UI (game state, turn management)
 *
 * Jester Protocol Notes:
 * - startEventId: ID of the game start event (used as game identifier)
 * - headEventId: ID of the current head move (updated after each move)
 * - Full move history is included in every published move event
 */
class LiveChessGameState(
    /** Start event ID - the game identifier in Jester protocol */
    val startEventId: String,
    val playerPubkey: String,
    val opponentPubkey: String,
    val playerColor: Color,
    val engine: ChessEngine,
    val createdAt: Long = TimeUtils.now(),
    val isSpectator: Boolean = false,
    val isPendingChallenge: Boolean = false,
    /** Initial head event ID - same as startEventId for new games */
    initialHeadEventId: String? = null,
) {
    /** Legacy alias for startEventId */
    @Deprecated("Use startEventId instead", ReplaceWith("startEventId"))
    val gameId: String get() = startEventId

    /** Current head event ID - tracks the most recent move for e-tag linking */
    private val _headEventId = MutableStateFlow(initialHeadEventId ?: startEventId)
    val headEventId: StateFlow<String> = _headEventId.asStateFlow()

    companion object {
        // Abandon timeout: 24 hours without a move
        const val ABANDON_TIMEOUT_SECONDS = 24 * 60 * 60L

        // Inactivity warning: 1 hour without a move
        const val INACTIVITY_WARNING_SECONDS = 60 * 60L
    }

    private val _gameStatus = MutableStateFlow<GameStatus>(GameStatus.InProgress)
    val gameStatus: StateFlow<GameStatus> = _gameStatus.asStateFlow()

    private val _currentPosition = MutableStateFlow(engine.getPosition())
    val currentPosition: StateFlow<ChessPosition> = _currentPosition.asStateFlow()

    // Initialize from engine's history so freshly loaded games show all moves
    private val _moveHistory = MutableStateFlow(engine.getMoveHistory())
    val moveHistory: StateFlow<List<String>> = _moveHistory.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // Track when last activity occurred (move made or received)
    private val _lastActivityAt = MutableStateFlow(createdAt)
    val lastActivityAt: StateFlow<Long> = _lastActivityAt.asStateFlow()

    // Track received move numbers to detect duplicates and out-of-order
    private val receivedMoveNumbers = mutableSetOf<Int>()

    // Pending moves waiting for earlier moves (move number -> move data)
    private val pendingMoves = mutableMapOf<Int, Pair<String, String>>()

    // Desync detection
    private val _isDesynced = MutableStateFlow(false)
    val isDesynced: StateFlow<Boolean> = _isDesynced.asStateFlow()

    // Draw offer tracking - pubkey of who offered the draw (null = no pending offer)
    private val _pendingDrawOffer = MutableStateFlow<String?>(null)
    val pendingDrawOffer: StateFlow<String?> = _pendingDrawOffer.asStateFlow()

    /**
     * Check if there's a pending draw offer from opponent
     */
    fun hasOpponentDrawOffer(): Boolean = _pendingDrawOffer.value == opponentPubkey

    /**
     * Check if we have an outgoing draw offer
     */
    fun hasOurDrawOffer(): Boolean = _pendingDrawOffer.value == playerPubkey

    /**
     * Check if it's the player's turn
     * Spectators never have a turn
     */
    fun isPlayerTurn(): Boolean = !isSpectator && engine.getSideToMove() == playerColor

    /**
     * Make a move (called when player makes a move on the board)
     * Returns the move event data to be published to Nostr
     *
     * Jester Protocol: Move events include full history and link to previous event
     */
    fun makeMove(
        from: String,
        to: String,
        promotion: PieceType? = null,
    ): ChessMoveEvent? {
        if (!isPlayerTurn()) {
            _lastError.value = "Not your turn"
            return null
        }

        if (_gameStatus.value != GameStatus.InProgress) {
            _lastError.value = "Game is not in progress"
            return null
        }

        val result = engine.makeMove(from, to, promotion)

        if (result.success && result.san != null && result.position != null) {
            _currentPosition.value = result.position
            _moveHistory.value = engine.getMoveHistory()
            _lastActivityAt.value = TimeUtils.now()
            _lastError.value = null

            // Making a move implicitly declines any pending draw offer
            _pendingDrawOffer.value = null

            // Check for game end conditions
            checkGameEnd()

            // Return move event data with full history for Jester protocol
            return ChessMoveEvent(
                startEventId = startEventId,
                headEventId = _headEventId.value,
                san = result.san,
                fen = engine.getFen(),
                history = _moveHistory.value, // Full history for Jester
                opponentPubkey = opponentPubkey,
            )
        } else {
            _lastError.value = result.error ?: "Invalid move"
            return null
        }
    }

    /**
     * Update the head event ID after a move is successfully published.
     * Call this after the move event is signed and broadcast.
     *
     * @param newHeadEventId The ID of the newly published move event
     */
    fun updateHeadEventId(newHeadEventId: String) {
        _headEventId.value = newHeadEventId
    }

    /**
     * Undo the last move made.
     * Used to revert a move when publishing fails.
     *
     * @return true if the move was successfully undone, false if there was nothing to undo
     */
    fun undoLastMove(): Boolean {
        if (_moveHistory.value.isEmpty()) return false

        engine.undoMove()
        _currentPosition.value = engine.getPosition()
        _moveHistory.value = engine.getMoveHistory()
        _lastError.value = null

        // Reset game status in case checkmate was detected
        if (_gameStatus.value is GameStatus.Finished) {
            _gameStatus.value = GameStatus.InProgress
        }

        return true
    }

    /**
     * Apply opponent's move (called when receiving move event from Nostr)
     */
    fun applyOpponentMove(
        san: String,
        fen: String,
        moveNumber: Int? = null,
    ): Boolean {
        // Check for duplicate move
        if (moveNumber != null && receivedMoveNumbers.contains(moveNumber)) {
            // Already processed this move, ignore
            return true
        }

        // Check for out-of-order move
        val expectedMoveNumber = _moveHistory.value.size + 1
        if (moveNumber != null && moveNumber > expectedMoveNumber) {
            // Store for later processing
            pendingMoves[moveNumber] = san to fen
            return true
        }

        if (isPlayerTurn()) {
            _lastError.value = "Received move but it's not opponent's turn"
            return false
        }

        // Verify the move is valid by trying to make it
        val result = engine.makeMove(san)

        if (result.success) {
            // Mark as received
            if (moveNumber != null) {
                receivedMoveNumbers.add(moveNumber)
            }

            // Verify the resulting FEN matches what opponent sent
            val currentFen = engine.getFen()
            if (currentFen != fen) {
                // Positions don't match - desync detected
                _isDesynced.value = true
                _lastError.value = "Position mismatch - syncing to opponent's position"
                // Load the opponent's FEN to stay in sync
                engine.loadFen(fen)
            } else {
                _isDesynced.value = false
            }

            _currentPosition.value = engine.getPosition()
            _moveHistory.value = engine.getMoveHistory()
            _lastActivityAt.value = TimeUtils.now()
            _lastError.value = null

            // Opponent making a move declines any pending draw offer
            _pendingDrawOffer.value = null

            // Check for game end conditions
            checkGameEnd()

            // Process any pending moves that are now valid
            processPendingMoves()

            return true
        } else {
            _lastError.value = "Invalid opponent move: $san"
            return false
        }
    }

    /**
     * Process any pending out-of-order moves
     */
    private fun processPendingMoves() {
        val nextMoveNumber = _moveHistory.value.size + 1
        val pendingMove = pendingMoves.remove(nextMoveNumber)
        if (pendingMove != null) {
            val (san, fen) = pendingMove
            applyOpponentMove(san, fen, nextMoveNumber)
        }
    }

    /**
     * Force resync to a specific FEN (manual recovery)
     */
    fun forceResync(fen: String) {
        engine.loadFen(fen)
        _currentPosition.value = engine.getPosition()
        _moveHistory.value = engine.getMoveHistory()
        _isDesynced.value = false
        _lastError.value = null
    }

    /**
     * Apply moves from another game state while preserving this state's participant info.
     * This is used when relay reconstruction incorrectly marks us as spectator but has newer moves.
     *
     * @param other The other game state to take moves from
     */
    fun applyMovesFrom(other: LiveChessGameState) {
        val currentMoves = _moveHistory.value
        val otherMoves = other.moveHistory.value

        if (otherMoves.size <= currentMoves.size) return // Nothing new to apply

        // Apply only the new moves
        for (i in currentMoves.size until otherMoves.size) {
            val san = otherMoves[i]
            val result = engine.makeMove(san)
            if (!result.success) {
                // Try to resync from the other engine's FEN
                val otherFen = other.engine.getFen()
                engine.loadFen(otherFen)
                break
            }
        }

        // Update state flows
        _currentPosition.value = engine.getPosition()
        _moveHistory.value = engine.getMoveHistory()
        _lastActivityAt.value = other.lastActivityAt.value
        _isDesynced.value = false
        _lastError.value = null

        // Update head event ID from other state
        _headEventId.value = other.headEventId.value

        // Mark new moves as received
        val newMoveNumbers = (currentMoves.size + 1..otherMoves.size).toSet()
        receivedMoveNumbers.addAll(newMoveNumbers)

        // Check for game end conditions
        checkGameEnd()
    }

    /**
     * Mark move numbers as already received.
     * Used when loading a game from cache to prevent duplicate move application during refresh.
     *
     * @param moveNumbers Set of move numbers that have been loaded
     */
    fun markMovesAsReceived(moveNumbers: Set<Int>) {
        receivedMoveNumbers.addAll(moveNumbers)
    }

    /**
     * Check if game appears abandoned (opponent hasn't moved in a long time)
     */
    fun isAbandoned(): Boolean {
        if (_gameStatus.value != GameStatus.InProgress) return false
        if (isPlayerTurn()) return false // Only check when waiting for opponent

        val elapsed = TimeUtils.now() - _lastActivityAt.value
        return elapsed > ABANDON_TIMEOUT_SECONDS
    }

    /**
     * Check if opponent is inactive (warning threshold)
     */
    fun isOpponentInactive(): Boolean {
        if (_gameStatus.value != GameStatus.InProgress) return false
        if (isPlayerTurn()) return false

        val elapsed = TimeUtils.now() - _lastActivityAt.value
        return elapsed > INACTIVITY_WARNING_SECONDS
    }

    /**
     * Claim victory due to abandonment
     */
    fun claimAbandonmentVictory(): ChessGameEnd? {
        if (!isAbandoned()) return null

        _gameStatus.value = GameStatus.Finished(GameResult.getResultForWinner(playerColor))

        return ChessGameEnd(
            startEventId = startEventId,
            headEventId = _headEventId.value,
            lastMove = null,
            fen = engine.getFen(),
            history = _moveHistory.value,
            result = GameResult.getResultForWinner(playerColor),
            termination = GameTermination.ABANDONMENT,
            opponentPubkey = opponentPubkey,
        )
    }

    /**
     * Offer resignation (player resigns)
     */
    fun resign(): ChessGameEnd {
        _gameStatus.value = GameStatus.Finished(GameResult.getResultForWinner(playerColor.opposite()))

        return ChessGameEnd(
            startEventId = startEventId,
            headEventId = _headEventId.value,
            lastMove = null,
            fen = engine.getFen(),
            history = _moveHistory.value,
            result = GameResult.getResultForWinner(playerColor.opposite()),
            termination = GameTermination.RESIGNATION,
            opponentPubkey = opponentPubkey,
        )
    }

    /**
     * Offer a draw to opponent.
     * Returns the draw offer data to be published to Nostr.
     */
    fun offerDraw(): ChessDrawOffer {
        _pendingDrawOffer.value = playerPubkey
        return ChessDrawOffer(
            startEventId = startEventId,
            headEventId = _headEventId.value,
            opponentPubkey = opponentPubkey,
        )
    }

    /**
     * Handle receiving a draw offer from opponent (via Nostr event)
     */
    fun receiveDrawOffer(fromPubkey: String): Boolean {
        if (fromPubkey != opponentPubkey) return false
        _pendingDrawOffer.value = opponentPubkey
        return true
    }

    /**
     * Accept opponent's draw offer.
     * Returns game end data to be published to Nostr.
     */
    fun acceptDraw(): ChessGameEnd? {
        if (_pendingDrawOffer.value != opponentPubkey) {
            _lastError.value = "No draw offer to accept"
            return null
        }

        _pendingDrawOffer.value = null
        _gameStatus.value = GameStatus.Finished(GameResult.DRAW)

        return ChessGameEnd(
            startEventId = startEventId,
            headEventId = _headEventId.value,
            lastMove = null,
            fen = engine.getFen(),
            history = _moveHistory.value,
            result = GameResult.DRAW,
            termination = GameTermination.DRAW_AGREEMENT,
            opponentPubkey = opponentPubkey,
        )
    }

    /**
     * Decline a draw offer (explicit decline, also happens implicitly on move)
     */
    fun declineDraw() {
        _pendingDrawOffer.value = null
    }

    /**
     * Check if game has ended (checkmate, stalemate, etc.)
     */
    private fun checkGameEnd() {
        when {
            engine.isCheckmate() -> {
                val winner = engine.getSideToMove().opposite()
                _gameStatus.value = GameStatus.Finished(GameResult.getResultForWinner(winner))
                // In a real implementation, you'd publish GameEnd event here
            }

            engine.isStalemate() -> {
                _gameStatus.value = GameStatus.Finished(GameResult.DRAW)
                // In a real implementation, you'd publish GameEnd event here
            }
        }
    }

    /**
     * Generate PGN for the current game
     */
    private fun generatePGN(): String {
        val moves = _moveHistory.value
        val movePairs = moves.chunked(2)

        val moveText =
            movePairs
                .mapIndexed { index, pair ->
                    val moveNum = index + 1
                    when (pair.size) {
                        2 -> "$moveNum. ${pair[0]} ${pair[1]}"
                        1 -> "$moveNum. ${pair[0]}"
                        else -> ""
                    }
                }.joinToString(" ")

        val result =
            when (val status = _gameStatus.value) {
                is GameStatus.Finished -> status.result.notation
                else -> "*"
            }

        return """
            [Event "Live Chess Game"]
            [Site "Nostr"]
            [White "${if (playerColor == Color.WHITE) playerPubkey else opponentPubkey}"]
            [Black "${if (playerColor == Color.BLACK) playerPubkey else opponentPubkey}"]
            [Result "$result"]

            $moveText $result
            """.trimIndent()
    }

    /**
     * Mark game as finished with the given result.
     * Used when loading a game from cache that already has an end event.
     */
    fun markAsFinished(result: GameResult) {
        _gameStatus.value = GameStatus.Finished(result)
    }

    /**
     * Reset game to initial position
     */
    fun reset() {
        engine.reset()
        _currentPosition.value = engine.getPosition()
        _moveHistory.value = emptyList()
        _gameStatus.value = GameStatus.InProgress
        _lastError.value = null
    }
}

/**
 * Game status
 */
sealed class GameStatus {
    data object InProgress : GameStatus()

    data class Finished(
        val result: GameResult,
    ) : GameStatus()
}

/**
 * Extension to get result for a winning color
 */
private fun GameResult.Companion.getResultForWinner(winner: Color): GameResult =
    when (winner) {
        Color.WHITE -> GameResult.WHITE_WINS
        Color.BLACK -> GameResult.BLACK_WINS
    }
