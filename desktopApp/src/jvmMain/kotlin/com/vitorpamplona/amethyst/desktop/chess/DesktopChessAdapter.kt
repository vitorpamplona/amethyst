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
package com.vitorpamplona.amethyst.desktop.chess

import com.vitorpamplona.amethyst.commons.chess.ChessEventPublisher
import com.vitorpamplona.amethyst.commons.chess.ChessRelayFetchHelper
import com.vitorpamplona.amethyst.commons.chess.ChessRelayFetcher
import com.vitorpamplona.amethyst.commons.chess.IUserMetadataProvider
import com.vitorpamplona.amethyst.commons.chess.RelayGameSummary
import com.vitorpamplona.amethyst.commons.chess.subscription.ChessFilterBuilder
import com.vitorpamplona.amethyst.commons.data.UserMetadataCache
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
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
 * Desktop implementation of ChessEventPublisher.
 * Wraps account.signer.sign() + relayManager.broadcastToAll() for event publishing.
 */
class DesktopChessPublisher(
    private val account: AccountState.LoggedIn,
    private val relayManager: DesktopRelayConnectionManager,
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
            val signedEvent = account.signer.sign(template)
            relayManager.broadcastToAll(signedEvent)
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
            val signedEvent = account.signer.sign(template)
            relayManager.broadcastToAll(signedEvent)
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
            val signedEvent = account.signer.sign(template)
            relayManager.broadcastToAll(signedEvent)
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
            val signedEvent = account.signer.sign(template)
            relayManager.broadcastToAll(signedEvent)
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
            val signedEvent = account.signer.sign(template)
            relayManager.broadcastToAll(signedEvent)
            true
        } catch (e: Exception) {
            false
        }

    override fun getWriteRelayCount(): Int = relayManager.connectedRelays.value.size
}

/**
 * Desktop implementation of ChessRelayFetcher.
 * Uses ChessRelayFetchHelper for one-shot relay queries.
 */
