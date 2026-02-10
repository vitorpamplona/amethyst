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

import com.vitorpamplona.quartz.nip64Chess.ChessEngine
import com.vitorpamplona.quartz.nip64Chess.ChessStateReconstructor
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.JesterGameEvents
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameState
import com.vitorpamplona.quartz.nip64Chess.ReconstructedGameState
import com.vitorpamplona.quartz.nip64Chess.ReconstructionResult
import com.vitorpamplona.quartz.nip64Chess.ViewerRole

/**
 * Converts a ReconstructedGameState (from ChessStateReconstructor) into a
 * LiveChessGameState (used by ViewModels for reactive updates).
 *
 * This bridges the gap between the deterministic reconstruction algorithm
 * and the stateful game management that ViewModels need.
 *
 * Usage:
 * ```
 * // Collect events from any source
 * val collector = ChessEventCollector(startEventId)
 * collector.addEvent(startEvent)
 * collector.addEvent(moveEvent1)
 * collector.addEvent(moveEvent2)
 *
 * // Reconstruct using shared algorithm
 * val result = ChessStateReconstructor.reconstruct(collector.getEvents(), viewerPubkey)
 *
 * // Convert to LiveChessGameState for ViewModel use
 * val gameState = ChessGameLoader.toLiveGameState(result, viewerPubkey)
 * ```
 */
object ChessGameLoader {
    /**
     * Convert a ReconstructionResult to a LiveChessGameState for ViewModel use.
     *
     * @param result The result from ChessStateReconstructor
     * @param viewerPubkey The pubkey of the user viewing the game
     * @return LiveChessGameState or null if reconstruction failed
     */
    fun toLiveGameState(
        result: ReconstructionResult,
        viewerPubkey: String,
    ): LiveChessGameState? {
        if (result !is ReconstructionResult.Success) return null

        val state = result.state
        val engine = result.engine

        val (playerPubkey, opponentPubkey) =
            when (state.viewerRole) {
                ViewerRole.WHITE_PLAYER -> viewerPubkey to (state.blackPubkey ?: "")
                ViewerRole.BLACK_PLAYER -> viewerPubkey to (state.whitePubkey ?: "")
                ViewerRole.SPECTATOR -> viewerPubkey to (state.blackPubkey ?: "")
            }

        return LiveChessGameState(
            startEventId = state.startEventId,
            playerPubkey = playerPubkey,
            opponentPubkey = opponentPubkey,
            playerColor = state.playerColor,
            engine = engine,
            createdAt = state.challengeCreatedAt,
            isSpectator = state.viewerRole == ViewerRole.SPECTATOR,
            isPendingChallenge = state.isPendingChallenge,
            initialHeadEventId = state.headEventId,
        ).also { gameState ->
            // Mark all loaded moves as received to prevent re-application during polling
            gameState.markMovesAsReceived(state.appliedMoveNumbers)

            // Mark finished if the game has ended
            if (state.isFinished() && state.gameStatus is com.vitorpamplona.quartz.nip64Chess.GameStatus.Finished) {
                gameState.markAsFinished(
                    (state.gameStatus as com.vitorpamplona.quartz.nip64Chess.GameStatus.Finished).result,
                )
            }

            // Handle pending draw offer
            val drawOfferer = state.pendingDrawOffer
            if (drawOfferer != null && drawOfferer != viewerPubkey) {
                gameState.receiveDrawOffer(drawOfferer)
            }
        }
    }

    /**
     * Load a game using the deterministic reconstruction algorithm.
     *
     * @param events Collected Jester events for the game
     * @param viewerPubkey The pubkey of the user viewing the game
     * @return Pair of (LiveChessGameState, ReconstructedGameState) or null if failed
     */
    fun loadGame(
        events: JesterGameEvents,
        viewerPubkey: String,
    ): LoadGameResult {
        val result = ChessStateReconstructor.reconstruct(events, viewerPubkey)

        return when (result) {
            is ReconstructionResult.Success -> {
                val liveState = toLiveGameState(result, viewerPubkey)
                if (liveState != null) {
                    LoadGameResult.Success(liveState, result.state)
                } else {
                    LoadGameResult.Error("Failed to convert reconstructed state to live state")
                }
            }

            is ReconstructionResult.Error -> {
                LoadGameResult.Error(result.message)
            }
        }
    }

    /**
     * Create a new game state without any events (for when accepting a challenge locally).
     *
     * @param startEventId The start event ID (game identifier)
     * @param playerPubkey The player's pubkey
     * @param opponentPubkey The opponent's pubkey
     * @param playerColor The player's color
     * @return A fresh LiveChessGameState
     */
    fun createNewGame(
        startEventId: String,
        playerPubkey: String,
        opponentPubkey: String,
        playerColor: Color,
        isPendingChallenge: Boolean = false,
    ): LiveChessGameState {
        val engine = ChessEngine()
        engine.reset()

        return LiveChessGameState(
            startEventId = startEventId,
            playerPubkey = playerPubkey,
            opponentPubkey = opponentPubkey,
            playerColor = playerColor,
            engine = engine,
            isPendingChallenge = isPendingChallenge,
        )
    }
}

/**
 * Result of loading a game.
 */
sealed class LoadGameResult {
    data class Success(
        val liveState: LiveChessGameState,
        val reconstructedState: ReconstructedGameState,
    ) : LoadGameResult()

    data class Error(
        val message: String,
    ) : LoadGameResult()

    fun isSuccess(): Boolean = this is Success

    fun getOrNull(): LiveChessGameState? = (this as? Success)?.liveState
}
