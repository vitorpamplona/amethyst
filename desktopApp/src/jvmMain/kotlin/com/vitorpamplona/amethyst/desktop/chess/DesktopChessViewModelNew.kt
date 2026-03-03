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
package com.vitorpamplona.amethyst.desktop.chess

import com.vitorpamplona.amethyst.commons.chess.ChessBroadcastStatus
import com.vitorpamplona.amethyst.commons.chess.ChessChallenge
import com.vitorpamplona.amethyst.commons.chess.ChessLobbyLogic
import com.vitorpamplona.amethyst.commons.chess.ChessPollingDefaults
import com.vitorpamplona.amethyst.commons.chess.ChessSyncStatus
import com.vitorpamplona.amethyst.commons.chess.CompletedGame
import com.vitorpamplona.amethyst.commons.chess.PublicGame
import com.vitorpamplona.amethyst.commons.data.UserMetadataCache
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.JesterProtocol
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameState
import com.vitorpamplona.quartz.nip64Chess.toJesterEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Slim Desktop ViewModel for chess (~120 lines).
 *
 * Delegates all business logic to ChessLobbyLogic.
 * Only handles Desktop-specific concerns:
 * - Platform adapter creation
 * - State exposure to Compose Desktop UI
 * - UserMetadataCache for profile display
 */
class DesktopChessViewModelNew(
    private val account: AccountState.LoggedIn,
    private val relayManager: DesktopRelayConnectionManager,
    private val scope: CoroutineScope,
) {
    // Desktop-specific metadata cache
    val userMetadataCache = UserMetadataCache()

    // Platform adapters
    private val publisher = DesktopChessPublisher(account, relayManager)
    private val fetcher = DesktopRelayFetcher(relayManager, account.pubKeyHex)
    private val metadataProvider = DesktopMetadataProvider(userMetadataCache)

    // Shared business logic (creates its own ChessLobbyState internally)
    private val logic =
        ChessLobbyLogic(
            userPubkey = account.pubKeyHex,
            publisher = publisher,
            fetcher = fetcher,
            metadataProvider = metadataProvider,
            scope = scope,
            pollingConfig = ChessPollingDefaults.desktop,
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
    val syncStatus: StateFlow<ChessSyncStatus> = logic.state.syncStatus
    val stateVersion: StateFlow<Long> = logic.state.stateVersion

    /** Badge count (incoming challenges + your turn games) - computed property */
    val badgeCount: Int get() = logic.state.badgeCount

    // ============================================
    // Lifecycle
    // ============================================

    init {
        logic.startPolling()
    }

    fun startPolling() = logic.startPolling()

    fun stopPolling() = logic.stopPolling()

    fun forceRefresh() = logic.forceRefresh()

    /**
     * Ensure a game ID is being polled for updates.
     * Call this when viewing a game.
     */
    fun ensureGamePolling(gameId: String) = logic.ensureGamePolling(gameId)

    /**
     * Set focused game mode - only poll this specific game.
     * Call this when viewing a game to avoid refreshing unrelated games.
     */
    fun setFocusedGame(gameId: String) = logic.setFocusedGame(gameId)

    /**
     * Clear focused game mode - return to lobby mode (poll all games).
     * Call this when returning to the lobby view.
     */
    fun clearFocusedGame() = logic.clearFocusedGame()

    // ============================================
    // Incoming event routing (from relay subscriptions)
    // ============================================

    fun handleIncomingEvent(event: Event) {
        if (event.kind != JesterProtocol.KIND) return
        val jesterEvent = event.toJesterEvent() ?: return
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

    // ============================================
    // Spectator operations
    // ============================================

    fun loadGame(gameId: String) = logic.loadGame(gameId)

    fun loadGameAsSpectator(gameId: String) = logic.loadGameAsSpectator(gameId)

    fun stopSpectating(gameId: String) = logic.state.removeSpectatingGame(gameId)

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
