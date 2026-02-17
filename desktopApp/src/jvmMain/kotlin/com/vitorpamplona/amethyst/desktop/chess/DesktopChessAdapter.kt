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

import com.vitorpamplona.amethyst.commons.chess.ChessConfig
import com.vitorpamplona.amethyst.commons.chess.ChessEventBroadcaster
import com.vitorpamplona.amethyst.commons.chess.ChessEventPublisher
import com.vitorpamplona.amethyst.commons.chess.ChessRelayFetchHelper
import com.vitorpamplona.amethyst.commons.chess.ChessRelayFetcher
import com.vitorpamplona.amethyst.commons.chess.IUserMetadataProvider
import com.vitorpamplona.amethyst.commons.chess.RelayFetchProgress
import com.vitorpamplona.amethyst.commons.chess.RelayGameSummary
import com.vitorpamplona.amethyst.commons.chess.subscription.ChessFilterBuilder
import com.vitorpamplona.amethyst.commons.data.UserMetadataCache
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip64Chess.ChessGameEnd
import com.vitorpamplona.quartz.nip64Chess.ChessMoveEvent
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.JesterGameEvents
import com.vitorpamplona.quartz.nip64Chess.JesterProtocol
import com.vitorpamplona.quartz.nip64Chess.toJesterEvent

/**
 * Desktop implementation of ChessEventPublisher using Jester protocol.
 * Wraps account.signer.sign() + relayManager.broadcastToAll() for event publishing.
 *
 * Jester Protocol:
 * - All chess events use kind 30
 * - publishStart creates a start event (content.kind=0)
 * - publishMove creates a move event (content.kind=1) with full history
 * - publishGameEnd creates a move event with result in content
 */
class DesktopChessPublisher(
    private val account: AccountState.LoggedIn,
    private val relayManager: DesktopRelayConnectionManager,
) : ChessEventPublisher {
    private val broadcaster = ChessEventBroadcaster(relayManager.client)

    /**
     * Broadcast an event to the dedicated chess relays with reliable delivery.
     * Uses ChessEventBroadcaster to ensure relay connections before sending.
     */
    private suspend fun broadcastToChessRelays(event: com.vitorpamplona.quartz.nip01Core.core.Event): Boolean {
        println("[DesktopChessPublisher] Broadcasting event ${event.id.take(8)} via ChessEventBroadcaster")
        val result = broadcaster.broadcast(event)
        println("[DesktopChessPublisher] Broadcast result: ${result.message}")
        return result.success
    }

    /**
     * Publish a game start event (challenge).
     * Returns the startEventId (event ID) if successful.
     */
    override suspend fun publishStart(
        playerColor: Color,
        opponentPubkey: String?,
    ): String? =
        try {
            val template =
                if (opponentPubkey != null) {
                    JesterEvent.buildPrivateStart(
                        opponentPubkey = opponentPubkey,
                        playerColor = playerColor,
                    )
                } else {
                    JesterEvent.buildStart(
                        playerColor = playerColor,
                    )
                }
            val signedEvent = account.signer.sign(template)
            val success = broadcastToChessRelays(signedEvent)
            if (success) signedEvent.id else null
        } catch (e: Exception) {
            println("[DesktopChessPublisher] publishStart failed: ${e.message}")
            null
        }

    /**
     * Publish a move event.
     * Returns the move event ID if successful.
     */
    override suspend fun publishMove(move: ChessMoveEvent): String? =
        try {
            val template =
                JesterEvent.buildMove(
                    startEventId = move.startEventId,
                    headEventId = move.headEventId,
                    move = move.san,
                    fen = move.fen,
                    history = move.history,
                    opponentPubkey = move.opponentPubkey,
                )
            val signedEvent = account.signer.sign(template)
            val success = broadcastToChessRelays(signedEvent)
            if (success) signedEvent.id else null
        } catch (e: Exception) {
            println("[DesktopChessPublisher] publishMove failed: ${e.message}")
            null
        }

    /**
     * Publish a game end event (includes result in content).
     */
    override suspend fun publishGameEnd(gameEnd: ChessGameEnd): Boolean =
        try {
            val template =
                JesterEvent.buildEndMove(
                    startEventId = gameEnd.startEventId,
                    headEventId = gameEnd.headEventId,
                    move = gameEnd.lastMove,
                    fen = gameEnd.fen,
                    history = gameEnd.history,
                    opponentPubkey = gameEnd.opponentPubkey,
                    result = gameEnd.result,
                    termination = gameEnd.termination,
                )
            val signedEvent = account.signer.sign(template)
            broadcastToChessRelays(signedEvent)
        } catch (e: Exception) {
            println("[DesktopChessPublisher] publishGameEnd failed: ${e.message}")
            false
        }

    override fun getWriteRelayCount(): Int = ChessConfig.CHESS_RELAYS.size
}

