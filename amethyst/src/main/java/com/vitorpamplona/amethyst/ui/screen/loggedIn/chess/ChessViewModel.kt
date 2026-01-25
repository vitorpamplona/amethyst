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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.quartz.nip64Chess.ChessEngine
import com.vitorpamplona.quartz.nip64Chess.ChessMoveEvent
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameState
import com.vitorpamplona.quartz.nip64Chess.LiveChessMoveEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel for managing chess game state, challenges, and event publishing
 */
class ChessViewModel(
    private val account: Account,
) : ViewModel() {
    // Active games being played
    private val _activeGames = MutableStateFlow<Map<String, LiveChessGameState>>(emptyMap())
    val activeGames: StateFlow<Map<String, LiveChessGameState>> = _activeGames.asStateFlow()

    // Pending challenges (incoming and outgoing)
    private val _challenges = MutableStateFlow<List<Note>>(emptyList())
    val challenges: StateFlow<List<Note>> = _challenges.asStateFlow()

    // Badge count for notifications (incoming challenges + your turn games)
    private val _badgeCount = MutableStateFlow(0)
    val badgeCount: StateFlow<Int> = _badgeCount.asStateFlow()

    // Currently selected game (for navigation)
    private val _selectedGameId = MutableStateFlow<String?>(null)
    val selectedGameId: StateFlow<String?> = _selectedGameId.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refreshChallenges()
        subscribeToChessEvents()
    }

    /**
     * Subscribe to incoming chess events from LocalCache
     */
    private fun subscribeToChessEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect { newNotes ->
                for (note in newNotes) {
                    when (val event = note.event) {
                        is LiveChessMoveEvent -> handleIncomingMove(event)
                        is LiveChessGameAcceptEvent -> handleGameAccepted(event)
                        is LiveChessGameEndEvent -> handleGameEnded(event)
                        is LiveChessGameChallengeEvent -> handleNewChallenge(note, event)
                    }
                }
            }
        }
    }

    /**
     * Handle incoming move event from opponent
     */
    private fun handleIncomingMove(event: LiveChessMoveEvent) {
        // Only process moves from opponents (not our own)
        if (event.pubKey == account.signer.pubKey) return

        val gameId = event.gameId() ?: return
        val gameState = _activeGames.value[gameId] ?: return

        // Verify this move is from our opponent
        if (event.pubKey != gameState.opponentPubkey) return

        val san = event.san() ?: return
        val fen = event.fen() ?: return

        gameState.applyOpponentMove(san, fen)
        updateBadgeCount()
    }

    /**
     * Handle game acceptance event
     */
    private fun handleGameAccepted(event: LiveChessGameAcceptEvent) {
        // Check if this is acceptance of our challenge
        if (event.challengerPubkey() == account.signer.pubKey) {
            startGameFromAcceptance(event)
        }
    }

    /**
     * Handle game end event
     */
    private fun handleGameEnded(event: LiveChessGameEndEvent) {
        val gameId = event.gameId() ?: return

        // Remove from active games if present
        if (_activeGames.value.containsKey(gameId)) {
            _activeGames.value = _activeGames.value - gameId
            updateBadgeCount()
        }
    }

    /**
     * Handle new challenge event (for feed display)
     */
    private fun handleNewChallenge(
        note: Note,
        event: LiveChessGameChallengeEvent,
    ) {
        // Add to challenges if directed at us or is open
        val opponentPubkey = event.opponentPubkey()
        if (opponentPubkey == null || opponentPubkey == account.signer.pubKey) {
            val currentChallenges = _challenges.value.toMutableList()
            if (currentChallenges.none { it.idHex == note.idHex }) {
                currentChallenges.add(note)
                _challenges.value = currentChallenges
                updateBadgeCount()
            }
        }
    }

    /**
     * Create a new chess challenge (open or directed)
     */
    fun createChallenge(
        opponentPubkey: String? = null,
        playerColor: Color = Color.WHITE,
        timeControl: String? = null,
    ) {
        val acc = account

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gameId = generateGameId()

                val template =
                    LiveChessGameChallengeEvent.build(
                        gameId = gameId,
                        playerColor = playerColor,
                        opponentPubkey = opponentPubkey,
                        timeControl = timeControl,
                    )

                acc.signAndComputeBroadcast(template)

                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to create challenge: ${e.message}"
            }
        }
    }

    /**
     * Accept a chess challenge (simple version for UI callbacks)
     */
    fun acceptChallenge(
        challengeEventId: String,
        gameId: String,
        challengerPubkey: String,
        playerColor: Color,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val template =
                    LiveChessGameAcceptEvent.build(
                        gameId = gameId,
                        challengeEventId = challengeEventId,
                        challengerPubkey = challengerPubkey,
                    )

                account.signAndComputeBroadcast(template)

                // Create game state
                val engine = ChessEngine()
                engine.reset()

                val gameState =
                    LiveChessGameState(
                        gameId = gameId,
                        playerPubkey = account.signer.pubKey,
                        opponentPubkey = challengerPubkey,
                        playerColor = playerColor,
                        engine = engine,
                    )

                _activeGames.value = _activeGames.value + (gameId to gameState)
                _selectedGameId.value = gameId
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to accept challenge: ${e.message}"
            }
        }
    }

    /**
     * Accept a chess challenge (from Note)
     */
    fun acceptChallenge(challengeNote: Note) {
        val challengeEvent = challengeNote.event as? LiveChessGameChallengeEvent ?: return

        val gameId = challengeEvent.gameId() ?: return
        val challengerPubkey = challengeEvent.pubKey
        val challengerColor = challengeEvent.playerColor() ?: Color.WHITE
        val playerColor = challengerColor.opposite()

        acceptChallenge(challengeEvent.id, gameId, challengerPubkey, playerColor)
    }

    /**
     * Start a game after your challenge was accepted
     */
    fun startGameFromAcceptance(acceptEvent: LiveChessGameAcceptEvent) {
        val acc = account

        viewModelScope.launch(Dispatchers.IO) {
            val gameId = acceptEvent.gameId() ?: return@launch
            val opponentPubkey = acceptEvent.pubKey

            // Find our challenge to get player color
            val challengeNote =
                _challenges.value.find { note ->
                    (note.event as? LiveChessGameChallengeEvent)?.gameId() == gameId
                }
            val challengeEvent = challengeNote?.event as? LiveChessGameChallengeEvent
            val playerColor = challengeEvent?.playerColor() ?: Color.WHITE

            val engine = ChessEngine()
            engine.reset()

            val gameState =
                LiveChessGameState(
                    gameId = gameId,
                    playerPubkey = acc.signer.pubKey,
                    opponentPubkey = opponentPubkey,
                    playerColor = playerColor,
                    engine = engine,
                )

            _activeGames.value = _activeGames.value + (gameId to gameState)
            _selectedGameId.value = gameId
        }
    }

    /**
     * Publish a move for the current game (simple version for UI callbacks)
     */
    fun publishMove(
        gameId: String,
        from: String,
        to: String,
    ) {
        val gameState = _activeGames.value[gameId] ?: return
        val moveResult = gameState.makeMove(from, to)
        if (moveResult != null) {
            publishMove(gameId, moveResult)
        }
    }

    /**
     * Publish a move for the current game (full version with ChessMoveEvent)
     */
    fun publishMove(
        gameId: String,
        moveEvent: ChessMoveEvent,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val template =
                    LiveChessMoveEvent.build(
                        gameId = moveEvent.gameId,
                        moveNumber = moveEvent.moveNumber,
                        san = moveEvent.san,
                        fen = moveEvent.fen,
                        opponentPubkey = moveEvent.opponentPubkey,
                    )

                account.signAndComputeBroadcast(template)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to publish move: ${e.message}"
            }
        }
    }

    /**
     * Handle incoming move from opponent
     */
    fun handleOpponentMove(moveEvent: LiveChessMoveEvent) {
        val gameId = moveEvent.gameId() ?: return
        val gameState = _activeGames.value[gameId] ?: return

        val san = moveEvent.san() ?: return
        val fen = moveEvent.fen() ?: return

        gameState.applyOpponentMove(san, fen)
        updateBadgeCount()
    }

    /**
     * Resign from a game
     */
    fun resign(gameId: String) {
        val acc = account
        val gameState = _activeGames.value[gameId] ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val endData = gameState.resign()

                val template =
                    LiveChessGameEndEvent.build(
                        gameId = endData.gameId,
                        result = endData.result,
                        termination = endData.termination,
                        winnerPubkey = endData.winnerPubkey,
                        opponentPubkey = endData.opponentPubkey,
                        pgn = endData.pgn ?: "",
                    )

                acc.signAndComputeBroadcast(template)

                // Remove from active games
                _activeGames.value = _activeGames.value - gameId
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to resign: ${e.message}"
            }
        }
    }

    /**
     * Offer/accept draw
     */
    fun offerDraw(gameId: String) {
        val acc = account
        val gameState = _activeGames.value[gameId] ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val endData = gameState.offerDraw()

                val template =
                    LiveChessGameEndEvent.build(
                        gameId = endData.gameId,
                        result = endData.result,
                        termination = endData.termination,
                        winnerPubkey = endData.winnerPubkey,
                        opponentPubkey = endData.opponentPubkey,
                        pgn = endData.pgn ?: "",
                    )

                acc.signAndComputeBroadcast(template)

                // Remove from active games
                _activeGames.value = _activeGames.value - gameId
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to offer draw: ${e.message}"
            }
        }
    }

    /**
     * Select a game to view/play
     */
    fun selectGame(gameId: String?) {
        _selectedGameId.value = gameId
    }

    /**
     * Get game state for a specific game
     */
    fun getGameState(gameId: String): LiveChessGameState? = _activeGames.value[gameId]

    /**
     * Refresh challenges from local cache
     */
    private fun refreshChallenges() {
        viewModelScope.launch(Dispatchers.IO) {
            // TODO: Subscribe to challenge events via relay filter
            // For now, this is a placeholder
            updateBadgeCount()
        }
    }

    /**
     * Update badge count based on incoming challenges and games where it's your turn
     */
    private fun updateBadgeCount() {
        val acc = account
        val userPubkey = acc.signer.pubKey

        val incomingChallenges =
            _challenges.value.count { note ->
                val event = note.event as? LiveChessGameChallengeEvent
                event?.opponentPubkey() == userPubkey
            }

        val yourTurnGames = _activeGames.value.values.count { it.isPlayerTurn() }

        _badgeCount.value = incomingChallenges + yourTurnGames
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Generate unique game ID
     */
    private fun generateGameId(): String {
        val timestamp = TimeUtils.now()
        val random = UUID.randomUUID().toString().take(8)
        return "chess-$timestamp-$random"
    }
}
