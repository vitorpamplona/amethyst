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
package com.vitorpamplona.amethyst.commons.chess

import com.vitorpamplona.quartz.nip64Chess.ChessGameEnd
import com.vitorpamplona.quartz.nip64Chess.ChessMoveEvent
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.GameResult
import com.vitorpamplona.quartz.nip64Chess.GameStatus
import com.vitorpamplona.quartz.nip64Chess.PieceType
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterGameEvents
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Interface for platform-specific chess event publishing using Jester protocol.
 *
 * All chess events use kind 30 with JSON content:
 * - Start events: content.kind=0
 * - Move events: content.kind=1 with full history
 */
interface ChessEventPublisher {
    /**
     * Publish a game start event (challenge).
     * Returns the startEventId (event ID) if successful.
     */
    suspend fun publishStart(
        playerColor: Color,
        opponentPubkey: String?,
    ): String?

    /**
     * Publish a move event.
     * Move events include full history and link to previous move.
     */
    suspend fun publishMove(move: ChessMoveEvent): String?

    /**
     * Publish a game end event (includes result in content).
     */
    suspend fun publishGameEnd(gameEnd: ChessGameEnd): Boolean

    /**
     * Get count of write relays for UI feedback.
     */
    fun getWriteRelayCount(): Int
}

/**
 * Relay-first fetcher interface for Jester protocol.
 * Platforms provide one-shot relay queries.
 *
 * Every fetch does: one-shot REQ → collect events → EOSE → close.
 * No caching — relays are the single source of truth.
 */
interface ChessRelayFetcher {
    /** Fetch all events for a specific game by startEventId */
    suspend fun fetchGameEvents(startEventId: String): JesterGameEvents

    /** Fetch recent start/challenge events with optional progress callback */
    suspend fun fetchChallenges(onProgress: ((RelayFetchProgress) -> Unit)? = null): List<JesterEvent>

    /** Fetch recent public game summaries for spectating */
    suspend fun fetchRecentGames(): List<RelayGameSummary>

    /** Fetch game IDs (startEventIds) where user is a participant */
    suspend fun fetchUserGameIds(onProgress: ((RelayFetchProgress) -> Unit)? = null): Set<String>

    /** Get the list of relay URLs that will be used for fetching */
    fun getRelayUrls(): List<String>
}

/**
 * Summary of a game found on relays (for lobby display / spectating)
 */
data class RelayGameSummary(
    val startEventId: String,
    val whitePubkey: String,
    val blackPubkey: String,
    val moveCount: Int,
    val lastMoveTime: Long,
    val isActive: Boolean,
) {
    // Legacy compatibility
    @Deprecated("Use startEventId instead", ReplaceWith("startEventId"))
    val gameId: String get() = startEventId
}

/**
 * Shared chess lobby logic — relay-first architecture with Jester protocol.
 *
 * Both Android and Desktop use this identically.
 * Platform-specific code only implements:
 * - ChessEventPublisher: sign + broadcast events
 * - ChessRelayFetcher: one-shot relay queries
 * - IUserMetadataProvider: display names / avatars
 */
