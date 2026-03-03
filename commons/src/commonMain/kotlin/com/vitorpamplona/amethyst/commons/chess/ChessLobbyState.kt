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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Challenge expiry: 24 hours
 */
const val CHALLENGE_EXPIRY_SECONDS = 24 * 60 * 60L

/**
 * Represents a chess challenge that can be displayed in the lobby
 */
@Immutable
data class ChessChallenge(
    val eventId: String,
    val gameId: String,
    val challengerPubkey: String,
    val challengerDisplayName: String?,
    val challengerAvatarUrl: String?,
    val opponentPubkey: String?,
    val challengerColor: Color,
    val createdAt: Long,
) {
    /** Whether this is an open challenge anyone can accept */
    val isOpen: Boolean get() = opponentPubkey == null

    /** Whether this challenge is directed at a specific user */
    fun isDirectedAt(pubkey: String): Boolean = opponentPubkey == pubkey

    /** Whether this challenge was created by a specific user */
    fun isFrom(pubkey: String): Boolean = challengerPubkey == pubkey
}

/**
 * Represents a public game that can be spectated
 */
@Immutable
data class PublicGame(
    val gameId: String,
    val whitePubkey: String,
    val whiteDisplayName: String?,
    val blackPubkey: String,
    val blackDisplayName: String?,
    val moveCount: Int,
    val lastMoveTime: Long,
    val isActive: Boolean,
)

/**
 * Represents a completed game for history display
 */
@Immutable
data class CompletedGame(
    val gameId: String,
    val whitePubkey: String,
    val whiteDisplayName: String?,
    val blackPubkey: String,
    val blackDisplayName: String?,
    val result: String,
    val termination: String?,
    val moveCount: Int,
    val completedAt: Long,
) {
    /** Whether user won this game */
    fun didUserWin(userPubkey: String): Boolean =
        when (result) {
            "1-0" -> whitePubkey == userPubkey
            "0-1" -> blackPubkey == userPubkey
            else -> false
        }

    /** Whether this was a draw */
    val isDraw: Boolean get() = result == "1/2-1/2"
}

/**
 * Individual relay status during sync
 */
@Immutable
data class RelaySyncState(
    val url: String,
    val displayName: String,
    val status: RelaySyncStatus,
    val eventsReceived: Int = 0,
)

@Immutable
enum class RelaySyncStatus {
    CONNECTING,
    WAITING,
    RECEIVING,
    EOSE_RECEIVED,
    FAILED,
}

/**
 * Sync status for incoming events (subscription-side)
 */
@Immutable
sealed class ChessSyncStatus {
    data object Idle : ChessSyncStatus()

    data class Syncing(
        val phase: String,
        val relayStates: List<RelaySyncState>,
        val totalEventsReceived: Int,
    ) : ChessSyncStatus() {
        val connectedCount: Int get() = relayStates.count { it.status != RelaySyncStatus.FAILED }
        val eoseCount: Int get() = relayStates.count { it.status == RelaySyncStatus.EOSE_RECEIVED }
        val totalCount: Int get() = relayStates.size
    }

    data class Synced(
        val relayStates: List<RelaySyncState>,
        val challengeCount: Int,
        val gameCount: Int,
        val totalEventsReceived: Int,
    ) : ChessSyncStatus() {
        val successCount: Int get() = relayStates.count { it.status == RelaySyncStatus.EOSE_RECEIVED }
        val totalCount: Int get() = relayStates.size
    }

    data class PartialSync(
        val relayStates: List<RelaySyncState>,
        val message: String,
    ) : ChessSyncStatus() {
        val successCount: Int get() = relayStates.count { it.status == RelaySyncStatus.EOSE_RECEIVED }
        val failedCount: Int get() = relayStates.count { it.status == RelaySyncStatus.FAILED }
        val totalCount: Int get() = relayStates.size
    }
}

/**
 * Chess status for UI feedback
 */