class DesktopRelayFetcher(
    private val relayManager: DesktopRelayConnectionManager,
    private val userPubkey: String,
) : ChessRelayFetcher {
    private val fetchHelper = ChessRelayFetchHelper(relayManager.client)

    override suspend fun fetchGameEvents(gameId: String): ChessGameEvents {
        val filters = ChessFilterBuilder.gameEventsFilter(gameId)
        val relayFilters = relayManager.connectedRelays.value.associateWith { listOf(filters) }

        val events = fetchHelper.fetchEvents(relayFilters)

        val challengeEvent =
            events
                .filterIsInstance<LiveChessGameChallengeEvent>()
                .firstOrNull { it.gameId() == gameId }
                ?: events
                    .filter { it.kind == LiveChessGameChallengeEvent.KIND }
                    .mapNotNull { event ->
                        LiveChessGameChallengeEvent(
                            event.id,
                            event.pubKey,
                            event.createdAt,
                            event.tags,
                            event.content,
                            event.sig,
                        ).takeIf { it.gameId() == gameId }
                    }.firstOrNull()

        val acceptEvent =
            events
                .filterIsInstance<LiveChessGameAcceptEvent>()
                .firstOrNull { it.gameId() == gameId }
                ?: events
                    .filter { it.kind == LiveChessGameAcceptEvent.KIND }
                    .mapNotNull { event ->
                        LiveChessGameAcceptEvent(
                            event.id,
                            event.pubKey,
                            event.createdAt,
                            event.tags,
                            event.content,
                            event.sig,
                        ).takeIf { it.gameId() == gameId }
                    }.firstOrNull()

        val moveEvents =
            events
                .filterIsInstance<LiveChessMoveEvent>()
                .filter { it.gameId() == gameId }
                .ifEmpty {
                    events
                        .filter { it.kind == LiveChessMoveEvent.KIND }
                        .mapNotNull { event ->
                            LiveChessMoveEvent(
                                event.id,
                                event.pubKey,
                                event.createdAt,
                                event.tags,
                                event.content,
                                event.sig,
                            ).takeIf { it.gameId() == gameId }
                        }
                }

        val endEvent =
            events
                .filterIsInstance<LiveChessGameEndEvent>()
                .firstOrNull { it.gameId() == gameId }
                ?: events
                    .filter { it.kind == LiveChessGameEndEvent.KIND }
                    .mapNotNull { event ->
                        LiveChessGameEndEvent(
                            event.id,
                            event.pubKey,
                            event.createdAt,
                            event.tags,
                            event.content,
                            event.sig,
                        ).takeIf { it.gameId() == gameId }
                    }.firstOrNull()

        val drawOffers =
            events
                .filterIsInstance<LiveChessDrawOfferEvent>()
                .filter { it.gameId() == gameId }
                .ifEmpty {
                    events
                        .filter { it.kind == LiveChessDrawOfferEvent.KIND }
                        .mapNotNull { event ->
                            LiveChessDrawOfferEvent(
                                event.id,
                                event.pubKey,
                                event.createdAt,
                                event.tags,
                                event.content,
                                event.sig,
                            ).takeIf { it.gameId() == gameId }
                        }
                }

        return ChessGameEvents(
            challenge = challengeEvent,
            accept = acceptEvent,
            moves = moveEvents,
            end = endEvent,
            drawOffers = drawOffers,
        )
    }

    override suspend fun fetchChallenges(): List<LiveChessGameChallengeEvent> {
        val filters = ChessFilterBuilder.challengesFilter(userPubkey)
        val relayFilters = relayManager.connectedRelays.value.associateWith { listOf(filters) }

        val events = fetchHelper.fetchEvents(relayFilters)

        return events
            .filterIsInstance<LiveChessGameChallengeEvent>()
            .ifEmpty {
                events
                    .filter { it.kind == LiveChessGameChallengeEvent.KIND }
                    .map { event ->
                        LiveChessGameChallengeEvent(
                            event.id,
                            event.pubKey,
                            event.createdAt,
                            event.tags,
                            event.content,
                            event.sig,
                        )
                    }
            }
    }

    override suspend fun fetchRecentGames(): List<RelayGameSummary> {
        val filters = ChessFilterBuilder.recentGamesFilter()
        val relayFilters = relayManager.connectedRelays.value.associateWith { listOf(filters) }

        val events = fetchHelper.fetchEvents(relayFilters)

        // Group by game ID and create summaries
        val gameIds =
            events
                .filter { it.kind == LiveChessMoveEvent.KIND }
                .mapNotNull { event ->
                    val move =
                        if (event is LiveChessMoveEvent) {
                            event
                        } else {
                            LiveChessMoveEvent(
                                event.id,
                                event.pubKey,
                                event.createdAt,
                                event.tags,
                                event.content,
                                event.sig,
                            )
                        }
                    move.gameId()
                }.distinct()

        return gameIds.mapNotNull { gameId ->
            // Find challenge and accept for this game
            val challenge =
                events
                    .filter { it.kind == LiveChessGameChallengeEvent.KIND }
                    .mapNotNull { event ->
                        val e =
                            if (event is LiveChessGameChallengeEvent) {
                                event
                            } else {
                                LiveChessGameChallengeEvent(
                                    event.id,
                                    event.pubKey,
                                    event.createdAt,
                                    event.tags,
                                    event.content,
                                    event.sig,
                                )
                            }
                        e.takeIf { it.gameId() == gameId }
                    }.firstOrNull() ?: return@mapNotNull null

            val accept =
                events
                    .filter { it.kind == LiveChessGameAcceptEvent.KIND }
                    .mapNotNull { event ->
                        val e =
                            if (event is LiveChessGameAcceptEvent) {
                                event
                            } else {
                                LiveChessGameAcceptEvent(
                                    event.id,
                                    event.pubKey,
                                    event.createdAt,
                                    event.tags,
                                    event.content,
                                    event.sig,
                                )
                            }
                        e.takeIf { it.gameId() == gameId }
                    }.firstOrNull()

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

            val moves =
                events
                    .filter { it.kind == LiveChessMoveEvent.KIND }
                    .mapNotNull { event ->
                        val m =
                            if (event is LiveChessMoveEvent) {
                                event
                            } else {
                                LiveChessMoveEvent(
                                    event.id,
                                    event.pubKey,
                                    event.createdAt,
                                    event.tags,
                                    event.content,
                                    event.sig,
                                )
                            }
                        m.takeIf { it.gameId() == gameId }
                    }

            val endEvent =
                events
                    .filter { it.kind == LiveChessGameEndEvent.KIND }
                    .mapNotNull { event ->
                        val e =
                            if (event is LiveChessGameEndEvent) {
                                event
                            } else {
                                LiveChessGameEndEvent(
                                    event.id,
                                    event.pubKey,
                                    event.createdAt,
                                    event.tags,
                                    event.content,
                                    event.sig,
                                )
                            }
                        e.takeIf { it.gameId() == gameId }
                    }.firstOrNull()

            val lastMove = moves.maxByOrNull { it.createdAt }

            RelayGameSummary(
                gameId = gameId,
                whitePubkey = whitePubkey,
                blackPubkey = blackPubkey,
                moveCount = moves.size,
                lastMoveTime = lastMove?.createdAt ?: challenge.createdAt,
                isActive = endEvent == null,
            )
        }
    }
}

/**
 * Desktop implementation of IUserMetadataProvider.
 * Wraps UserMetadataCache for user metadata lookup.
 */
class DesktopMetadataProvider(
    private val metadataCache: UserMetadataCache,
) : IUserMetadataProvider {
    override fun getDisplayName(pubkey: String): String = metadataCache.getDisplayName(pubkey)

    override fun getPictureUrl(pubkey: String): String? = metadataCache.getPictureUrl(pubkey)
}
