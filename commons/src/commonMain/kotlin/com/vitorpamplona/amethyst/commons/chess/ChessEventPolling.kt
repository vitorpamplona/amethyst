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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Configuration for chess event polling
 */
data class ChessPollingConfig(
    /** Interval for polling active games (ms) */
    val activeGamePollInterval: Long = 10_000L,
    /** Interval for polling challenges (ms) */
    val challengePollInterval: Long = 30_000L,
    /** Whether to continue polling in background */
    val pollInBackground: Boolean = false,
    /** Challenge expiry time (seconds) */
    val challengeExpirySeconds: Long = 24 * 60 * 60L,
    /** Cleanup interval (ms) */
    val cleanupInterval: Long = 5 * 60 * 1000L,
)

/**
 * Platform-specific defaults
 */
object ChessPollingDefaults {
    /** Android: moderate intervals, no background polling */
    val android =
        ChessPollingConfig(
            activeGamePollInterval = 5_000L, // 5 seconds for responsive gameplay
            challengePollInterval = 15_000L, // 15 seconds for challenges
            pollInBackground = false,
        )

    /** Desktop: fast polling for responsive gameplay */
    val desktop =
        ChessPollingConfig(
            activeGamePollInterval = 2_000L, // 2 seconds for fast updates
            challengePollInterval = 10_000L, // 10 seconds for challenges
            pollInBackground = true,
        )
}

/**
 * Delegate for managing chess event polling
 *
 * Usage:
 * ```
 * class ChessViewModel(...) : ViewModel() {
 *     private val pollingDelegate = ChessPollingDelegate(
 *         config = ChessPollingDefaults.android,
 *         scope = viewModelScope,
 *         onRefreshGames = { gameIds -> refreshGamesFromCache(gameIds) },
 *         onRefreshChallenges = { refreshChallengesFromCache() },
 *     )
 *
 *     fun startPolling() = pollingDelegate.start()
 *     fun stopPolling() = pollingDelegate.stop()
 * }
 * ```
 */