class ChessLobbyLogic(
    private val userPubkey: String,
    private val publisher: ChessEventPublisher,
    private val fetcher: ChessRelayFetcher,
    private val metadataProvider: IUserMetadataProvider,
    private val scope: CoroutineScope,
    pollingConfig: ChessPollingConfig = ChessPollingDefaults.android,
) {
    val state = ChessLobbyState(userPubkey, scope)

    // Track when games were last loaded to prevent duplicate fetches
    // (e.g., discoverUserGames loads a game, then polling immediately re-fetches it)
    private val recentlyLoadedGames = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // Dedup incoming events (same event delivered by multiple relays)
    // Bounded LRU: evict oldest when exceeding capacity
    private val seenEventIds = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
    private val seenEventIdsMax = 500

    private val pollingDelegate =
        ChessPollingDelegate(
            config = pollingConfig,
            scope = scope,
            onRefreshGames = { gameIds -> refreshGames(gameIds) },
            onRefreshChallenges = { refreshChallenges() },
            onCleanup = { cleanupExpiredChallenges() },
        )

    // ========================================
    // Lifecycle
    // ========================================

    fun startPolling() {
        pollingDelegate.start()
    }

    fun stopPolling() {
        pollingDelegate.stop()
    }

    /**
     * Ensure a game ID is being polled for updates.
     * Call this when entering a game screen to guarantee polling is active for that game.
     */
    fun ensureGamePolling(gameId: String) {
        pollingDelegate.addGameId(gameId)
    }

    /**
     * Set focused game mode - only poll this specific game.
     * Call this when entering a game screen to avoid refreshing unrelated games.
     * Also ensures the game is in the polling set.
     */
    fun setFocusedGame(gameId: String) {
        pollingDelegate.addGameId(gameId)
        pollingDelegate.setFocusedGame(gameId)
    }

    /**
     * Clear focused game mode - return to lobby mode (poll all games).
     * Call this when returning to the lobby screen.
     */
    fun clearFocusedGame() {
        pollingDelegate.setFocusedGame(null)
    }

    fun forceRefresh() {
        pollingDelegate.refreshNow()
    }

    // ========================================
    // Incoming event routing (real-time / optimistic)
    // ========================================

    /**
     * Route an incoming Jester event to the appropriate handler.
     * Called by platform subscription callbacks for real-time updates.
     */
    fun handleIncomingEvent(event: JesterEvent) {
        // Skip non-chess events (kind 30 but not start or move)
        if (!event.isStartEvent() && !event.isMoveEvent()) return

        // Dedup: skip if we already processed this event ID (multiple relays deliver same event)
        synchronized(seenEventIds) {
            if (!seenEventIds.add(event.id)) return
            if (seenEventIds.size > seenEventIdsMax) {
                seenEventIds.iterator().let {
                    it.next()
                    it.remove()
                }
            }
        }

        Log.d("chessdebug", "[Lobby] handleIncomingEvent: id=${event.id.take(8)}, pubkey=${event.pubKey.take(8)}, isStart=${event.isStartEvent()}, isMove=${event.isMoveEvent()}, createdAt=${event.createdAt}")
        when {
            event.isStartEvent() -> handleStartEvent(event)
            event.isMoveEvent() -> handleMoveEvent(event)
        }
    }

    private fun handleStartEvent(event: JesterEvent) {
        val startEventId = event.id
        val challengerColor = event.playerColor() ?: Color.WHITE

        Log.d("chessdebug", "[Lobby] handleStartEvent: game=${startEventId.take(8)}, challenger=${event.pubKey.take(8)}, color=$challengerColor, opponent=${event.opponentPubkey()?.take(8)}")

        val challenge =
            ChessChallenge(
                eventId = event.id,
                gameId = startEventId, // In Jester, gameId = startEventId
                challengerPubkey = event.pubKey,
                challengerDisplayName = metadataProvider.getDisplayName(event.pubKey),
                challengerAvatarUrl = metadataProvider.getPictureUrl(event.pubKey),
                opponentPubkey = event.opponentPubkey(),
                challengerColor = challengerColor,
                createdAt = event.createdAt,
            )
        state.addChallenge(challenge)
    }

    private fun handleMoveEvent(event: JesterEvent) {
        val startEventId =
            event.startEventId() ?: run {
                Log.d("chessdebug", "[Lobby] handleMoveEvent: REJECTED - no startEventId for event ${event.id.take(8)}")
                return
            }
        val san =
            event.move() ?: run {
                Log.d("chessdebug", "[Lobby] handleMoveEvent: REJECTED - no move in event ${event.id.take(8)}")
                return
            }
        val fen =
            event.fen() ?: run {
                Log.d("chessdebug", "[Lobby] handleMoveEvent: REJECTED - no FEN in event ${event.id.take(8)}")
                return
            }
        val history = event.history()
        val moveNumber = history.size

        Log.d("chessdebug", "[Lobby] handleMoveEvent: game=${startEventId.take(8)}, move=$san, moveNumber=$moveNumber, from=${event.pubKey.take(8)}, result=${event.result()}")

        // Check if this is our game (we're either the author or tagged as opponent)
        val opponentFromTag = event.opponentPubkey()
        val isOurGame = event.pubKey == userPubkey || opponentFromTag == userPubkey

        var gameState = state.getGameState(startEventId)

        // If this is our game but we haven't loaded it yet, load it now
        // This happens when someone accepts our challenge (makes first move)
        if (gameState == null && isOurGame) {
            Log.d("chessdebug", "[Lobby] handleMoveEvent: game ${startEventId.take(8)} not loaded but is ours - calling handleGameAccepted")
            handleGameAccepted(startEventId)
            return // handleGameAccepted will load the game and poll for events
        }

        if (gameState == null) {
            Log.d("chessdebug", "[Lobby] handleMoveEvent: game ${startEventId.take(8)} not loaded and not ours - ignoring")
            return
        }

        // Check for game end
        val result = event.result()
        if (result != null) {
            val gameResult =
                when (result) {
                    "1-0" -> GameResult.WHITE_WINS
                    "0-1" -> GameResult.BLACK_WINS
                    "1/2-1/2" -> GameResult.DRAW
                    else -> null
                }
            if (gameResult != null) {
                Log.d("chessdebug", "[Lobby] handleMoveEvent: game ${startEventId.take(8)} ENDED: $result, termination=${event.termination()}")
                gameState.markAsFinished(gameResult)
                state.moveToCompleted(startEventId, result, event.termination())
                pollingDelegate.removeGameId(startEventId)
                return
            }
        }

        // Only apply opponent moves optimistically
        if (event.pubKey != userPubkey) {
            Log.d("chessdebug", "[Lobby] handleMoveEvent: applying opponent move $san (move #$moveNumber) to game ${startEventId.take(8)}")
            gameState.applyOpponentMove(san, fen, moveNumber)
            // Update head event ID for move linking
            gameState.updateHeadEventId(event.id)
        } else {
            Log.d("chessdebug", "[Lobby] handleMoveEvent: skipping own move $san for game ${startEventId.take(8)}")
        }
    }

    // ========================================
    // Challenge operations
    // ========================================

    fun createChallenge(
        opponentPubkey: String? = null,
        playerColor: Color = Color.WHITE,
        timeControl: String? = null, // Not supported in Jester, kept for API compatibility
    ) {
        Log.d("chessdebug", "[Lobby] createChallenge: opponent=${opponentPubkey?.take(8)}, color=$playerColor")
        scope.launch(Dispatchers.Default) {
            state.setBroadcastStatus(
                ChessBroadcastStatus.Broadcasting(
                    san = "Challenge",
                    successCount = 0,
                    totalRelays = publisher.getWriteRelayCount(),
                ),
            )

            val startEventId = retryWithBackoffResult { publisher.publishStart(playerColor, opponentPubkey) }

            if (startEventId != null) {
                Log.d("chessdebug", "[Lobby] createChallenge SUCCESS: startEventId=${startEventId.take(8)}")
                // Add challenge to local state - shows in "Your Challenges" section
                val challenge =
                    ChessChallenge(
                        eventId = startEventId,
                        gameId = startEventId,
                        challengerPubkey = userPubkey,
                        challengerDisplayName = metadataProvider.getDisplayName(userPubkey),
                        challengerAvatarUrl = metadataProvider.getPictureUrl(userPubkey),
                        opponentPubkey = opponentPubkey,
                        challengerColor = playerColor,
                        createdAt = TimeUtils.now(),
                    )
                state.addChallenge(challenge)
                pollingDelegate.addGameId(startEventId)

                state.setBroadcastStatus(
                    ChessBroadcastStatus.Success("Challenge", publisher.getWriteRelayCount()),
                )
                delay(2000)
                state.setBroadcastStatus(ChessBroadcastStatus.Idle)
                state.setError(null)
            } else {
                Log.d("chessdebug", "[Lobby] createChallenge FAILED: publish returned null")
                state.setBroadcastStatus(
                    ChessBroadcastStatus.Failed("Challenge", "Failed to publish"),
                )
                state.setError("Failed to create challenge")
            }
        }
    }

    /**
     * Accept a challenge by loading the game and making the first move (if we're black)
     * or waiting for opponent's move (if we're white).
     *
     * In Jester protocol, acceptance is implicit - we just track the game locally.
     */
    fun acceptChallenge(challenge: ChessChallenge) {
        Log.d("chessdebug", "[Lobby] acceptChallenge: game=${challenge.gameId.take(8)}, challenger=${challenge.challengerPubkey.take(8)}, color=${challenge.challengerColor.opposite()}")
        // Mark as accepted SYNCHRONOUSLY before launching coroutine
        // This prevents race where navigation happens before coroutine runs,
        // which would cause loadGame() to incorrectly mark as spectator
        state.markAsAccepted(challenge.gameId)

        state.removeChallenge(challenge.gameId)

        // Add to polling delegate FIRST - before adding to activeGames
        // This prevents race where Compose sees the game but forceRefresh() doesn't include it
        pollingDelegate.addGameId(challenge.gameId)

        scope.launch(Dispatchers.Default) {
            val playerColor = challenge.challengerColor.opposite()
            val gameState =
                ChessGameLoader.createNewGame(
                    startEventId = challenge.gameId, // gameId = startEventId in Jester
                    playerPubkey = userPubkey,
                    opponentPubkey = challenge.challengerPubkey,
                    playerColor = playerColor,
                )
            state.addActiveGame(challenge.gameId, gameState)
            state.selectGame(challenge.gameId)
            state.setError(null)

            // Immediately fetch game from relays to load any existing moves
            // This ensures moves are loaded without waiting for polling interval
            refreshGame(challenge.gameId)
        }
    }

    /**
     * Open user's own outgoing challenge to view the board and make moves.
     * Creates game state and navigates to game view.
     */
    fun openOwnChallenge(challenge: ChessChallenge) {
        // Add to polling delegate FIRST - before adding to activeGames
        pollingDelegate.addGameId(challenge.gameId)

        // Create game state with user's chosen color
        val gameState =
            ChessGameLoader.createNewGame(
                startEventId = challenge.gameId,
                playerPubkey = userPubkey,
                opponentPubkey = challenge.opponentPubkey ?: "",
                playerColor = challenge.challengerColor,
                isPendingChallenge = true,
            )

        state.addActiveGame(challenge.gameId, gameState)
        state.selectGame(challenge.gameId)

        // Fetch from relays in case opponent has already made moves
        scope.launch(Dispatchers.Default) {
            refreshGame(challenge.gameId)
        }
    }

    /**
     * When we detect our challenge was accepted (opponent made first move), load game from relays.
     */
    fun handleGameAccepted(startEventId: String) {
        // Skip if already loaded or in-flight (multiple move events from same game trigger this)
        val lastLoaded = recentlyLoadedGames[startEventId]
        if (lastLoaded != null && (TimeUtils.now() - lastLoaded) < 10) {
            Log.d("chessdebug", "[Lobby] handleGameAccepted: SKIPPED game ${startEventId.take(8)} - loaded ${TimeUtils.now() - lastLoaded}s ago")
            return
        }
        // Mark immediately to prevent concurrent launches
        recentlyLoadedGames[startEventId] = TimeUtils.now()

        Log.d("chessdebug", "[Lobby] handleGameAccepted: game ${startEventId.take(8)} - fetching from relays")
        scope.launch(Dispatchers.Default) {
            state.setBroadcastStatus(ChessBroadcastStatus.Syncing(0f))

            val events = fetcher.fetchGameEvents(startEventId)
            Log.d("chessdebug", "[Lobby] handleGameAccepted: fetched ${events.moves.size} moves for game ${startEventId.take(8)}")
            val result = ChessGameLoader.loadGame(events, userPubkey)

            when (result) {
                is LoadGameResult.Success -> {
                    recentlyLoadedGames[startEventId] = TimeUtils.now()
                    Log.d("chessdebug", "[Lobby] handleGameAccepted SUCCESS: game ${startEventId.take(8)}, role=${result.reconstructedState.viewerRole}")
                    state.addActiveGame(startEventId, result.liveState)
                    pollingDelegate.addGameId(startEventId)
                    state.setBroadcastStatus(ChessBroadcastStatus.Idle)
                    state.setError(null)
                }

                is LoadGameResult.Error -> {
                    Log.d("chessdebug", "[Lobby] handleGameAccepted FAILED: game ${startEventId.take(8)}, error=${result.message}")
                    state.setError("Failed to load game: ${result.message}")
                    state.setBroadcastStatus(ChessBroadcastStatus.Idle)
                }
            }
        }
    }

    // ========================================
    // Game operations
    // ========================================

    fun publishMove(
        startEventId: String,
        from: String,
        to: String,
    ) {
        Log.d("chessdebug", "[Lobby] publishMove: game=${startEventId.take(8)}, from=$from, to=$to")
        val gameState =
            state.getGameState(startEventId) ?: run {
                Log.d("chessdebug", "[Lobby] publishMove: REJECTED - no game state for ${startEventId.take(8)}")
                return
            }

        if (state.isSpectating(startEventId)) {
            Log.d("chessdebug", "[Lobby] publishMove: REJECTED - spectating game ${startEventId.take(8)}")
            state.setError("Cannot move while spectating")
            return
        }

        // Parse promotion suffix from `to` if present (e.g., "e8q" -> "e8" + QUEEN)
        val (actualTo, promotion) = parsePromotionFromTo(to)

        val moveResult =
            gameState.makeMove(from, actualTo, promotion) ?: run {
                Log.d("chessdebug", "[Lobby] publishMove: REJECTED - makeMove returned null for $from->$actualTo in game ${startEventId.take(8)}")
                return
            }

        Log.d("chessdebug", "[Lobby] publishMove: move validated: san=${moveResult.san}, fen=${moveResult.fen.take(30)}, history=${moveResult.history.size} moves, headEvent=${moveResult.headEventId.take(8)}")

        scope.launch(Dispatchers.Default) {
            state.setBroadcastStatus(
                ChessBroadcastStatus.Broadcasting(
                    san = moveResult.san,
                    successCount = 0,
                    totalRelays = publisher.getWriteRelayCount(),
                ),
            )

            val newEventId = retryWithBackoffResult { publisher.publishMove(moveResult) }

            if (newEventId != null) {
                Log.d("chessdebug", "[Lobby] publishMove SUCCESS: newEventId=${newEventId.take(8)} for game ${startEventId.take(8)}")
                // Update head event ID for next move linking
                gameState.updateHeadEventId(newEventId)

                state.setBroadcastStatus(
                    ChessBroadcastStatus.Success(moveResult.san, publisher.getWriteRelayCount()),
                )
                delay(3000)

                val currentState = state.getGameState(startEventId)
                state.setBroadcastStatus(
                    if (currentState?.isPlayerTurn() == false) {
                        ChessBroadcastStatus.WaitingForOpponent
                    } else {
                        ChessBroadcastStatus.Idle
                    },
                )
                state.setError(null)
            } else {
                Log.d("chessdebug", "[Lobby] publishMove FAILED: reverting move ${moveResult.san} for game ${startEventId.take(8)}")
                // Revert the move since publishing failed
                gameState.undoLastMove()

                state.setBroadcastStatus(
                    ChessBroadcastStatus.Failed(moveResult.san, "Failed to publish move"),
                )
                state.setError("Failed to publish move - move reverted")
            }
        }
    }

    fun resign(startEventId: String) {
        Log.d("chessdebug", "[Lobby] resign: game=${startEventId.take(8)}")
        val gameState = state.getGameState(startEventId) ?: return

        if (state.isSpectating(startEventId)) {
            state.setError("Cannot resign while spectating")
            return
        }

        scope.launch(Dispatchers.Default) {
            val endData = gameState.resign()
            val success = retryWithBackoff { publisher.publishGameEnd(endData) }

            if (success) {
                state.moveToCompleted(startEventId, endData.result.notation, endData.termination.name.lowercase())
                pollingDelegate.removeGameId(startEventId)
                state.setError(null)
            } else {
                state.setError("Failed to resign")
            }
        }
    }

    fun claimAbandonmentVictory(startEventId: String) {
        val gameState = state.getGameState(startEventId) ?: return
        val endData = gameState.claimAbandonmentVictory() ?: return

        scope.launch(Dispatchers.Default) {
            val success = retryWithBackoff { publisher.publishGameEnd(endData) }

            if (success) {
                state.moveToCompleted(startEventId, endData.result.notation, "abandonment")
                pollingDelegate.removeGameId(startEventId)
                state.setError(null)
            } else {
                state.setError("Failed to claim abandonment victory")
            }
        }
    }

    // ========================================
    // Spectator mode
    // ========================================

    fun stopSpectating(gameId: String) {
        state.removeSpectatingGame(gameId)
        pollingDelegate.removeGameId(gameId)
    }

    fun loadGameAsSpectator(startEventId: String) {
        Log.d("chessdebug", "[Lobby] loadGameAsSpectator: game=${startEventId.take(8)}")
        scope.launch(Dispatchers.Default) {
            state.setBroadcastStatus(ChessBroadcastStatus.Syncing(0f))

            val events = fetcher.fetchGameEvents(startEventId)
            val result = ChessGameLoader.loadGame(events, userPubkey)

            when (result) {
                is LoadGameResult.Success -> {
                    recentlyLoadedGames[startEventId] = TimeUtils.now()
                    state.addSpectatingGame(startEventId, result.liveState)
                    pollingDelegate.addGameId(startEventId)
                    state.setBroadcastStatus(ChessBroadcastStatus.Idle)
                    state.setError(null)
                    // Auto-select the game for Desktop (Android uses route navigation)
                    state.selectGame(startEventId)
                }

                is LoadGameResult.Error -> {
                    state.setError("Failed to load game: ${result.message}")
                    state.setBroadcastStatus(ChessBroadcastStatus.Idle)
                }
            }
        }
    }

    fun loadGame(startEventId: String) {
        Log.d("chessdebug", "[Lobby] loadGame: game=${startEventId.take(8)}")
        scope.launch(Dispatchers.Default) {
            // Don't load if game already exists or was accepted (acceptChallenge will handle it)
            if (state.getGameState(startEventId) != null || state.wasAccepted(startEventId)) {
                Log.d("chessdebug", "[Lobby] loadGame: skipping - already exists or was accepted for ${startEventId.take(8)}")
                return@launch
            }

            state.setBroadcastStatus(ChessBroadcastStatus.Syncing(0f))

            val events = fetcher.fetchGameEvents(startEventId)
            val result = ChessGameLoader.loadGame(events, userPubkey)

            when (result) {
                is LoadGameResult.Success -> {
                    recentlyLoadedGames[startEventId] = TimeUtils.now()
                    if (result.liveState.isSpectator) {
                        state.addSpectatingGame(startEventId, result.liveState)
                    } else {
                        state.addActiveGame(startEventId, result.liveState)
                    }
                    pollingDelegate.addGameId(startEventId)
                    state.setBroadcastStatus(ChessBroadcastStatus.Idle)
                    state.setError(null)
                }

                is LoadGameResult.Error -> {
                    // Check again if game was added while we were fetching
                    // (e.g., by acceptChallenge completing in parallel)
                    if (state.getGameState(startEventId) != null) {
                        state.setBroadcastStatus(ChessBroadcastStatus.Idle)
                        return@launch
                    }
                    state.setError("Failed to load game: ${result.message}")
                    state.setBroadcastStatus(ChessBroadcastStatus.Idle)
                }
            }
        }
    }

    // ========================================
    // Relay-first refresh (periodic reconstruction)
    // ========================================

    private suspend fun refreshGames(gameIds: Set<String>) {
        for (gameId in gameIds) {
            refreshGame(gameId)
        }
    }

    private suspend fun refreshGame(startEventId: String) {
        // Skip if this game was just loaded (prevents duplicate fetch after discoverUserGames)
        val lastLoaded = recentlyLoadedGames[startEventId]
        if (lastLoaded != null && (TimeUtils.now() - lastLoaded) < 10) {
            Log.d("chessdebug", "[Lobby] refreshGame: SKIPPED game ${startEventId.take(8)} - loaded ${TimeUtils.now() - lastLoaded}s ago")
            return
        }

        Log.d("chessdebug", "[Lobby] refreshGame: fetching game ${startEventId.take(8)} from relays")
        val events = fetcher.fetchGameEvents(startEventId)

        val result = ChessGameLoader.loadGame(events, userPubkey)

        when (result) {
            is LoadGameResult.Success -> {
                // Check status FIRST to avoid briefly emitting a finished game through activeGames
                val gameStatus = result.liveState.gameStatus.value
                if (gameStatus is GameStatus.Finished) {
                    Log.d("chessdebug", "[Lobby] refreshGame: game ${startEventId.take(8)} is FINISHED (${(gameStatus as GameStatus.Finished).result}), moving to completed")
                    state.moveToCompleted(startEventId, gameStatus.result.notation, null)
                    pollingDelegate.removeGameId(startEventId)
                } else {
                    Log.d("chessdebug", "[Lobby] refreshGame: game ${startEventId.take(8)} updated, moves=${result.liveState.moveHistory.value.size}, isPlayerTurn=${result.liveState.isPlayerTurn()}")
                    state.replaceGameState(startEventId, result.liveState)
                }
            }

            is LoadGameResult.Error -> {
                Log.d("chessdebug", "[Lobby] refreshGame: ERROR for game ${startEventId.take(8)}: ${result.message}")
                // Don't overwrite error for periodic refresh failures
            }
        }
    }

    private suspend fun refreshChallenges() {
        state.setRefreshing(true)
        try {
            refreshChallengesInternal()
        } finally {
            state.setRefreshing(false)
        }
    }

    private suspend fun refreshChallengesInternal() {
        val relayUrls = fetcher.getRelayUrls()
        val relayStatesMap = mutableMapOf<String, RelaySyncState>()
        relayUrls.forEach { url ->
            val displayName = url.substringAfter("://").substringBefore("/")
            relayStatesMap[url] = RelaySyncState(url, displayName, RelaySyncStatus.CONNECTING, 0)
        }
        var totalEvents = 0

        state.setSyncStatus(
            ChessSyncStatus.Syncing(
                phase = "challenges",
                relayStates = relayStatesMap.values.toList(),
                totalEventsReceived = 0,
            ),
        )

        val startEvents =
            fetcher.fetchChallenges { progress ->
                val relayUrl = progress.relay.url
                val displayName = relayUrl.substringAfter("://").substringBefore("/")
                val status =
                    when (progress.status) {
                        RelayFetchStatus.WAITING -> RelaySyncStatus.WAITING
                        RelayFetchStatus.RECEIVING -> RelaySyncStatus.RECEIVING
                        RelayFetchStatus.EOSE_RECEIVED -> RelaySyncStatus.EOSE_RECEIVED
                        RelayFetchStatus.TIMEOUT -> RelaySyncStatus.FAILED
                    }
                relayStatesMap[relayUrl] =
                    RelaySyncState(
                        relayUrl,
                        displayName,
                        status,
                        progress.eventCount,
                    )
                totalEvents = relayStatesMap.values.sumOf { it.eventsReceived }
                state.setSyncStatus(
                    ChessSyncStatus.Syncing(
                        phase = "challenges",
                        relayStates = relayStatesMap.values.toList(),
                        totalEventsReceived = totalEvents,
                    ),
                )
            }

        val fetchedChallenges =
            startEvents.mapNotNull { event ->
                if (!event.isStartEvent()) return@mapNotNull null
                val startEventId = event.id

                // Skip challenges that user has already accepted
                if (state.wasAccepted(startEventId)) return@mapNotNull null

                val challengerColor = event.playerColor() ?: Color.WHITE

                ChessChallenge(
                    eventId = event.id,
                    gameId = startEventId,
                    challengerPubkey = event.pubKey,
                    challengerDisplayName = metadataProvider.getDisplayName(event.pubKey),
                    challengerAvatarUrl = metadataProvider.getPictureUrl(event.pubKey),
                    opponentPubkey = event.opponentPubkey(),
                    challengerColor = challengerColor,
                    createdAt = event.createdAt,
                )
            }

        // Merge only RECENT optimistic challenges (created in last 5 minutes, not yet propagated)
        // This prevents stale challenges from accumulating across sessions
        // Also exclude challenges that user has accepted (tracked in _acceptedGameIds)
        val now = TimeUtils.now()
        val recentThreshold = 5 * 60L // 5 minutes
        val fetchedGameIds = fetchedChallenges.map { it.gameId }.toSet()
        val optimisticChallenges =
            state.challenges.value.filter { challenge ->
                val isRecent = (now - challenge.createdAt) < recentThreshold
                val notFetched = challenge.gameId !in fetchedGameIds
                val notAccepted = !state.wasAccepted(challenge.gameId)
                isRecent && notFetched && notAccepted
            }
        val mergedChallenges = fetchedChallenges + optimisticChallenges

        state.updateChallenges(mergedChallenges)

        state.setSyncStatus(
            ChessSyncStatus.Syncing(
                phase = "games",
                relayStates = relayStatesMap.values.toList(),
                totalEventsReceived = totalEvents,
            ),
        )

        discoverUserGames()

        val recentGames = fetcher.fetchRecentGames()
        val publicGames =
            recentGames.map { summary ->
                PublicGame(
                    gameId = summary.startEventId,
                    whitePubkey = summary.whitePubkey,
                    whiteDisplayName = metadataProvider.getDisplayName(summary.whitePubkey),
                    blackPubkey = summary.blackPubkey,
                    blackDisplayName = metadataProvider.getDisplayName(summary.blackPubkey),
                    moveCount = summary.moveCount,
                    lastMoveTime = summary.lastMoveTime,
                    isActive = summary.isActive,
                )
            }
        state.updatePublicGames(publicGames)

        val failedCount = relayStatesMap.values.count { it.status == RelaySyncStatus.FAILED }
        val activeGamesCount = state.activeGames.value.size

        if (failedCount > 0 && failedCount < relayStatesMap.size) {
            state.setSyncStatus(
                ChessSyncStatus.PartialSync(
                    relayStates = relayStatesMap.values.toList(),
                    message = "$failedCount relay(s) timed out",
                ),
            )
        } else if (failedCount == relayStatesMap.size) {
            state.setSyncStatus(
                ChessSyncStatus.PartialSync(
                    relayStates = relayStatesMap.values.toList(),
                    message = "All relays failed",
                ),
            )
        } else {
            state.setSyncStatus(
                ChessSyncStatus.Synced(
                    relayStates = relayStatesMap.values.toList(),
                    challengeCount = mergedChallenges.size,
                    gameCount = activeGamesCount,
                    totalEventsReceived = totalEvents,
                ),
            )
        }
    }

    private suspend fun discoverUserGames() {
        val discoveredGameIds = fetcher.fetchUserGameIds()
        val currentActiveIds = state.activeGames.value.keys
        val currentSpectatingIds = state.spectatingGames.value.keys

        val newGameIds = discoveredGameIds - currentActiveIds - currentSpectatingIds

        // Also check completed games to avoid re-adding them
        val completedGameIds =
            state.completedGames.value
                .map { it.gameId }
                .toSet()

        for (startEventId in newGameIds) {
            if (startEventId in completedGameIds) continue

            val events = fetcher.fetchGameEvents(startEventId)
            val result = ChessGameLoader.loadGame(events, userPubkey)

            when (result) {
                is LoadGameResult.Success -> {
                    recentlyLoadedGames[startEventId] = TimeUtils.now()
                    // If the discovered game is already finished, send it straight to completed
                    val gameStatus = result.liveState.gameStatus.value
                    if (gameStatus is GameStatus.Finished) {
                        state.moveToCompleted(startEventId, gameStatus.result.notation, null, liveState = result.liveState)
                    } else if (!result.liveState.isSpectator) {
                        state.addActiveGame(startEventId, result.liveState)
                        pollingDelegate.addGameId(startEventId)
                    } else {
                        state.addSpectatingGame(startEventId, result.liveState)
                        pollingDelegate.addGameId(startEventId)
                    }
                }

                is LoadGameResult.Error -> {
                    // Failed to load game - continue with others
                }
            }
        }
    }

    private fun cleanupExpiredChallenges() {
        val now = TimeUtils.now()
        val validChallenges =
            state.challenges.value.filter { challenge ->
                (now - challenge.createdAt) < CHALLENGE_EXPIRY_SECONDS
            }
        state.updateChallenges(validChallenges)
    }

    // ========================================
    // Utilities
    // ========================================

    private suspend fun retryWithBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        action: suspend () -> Boolean,
    ): Boolean {
        var delayMs = initialDelayMs
        repeat(maxRetries) { attempt ->
            if (action()) return true
            if (attempt < maxRetries - 1) {
                delay(delayMs)
                delayMs *= 2
            }
        }
        return false
    }

    private suspend fun <T> retryWithBackoffResult(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        action: suspend () -> T?,
    ): T? {
        var delayMs = initialDelayMs
        repeat(maxRetries) { attempt ->
            val result = action()
            if (result != null) return result
            if (attempt < maxRetries - 1) {
                delay(delayMs)
                delayMs *= 2
            }
        }
        return null
    }

    /**
     * Dismiss a finished game from the active/spectating list and move it to completed.
     * Called when the user clicks "Continue" on the game end overlay, or automatically
     * when a finished game is detected during polling refresh.
     */
    fun dismissGame(gameId: String) {
        val gameState = state.getGameState(gameId) ?: return
        val status = gameState.gameStatus.value
        if (status is GameStatus.Finished) {
            state.moveToCompleted(gameId, status.result.notation, null)
            pollingDelegate.removeGameId(gameId)
        }
    }

    /**
     * Remove a game from all lobby lists (active, spectating) and stop polling.
     * Used when a game fails to load ("Game not found") so the user can dismiss the stale entry.
     */
    fun removeGame(gameId: String) {
        state.removeActiveGame(gameId)
        state.removeSpectatingGame(gameId)
        pollingDelegate.removeGameId(gameId)
    }

    fun clearError() {
        state.setError(null)
    }

    fun selectGame(gameId: String?) {
        state.selectGame(gameId)
    }
}

/**
 * Parse promotion piece from a "to" square string.
 * For example: "e8q" -> Pair("e8", PieceType.QUEEN)
 * Regular moves: "e4" -> Pair("e4", null)
 */
private fun parsePromotionFromTo(to: String): Pair<String, PieceType?> {
    if (to.length == 3) {
        val square = to.take(2)
        val promotion =
            when (to.last().lowercaseChar()) {
                'q' -> PieceType.QUEEN
                'r' -> PieceType.ROOK
                'b' -> PieceType.BISHOP
                'n' -> PieceType.KNIGHT
                else -> null
            }
        if (promotion != null) {
            return square to promotion
        }
    }
    return to to null
}
