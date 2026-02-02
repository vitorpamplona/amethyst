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
import com.vitorpamplona.amethyst.commons.chess.ChessBroadcastStatus
import com.vitorpamplona.amethyst.commons.chess.ChessChallenge
import com.vitorpamplona.amethyst.commons.chess.ChessLobbyLogic
import com.vitorpamplona.amethyst.commons.chess.ChessPollingDefaults
import com.vitorpamplona.amethyst.commons.chess.CompletedGame
import com.vitorpamplona.amethyst.commons.chess.PublicGame
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameState
import kotlinx.coroutines.flow.StateFlow

/**
 * Slim Android ViewModel for chess (~130 lines).
 *
 * Delegates all business logic to ChessLobbyLogic.
 * Only handles Android-specific concerns:
 * - ViewModel lifecycle (viewModelScope)
 * - Platform adapter creation
 * - State exposure to Compose UI
 */
class ChessViewModelNew(
    private val account: Account,
) : ViewModel() {
    // Platform adapters
    private val publisher = AndroidChessPublisher(account)
    private val fetcher = AndroidRelayFetcher(account)
    private val metadataProvider = AndroidMetadataProvider()

    // Shared business logic (creates its own ChessLobbyState internally)
    private val logic =
        ChessLobbyLogic(
            userPubkey = account.userProfile().pubkeyHex,
            publisher = publisher,
            fetcher = fetcher,
            metadataProvider = metadataProvider,
            scope = viewModelScope,
            pollingConfig = ChessPollingDefaults.android,
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

    override fun onCleared() {
        super.onCleared()
        logic.stopPolling()
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

    fun offerDraw(gameId: String) = logic.offerDraw(gameId)

    fun acceptDraw(gameId: String) = logic.acceptDraw(gameId)

    fun declineDraw(gameId: String) = logic.declineDraw(gameId)

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

    /** Helper for derived challenge lists */
    fun incomingChallenges(): List<ChessChallenge> = logic.state.incomingChallenges()

    fun outgoingChallenges(): List<ChessChallenge> = logic.state.outgoingChallenges()

    fun openChallenges(): List<ChessChallenge> = logic.state.openChallenges()
}