class ChessPollingDelegate(
    private val config: ChessPollingConfig,
    private val scope: CoroutineScope,
    private val onRefreshGames: suspend (Set<String>) -> Unit,
    private val onRefreshChallenges: suspend () -> Unit,
    private val onCleanup: suspend () -> Unit = {},
) {
    private var gamePollingJob: Job? = null
    private var challengePollingJob: Job? = null
    private var cleanupJob: Job? = null
    private var manualRefreshJob: Job? = null

    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    internal val activeGameIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Focused game ID - when set, only this game is polled for updates.
     * Used when viewing a specific game screen to avoid refreshing unrelated games.
     * When null, all games in activeGameIdsFlow are polled (lobby mode).
     */
    private val _focusedGameId = MutableStateFlow<String?>(null)

    /**
     * Set focused game mode - only poll this specific game.
     * Call this when entering a game screen.
     * Pass null to return to lobby mode (poll all games).
     */
    fun setFocusedGame(gameId: String?) {
        _focusedGameId.value = gameId
    }

    /**
     * Get current focused game ID (null = lobby mode, polls all games)
     */
    fun getFocusedGameId(): String? = _focusedGameId.value

    /**
     * Get the effective game IDs to poll based on focused mode.
     * In focused mode: only the focused game.
     * In lobby mode: all active games.
     */
    private fun getEffectiveGameIds(): Set<String> {
        val focused = _focusedGameId.value
        return if (focused != null) {
            // Focused mode - only poll this game
            setOf(focused)
        } else {
            // Lobby mode - poll all games
            activeGameIdsFlow.value
        }
    }

    /**
     * Update the set of game IDs to poll for
     */
    fun setActiveGameIds(gameIds: Set<String>) {
        activeGameIdsFlow.value = gameIds
    }

    /**
     * Add a game ID to poll for
     */
    fun addGameId(gameId: String) {
        activeGameIdsFlow.value = activeGameIdsFlow.value + gameId
    }

    /**
     * Remove a game ID from polling
     */
    fun removeGameId(gameId: String) {
        activeGameIdsFlow.value = activeGameIdsFlow.value - gameId
    }

    /**
     * Start polling for chess events
     */
    fun start() {
        if (_isPolling.value) {
            return
        }
        _isPolling.value = true

        // Poll for active games
        gamePollingJob =
            scope.launch {
                while (isActive) {
                    val gameIds = getEffectiveGameIds()
                    if (gameIds.isNotEmpty()) {
                        try {
                            onRefreshGames(gameIds)
                        } catch (_: Exception) {
                            // Error during refresh - continue polling
                        }
                    }
                    delay(config.activeGamePollInterval)
                }
            }

        // Poll for challenges (only in lobby mode)
        challengePollingJob =
            scope.launch {
                // Initial fetch (only if not in focused mode)
                if (_focusedGameId.value == null) {
                    onRefreshChallenges()
                }

                while (isActive) {
                    delay(config.challengePollInterval)
                    // Skip challenge polling in focused mode - game screen doesn't need it
                    if (_focusedGameId.value == null) {
                        onRefreshChallenges()
                    }
                }
            }

        // Cleanup job
        cleanupJob =
            scope.launch {
                while (isActive) {
                    delay(config.cleanupInterval)
                    onCleanup()
                }
            }
    }

    /**
     * Stop polling
     */
    fun stop() {
        _isPolling.value = false
        gamePollingJob?.cancel()
        challengePollingJob?.cancel()
        cleanupJob?.cancel()
        gamePollingJob = null
        challengePollingJob = null
        cleanupJob = null
    }

    /**
     * Pause polling (e.g., when app goes to background on Android)
     */
    fun pause() {
        if (!config.pollInBackground) {
            stop()
        }
    }

    /**
     * Resume polling (e.g., when app comes to foreground on Android)
     */
    fun resume() {
        if (!_isPolling.value) {
            start()
        }
    }

    /**
     * Force an immediate refresh (debounced - ignores calls if refresh already in progress)
     */
    fun refreshNow() {
        // Debounce: skip if already refreshing
        if (_isRefreshing.value) {
            return
        }

        // Cancel any pending manual refresh
        manualRefreshJob?.cancel()

        manualRefreshJob =
            scope.launch {
                _isRefreshing.value = true
                try {
                    // In focused mode, skip challenge refresh (game screen doesn't need it)
                    val focusedId = _focusedGameId.value
                    if (focusedId == null) {
                        onRefreshChallenges()
                    }
                    val gameIds = getEffectiveGameIds()
                    if (gameIds.isNotEmpty()) {
                        onRefreshGames(gameIds)
                    }
                } finally {
                    _isRefreshing.value = false
                }
            }
    }
}

/**
 * Interface for chess event sources that can be refreshed
 */
interface ChessEventSource {
    /**
     * Fetch/refresh challenges from the event source
     */
    suspend fun fetchChallenges(): List<ChessChallengeData>

    /**
     * Fetch/refresh game state for specific game IDs
     */
    suspend fun fetchGameUpdates(gameIds: Set<String>): Map<String, ChessGameUpdate>
}

/**
 * Data class representing a chess challenge
 */
data class ChessChallengeData(
    val id: String,
    val gameId: String,
    val challengerPubkey: String,
    val opponentPubkey: String?,
    val challengerColor: com.vitorpamplona.quartz.nip64Chess.Color,
    val createdAt: Long,
    val isExpired: Boolean = false,
)

/**
 * Data class representing a game update
 */
data class ChessGameUpdate(
    val gameId: String,
    val moves: List<ChessMoveData>,
    val isEnded: Boolean = false,
    val endReason: String? = null,
)

/**
 * Data class representing a chess move
 */
data class ChessMoveData(
    val san: String,
    val fen: String,
    val moveNumber: Int,
    val playerPubkey: String,
    val timestamp: Long,
)
