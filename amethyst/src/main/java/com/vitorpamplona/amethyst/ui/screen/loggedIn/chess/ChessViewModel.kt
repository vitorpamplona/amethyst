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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chess

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.nip64Chess.ChessAction
import com.vitorpamplona.quartz.nip64Chess.ChessEngine
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.GameResult
import com.vitorpamplona.quartz.nip64Chess.GameTermination
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameState
import com.vitorpamplona.quartz.nip64Chess.LiveChessMoveEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel for managing live chess games
 *
 * Coordinates between:
 * - LiveChessGameState (game logic)
 * - Nostr relay (event publishing/subscriptions)
 * - UI (game state updates)
 */
@Stable
class ChessViewModel(
    private val account: Account,
) : ViewModel() {
    private val _activeGames = MutableStateFlow<Map<String, LiveChessGameState>>(emptyMap())
    val activeGames: StateFlow<Map<String, LiveChessGameState>> = _activeGames.asStateFlow()

    private val _badgeCount = MutableStateFlow(0)
    val badgeCount: StateFlow<Int> = _badgeCount.asStateFlow()

    init {
        Log.d("Init", "Starting new ChessViewModel")
    }

    /**
     * Get a specific game by ID
     */
    fun getGame(gameId: String): LiveChessGameState? = _activeGames.value[gameId]

    /**
     * Create a new chess game challenge
     *
     * @param opponentPubkey Opponent's pubkey (null for open challenge)
     * @param playerColor Color the player wants to play
     * @param timeControl Optional time control
     */
    fun createChallenge(
        opponentPubkey: String?,
        playerColor: Color,
        timeControl: String? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gameId = UUID.randomUUID().toString()

                // Create and sign challenge event
                val challengeEvent =
                    ChessAction.createChallenge(
                        gameId = gameId,
                        playerColor = playerColor,
                        opponentPubkey = opponentPubkey,
                        timeControl = timeControl,
                        signer = account.signer,
                    )

                // Send to relays
                account.client.send(challengeEvent, account.outboxRelays.flow.value)

                // Cache locally
                account.cache.justConsumeMyOwnEvent(challengeEvent)

                Log.d("Chess", "Challenge created: $gameId")
            } catch (e: Exception) {
                Log.e("Chess", "Failed to create challenge", e)
            }
        }
    }

    /**
     * Accept an incoming challenge and create game state
     *
     * @param challengeEventId Event ID of the challenge
     * @param gameId Game identifier from challenge
     * @param challengerPubkey Challenger's pubkey
     * @param playerColor Color the accepting player will be
     */
    fun acceptChallenge(
        challengeEventId: String,
        gameId: String,
        challengerPubkey: String,
        playerColor: Color,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Create and sign acceptance event
                val acceptEvent =
                    ChessAction.acceptChallenge(
                        gameId = gameId,
                        challengeEventId = challengeEventId,
                        challengerPubkey = challengerPubkey,
                        signer = account.signer,
                    )

                // Send to relays
                account.client.send(acceptEvent, account.outboxRelays.flow.value)

                // Cache locally
                account.cache.justConsumeMyOwnEvent(acceptEvent)

                // Create game state
                createGameState(gameId, challengerPubkey, playerColor)

                Log.d("Chess", "Challenge accepted: $gameId")
            } catch (e: Exception) {
                Log.e("Chess", "Failed to accept challenge", e)
            }
        }
    }

    /**
     * Create a new game state (called when challenge is accepted)
     */
    private fun createGameState(
        gameId: String,
        opponentPubkey: String,
        playerColor: Color,
    ) {
        val engine = ChessEngine()
        engine.reset()

        val gameState =
            LiveChessGameState(
                gameId = gameId,
                playerPubkey = account.signer.pubKey,
                opponentPubkey = opponentPubkey,
                playerColor = playerColor,
                engine = engine,
            )

        _activeGames.value = _activeGames.value + (gameId to gameState)

        // Subscribe to moves for this game
        // TODO: Implement relay subscription for Kind 30066 with d tag = gameId
        // This would listen for opponent's moves

        updateBadgeCount()
    }

    /**
     * Publish a move in an active game
     *
     * @param gameId Game identifier
     * @param from Source square (e.g., "e2")
     * @param to Destination square (e.g., "e4")
     * @param promotion Optional promotion piece type
     */
    fun publishMove(
        gameId: String,
        from: String,
        to: String,
        promotion: com.vitorpamplona.quartz.nip64Chess.PieceType? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gameState = _activeGames.value[gameId] ?: return@launch

                // Make move and get event to publish
                val moveEvent = gameState.makeMove(from, to, promotion) ?: return@launch

                // Sign and send move event
                val signedMoveEvent =
                    ChessAction.publishMove(
                        gameId = moveEvent.gameId,
                        moveNumber = moveEvent.moveNumber,
                        san = moveEvent.san,
                        fen = moveEvent.fen,
                        opponentPubkey = moveEvent.opponentPubkey,
                        comment = moveEvent.comment ?: "",
                        signer = account.signer,
                    )

                account.client.send(signedMoveEvent, account.outboxRelays.flow.value)
                account.cache.justConsumeMyOwnEvent(signedMoveEvent)

                // Check if game ended
                checkAndPublishGameEnd(gameId)

                Log.d("Chess", "Move published: ${moveEvent.san}")
            } catch (e: Exception) {
                Log.e("Chess", "Failed to publish move", e)
            }
        }
    }

    /**
     * Handle incoming opponent move
     */
    fun handleOpponentMove(moveEvent: LiveChessMoveEvent) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gameId = moveEvent.gameId() ?: return@launch
                val gameState = _activeGames.value[gameId] ?: return@launch

                val san = moveEvent.san() ?: return@launch
                val fen = moveEvent.fen() ?: return@launch

                gameState.applyOpponentMove(san, fen)

                // Update badge count (it's now your turn)
                updateBadgeCount()

                Log.d("Chess", "Opponent move applied: $san")
            } catch (e: Exception) {
                Log.e("Chess", "Failed to apply opponent move", e)
            }
        }
    }

    /**
     * Resign from a game
     */
    fun resign(gameId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gameState = _activeGames.value[gameId] ?: return@launch

                val endEvent = gameState.resign()

                val signedEndEvent =
                    ChessAction.endGame(
                        gameId = endEvent.gameId,
                        result = endEvent.result,
                        termination = endEvent.termination,
                        winnerPubkey = endEvent.winnerPubkey,
                        opponentPubkey = endEvent.opponentPubkey,
                        pgn = endEvent.pgn ?: "",
                        signer = account.signer,
                    )

                account.client.send(signedEndEvent, account.outboxRelays.flow.value)
                account.cache.justConsumeMyOwnEvent(signedEndEvent)

                // Remove from active games
                _activeGames.value = _activeGames.value - gameId

                updateBadgeCount()

                Log.d("Chess", "Game resigned: $gameId")
            } catch (e: Exception) {
                Log.e("Chess", "Failed to resign", e)
            }
        }
    }

    /**
     * Offer or accept a draw
     */
    fun offerDraw(gameId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gameState = _activeGames.value[gameId] ?: return@launch

                val endEvent = gameState.offerDraw()

                val signedEndEvent =
                    ChessAction.endGame(
                        gameId = endEvent.gameId,
                        result = endEvent.result,
                        termination = endEvent.termination,
                        winnerPubkey = null,
                        opponentPubkey = endEvent.opponentPubkey,
                        pgn = endEvent.pgn ?: "",
                        signer = account.signer,
                    )

                account.client.send(signedEndEvent, account.outboxRelays.flow.value)
                account.cache.justConsumeMyOwnEvent(signedEndEvent)

                // Remove from active games
                _activeGames.value = _activeGames.value - gameId

                updateBadgeCount()

                Log.d("Chess", "Draw offered/accepted: $gameId")
            } catch (e: Exception) {
                Log.e("Chess", "Failed to offer draw", e)
            }
        }
    }

    /**
     * Check if game ended (checkmate/stalemate) and publish result
     */
    private fun checkAndPublishGameEnd(gameId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gameState = _activeGames.value[gameId] ?: return@launch

                if (gameState.gameStatus.value !is com.vitorpamplona.quartz.nip64Chess.GameStatus.Finished) {
                    return@launch
                }

                val finishedStatus =
                    gameState.gameStatus.value as com.vitorpamplona.quartz.nip64Chess.GameStatus.Finished

                val termination =
                    when {
                        gameState.engine.isCheckmate() -> GameTermination.CHECKMATE
                        gameState.engine.isStalemate() -> GameTermination.STALEMATE
                        else -> GameTermination.DRAW_AGREEMENT
                    }

                val winnerPubkey =
                    when (finishedStatus.result) {
                        GameResult.WHITE_WINS ->
                            if (gameState.playerColor == Color.WHITE) {
                                gameState.playerPubkey
                            } else {
                                gameState.opponentPubkey
                            }
                        GameResult.BLACK_WINS ->
                            if (gameState.playerColor == Color.BLACK) {
                                gameState.playerPubkey
                            } else {
                                gameState.opponentPubkey
                            }
                        GameResult.DRAW -> null
                        GameResult.IN_PROGRESS -> null
                    }

                // Generate PGN from move history
                val pgn = buildPGN(gameState, finishedStatus.result)

                val signedEndEvent =
                    ChessAction.endGame(
                        gameId = gameId,
                        result = finishedStatus.result,
                        termination = termination,
                        winnerPubkey = winnerPubkey,
                        opponentPubkey = gameState.opponentPubkey,
                        pgn = pgn,
                        signer = account.signer,
                    )

                account.client.send(signedEndEvent, account.outboxRelays.flow.value)
                account.cache.justConsumeMyOwnEvent(signedEndEvent)

                // Remove from active games
                _activeGames.value = _activeGames.value - gameId

                updateBadgeCount()

                Log.d("Chess", "Game ended automatically: $gameId")
            } catch (e: Exception) {
                Log.e("Chess", "Failed to publish game end", e)
            }
        }
    }

    /**
     * Update badge count (incoming challenges + your turn games)
     */
    private fun updateBadgeCount() {
        val yourTurnCount =
            _activeGames.value.values.count { gameState ->
                gameState.isPlayerTurn()
            }

        // TODO: Add incoming challenges count
        // For now, just count "your turn" games

        _badgeCount.value = yourTurnCount
    }

    /**
     * Build PGN from game state
     */
    private fun buildPGN(
        gameState: LiveChessGameState,
        result: GameResult,
    ): String {
        val moves = gameState.moveHistory.value
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

        return """
            [Event "Live Chess Game"]
            [Site "Nostr"]
            [White "${if (gameState.playerColor == Color.WHITE) gameState.playerPubkey else gameState.opponentPubkey}"]
            [Black "${if (gameState.playerColor == Color.BLACK) gameState.playerPubkey else gameState.opponentPubkey}"]
            [Result "${result.notation}"]

            $moveText ${result.notation}
            """.trimIndent()
    }

    override fun onCleared() {
        Log.d("Init", "OnCleared: ChessViewModel")
        super.onCleared()
    }
}