@Immutable
sealed class ChessBroadcastStatus {
    data object Idle : ChessBroadcastStatus()

    data class Broadcasting(
        val san: String,
        val successCount: Int,
        val totalRelays: Int,
    ) : ChessBroadcastStatus() {
        val progress: Float get() = if (totalRelays > 0) successCount.toFloat() / totalRelays else 0f
    }

    data class Success(
        val san: String,
        val relayCount: Int,
    ) : ChessBroadcastStatus()

    data class Failed(
        val san: String,
        val error: String,
    ) : ChessBroadcastStatus()

    data object WaitingForOpponent : ChessBroadcastStatus()

    data class Syncing(
        val progress: Float = 0f,
    ) : ChessBroadcastStatus()

    data class Desynced(
        val message: String,
    ) : ChessBroadcastStatus()
}

/**
 * Shared chess lobby state that can be used by both Android and Desktop
 */
class ChessLobbyState(
    private val userPubkey: String,
    private val scope: CoroutineScope,
) {
    // Active games where user is a participant
    private val _activeGames = MutableStateFlow<Map<String, LiveChessGameState>>(emptyMap())
    val activeGames: StateFlow<Map<String, LiveChessGameState>> = _activeGames.asStateFlow()

    // Public games that can be spectated
    private val _publicGames = MutableStateFlow<List<PublicGame>>(emptyList())
    val publicGames: StateFlow<List<PublicGame>> = _publicGames.asStateFlow()

    // All challenges (filtered by UI based on type)
    private val _challenges = MutableStateFlow<List<ChessChallenge>>(emptyList())
    val challenges: StateFlow<List<ChessChallenge>> = _challenges.asStateFlow()

    // Spectating games (user is watching but not playing)
    private val _spectatingGames = MutableStateFlow<Map<String, LiveChessGameState>>(emptyMap())
    val spectatingGames: StateFlow<Map<String, LiveChessGameState>> = _spectatingGames.asStateFlow()

    // Completed games history
    private val _completedGames = MutableStateFlow<List<CompletedGame>>(emptyList())
    val completedGames: StateFlow<List<CompletedGame>> = _completedGames.asStateFlow()

    // Game IDs that user has accepted - uses global singleton to share across ViewModel instances
    // This is critical because lobby and game screen may have different ViewModel instances

    // Broadcast status
    private val _broadcastStatus = MutableStateFlow<ChessBroadcastStatus>(ChessBroadcastStatus.Idle)
    val broadcastStatus: StateFlow<ChessBroadcastStatus> = _broadcastStatus.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Loading/refreshing state
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Sync status for subscription banner
    private val _syncStatus = MutableStateFlow<ChessSyncStatus>(ChessSyncStatus.Idle)
    val syncStatus: StateFlow<ChessSyncStatus> = _syncStatus.asStateFlow()

    // Selected game ID for navigation
    private val _selectedGameId = MutableStateFlow<String?>(null)
    val selectedGameId: StateFlow<String?> = _selectedGameId.asStateFlow()

    // State version counter - increments on every game state update
    // UI can observe this to force recomposition when internal state changes
    private val stateVersionCounter = AtomicLong(0)
    private val _stateVersion = MutableStateFlow(0L)
    val stateVersion: StateFlow<Long> = _stateVersion.asStateFlow()

    // Badge count (incoming challenges + your turn games)
    val badgeCount: Int
        get() {
            val incomingChallenges = _challenges.value.count { it.isDirectedAt(userPubkey) }
            val yourTurnGames = _activeGames.value.values.count { it.isPlayerTurn() }
            return incomingChallenges + yourTurnGames
        }

    // ========================================
    // Derived state for UI sections
    // ========================================

    /** Challenges directed at the user */
    fun incomingChallenges(): List<ChessChallenge> = _challenges.value.filter { it.isDirectedAt(userPubkey) }

    /** Challenges created by the user */
    fun outgoingChallenges(): List<ChessChallenge> = _challenges.value.filter { it.isFrom(userPubkey) }

    /** Open challenges from others that user can join */
    fun openChallenges(): List<ChessChallenge> = _challenges.value.filter { it.isOpen && !it.isFrom(userPubkey) }

    // ========================================
    // State updates
    // ========================================

    fun updateChallenges(challenges: List<ChessChallenge>) {
        _challenges.value = challenges
    }

    fun addChallenge(challenge: ChessChallenge) {
        _challenges.update { current ->
            if (current.any { it.eventId == challenge.eventId || it.gameId == challenge.gameId }) {
                current
            } else {
                current + challenge
            }
        }
    }

    fun removeChallenge(gameId: String) {
        _challenges.update { current ->
            current.filter { it.gameId != gameId }
        }
    }

    fun updatePublicGames(games: List<PublicGame>) {
        _publicGames.value = games
    }

    fun addActiveGame(
        gameId: String,
        state: LiveChessGameState,
    ) {
        _activeGames.update { current ->
            val existing = current[gameId]
            if (existing != null) {
                // Only replace if new state has at least as many moves
                val existingMoves = existing.moveHistory.value.size
                val newMoves = state.moveHistory.value.size
                if (newMoves < existingMoves) {
                    return@update current
                }
            }
            current + (gameId to state)
        }
        // Track as accepted to prevent refresh from re-adding as optimistic challenge
        AcceptedGamesRegistry.markAsAccepted(gameId)
        // Remove from challenges if present
        removeChallenge(gameId)
    }

    fun removeActiveGame(gameId: String) {
        _activeGames.update { it - gameId }
    }

    fun updateActiveGame(
        gameId: String,
        update: (LiveChessGameState) -> LiveChessGameState,
    ) {
        _activeGames.update { current ->
            current[gameId]?.let { state ->
                current + (gameId to update(state))
            } ?: current
        }
    }

    /**
     * Replace game state entirely after full reconstruction from relays.
     * Preserves game location (active vs spectating).
     *
     * IMPORTANT: Only replaces if new state has >= moves than current state.
     * This prevents race conditions where polling refresh could revert
     * a user's move before it propagates to relays.
     *
     * IMPORTANT: Never replace a participant game with a spectator state.
     * This prevents the race where relay fetch returns before acceptance propagates,
     * which would incorrectly mark an accepted game as spectating.
     */
    fun replaceGameState(
        gameId: String,
        newState: LiveChessGameState,
    ) {
        val inActiveGames = _activeGames.value.containsKey(gameId)
        val inSpectatingGames = _spectatingGames.value.containsKey(gameId)

        val currentState = _activeGames.value[gameId] ?: _spectatingGames.value[gameId]
        val currentMoveCount = currentState?.moveHistory?.value?.size ?: 0
        val newMoveCount = newState.moveHistory.value.size

        // Only replace if new state has at least as many moves
        // This prevents reverting user's local moves during refresh
        if (newMoveCount < currentMoveCount) {
            return
        }

        // Handle case where new state incorrectly has isSpectator=true but current is participant
        // This happens with open challenges where opponent can't be determined from relay events alone
        // Solution: Apply moves from new state while preserving participant status from current
        val stateToUse =
            if (currentState != null && !currentState.isSpectator && newState.isSpectator && newMoveCount > currentMoveCount) {
                // Apply opponent's moves to current state's engine
                currentState.applyMovesFrom(newState)
                currentState // Keep using current state with updated engine
            } else if (currentState != null && !currentState.isSpectator && newState.isSpectator) {
                return
            } else {
                newState
            }

        if (inActiveGames) {
            _activeGames.update { it + (gameId to stateToUse) }
            // Increment version to force UI recomposition even if map equals() returns true
            val newVersion = stateVersionCounter.incrementAndGet()
            _stateVersion.value = newVersion
        } else if (inSpectatingGames) {
            _spectatingGames.update { it + (gameId to stateToUse) }
            val newVersion = stateVersionCounter.incrementAndGet()
            _stateVersion.value = newVersion
        }
    }

    /**
     * Move a game from active/spectating to completed.
     * Display names are optional; UI can look them up later if needed.
     */
    fun moveToCompleted(
        gameId: String,
        result: String,
        termination: String?,
        whiteDisplayName: String? = null,
        blackDisplayName: String? = null,
    ) {
        val existingState = _activeGames.value[gameId] ?: _spectatingGames.value[gameId]
        existingState?.let { gameState ->
            // Derive white/black pubkeys from player color
            val whitePubkey =
                if (gameState.playerColor == Color.WHITE) {
                    gameState.playerPubkey
                } else {
                    gameState.opponentPubkey
                }
            val blackPubkey =
                if (gameState.playerColor == Color.BLACK) {
                    gameState.playerPubkey
                } else {
                    gameState.opponentPubkey
                }

            val completed =
                CompletedGame(
                    gameId = gameId,
                    whitePubkey = whitePubkey,
                    whiteDisplayName = whiteDisplayName,
                    blackPubkey = blackPubkey,
                    blackDisplayName = blackDisplayName,
                    result = result,
                    termination = termination,
                    moveCount = gameState.moveHistory.value.size,
                    completedAt =
                        com.vitorpamplona.quartz.utils.TimeUtils
                            .now(),
                )

            _completedGames.update { current ->
                // Avoid duplicates
                if (current.any { it.gameId == gameId }) {
                    current
                } else {
                    listOf(completed) + current
                }
            }

            // Remove from active/spectating
            _activeGames.update { it - gameId }
            _spectatingGames.update { it - gameId }

            // Clear selection if this game was selected
            if (_selectedGameId.value == gameId) {
                _selectedGameId.value = null
            }
        }
    }

    fun addSpectatingGame(
        gameId: String,
        state: LiveChessGameState,
    ) {
        // Never add to spectating if this game was accepted - user is a participant
        if (AcceptedGamesRegistry.wasAccepted(gameId)) {
            // Add to active games instead
            _activeGames.update { it + (gameId to state) }
            return
        }
        _spectatingGames.update { it + (gameId to state) }
    }

    fun removeSpectatingGame(gameId: String) {
        _spectatingGames.update { it - gameId }
    }

    fun setBroadcastStatus(status: ChessBroadcastStatus) {
        _broadcastStatus.value = status
    }

    fun setError(error: String?) {
        _error.value = error
    }

    fun setRefreshing(refreshing: Boolean) {
        _isRefreshing.value = refreshing
    }

    fun setSyncStatus(status: ChessSyncStatus) {
        _syncStatus.value = status
    }

    fun selectGame(gameId: String?) {
        _selectedGameId.value = gameId
    }

    fun getGameState(gameId: String): LiveChessGameState? = _activeGames.value[gameId] ?: _spectatingGames.value[gameId]

    fun isUserParticipant(gameId: String): Boolean = _activeGames.value.containsKey(gameId)

    fun isSpectating(gameId: String): Boolean = _spectatingGames.value.containsKey(gameId)

    /** Whether a game ID was accepted (prevents refresh from re-adding as challenge) */
    fun wasAccepted(gameId: String): Boolean = AcceptedGamesRegistry.wasAccepted(gameId)

    /** Mark a game as accepted synchronously (call before async game creation) */
    fun markAsAccepted(gameId: String) {
        AcceptedGamesRegistry.markAsAccepted(gameId)
    }

    fun clearAll() {
        _activeGames.value = emptyMap()
        _publicGames.value = emptyList()
        _challenges.value = emptyList()
        _spectatingGames.value = emptyMap()
        _completedGames.value = emptyList()
        AcceptedGamesRegistry.clear()
        _broadcastStatus.value = ChessBroadcastStatus.Idle
        _error.value = null
        _selectedGameId.value = null
    }
}
