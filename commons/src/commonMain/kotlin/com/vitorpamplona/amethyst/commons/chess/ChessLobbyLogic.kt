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
package com.vitorpamplona.amethyst.commons.chess

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip64Chess.ChessGameEnd
import com.vitorpamplona.quartz.nip64Chess.ChessGameEvents
import com.vitorpamplona.quartz.nip64Chess.ChessMoveEvent
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.LiveChessDrawOfferEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessMoveEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Interface for platform-specific chess event publishing
 */
interface ChessEventPublisher {
    suspend fun publishChallenge(
        gameId: String,
        playerColor: Color,
        opponentPubkey: String?,
        timeControl: String?,
    ): Boolean

    suspend fun publishAccept(
        gameId: String,
        challengeEventId: String,
        challengerPubkey: String,
    ): Boolean

    suspend fun publishMove(move: ChessMoveEvent): Boolean

    suspend fun publishGameEnd(gameEnd: ChessGameEnd): Boolean

    suspend fun publishDrawOffer(
        gameId: String,
        opponentPubkey: String,
        message: String?,
    ): Boolean

    fun getWriteRelayCount(): Int
}

/**
 * Relay-first fetcher interface. Platforms provide one-shot relay queries.
 *
 * Every fetch does: one-shot REQ → collect events → EOSE → close.
 * No caching — relays are the single source of truth.
 */
interface ChessRelayFetcher {
    /** Fetch all events for a specific game */
    suspend fun fetchGameEvents(gameId: String): ChessGameEvents

    /** Fetch recent challenge events */
    suspend fun fetchChallenges(): List<LiveChessGameChallengeEvent>

    /** Fetch recent public game summaries for spectating */
    suspend fun fetchRecentGames(): List<RelayGameSummary>
}

/**
 * Summary of a game found on relays (for lobby display / spectating)
 */
data class RelayGameSummary(
    val gameId: String,
    val whitePubkey: String,
    val blackPubkey: String,
    val moveCount: Int,
    val lastMoveTime: Long,
    val isActive: Boolean,
)

