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
 */
class LiveChessGameState(
    val gameId: String,
    val playerPubkey: String,
    val opponentPubkey: String,
    val playerColor: Color,
    val engine: ChessEngine,
) {
    private val _gameStatus = MutableStateFlow<GameStatus>(GameStatus.InProgress)
    val gameStatus: StateFlow<GameStatus> = _gameStatus.asStateFlow()

    private val _currentPosition = MutableStateFlow(engine.getPosition())
    val currentPosition: StateFlow<ChessPosition> = _currentPosition.asStateFlow()

    private val _moveHistory = MutableStateFlow<List<String>>(emptyList())
    val moveHistory: StateFlow<List<String>> = _moveHistory.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * Check if it's the player's turn
     */
    fun isPlayerTurn(): Boolean = engine.getSideToMove() == playerColor

    /**
     * Make a move (called when player makes a move on the board)
     * Returns the move event to be published to Nostr
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
            _lastError.value = null

            // Check for game end conditions
            checkGameEnd()

            // Return move event to publish
            return ChessMoveEvent(
                gameId = gameId,
                moveNumber = result.position.moveNumber,
                san = result.san,
                fen = engine.getFen(),
                opponentPubkey = opponentPubkey,
            )
        } else {
            _lastError.value = result.error ?: "Invalid move"
            return null
        }
    }

    /**
     * Apply opponent's move (called when receiving move event from Nostr)
     */
    fun applyOpponentMove(
        san: String,
        fen: String,
    ): Boolean {
        if (isPlayerTurn()) {
            _lastError.value = "Received move but it's not opponent's turn"
            return false
        }

        // Verify the move is valid by trying to make it
        val result = engine.makeMove(san)

        if (result.success) {
            // Verify the resulting FEN matches what opponent sent
            val currentFen = engine.getFen()
            if (currentFen != fen) {
                // Positions don't match - possible desync
                _lastError.value = "Position mismatch after opponent move"
                // Load the opponent's FEN to stay in sync
                engine.loadFen(fen)
            }

            _currentPosition.value = engine.getPosition()
            _moveHistory.value = engine.getMoveHistory()
            _lastError.value = null

            // Check for game end conditions
            checkGameEnd()

            return true
        } else {
            _lastError.value = "Invalid opponent move: $san"
            return false
        }
    }

    /**
     * Offer resignation (player resigns)
     */
    fun resign(): ChessGameEnd {
        _gameStatus.value = GameStatus.Finished(GameResult.getResultForWinner(playerColor.opposite()))

        return ChessGameEnd(
            gameId = gameId,
            result = GameResult.getResultForWinner(playerColor.opposite()),
            termination = GameTermination.RESIGNATION,
            winnerPubkey = opponentPubkey,
            opponentPubkey = opponentPubkey,
            pgn = generatePGN(),
        )
    }

    /**
     * Offer or accept draw
     */
    fun offerDraw(): ChessGameEnd {
        _gameStatus.value = GameStatus.Finished(GameResult.DRAW)

        return ChessGameEnd(
            gameId = gameId,
            result = GameResult.DRAW,
            termination = GameTermination.DRAW_AGREEMENT,
            winnerPubkey = null,
            opponentPubkey = opponentPubkey,
            pgn = generatePGN(),
        )
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
