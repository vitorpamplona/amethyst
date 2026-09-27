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
package com.vitorpamplona.amethyst.commons.nip64Chess

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameState
import com.vitorpamplona.quartz.nip64Chess.jester.JesterProtocol
import com.vitorpamplona.quartz.nip64Chess.jester.toJesterEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * The chess screen's state holder, shared by Android and Desktop.
 *
 * All business logic lives in [ChessLobbyLogic]; this class only exposes its state and forwards
 * the user's actions. Each platform supplies its own adapters (how to publish, fetch, resolve
 * metadata and remember dismissed games) and its polling cadence.
 *
 * On Android it is a lifecycle-scoped `ViewModel`: [scope] defaults to `viewModelScope` and
 * polling stops in [onCleared]. Desktop, which has no `ViewModelStore`, passes the scope of the
 * composition that owns it; polling then ends when that scope is cancelled.
 */
@Stable
class ChessViewModel(
    private val userPubkey: HexKey,
    publisher: ChessEventPublisher,
    fetcher: ChessRelayFetcher,
    metadataProvider: IUserMetadataProvider,
    pollingConfig: ChessPollingConfig,
    dismissedStorage: ChessDismissedGamesStore?,
    scope: CoroutineScope? = null,
) : ViewModel() {
    // Shared business logic (creates its own ChessLobbyState internally)
    private val logic =
        ChessLobbyLogic(
            userPubkey = userPubkey,
            publisher = publisher,
            fetcher = fetcher,
            metadataProvider = metadataProvider,
            scope = scope ?: viewModelScope,
            pollingConfig = pollingConfig,
            dismissedStorage = dismissedStorage,
        )

    // ============================================
    // State exposure (delegated from ChessLobbyLogic.state)
    // ============================================

    val activeGames: StateFlow<Map<String, LiveChessGameState>> = logic.state.activeGames
    val spectatingGames: StateFlow<Map<String, LiveChessGameState>> = logic.state.spectatingGames
    val challenges: StateFlow<List<ChessChallenge>> = logic.state.challenges
    val publicGames: StateFlow<List<PublicGame>> = logic.state.publicGames
    val completedGames: StateFlow<List<CompletedGame>> = logic.state.completedGames
    val broadcastStatus: StateFlow<ChessBroadcastStatus> = logic.state.broadcastStatus
    val error: StateFlow<String?> = logic.state.error
    val selectedGameId: StateFlow<String?> = logic.state.selectedGameId
    val isRefreshing: StateFlow<Boolean> = logic.state.isRefreshing
    val stateVersion: StateFlow<Long> = logic.state.stateVersion
    val syncStatus: StateFlow<ChessSyncStatus> = logic.state.syncStatus

    /** Badge count (incoming challenges + your turn games) - computed property */
    val badgeCount: Int get() = logic.state.badgeCount

    // ============================================
    // Lifecycle
    // ============================================

    init {
        Log.d("chessdebug") { "[ChessVM] init: userPubkey=${userPubkey.take(8)}" }
        logic.startPolling()
    }

    fun startPolling() = logic.startPolling()

    fun stopPolling() = logic.stopPolling()

    fun forceRefresh() = logic.forceRefresh()

    fun dismissCompletedGame(gameId: String) = logic.dismissCompletedGame(gameId)

    fun dismissAllCompletedGames() = logic.dismissAllCompletedGames()

    /**
     * Ensure a game ID is being polled for updates.
     * Call this when entering a game screen.
     */
    fun ensureGamePolling(gameId: String) = logic.ensureGamePolling(gameId)

    /**
     * Set focused game mode - only poll this specific game.
     * Call this when entering a game screen to avoid refreshing unrelated games.
     */
    fun setFocusedGame(gameId: String) = logic.setFocusedGame(gameId)

    /**
     * Clear focused game mode - return to lobby mode (poll all games).
     * Call this when returning to the lobby screen.
     */
    fun clearFocusedGame() = logic.clearFocusedGame()

    override fun onCleared() {
        logic.stopPolling()
    }

    // ============================================
    // Incoming event routing (from relay subscriptions)
    // ============================================

    fun handleIncomingEvent(event: Event) {
        if (event.kind != JesterProtocol.KIND) return
        val jesterEvent =
            event.toJesterEvent() ?: run {
                Log.d("chessdebug") { "[ChessVM] handleIncomingEvent: failed to parse kind ${event.kind} event ${event.id.take(8)} as JesterEvent" }
                return
            }
        Log.d("chessdebug") { "[ChessVM] handleIncomingEvent: id=${event.id.take(8)}, pubkey=${event.pubKey.take(8)}, isStart=${jesterEvent.isStartEvent()}, isMove=${jesterEvent.isMoveEvent()}" }
        logic.handleIncomingEvent(jesterEvent)
    }

    // ============================================
    // Challenge operations
    // ============================================

    fun createChallenge(
        opponentPubkey: String? = null,
        playerColor: Color = Color.WHITE,
        timeControl: String? = null,
    ) = logic.createChallenge(opponentPubkey, playerColor, timeControl)

    fun acceptChallenge(challenge: ChessChallenge) = logic.acceptChallenge(challenge)

    fun openOwnChallenge(challenge: ChessChallenge) = logic.openOwnChallenge(challenge)

    // ============================================
    // Game operations
    // ============================================

    fun selectGame(gameId: String?) = logic.selectGame(gameId)

    fun publishMove(
        gameId: String,
        from: String,
        to: String,
    ) = logic.publishMove(gameId, from, to)

    fun resign(gameId: String) = logic.resign(gameId)

    fun claimAbandonmentVictory(gameId: String) = logic.claimAbandonmentVictory(gameId)

    fun dismissGame(gameId: String) = logic.dismissGame(gameId)

    // ============================================
    // Spectator operations
    // ============================================

    fun loadGame(gameId: String) = logic.loadGame(gameId)

    fun loadGameAsSpectator(gameId: String) = logic.loadGameAsSpectator(gameId)

    fun stopSpectating(gameId: String) = logic.stopSpectating(gameId)

    fun removeGame(gameId: String) = logic.removeGame(gameId)

    // ============================================
    // Utility
    // ============================================

    fun clearError() = logic.clearError()

    fun getGameState(gameId: String): LiveChessGameState? = logic.state.getGameState(gameId)

    /** Check if a game was accepted (prevents loading as spectator during race) */
    fun wasAccepted(gameId: String): Boolean = logic.state.wasAccepted(gameId)

    /** Helper for derived challenge lists */
    fun incomingChallenges(): List<ChessChallenge> = logic.state.incomingChallenges()

    fun outgoingChallenges(): List<ChessChallenge> = logic.state.outgoingChallenges()

    fun openChallenges(): List<ChessChallenge> = logic.state.openChallenges()
}
