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
import com.vitorpamplona.amethyst.commons.chess.ChessPollingDefaults
import com.vitorpamplona.amethyst.commons.chess.ChessPollingDelegate
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.filterIntoSet
import com.vitorpamplona.quartz.nip64Chess.ChessEngine
import com.vitorpamplona.quartz.nip64Chess.ChessGameNameGenerator
import com.vitorpamplona.quartz.nip64Chess.ChessMoveEvent
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.GameResult
import com.vitorpamplona.quartz.nip64Chess.LiveChessDrawOfferEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameState
import com.vitorpamplona.quartz.nip64Chess.LiveChessMoveEvent
import com.vitorpamplona.quartz.nip64Chess.PieceType
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing chess game state, challenges, and event publishing
 */
class ChessViewModel(
    private val account: Account,
) : ViewModel() {
    companion object {
        // Challenge expiry: 24 hours
        const val CHALLENGE_EXPIRY_SECONDS = 24 * 60 * 60L

        // Retry configuration
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_MS = 1000L

        // Cleanup interval: check every 5 minutes
        const val CLEANUP_INTERVAL_MS = 5 * 60 * 1000L
    }

    // Active games being played (user is a participant)
    private val _activeGames = MutableStateFlow<Map<String, LiveChessGameState>>(emptyMap())
    val activeGames: StateFlow<Map<String, LiveChessGameState>> = _activeGames.asStateFlow()

    // Public games we can spectate (user is NOT a participant)
    private val _publicGames = MutableStateFlow<List<PublicGameInfo>>(emptyList())
    val publicGames: StateFlow<List<PublicGameInfo>> = _publicGames.asStateFlow()

    // Spectating games (user is watching but not playing)
    private val _spectatingGames = MutableStateFlow<Map<String, LiveChessGameState>>(emptyMap())
    val spectatingGames: StateFlow<Map<String, LiveChessGameState>> = _spectatingGames.asStateFlow()

    // Pending challenges (ALL non-expired challenges)
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

    // Pending retry operations
    private val _pendingRetries = MutableStateFlow<Map<String, RetryOperation>>(emptyMap())
    val pendingRetries: StateFlow<Map<String, RetryOperation>> = _pendingRetries.asStateFlow()

    // Chess status for UI feedback (broadcasting, syncing, etc.)
    private val _chessStatus = MutableStateFlow<ChessStatus>(ChessStatus.Idle)
    val chessStatus: StateFlow<ChessStatus> = _chessStatus.asStateFlow()

    // Connected relays for display
    private val _connectedRelays = MutableStateFlow<List<String>>(emptyList())
    val connectedRelays: StateFlow<List<String>> = _connectedRelays.asStateFlow()

    // Polling delegate for periodic refresh
    private val pollingDelegate =
        ChessPollingDelegate(
            config = ChessPollingDefaults.android,
            scope = viewModelScope,
            onRefreshGames = { gameIds -> refreshGamesFromCache(gameIds) },
            onRefreshChallenges = { refreshChallenges() },
            onCleanup = {
                cleanupExpiredChallenges()
                checkAbandonedGames()
            },
        )

    // Track games currently being created to prevent race conditions (like Desktop)
    private val gamesBeingCreated = mutableSetOf<String>()

    init {
        subscribeToChessEvents()
        // Start polling - will do initial fetch
        startPolling()
    }

    /**
     * Start polling for chess events
     */
    fun startPolling() {
        pollingDelegate.start()
    }

    /**
     * Stop polling (call when screen is not visible on Android)
     */
    fun stopPolling() {
        pollingDelegate.stop()
    }

    /**
     * Force immediate refresh
     */
    fun forceRefresh() {
        pollingDelegate.refreshNow()
    }

    /**
     * Remove expired challenges
     */
    private fun cleanupExpiredChallenges() {
        val now = TimeUtils.now()
        val validChallenges =
            _challenges.value.filter { note ->
                val event = note.event as? LiveChessGameChallengeEvent
                val createdAt = event?.createdAt ?: 0
                (now - createdAt) < CHALLENGE_EXPIRY_SECONDS
            }

        if (validChallenges.size != _challenges.value.size) {
            _challenges.value = validChallenges
            updateBadgeCount()
        }
    }

    /**
     * Check for abandoned games and notify
     */
    private fun checkAbandonedGames() {
        _activeGames.value.forEach { (gameId, state) ->
            if (state.isAbandoned()) {
                // Game is abandoned, could auto-claim or notify user
                _error.value = "Game $gameId appears abandoned. You can claim victory."
            }
        }
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
        val moveNumber = event.moveNumber()

        gameState.applyOpponentMove(san, fen, moveNumber)
        updateBadgeCount()
    }

    /**
     * Handle game acceptance event
     */
    private fun handleGameAccepted(event: LiveChessGameAcceptEvent) {
        val gameId = event.gameId() ?: return

        // Skip if game already exists (prevents overwrite)
        if (_activeGames.value.containsKey(gameId)) return

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
            pollingDelegate.removeGameId(gameId)
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
        val gameId = event.gameId() ?: return

        // Skip if this game is already active
        if (_activeGames.value.containsKey(gameId)) return

        // Add to challenges if directed at us, from us, or is open
        val opponentPubkey = event.opponentPubkey()
        val isFromUs = event.pubKey == account.signer.pubKey
        val isDirectedAtUs = opponentPubkey == account.signer.pubKey
        val isOpenChallenge = opponentPubkey == null

        if (isFromUs || isDirectedAtUs || isOpenChallenge) {
            val currentChallenges = _challenges.value.toMutableList()
            // Deduplicate by both event ID and game ID
            val isDuplicateEvent = currentChallenges.any { it.idHex == note.idHex }
            val isDuplicateGame =
                currentChallenges.any {
                    (it.event as? LiveChessGameChallengeEvent)?.gameId() == gameId
                }
            if (!isDuplicateEvent && !isDuplicateGame) {
                currentChallenges.add(note)
                _challenges.value = currentChallenges
                updateBadgeCount()
            }
        }
    }

    /**
     * Create a new chess challenge (open or directed) with retry
     */
    fun createChallenge(
        opponentPubkey: String? = null,
        playerColor: Color = Color.WHITE,
        timeControl: String? = null,
    ) {
        val acc = account
        val gameId = generateGameId()

        viewModelScope.launch(Dispatchers.IO) {
            val relayCount = acc.outboxRelays.flow.value.size
            _chessStatus.value =
                ChessStatus.BroadcastingMove(
                    san = "Challenge",
                    successCount = 0,
                    totalRelays = relayCount,
                )

            val success =
                retryWithBackoff("challenge-$gameId") {
                    val template =
                        LiveChessGameChallengeEvent.build(
                            gameId = gameId,
                            playerColor = playerColor,
                            opponentPubkey = opponentPubkey,
                            timeControl = timeControl,
                        )

                    acc.signAndComputeBroadcast(template)
                }

            if (success) {
                _chessStatus.value =
                    ChessStatus.MoveSuccess(
                        san = "Challenge",
                        relayCount = relayCount,
                    )
                _error.value = null
                refreshChallenges()
                delay(3000)
                if (_chessStatus.value is ChessStatus.MoveSuccess) {
                    _chessStatus.value = ChessStatus.Idle
                }
            } else {
                _chessStatus.value =
                    ChessStatus.MoveFailed(
                        san = "Challenge",
                        error = "Failed after $MAX_RETRIES attempts",
                    )
                _error.value = "Failed to create challenge after $MAX_RETRIES attempts"
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
        // Mark as being created to prevent duplicate from relay echo
        synchronized(gamesBeingCreated) {
            if (!gamesBeingCreated.add(gameId)) return // Already being created
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val template =
                    LiveChessGameAcceptEvent.build(
                        gameId = gameId,
                        challengeEventId = challengeEventId,
                        challengerPubkey = challengerPubkey,
                    )

                account.signAndComputeBroadcast(template)

                // Check if game was already created by relay echo while we were broadcasting
                if (_activeGames.value.containsKey(gameId)) {
                    synchronized(gamesBeingCreated) {
                        gamesBeingCreated.remove(gameId)
                    }
                    _selectedGameId.value = gameId
                    _error.value = null
                    return@launch
                }

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
                synchronized(gamesBeingCreated) {
                    gamesBeingCreated.remove(gameId)
                }
                pollingDelegate.addGameId(gameId)
                _selectedGameId.value = gameId
                _error.value = null
            } catch (e: Exception) {
                synchronized(gamesBeingCreated) {
                    gamesBeingCreated.remove(gameId)
                }
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

        // Skip if game already exists (prevents overwrite)
        if (_activeGames.value.containsKey(gameId)) {
            _selectedGameId.value = gameId
            return
        }

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

            // Skip if game already exists or is being created (prevents overwrite)
            if (_activeGames.value.containsKey(gameId)) return@launch
            synchronized(gamesBeingCreated) {
                if (!gamesBeingCreated.add(gameId)) return@launch // Already being created
            }

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
            synchronized(gamesBeingCreated) {
                gamesBeingCreated.remove(gameId)
            }
            pollingDelegate.addGameId(gameId)
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

        // Parse promotion from 'to' if present (e.g., "e8q" -> square="e8", promotion=QUEEN)
        val (targetSquare, promotion) = parsePromotionFromTarget(to)

        val moveResult = gameState.makeMove(from, targetSquare, promotion)
        if (moveResult != null) {
            publishMove(gameId, moveResult)
        }
    }

    /**
     * Parse promotion piece from target square string.
     * e.g., "e8q" -> ("e8", QUEEN), "e4" -> ("e4", null)
     */
    private fun parsePromotionFromTarget(to: String): Pair<String, PieceType?> {
        if (to.length == 3) {
            val square = to.substring(0, 2)
            val promotion =
                when (to[2].lowercaseChar()) {
                    'q' -> PieceType.QUEEN
                    'r' -> PieceType.ROOK
                    'b' -> PieceType.BISHOP
                    'n' -> PieceType.KNIGHT
                    else -> null
                }
            return square to promotion
        }
        return to to null
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
                // Update status to broadcasting
                val relayCount = account.outboxRelays.flow.value.size
                _chessStatus.value =
                    ChessStatus.BroadcastingMove(
                        san = moveEvent.san,
                        successCount = 0,
                        totalRelays = relayCount,
                    )

                val template =
                    LiveChessMoveEvent.build(
                        gameId = moveEvent.gameId,
                        moveNumber = moveEvent.moveNumber,
                        san = moveEvent.san,
                        fen = moveEvent.fen,
                        opponentPubkey = moveEvent.opponentPubkey,
                    )

                account.signAndComputeBroadcast(template)

                // Update status to success
                _chessStatus.value =
                    ChessStatus.MoveSuccess(
                        san = moveEvent.san,
                        relayCount = relayCount,
                    )

                // Clear status after delay
                delay(3000)
                if (_chessStatus.value is ChessStatus.MoveSuccess) {
                    val gameState = _activeGames.value[gameId]
                    _chessStatus.value =
                        if (gameState?.isPlayerTurn() == false) {
                            ChessStatus.WaitingForOpponent
                        } else {
                            ChessStatus.Idle
                        }
                }

                _error.value = null
            } catch (e: Exception) {
                _chessStatus.value =
                    ChessStatus.MoveFailed(
                        san = moveEvent.san,
                        error = e.message ?: "Unknown error",
                    )
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
     * Offer a draw to opponent - sends a draw offer event that opponent can accept or decline
     */
    fun offerDraw(gameId: String) {
        val acc = account
        val gameState = _activeGames.value[gameId] ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val drawOffer = gameState.offerDraw()

                val template =
                    LiveChessDrawOfferEvent.build(
                        gameId = drawOffer.gameId,
                        opponentPubkey = drawOffer.opponentPubkey,
                        message = drawOffer.message ?: "",
                    )

                acc.signAndComputeBroadcast(template)
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
     *
     * Following jesterui pattern: fetch ALL non-expired challenges,
     * let UI filter by category (incoming, outgoing, open)
     *
     * NOTE: Chess events are addressable (kinds 30064+), so we query LocalCache.addressables
     */
    fun refreshChallenges() {
        viewModelScope.launch(Dispatchers.IO) {
            val userPubkey = account.signer.pubKey
            val now = TimeUtils.now()

            // Query LocalCache.addressables for chess challenge events (kind 30064)
            // Chess events are addressable events, not regular notes!
            val challengeNotes =
                LocalCache.addressables.filterIntoSet(LiveChessGameChallengeEvent.KIND) { _, note ->
                    val event = note.event as? LiveChessGameChallengeEvent ?: return@filterIntoSet false

                    // Check if challenge is not expired (24h)
                    val createdAt = event.createdAt
                    if ((now - createdAt) >= CHALLENGE_EXPIRY_SECONDS) return@filterIntoSet false

                    // Check if game isn't already active (accepted)
                    val gameId = event.gameId() ?: return@filterIntoSet false
                    if (_activeGames.value.containsKey(gameId)) return@filterIntoSet false

                    // Include all challenges:
                    // - Directed at us
                    // - Open challenges (no specific opponent)
                    // - Our own challenges (waiting for accept)
                    val opponentPubkey = event.opponentPubkey()
                    val isDirectedAtUs = opponentPubkey == userPubkey
                    val isOpenChallenge = opponentPubkey == null
                    val isFromUs = event.pubKey == userPubkey

                    isDirectedAtUs || isOpenChallenge || isFromUs
                }

            _challenges.value = challengeNotes.toList()
            updateBadgeCount()

            // Also refresh public games
            refreshPublicGames()
        }
    }

    /**
     * Refresh list of public games that can be spectated
     *
     * NOTE: Chess events are addressable (kinds 30064+), so we query LocalCache.addressables
     */
    private fun refreshPublicGames() {
        val userPubkey = account.signer.pubKey
        val now = TimeUtils.now()

        // Find all games where both challenge and accept exist
        // Game ID -> (ChallengeEvent, AcceptEvent)
        val gameData = mutableMapOf<String, Pair<LiveChessGameChallengeEvent?, LiveChessGameAcceptEvent?>>()

        // Collect challenges from addressables (kind 30064)
        LocalCache.addressables.filterIntoSet(LiveChessGameChallengeEvent.KIND) { _, note ->
            val event = note.event as? LiveChessGameChallengeEvent ?: return@filterIntoSet false
            val gameId = event.gameId() ?: return@filterIntoSet false
            gameData[gameId] = (event to gameData[gameId]?.second)
            false // Don't need to collect, just iterate
        }

        // Collect accepts from addressables (kind 30065)
        LocalCache.addressables.filterIntoSet(LiveChessGameAcceptEvent.KIND) { _, note ->
            val event = note.event as? LiveChessGameAcceptEvent ?: return@filterIntoSet false
            val gameId = event.gameId() ?: return@filterIntoSet false
            gameData[gameId] = (gameData[gameId]?.first to event)
            false
        }

        // Filter to games that are active and user is NOT a participant
        val publicGames = mutableListOf<PublicGameInfo>()

        for ((gameId, data) in gameData) {
            val (challenge, accept) = data
            if (challenge == null || accept == null) continue

            // Skip if user is a participant
            val challengerPubkey = challenge.pubKey
            val acceptorPubkey = accept.pubKey
            if (challengerPubkey == userPubkey || acceptorPubkey == userPubkey) continue

            // Skip if already in our active/spectating games
            if (_activeGames.value.containsKey(gameId) || _spectatingGames.value.containsKey(gameId)) continue

            // Determine white/black based on challenger color
            val challengerColor = challenge.playerColor() ?: Color.WHITE
            val (whitePubkey, blackPubkey) =
                if (challengerColor == Color.WHITE) {
                    challengerPubkey to acceptorPubkey
                } else {
                    acceptorPubkey to challengerPubkey
                }

            // Count moves and get last move time from addressables (kind 30066)
            var moveCount = 0
            var lastMoveTime = accept.createdAt

            LocalCache.addressables.filterIntoSet(LiveChessMoveEvent.KIND) { _, note ->
                val moveEvent = note.event as? LiveChessMoveEvent ?: return@filterIntoSet false
                if (moveEvent.gameId() != gameId) return@filterIntoSet false
                moveCount++
                if (moveEvent.createdAt > lastMoveTime) {
                    lastMoveTime = moveEvent.createdAt
                }
                false
            }

            // Skip inactive games (no moves in 24 hours)
            if ((now - lastMoveTime) > CHALLENGE_EXPIRY_SECONDS) continue

            publicGames.add(
                PublicGameInfo(
                    gameId = gameId,
                    whitePubkey = whitePubkey,
                    blackPubkey = blackPubkey,
                    moveCount = moveCount,
                    lastMoveTime = lastMoveTime,
                ),
            )
        }

        // Sort by most recent activity
        _publicGames.value = publicGames.sortedByDescending { it.lastMoveTime }
    }

    /**
     * Load a game as spectator (watch-only mode)
     * Returns the game state or null if loading failed
     *
     * NOTE: Chess events are addressable (kinds 30064+), so we query LocalCache.addressables
     */
    fun loadGameAsSpectator(gameId: String): LiveChessGameState? {
        // Check if already spectating
        _spectatingGames.value[gameId]?.let { return it }

        // Check if we're actually a participant (shouldn't load as spectator)
        _activeGames.value[gameId]?.let {
            _error.value = "You are a participant in this game"
            return null
        }

        val userPubkey = account.signer.pubKey

        // Find challenge and accept events from addressables
        val challengeNotes =
            LocalCache.addressables.filterIntoSet(LiveChessGameChallengeEvent.KIND) { _, note ->
                val event = note.event as? LiveChessGameChallengeEvent ?: return@filterIntoSet false
                event.gameId() == gameId
            }
        val acceptNotes =
            LocalCache.addressables.filterIntoSet(LiveChessGameAcceptEvent.KIND) { _, note ->
                val event = note.event as? LiveChessGameAcceptEvent ?: return@filterIntoSet false
                event.gameId() == gameId
            }

        val challengeEvent = challengeNotes.firstOrNull()?.event as? LiveChessGameChallengeEvent
        val acceptEvent = acceptNotes.firstOrNull()?.event as? LiveChessGameAcceptEvent

        if (challengeEvent == null) {
            _error.value = "Challenge not found for game"
            return null
        }

        if (acceptEvent == null) {
            _error.value = "Game not started yet - waiting for opponent"
            return null
        }

        // Determine white/black
        val challengerPubkey = challengeEvent.pubKey
        val acceptorPubkey = acceptEvent.pubKey
        val challengerColor = challengeEvent.playerColor() ?: Color.WHITE

        val (whitePubkey, blackPubkey) =
            if (challengerColor == Color.WHITE) {
                challengerPubkey to acceptorPubkey
            } else {
                acceptorPubkey to challengerPubkey
            }

        // Build engine state by replaying moves from addressables
        val engine = ChessEngine()
        engine.reset()

        val moveNotes =
            LocalCache.addressables.filterIntoSet(LiveChessMoveEvent.KIND) { _, note ->
                val event = note.event as? LiveChessMoveEvent ?: return@filterIntoSet false
                event.gameId() == gameId
            }

        val sortedMoves =
            moveNotes
                .mapNotNull { it.event as? LiveChessMoveEvent }
                .sortedBy { it.moveNumber() ?: Int.MAX_VALUE }

        for (moveEvent in sortedMoves) {
            val san = moveEvent.san() ?: continue
            try {
                engine.makeMove(san)
            } catch (e: Exception) {
                _error.value = "Error loading move $san: ${e.message}"
            }
        }

        // Create spectator game state - always view from white's perspective
        val gameState =
            LiveChessGameState(
                gameId = gameId,
                playerPubkey = userPubkey,
                opponentPubkey = blackPubkey, // Opponent relative to spectator view (white)
                playerColor = Color.WHITE, // Spectators see from white's perspective
                engine = engine,
                isSpectator = true,
            )

        // Add to spectating games
        _spectatingGames.value = _spectatingGames.value + (gameId to gameState)
        pollingDelegate.addGameId(gameId)

        _error.value = null
        return gameState
    }

    /**
     * Stop spectating a game
     */
    fun stopSpectating(gameId: String) {
        if (_spectatingGames.value.containsKey(gameId)) {
            _spectatingGames.value = _spectatingGames.value - gameId
            pollingDelegate.removeGameId(gameId)
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
     * Claim victory for an abandoned game
     */
    fun claimAbandonmentVictory(gameId: String) {
        val gameState = _activeGames.value[gameId] ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val endData = gameState.claimAbandonmentVictory() ?: return@launch

            val success =
                retryWithBackoff("abandon-$gameId") {
                    val template =
                        LiveChessGameEndEvent.build(
                            gameId = endData.gameId,
                            result = endData.result,
                            termination = endData.termination,
                            winnerPubkey = endData.winnerPubkey,
                            opponentPubkey = endData.opponentPubkey,
                            pgn = endData.pgn ?: "",
                        )

                    account.signAndComputeBroadcast(template)
                }

            if (success) {
                _activeGames.value = _activeGames.value - gameId
                _error.value = null
            } else {
                _error.value = "Failed to claim victory"
            }
        }
    }

    /**
     * Force resync a game to opponent's position
     */
    fun forceResync(
        gameId: String,
        fen: String,
    ) {
        val gameState = _activeGames.value[gameId] ?: return
        gameState.forceResync(fen)
    }

    /**
     * Retry an operation with exponential backoff
     */
    private suspend fun retryWithBackoff(
        operationId: String,
        operation: suspend () -> Unit,
    ): Boolean {
        var attempt = 0
        while (attempt < MAX_RETRIES) {
            try {
                _pendingRetries.value =
                    _pendingRetries.value + (operationId to RetryOperation(operationId, attempt + 1, MAX_RETRIES))
                operation()
                _pendingRetries.value = _pendingRetries.value - operationId
                return true
            } catch (e: Exception) {
                attempt++
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAY_MS * attempt)
                }
            }
        }
        _pendingRetries.value = _pendingRetries.value - operationId
        return false
    }

    /**
     * Generate unique game ID with human-readable component
     */
    private fun generateGameId(): String = ChessGameNameGenerator.generateGameId(TimeUtils.now())

    /**
     * Refresh game state from LocalCache for specific game IDs
     * Called periodically by polling delegate
     *
     * NOTE: Chess events are addressable (kinds 30064+), so we query LocalCache.addressables
     */
    private suspend fun refreshGamesFromCache(gameIds: Set<String>) {
        val userPubkey = account.signer.pubKey

        for (gameId in gameIds) {
            // Find moves for this game from addressables (kind 30066)
            val moveNotes =
                LocalCache.addressables.filterIntoSet(LiveChessMoveEvent.KIND) { _, note ->
                    val event = note.event as? LiveChessMoveEvent ?: return@filterIntoSet false
                    event.gameId() == gameId
                }

            // Check active games first
            val activeGameState = _activeGames.value[gameId]
            if (activeGameState != null) {
                // Apply any new moves from opponent
                moveNotes
                    .mapNotNull { it.event as? LiveChessMoveEvent }
                    .filter { it.pubKey != userPubkey } // Only opponent moves
                    .sortedBy { it.moveNumber() }
                    .forEach { moveEvent ->
                        val san = moveEvent.san() ?: return@forEach
                        val fen = moveEvent.fen() ?: return@forEach
                        val moveNumber = moveEvent.moveNumber()
                        activeGameState.applyOpponentMove(san, fen, moveNumber)
                    }
            }

            // Check spectating games - apply ALL moves (from both players)
            val spectatingGameState = _spectatingGames.value[gameId]
            if (spectatingGameState != null) {
                moveNotes
                    .mapNotNull { it.event as? LiveChessMoveEvent }
                    .sortedBy { it.moveNumber() }
                    .forEach { moveEvent ->
                        val san = moveEvent.san() ?: return@forEach
                        val fen = moveEvent.fen() ?: return@forEach
                        val moveNumber = moveEvent.moveNumber()
                        spectatingGameState.applyOpponentMove(san, fen, moveNumber)
                    }
            }

            // Game is being polled but not yet active — accept event may have arrived since
            // initial load. Retry loading from cache to pick up late-arriving accept events.
            if (activeGameState == null && spectatingGameState == null) {
                loadGameFromCache(gameId)
            }
        }

        updateBadgeCount()
    }

    /**
     * Load or rebuild game state from LocalCache for a specific gameId
     * Used when navigating to a game screen
     *
     * NOTE: Chess events are addressable (kinds 30064+), so we query LocalCache.addressables
     *
     * @return GameLoadResult with either the game state or an error reason
     */
    fun loadGameFromCache(gameId: String): LiveChessGameState? {
        // Check if already loaded
        _activeGames.value[gameId]?.let { return it }

        val userPubkey = account.signer.pubKey

        // Find the challenge event for this game from addressables (kind 30064)
        val challengeNotes =
            LocalCache.addressables.filterIntoSet(LiveChessGameChallengeEvent.KIND) { _, note ->
                val event = note.event as? LiveChessGameChallengeEvent ?: return@filterIntoSet false
                event.gameId() == gameId
            }
        val challengeNote = challengeNotes.firstOrNull()

        // Find accept event for this game from addressables (kind 30065)
        val acceptNotes =
            LocalCache.addressables.filterIntoSet(LiveChessGameAcceptEvent.KIND) { _, note ->
                val event = note.event as? LiveChessGameAcceptEvent ?: return@filterIntoSet false
                event.gameId() == gameId
            }
        val acceptNote = acceptNotes.firstOrNull()

        // Need challenge to understand the game
        val challengeEvent = challengeNote?.event as? LiveChessGameChallengeEvent
        if (challengeEvent == null) {
            _error.value = "Challenge event not found for game $gameId"
            return null
        }

        val acceptEvent = acceptNote?.event as? LiveChessGameAcceptEvent

        // Determine if we're a participant
        val challengerPubkey = challengeEvent.pubKey
        val acceptorPubkey = acceptEvent?.pubKey
        val challengerColor = challengeEvent.playerColor() ?: Color.WHITE

        val (playerColor, opponentPubkey) =
            when {
                challengerPubkey == userPubkey && acceptorPubkey != null -> {
                    // We created the challenge, someone accepted
                    challengerColor to acceptorPubkey
                }
                acceptorPubkey == userPubkey -> {
                    // We accepted someone's challenge
                    challengerColor.opposite() to challengerPubkey
                }
                challengerPubkey == userPubkey && acceptorPubkey == null -> {
                    // We created challenge but no one accepted yet - show it anyway
                    // Use targeted opponent or empty string for open challenges
                    val targetedOpponent = challengeEvent.opponentPubkey() ?: ""
                    challengerColor to targetedOpponent
                }
                else -> {
                    _error.value = "You are not a participant in this game"
                    return null
                }
            }

        // Build game state
        val engine = ChessEngine()
        engine.reset()

        // Find and apply all moves in order from addressables (kind 30066)
        val moveNotes =
            LocalCache.addressables.filterIntoSet(LiveChessMoveEvent.KIND) { _, note ->
                val event = note.event as? LiveChessMoveEvent ?: return@filterIntoSet false
                event.gameId() == gameId
            }

        val sortedMoves =
            moveNotes
                .mapNotNull { it.event as? LiveChessMoveEvent }
                .sortedBy { it.moveNumber() ?: Int.MAX_VALUE }

        // Track move numbers we've loaded
        val loadedMoveNumbers = mutableSetOf<Int>()

        for (moveEvent in sortedMoves) {
            val san = moveEvent.san() ?: continue
            val moveNumber = moveEvent.moveNumber()
            try {
                engine.makeMove(san)
                if (moveNumber != null) {
                    loadedMoveNumbers.add(moveNumber)
                }
            } catch (e: Exception) {
                _error.value = "Error loading move $san: ${e.message}"
                // Continue trying to load other moves
            }
        }

        // Only pending if we're the challenger, no accept event found, AND no moves exist
        // (moves prove the game was accepted even if accept event isn't in cache)
        val isPendingChallenge = challengerPubkey == userPubkey && acceptorPubkey == null && sortedMoves.isEmpty()

        val gameState =
            LiveChessGameState(
                gameId = gameId,
                playerPubkey = userPubkey,
                opponentPubkey = opponentPubkey,
                playerColor = playerColor,
                engine = engine,
                isPendingChallenge = isPendingChallenge,
            )

        // Mark loaded moves as received to prevent re-application during refresh
        gameState.markMovesAsReceived(loadedMoveNumbers)

        // Check for end event (kind 30067) — game may have been resigned/finished
        val endNotes =
            LocalCache.addressables.filterIntoSet(LiveChessGameEndEvent.KIND) { _, note ->
                val event = note.event as? LiveChessGameEndEvent ?: return@filterIntoSet false
                event.gameId() == gameId
            }
        val endEvent = endNotes.firstOrNull()?.event as? LiveChessGameEndEvent
        if (endEvent != null) {
            val result = GameResult.parse(endEvent.result() ?: "*")
            gameState.markAsFinished(result)
        }

        // Only add to active games if not pending (pending challenges are view-only)
        if (!isPendingChallenge) {
            _activeGames.value = _activeGames.value + (gameId to gameState)
        }

        // Update polling delegate with this game
        pollingDelegate.addGameId(gameId)

        // Clear any error since we loaded successfully
        _error.value = null

        return gameState
    }

    /**
     * Called when ViewModel is cleared
     */
    override fun onCleared() {
        super.onCleared()
        pollingDelegate.stop()
    }
}

/**
 * Represents a pending retry operation for UI feedback
 */
data class RetryOperation(
    val id: String,
    val currentAttempt: Int,
    val maxAttempts: Int,
)

/**
 * Info about a public game that can be spectated
 */
data class PublicGameInfo(
    val gameId: String,
    val whitePubkey: String,
    val blackPubkey: String,
    val moveCount: Int,
    val lastMoveTime: Long,
)
