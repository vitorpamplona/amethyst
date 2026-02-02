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

import com.vitorpamplona.amethyst.commons.chess.ChessEventPublisher
import com.vitorpamplona.amethyst.commons.chess.ChessRelayFetcher
import com.vitorpamplona.amethyst.commons.chess.IUserMetadataProvider
import com.vitorpamplona.amethyst.commons.chess.RelayGameSummary
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.filterIntoSet
import com.vitorpamplona.quartz.nip64Chess.ChessGameEnd
import com.vitorpamplona.quartz.nip64Chess.ChessGameEvents
import com.vitorpamplona.quartz.nip64Chess.ChessMoveEvent
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.LiveChessDrawOfferEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessMoveEvent

/**
 * Android implementation of ChessEventPublisher.
 * Wraps account.signAndComputeBroadcast() for event publishing.
 */
class AndroidChessPublisher(
    private val account: Account,
) : ChessEventPublisher {
    override suspend fun publishChallenge(
        gameId: String,
        playerColor: Color,
        opponentPubkey: String?,
        timeControl: String?,
    ): Boolean =
        try {
            val template =
                LiveChessGameChallengeEvent.build(
                    gameId = gameId,
                    playerColor = playerColor,
                    opponentPubkey = opponentPubkey,
                    timeControl = timeControl,
                )
            account.signAndComputeBroadcast(template)
            true
        } catch (e: Exception) {
            false
        }

    override suspend fun publishAccept(
        gameId: String,
        challengeEventId: String,
        challengerPubkey: String,
    ): Boolean =
        try {
            val template =
                LiveChessGameAcceptEvent.build(
                    gameId = gameId,
                    challengeEventId = challengeEventId,
                    challengerPubkey = challengerPubkey,
                )
            account.signAndComputeBroadcast(template)
            true
        } catch (e: Exception) {
            false
        }

    override suspend fun publishMove(move: ChessMoveEvent): Boolean =
        try {
            val template =
                LiveChessMoveEvent.build(
                    gameId = move.gameId,
                    moveNumber = move.moveNumber,
                    san = move.san,
                    fen = move.fen,
                    opponentPubkey = move.opponentPubkey,
                )
            account.signAndComputeBroadcast(template)
            true
        } catch (e: Exception) {
            false
        }

    override suspend fun publishGameEnd(gameEnd: ChessGameEnd): Boolean =
        try {
            val template =
                LiveChessGameEndEvent.build(
                    gameId = gameEnd.gameId,
                    result = gameEnd.result,
                    termination = gameEnd.termination,
                    winnerPubkey = gameEnd.winnerPubkey,
                    opponentPubkey = gameEnd.opponentPubkey,
                    pgn = gameEnd.pgn ?: "",
                )
            account.signAndComputeBroadcast(template)
            true
        } catch (e: Exception) {
            false
        }

    override suspend fun publishDrawOffer(
        gameId: String,
        opponentPubkey: String,
        message: String?,
    ): Boolean =
        try {
            val template =
                LiveChessDrawOfferEvent.build(
                    gameId = gameId,
                    opponentPubkey = opponentPubkey,
                    message = message ?: "",
                )
            account.signAndComputeBroadcast(template)
            true
        } catch (e: Exception) {
            false
        }

    override fun getWriteRelayCount(): Int = account.outboxRelays.flow.value.size
}

/**
 * Android implementation of ChessRelayFetcher.
 * Uses LocalCache for game state (hybrid approach during migration).
 */