/**
 * Shared chess lobby logic — relay-first architecture.
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

    fun startPolling() = pollingDelegate.start()

    fun stopPolling() = pollingDelegate.stop()

    fun forceRefresh() = pollingDelegate.refreshNow()

    // ========================================
    // Incoming event routing (real-time / optimistic)
    // ========================================

    /**
     * Route an incoming relay event to the appropriate handler.
     * Called by platform subscription callbacks for real-time updates.
     */
    fun handleIncomingEvent(event: Event) {
        when (event) {
            is LiveChessGameChallengeEvent -> handleChallenge(event)
            is LiveChessGameAcceptEvent -> handleAccept(event)
            is LiveChessMoveEvent -> handleMove(event)
            is LiveChessGameEndEvent -> handleGameEnd(event)
            is LiveChessDrawOfferEvent -> handleDrawOffer(event)
        }
    }

    private fun handleChallenge(event: LiveChessGameChallengeEvent) {
        val gameId = event.gameId() ?: return
        val challengerColor = event.playerColor() ?: Color.WHITE

        val challenge =
            ChessChallenge(
                eventId = event.id,
                gameId = gameId,
                challengerPubkey = event.pubKey,
                challengerDisplayName = metadataProvider.getDisplayName(event.pubKey),
                challengerAvatarUrl = metadataProvider.getPictureUrl(event.pubKey),
                opponentPubkey = event.opponentPubkey(),
                challengerColor = challengerColor,
                createdAt = event.createdAt,
            )
        state.addChallenge(challenge)
    }

    private fun handleAccept(event: LiveChessGameAcceptEvent) {
        val gameId = event.gameId() ?: return

        // If this is an accept for our challenge, auto-load the game
        val ourChallenge = state.outgoingChallenges().find { it.gameId == gameId }
        if (ourChallenge != null) {
            handleGameAccepted(gameId)
        }
    }

    private fun handleMove(event: LiveChessMoveEvent) {
        val gameId = event.gameId() ?: return
        val san = event.san() ?: return
        val fen = event.fen() ?: return
        val moveNumber = event.moveNumber()

        val gameState = state.getGameState(gameId) ?: return

        // Only apply opponent moves optimistically
        if (event.pubKey != userPubkey) {
            gameState.applyOpponentMove(san, fen, moveNumber)
        }
    }

    private fun handleGameEnd(event: LiveChessGameEndEvent) {
        val gameId = event.gameId() ?: return
        val gameState = state.getGameState(gameId) ?: return

        val resultStr = event.result()
        val result =
            when (resultStr) {
                "1-0" -> com.vitorpamplona.quartz.nip64Chess.GameResult.WHITE_WINS
                "0-1" -> com.vitorpamplona.quartz.nip64Chess.GameResult.BLACK_WINS
                "1/2-1/2" -> com.vitorpamplona.quartz.nip64Chess.GameResult.DRAW
                else -> return
            }

        gameState.markAsFinished(result)
        state.moveToCompleted(gameId, result.notation, event.termination())
        pollingDelegate.removeGameId(gameId)
    }

    private fun handleDrawOffer(event: LiveChessDrawOfferEvent) {
        val gameId = event.gameId() ?: return
        val gameState = state.getGameState(gameId) ?: return

        if (event.pubKey != userPubkey) {
            gameState.receiveDrawOffer(event.pubKey)
        }
    }

    // ========================================
    // Challenge operations
    // ========================================

    fun createChallenge(
        opponentPubkey: String? = null,
        playerColor: Color = Color.WHITE,
        timeControl: String? = null,
    ) {
        val gameId = generateGameId()

        scope.launch(Dispatchers.Default) {
            state.setBroadcastStatus(
                ChessBroadcastStatus.Broadcasting(
                    san = "Challenge",
                    successCount = 0,
                    totalRelays = publisher.getWriteRelayCount(),
                ),
            )

            val success = retryWithBackoff { publisher.publishChallenge(gameId, playerColor, opponentPubkey, timeControl) }

            if (success) {
                state.setBroadcastStatus(
                    ChessBroadcastStatus.Success("Challenge", publisher.getWriteRelayCount()),
                )
                delay(2000)
                state.setBroadcastStatus(ChessBroadcastStatus.Idle)
                state.setError(null)
            } else {
                state.setBroadcastStatus(
                    ChessBroadcastStatus.Failed("Challenge", "Failed to publish"),
                )
                state.setError("Failed to create challenge")
            }
        }
    }

    fun acceptChallenge(challenge: ChessChallenge) {
        scope.launch(Dispatchers.Default) {
            val success =
                retryWithBackoff {
                    publisher.publishAccept(
                        gameId = challenge.gameId,
                        challengeEventId = challenge.eventId,
                        challengerPubkey = challenge.challengerPubkey,
                    )
                }

            if (success) {
                val playerColor = challenge.challengerColor.opposite()
                val gameState =
                    ChessGameLoader.createNewGame(
                        gameId = challenge.gameId,
                        playerPubkey = userPubkey,
                        opponentPubkey = challenge.challengerPubkey,
                        playerColor = playerColor,
                    )
                state.addActiveGame(challenge.gameId, gameState)
                pollingDelegate.addGameId(challenge.gameId)
                state.selectGame(challenge.gameId)
                state.setError(null)
            } else {
                state.setError("Failed to accept challenge")
            }
        }
    }

    /**
     * When we detect our challenge was accepted, load game from relays.
     */
    fun handleGameAccepted(gameId: String) {
        scope.launch(Dispatchers.Default) {
            state.setBroadcastStatus(ChessBroadcastStatus.Syncing(0f))

            val events = fetcher.fetchGameEvents(gameId)
            val result = ChessGameLoader.loadGame(events, userPubkey)

            when (result) {
                is LoadGameResult.Success -> {
                    state.addActiveGame(gameId, result.liveState)
                    pollingDelegate.addGameId(gameId)
                    state.selectGame(gameId)
                    state.setBroadcastStatus(ChessBroadcastStatus.Idle)
                    state.setError(null)
                }
                is LoadGameResult.Error -> {
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
        gameId: String,
        from: String,
        to: String,
    ) {
        val gameState = state.getGameState(gameId) ?: return

        if (state.isSpectating(gameId)) {
            state.setError("Cannot move while spectating")
            return
        }

        val moveResult = gameState.makeMove(from, to) ?: return

        scope.launch(Dispatchers.Default) {
            state.setBroadcastStatus(
                ChessBroadcastStatus.Broadcasting(
                    san = moveResult.san,
                    successCount = 0,
                    totalRelays = publisher.getWriteRelayCount(),
                ),
            )

            val success = retryWithBackoff { publisher.publishMove(moveResult) }

            if (success) {
                state.setBroadcastStatus(
                    ChessBroadcastStatus.Success(moveResult.san, publisher.getWriteRelayCount()),
                )
                delay(3000)

                val currentState = state.getGameState(gameId)
                state.setBroadcastStatus(
                    if (currentState?.isPlayerTurn() == false) {
                        ChessBroadcastStatus.WaitingForOpponent
                    } else {
                        ChessBroadcastStatus.Idle
                    },
                )
                state.setError(null)
            } else {
                state.setBroadcastStatus(
                    ChessBroadcastStatus.Failed(moveResult.san, "Failed to publish move"),
                )
                state.setError("Failed to publish move")
            }
        }
    }

    fun resign(gameId: String) {
        val gameState = state.getGameState(gameId) ?: return

        if (state.isSpectating(gameId)) {
            state.setError("Cannot resign while spectating")
            return
        }

        scope.launch(Dispatchers.Default) {
            val endData = gameState.resign()
            val success = retryWithBackoff { publisher.publishGameEnd(endData) }

            if (success) {
                state.moveToCompleted(gameId, endData.result.notation, endData.termination.name.lowercase())
                pollingDelegate.removeGameId(gameId)
                state.setError(null)
            } else {
                state.setError("Failed to resign")
            }
        }
    }

    fun offerDraw(gameId: String) {
        val gameState = state.getGameState(gameId) ?: return

        if (state.isSpectating(gameId)) {
            state.setError("Cannot offer draw while spectating")
            return
        }

        scope.launch(Dispatchers.Default) {
            val drawOffer = gameState.offerDraw()
            val success =
                retryWithBackoff {
                    publisher.publishDrawOffer(
                        gameId = drawOffer.gameId,
                        opponentPubkey = drawOffer.opponentPubkey,
                        message = drawOffer.message,
                    )
                }

            if (success) {
                state.setError(null)
            } else {
                state.setError("Failed to offer draw")
            }
        }
    }

    fun acceptDraw(gameId: String) {
        val gameState = state.getGameState(gameId) ?: return
        val endData = gameState.acceptDraw() ?: return

        scope.launch(Dispatchers.Default) {
            val success = retryWithBackoff { publisher.publishGameEnd(endData) }

            if (success) {
                state.moveToCompleted(gameId, endData.result.notation, "draw_agreement")
                pollingDelegate.removeGameId(gameId)
                state.setError(null)
            } else {
                state.setError("Failed to accept draw")
            }
        }
    }

    fun declineDraw(gameId: String) {
        val gameState = state.getGameState(gameId) ?: return
        gameState.declineDraw()
    }

    fun claimAbandonmentVictory(gameId: String) {
        val gameState = state.getGameState(gameId) ?: return
        val endData = gameState.claimAbandonmentVictory() ?: return

        scope.launch(Dispatchers.Default) {
            val success = retryWithBackoff { publisher.publishGameEnd(endData) }

            if (success) {
                state.moveToCompleted(gameId, endData.result.notation, "abandonment")
                pollingDelegate.removeGameId(gameId)
                state.setError(null)
            } else {
                state.setError("Failed to claim abandonment victory")
            }
        }
    }

    // ========================================
    // Spectator mode
    // ========================================

    fun loadGameAsSpectator(gameId: String) {
        scope.launch(Dispatchers.Default) {
            state.setBroadcastStatus(ChessBroadcastStatus.Syncing(0f))

            val events = fetcher.fetchGameEvents(gameId)
            val result = ChessGameLoader.loadGame(events, userPubkey)

            when (result) {
                is LoadGameResult.Success -> {
                    state.addSpectatingGame(gameId, result.liveState)
                    pollingDelegate.addGameId(gameId)
                    state.selectGame(gameId)
                    state.setBroadcastStatus(ChessBroadcastStatus.Idle)
                    state.setError(null)
                }
                is LoadGameResult.Error -> {
                    state.setError("Failed to load game: ${result.message}")
                    state.setBroadcastStatus(ChessBroadcastStatus.Idle)
                }
            }
        }
    }

    fun loadGame(gameId: String) {
        scope.launch(Dispatchers.Default) {
            if (state.getGameState(gameId) != null) {
                state.selectGame(gameId)
                return@launch
            }

            state.setBroadcastStatus(ChessBroadcastStatus.Syncing(0f))

            val events = fetcher.fetchGameEvents(gameId)
            val result = ChessGameLoader.loadGame(events, userPubkey)

            when (result) {
                is LoadGameResult.Success -> {
                    if (result.liveState.isSpectator) {
                        state.addSpectatingGame(gameId, result.liveState)
                    } else {
                        state.addActiveGame(gameId, result.liveState)
                    }
                    pollingDelegate.addGameId(gameId)
                    state.selectGame(gameId)
                    state.setBroadcastStatus(ChessBroadcastStatus.Idle)
                    state.setError(null)
                }
                is LoadGameResult.Error -> {
                    state.setError("Failed to load game: ${result.message}")
                    state.setBroadcastStatus(ChessBroadcastStatus.Idle)
                }
            }
        }
    }

    // ========================================
    // Relay-first refresh (periodic reconstruction)
    // ========================================

    /**
     * Full reconstruction refresh for active games.
     * One-shot REQ → transient collector → reconstruct → diff + update.
     */
    private suspend fun refreshGames(gameIds: Set<String>) {
        for (gameId in gameIds) {
            refreshGame(gameId)
        }
    }

    private suspend fun refreshGame(gameId: String) {
        val events = fetcher.fetchGameEvents(gameId)
        val result = ChessGameLoader.loadGame(events, userPubkey)

        when (result) {
            is LoadGameResult.Success -> {
                state.replaceGameState(gameId, result.liveState)
            }
            is LoadGameResult.Error -> {
                // Don't overwrite error for periodic refresh failures
            }
        }
    }

    private suspend fun refreshChallenges() {
        val challengeEvents = fetcher.fetchChallenges()

        val challenges =
            challengeEvents.mapNotNull { event ->
                val gameId = event.gameId() ?: return@mapNotNull null
                val challengerColor = event.playerColor() ?: Color.WHITE

                ChessChallenge(
                    eventId = event.id,
                    gameId = gameId,
                    challengerPubkey = event.pubKey,
                    challengerDisplayName = metadataProvider.getDisplayName(event.pubKey),
                    challengerAvatarUrl = metadataProvider.getPictureUrl(event.pubKey),
                    opponentPubkey = event.opponentPubkey(),
                    challengerColor = challengerColor,
                    createdAt = event.createdAt,
                )
            }

        state.updateChallenges(challenges)

        // Also refresh public games
        val recentGames = fetcher.fetchRecentGames()
        val publicGames =
            recentGames.map { summary ->
                PublicGame(
                    gameId = summary.gameId,
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

    private fun generateGameId(): String {
        val timestamp = TimeUtils.now()
        val random = UUID.randomUUID().toString().take(8)
        return "chess-$timestamp-$random"
    }

    /**
     * Retry a publish operation with exponential backoff.
     * Returns true if any attempt succeeds.
     */
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

    fun clearError() {
        state.setError(null)
    }

    fun selectGame(gameId: String?) {
        state.selectGame(gameId)
    }
}