/**
 * Desktop implementation of ChessRelayFetcher using Jester protocol.
 *
 * Hybrid approach (like Android's LocalCache pattern):
 * 1. Query DesktopChessEventCache first (populated by subscription)
 * 2. Also do relay queries and merge results for completeness
 *
 * This solves the "missing games" issue where pure one-shot relay queries
 * would miss events due to timing or slow relays.
 */
class DesktopRelayFetcher(
    private val relayManager: DesktopRelayConnectionManager,
    private val userPubkey: String,
) : ChessRelayFetcher {
    private val fetchHelper = ChessRelayFetchHelper(relayManager.client)

    /**
     * Get the normalized chess relay URLs.
     * Always uses the 3 dedicated chess relays from ChessConfig.
     */
    private fun chessRelayUrls(): List<NormalizedRelayUrl> = ChessConfig.CHESS_RELAYS.map { NormalizedRelayUrl(it) }

    override fun getRelayUrls(): List<String> {
        println("[DesktopRelayFetcher] getRelayUrls: using ${ChessConfig.CHESS_RELAYS.size} chess relays")
        return ChessConfig.CHESS_RELAYS
    }

    /**
     * Fetch game events - queries cache first, supplements with relay query.
     * Similar to Android's LocalCache.addressables pattern.
     *
     * Uses TWO filters:
     * 1. Fetch start event by ID (start events don't have #e tag to themselves)
     * 2. Fetch moves that reference the start event via #e tag
     */
    override suspend fun fetchGameEvents(startEventId: String): JesterGameEvents {
        println("[DesktopRelayFetcher] fetchGameEvents: querying cache for startEventId=$startEventId")

        // Query cache first (populated by subscription)
        val cachedEvents = DesktopChessEventCache.getGameEvents(startEventId)
        println("[DesktopRelayFetcher] fetchGameEvents cache: start=${cachedEvents.startEvent != null}, moves=${cachedEvents.moves.size}")

        // Also do relay query to catch any new events and populate cache
        // Filter 1: Fetch the start event by its ID
        val startEventFilter =
            Filter(
                ids = listOf(startEventId),
                kinds = listOf(JesterProtocol.KIND),
            )
        // Filter 2: Fetch moves that reference the start event
        val movesFilter = ChessFilterBuilder.gameEventsFilter(startEventId)
        val relayFilters = chessRelayUrls().associateWith { listOf(startEventFilter, movesFilter) }
        val relayEvents = fetchHelper.fetchEvents(relayFilters)

        // Add relay events to cache
        relayEvents.forEach { DesktopChessEventCache.add(it) }

        // Convert to JesterEvents and merge
        val relayJesterEvents =
            relayEvents
                .filter { it.kind == JesterProtocol.KIND }
                .mapNotNull { it.toJesterEvent() }

        val relayStartEvent =
            relayJesterEvents
                .firstOrNull { it.isStartEvent() && it.id == startEventId }

        val relayMoves =
            relayJesterEvents
                .filter { it.isMoveEvent() && it.startEventId() == startEventId }

        // Merge: prefer cache start event, merge all moves
        val allMoves = (cachedEvents.moves + relayMoves).distinctBy { it.id }

        println("[DesktopRelayFetcher] fetchGameEvents: found start=${cachedEvents.startEvent ?: relayStartEvent != null}, moves=${allMoves.size}")

        return JesterGameEvents(
            startEvent = cachedEvents.startEvent ?: relayStartEvent,
            moves = allMoves,
        )
    }

    /**
     * Fetch start events (challenges) - queries cache first.
     */
    override suspend fun fetchChallenges(onProgress: ((RelayFetchProgress) -> Unit)?): List<JesterEvent> {
        val relays = chessRelayUrls()
        println("[DesktopRelayFetcher] fetchChallenges: querying cache first, then ${relays.size} chess relays")

        // Query cache for start events that are:
        // 1. Open challenges (no specific opponent)
        // 2. Directed at us
        // 3. Created by us
        val cachedStartEvents =
            DesktopChessEventCache.queryStartEvents { event ->
                event.opponentPubkey() == null ||
                    event.opponentPubkey() == userPubkey ||
                    event.pubKey == userPubkey
            }

        println("[DesktopRelayFetcher] fetchChallenges: found ${cachedStartEvents.size} in cache")

        // Also do relay query to catch any new challenges
        val filter = ChessFilterBuilder.challengesFilter(userPubkey)
        val relayFilters = relays.associateWith { listOf(filter) }
        val relayEvents = fetchHelper.fetchEvents(relayFilters, onProgress = onProgress)

        // Add to cache
        relayEvents.forEach { DesktopChessEventCache.add(it) }

        val relayStartEvents =
            relayEvents
                .filter { it.kind == JesterProtocol.KIND }
                .mapNotNull { it.toJesterEvent() }
                .filter { it.isStartEvent() }

        // Merge and deduplicate
        val allStartEvents = (cachedStartEvents + relayStartEvents).distinctBy { it.id }
        println("[DesktopRelayFetcher] fetchChallenges: total ${allStartEvents.size} after merge")
        return allStartEvents
    }

    /**
     * Fetch user game IDs (startEventIds) - queries cache first.
     */
    override suspend fun fetchUserGameIds(onProgress: ((RelayFetchProgress) -> Unit)?): Set<String> {
        val relays = chessRelayUrls()
        println("[DesktopRelayFetcher] fetchUserGameIds: querying cache first, then ${relays.size} chess relays")

        val gameIds = mutableSetOf<String>()

        // Query cache for start events created by user or directed at user
        DesktopChessEventCache
            .queryStartEvents { event ->
                event.pubKey == userPubkey || event.opponentPubkey() == userPubkey
            }.forEach { gameIds.add(it.id) }

        // Query cache for moves by user or tagged with user
        DesktopChessEventCache
            .queryMoveEvents { event ->
                event.pubKey == userPubkey || event.opponentPubkey() == userPubkey
            }.forEach { event ->
                event.startEventId()?.let { gameIds.add(it) }
            }

        println("[DesktopRelayFetcher] fetchUserGameIds: found ${gameIds.size} in cache")

        // Also do relay query to catch any new game IDs
        val userFilter = ChessFilterBuilder.userGamesFilter(userPubkey)
        val taggedFilter = ChessFilterBuilder.userTaggedFilter(userPubkey)
        val relayFilters = relays.associateWith { listOf(userFilter, taggedFilter) }
        val relayEvents = fetchHelper.fetchEvents(relayFilters, onProgress = onProgress)

        // Add to cache
        relayEvents.forEach { DesktopChessEventCache.add(it) }

        // Extract game IDs from relay events
        relayEvents
            .filter { it.kind == JesterProtocol.KIND }
            .mapNotNull { it.toJesterEvent() }
            .forEach { event ->
                if (event.isStartEvent()) {
                    gameIds.add(event.id)
                } else if (event.isMoveEvent()) {
                    event.startEventId()?.let { gameIds.add(it) }
                }
            }

        println("[DesktopRelayFetcher] fetchUserGameIds: total ${gameIds.size} after merge")
        return gameIds
    }

    /**
     * Fetch recent games for spectating.
     */
    override suspend fun fetchRecentGames(): List<RelayGameSummary> {
        val filter = ChessFilterBuilder.recentGamesFilter()
        val relayFilters = chessRelayUrls().associateWith { listOf(filter) }

        val events = fetchHelper.fetchEvents(relayFilters)

        // Cache events so they're available when watching
        events.forEach { DesktopChessEventCache.add(it) }

        // Convert to JesterEvents
        val jesterEvents =
            events
                .filter { it.kind == JesterProtocol.KIND }
                .mapNotNull { it.toJesterEvent() }

        // Group by game (startEventId for moves, id for start events)
        val startEventsById =
            jesterEvents
                .filter { it.isStartEvent() }
                .associateBy { it.id }

        val movesByGameId =
            jesterEvents
                .filter { it.isMoveEvent() }
                .groupBy { it.startEventId() ?: "" }
                .filterKeys { it.isNotEmpty() }

        // Get all game IDs (from start events and moves)
        val allGameIds = startEventsById.keys + movesByGameId.keys

        return allGameIds.mapNotNull { startEventId ->
            val startEvent = startEventsById[startEventId] ?: return@mapNotNull null
            val moves = movesByGameId[startEventId] ?: emptyList()

            val challengerColor = startEvent.playerColor() ?: Color.WHITE

            // Determine players from start event and moves
            val challengerPubkey = startEvent.pubKey
            val opponentFromMoves = moves.firstOrNull { it.pubKey != challengerPubkey }?.pubKey
            val opponentPubkey = startEvent.opponentPubkey() ?: opponentFromMoves ?: return@mapNotNull null

            val (whitePubkey, blackPubkey) =
                if (challengerColor == Color.WHITE) {
                    challengerPubkey to opponentPubkey
                } else {
                    opponentPubkey to challengerPubkey
                }

            val lastMove = moves.maxByOrNull { it.history().size }
            val hasEnded = lastMove?.result() != null

            RelayGameSummary(
                startEventId = startEventId,
                whitePubkey = whitePubkey,
                blackPubkey = blackPubkey,
                moveCount = lastMove?.history()?.size ?: 0,
                lastMoveTime = lastMove?.createdAt ?: startEvent.createdAt,
                isActive = !hasEnded,
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