class AndroidRelayFetcher(
    private val account: Account,
) : ChessRelayFetcher {
    override suspend fun fetchGameEvents(gameId: String): ChessGameEvents {
        // Query LocalCache for all game events
        val challengeNotes =
            LocalCache.addressables.filterIntoSet(LiveChessGameChallengeEvent.KIND) { _, note ->
                val event = note.event as? LiveChessGameChallengeEvent ?: return@filterIntoSet false
                event.gameId() == gameId
            }
        val challengeEvent = challengeNotes.firstOrNull()?.event as? LiveChessGameChallengeEvent

        val acceptNotes =
            LocalCache.addressables.filterIntoSet(LiveChessGameAcceptEvent.KIND) { _, note ->
                val event = note.event as? LiveChessGameAcceptEvent ?: return@filterIntoSet false
                event.gameId() == gameId
            }
        val acceptEvent = acceptNotes.firstOrNull()?.event as? LiveChessGameAcceptEvent

        val moveNotes =
            LocalCache.addressables.filterIntoSet(LiveChessMoveEvent.KIND) { _, note ->
                val event = note.event as? LiveChessMoveEvent ?: return@filterIntoSet false
                event.gameId() == gameId
            }
        val moveEvents = moveNotes.mapNotNull { it.event as? LiveChessMoveEvent }

        val endNotes =
            LocalCache.addressables.filterIntoSet(LiveChessGameEndEvent.KIND) { _, note ->
                val event = note.event as? LiveChessGameEndEvent ?: return@filterIntoSet false
                event.gameId() == gameId
            }
        val endEvent = endNotes.firstOrNull()?.event as? LiveChessGameEndEvent

        return ChessGameEvents(
            challenge = challengeEvent,
            accept = acceptEvent,
            moves = moveEvents,
            end = endEvent,
            drawOffers = emptyList(), // TODO: Fetch draw offers
        )
    }

    override suspend fun fetchChallenges(): List<LiveChessGameChallengeEvent> {
        val challengeNotes =
            LocalCache.addressables.filterIntoSet(LiveChessGameChallengeEvent.KIND) { _, note ->
                note.event is LiveChessGameChallengeEvent
            }
        return challengeNotes.mapNotNull { it.event as? LiveChessGameChallengeEvent }
    }

    override suspend fun fetchRecentGames(): List<RelayGameSummary> {
        // Query LocalCache for recent active games
        // Group moves by game ID and create summaries
        val moveNotes =
            LocalCache.addressables.filterIntoSet(LiveChessMoveEvent.KIND) { _, note ->
                note.event is LiveChessMoveEvent
            }
        val gameIds = moveNotes.mapNotNull { (it.event as? LiveChessMoveEvent)?.gameId() }.distinct()

        return gameIds.mapNotNull { gameId ->
            val events = fetchGameEvents(gameId)
            val challenge = events.challenge ?: return@mapNotNull null
            val accept = events.accept

            // Determine white/black pubkeys
            val challengerColor = challenge.playerColor() ?: Color.WHITE
            val whitePubkey =
                if (challengerColor == Color.WHITE) {
                    challenge.pubKey
                } else {
                    accept?.pubKey ?: return@mapNotNull null
                }
            val blackPubkey =
                if (challengerColor == Color.BLACK) {
                    challenge.pubKey
                } else {
                    accept?.pubKey ?: return@mapNotNull null
                }

            val lastMove = events.moves.maxByOrNull { it.createdAt }

            RelayGameSummary(
                gameId = gameId,
                whitePubkey = whitePubkey,
                blackPubkey = blackPubkey,
                moveCount = events.moves.size,
                lastMoveTime = lastMove?.createdAt ?: challenge.createdAt,
                isActive = events.end == null,
            )
        }
    }
}

/**
 * Android implementation of IUserMetadataProvider.
 * Wraps LocalCache.getOrCreateUser() for user metadata lookup.
 */
class AndroidMetadataProvider : IUserMetadataProvider {
    override fun getDisplayName(pubkey: String): String {
        val user = LocalCache.getOrCreateUser(pubkey)
        return user.info?.bestName()
            ?: user.pubkeyDisplayHex()
    }

    override fun getPictureUrl(pubkey: String): String? {
        val user = LocalCache.getOrCreateUser(pubkey)
        return user.info?.profilePicture()
    }
}
