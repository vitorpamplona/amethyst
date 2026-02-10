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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chess

import com.vitorpamplona.amethyst.commons.chess.ChessConfig
import com.vitorpamplona.amethyst.commons.chess.ChessEventBroadcaster
import com.vitorpamplona.amethyst.commons.chess.ChessEventPublisher
import com.vitorpamplona.amethyst.commons.chess.ChessRelayFetchHelper
import com.vitorpamplona.amethyst.commons.chess.ChessRelayFetcher
import com.vitorpamplona.amethyst.commons.chess.IUserMetadataProvider
import com.vitorpamplona.amethyst.commons.chess.RelayFetchProgress
import com.vitorpamplona.amethyst.commons.chess.RelayGameSummary
import com.vitorpamplona.amethyst.commons.chess.subscription.ChessFilterBuilder
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.nip64Chess.ChessGameEnd
import com.vitorpamplona.quartz.nip64Chess.ChessMoveEvent
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.JesterGameEvents
import com.vitorpamplona.quartz.nip64Chess.JesterProtocol
import com.vitorpamplona.quartz.nip64Chess.toJesterEvent

/**
 * Android implementation of ChessEventPublisher using Jester protocol.
 * Wraps account.signAndComputeBroadcast() for event publishing.
 *
 * Jester Protocol:
 * - All chess events use kind 30
 * - publishStart creates a start event (content.kind=0)
 * - publishMove creates a move event (content.kind=1) with full history
 * - publishGameEnd creates a move event with result in content
 */
class AndroidChessPublisher(
    private val account: Account,
) : ChessEventPublisher {
    private val broadcaster = ChessEventBroadcaster(account.client)

    /**
     * Broadcast an event to the dedicated chess relays with reliable delivery.
     * Uses ChessEventBroadcaster to ensure relay connections before sending.
     */
    private suspend fun broadcastToChessRelays(event: com.vitorpamplona.quartz.nip01Core.core.Event): Boolean {
        println("[AndroidChessPublisher] Broadcasting event ${event.id.take(8)} via ChessEventBroadcaster")
        val result = broadcaster.broadcast(event)
        println("[AndroidChessPublisher] Broadcast result: ${result.message}")
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
            // Broadcast to chess relays with reliable delivery
            val success = broadcastToChessRelays(signedEvent)
            // Also add to local cache
            account.cache.justConsumeMyOwnEvent(signedEvent)
            if (success) signedEvent.id else null
        } catch (e: Exception) {
            println("[AndroidChessPublisher] publishStart failed: ${e.message}")
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
            // Broadcast to chess relays with reliable delivery
            val success = broadcastToChessRelays(signedEvent)
            // Also add to local cache
            account.cache.justConsumeMyOwnEvent(signedEvent)
            if (success) signedEvent.id else null
        } catch (e: Exception) {
            println("[AndroidChessPublisher] publishMove failed: ${e.message}")
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
            // Broadcast to chess relays with reliable delivery
            val success = broadcastToChessRelays(signedEvent)
            // Also add to local cache
            account.cache.justConsumeMyOwnEvent(signedEvent)
            success
        } catch (e: Exception) {
            println("[AndroidChessPublisher] publishGameEnd failed: ${e.message}")
            false
        }

    override fun getWriteRelayCount(): Int = ChessConfig.CHESS_RELAYS.size
}

/**
 * Android implementation of ChessRelayFetcher using Jester protocol.
 *
 * Uses direct one-shot relay fetching for reliability:
 * - fetchGameEvents: fetches all events for a specific game
 * - fetchChallenges: fetches start events (challenges)
 * - fetchUserGameIds: discovers games where user is a participant
 * - fetchRecentGames: fetches recent games for spectating
 *
 * Each fetch opens a temporary subscription, waits for EOSE, and returns results.
 */
class AndroidRelayFetcher(
    private val account: Account,
) : ChessRelayFetcher {
    private val fetchHelper = ChessRelayFetchHelper(account.client)
    private val userPubkey = account.userProfile().pubkeyHex

    /**
     * Get the normalized chess relay URLs.
     * Always uses the 3 dedicated chess relays from ChessConfig.
     */
    private fun chessRelayUrls(): List<com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl> =
        ChessConfig.CHESS_RELAYS.map {
            com.vitorpamplona.quartz.nip01Core.relay.normalizer
                .NormalizedRelayUrl(it)
        }

    override fun getRelayUrls(): List<String> {
        println("[AndroidRelayFetcher] getRelayUrls: using ${ChessConfig.CHESS_RELAYS.size} chess relays")
        return ChessConfig.CHESS_RELAYS
    }

    /**
     * Fetch game events directly from relays.
     * Uses one-shot fetch for reliability.
     *
     * Uses TWO filters:
     * 1. Fetch start event by ID (start events don't have #e tag to themselves)
     * 2. Fetch moves that reference the start event via #e tag
     */
    override suspend fun fetchGameEvents(startEventId: String): JesterGameEvents {
        println("[AndroidRelayFetcher] fetchGameEvents: fetching from relays for startEventId=$startEventId")

        // Filter 1: Fetch the start event by its ID
        val startEventFilter =
            com.vitorpamplona.quartz.nip01Core.relay.filters.Filter(
                ids = listOf(startEventId),
                kinds = listOf(JesterProtocol.KIND),
            )

        // Filter 2: Fetch moves that reference the start event
        val movesFilter = ChessFilterBuilder.gameEventsFilter(startEventId)

        // Combine both filters
        val relayFilters = chessRelayUrls().associateWith { listOf(startEventFilter, movesFilter) }
        val events = fetchHelper.fetchEvents(relayFilters)

        var startEvent: JesterEvent? = null
        val moves = mutableListOf<JesterEvent>()

        events.forEach { event ->
            if (event.kind != JesterProtocol.KIND) return@forEach
            val jesterEvent = event.toJesterEvent() ?: return@forEach

            if (jesterEvent.isStartEvent() && jesterEvent.id == startEventId) {
                startEvent = jesterEvent
            } else if (jesterEvent.isMoveEvent() && jesterEvent.startEventId() == startEventId) {
                moves.add(jesterEvent)
            }
        }

        println("[AndroidRelayFetcher] fetchGameEvents: found start=${startEvent != null}, moves=${moves.size}")

        return JesterGameEvents(
            startEvent = startEvent,
            moves = moves,
        )
    }

    /**
     * Fetch challenges (start events) directly from relays.
     * Uses one-shot fetch for reliability.
     */
    override suspend fun fetchChallenges(onProgress: ((RelayFetchProgress) -> Unit)?): List<JesterEvent> {
        val relays = chessRelayUrls()
        println("[AndroidRelayFetcher] fetchChallenges: fetching from ${relays.size} relays: $relays")

        if (relays.isEmpty()) {
            println("[AndroidRelayFetcher] fetchChallenges: WARNING - no connected relays!")
            return emptyList()
        }

        val filter = ChessFilterBuilder.challengesFilter(userPubkey)
        println("[AndroidRelayFetcher] fetchChallenges: filter kinds=${filter.kinds}, tags=${filter.tags}, since=${filter.since}, limit=${filter.limit}")
        println("[AndroidRelayFetcher] fetchChallenges: filter JSON=${filter.toJson()}")

        val relayFilters = relays.associateWith { listOf(filter) }
        val events = fetchHelper.fetchEvents(relayFilters, onProgress = onProgress)
        println("[AndroidRelayFetcher] fetchChallenges: received ${events.size} raw events")

        // Debug: If no events, also try without #e filter to see if relays have ANY kind 30 events
        if (events.isEmpty()) {
            println("[AndroidRelayFetcher] DEBUG: No events with #e filter, trying without tag filter...")
            val debugFilter = ChessFilterBuilder.recentGamesFilter()
            val debugFilters = relays.associateWith { listOf(debugFilter) }
            val debugEvents = fetchHelper.fetchEvents(debugFilters, timeoutMs = 10_000)
            println("[AndroidRelayFetcher] DEBUG: recentGamesFilter (no #e) returned ${debugEvents.size} events")
            debugEvents.take(5).forEach { e ->
                println("[AndroidRelayFetcher] DEBUG: kind=${e.kind}, id=${e.id.take(8)}, tags=${e.tags.take(3).map { it.toList() }}")
            }
        }

        val challenges = mutableListOf<JesterEvent>()

        events.forEach { event ->
            if (event.kind != JesterProtocol.KIND) return@forEach
            val jesterEvent = event.toJesterEvent() ?: return@forEach

            if (!jesterEvent.isStartEvent()) return@forEach

            // Include challenges that are:
            // 1. Open challenges (no specific opponent)
            // 2. Directed at us
            // 3. Created by us
            val isRelevant =
                jesterEvent.opponentPubkey() == null ||
                    jesterEvent.opponentPubkey() == userPubkey ||
                    jesterEvent.pubKey == userPubkey

            if (isRelevant) {
                challenges.add(jesterEvent)
            }
        }

        println("[AndroidRelayFetcher] fetchChallenges: found ${challenges.size} challenges from ${events.size} events")
        return challenges
    }

    /**
     * Fetch recent games for spectating.
     */
    override suspend fun fetchRecentGames(): List<RelayGameSummary> {
        val filter = ChessFilterBuilder.recentGamesFilter()
        val relayFilters = chessRelayUrls().associateWith { listOf(filter) }
        val events = fetchHelper.fetchEvents(relayFilters)

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

    /**
     * Fetch user's game IDs directly from relays.
     * Uses one-shot fetch for reliability.
     */
    override suspend fun fetchUserGameIds(onProgress: ((RelayFetchProgress) -> Unit)?): Set<String> {
        println("[AndroidRelayFetcher] fetchUserGameIds: fetching from relays")

        // Fetch events authored by user AND events tagging user
        val authoredFilter = ChessFilterBuilder.userGamesFilter(userPubkey)
        val taggedFilter = ChessFilterBuilder.userTaggedFilter(userPubkey)

        val relayFilters =
            chessRelayUrls().associateWith {
                listOf(authoredFilter, taggedFilter)
            }

        val events = fetchHelper.fetchEvents(relayFilters, onProgress = onProgress)

        val gameIds = mutableSetOf<String>()

        events.forEach { event ->
            if (event.kind != JesterProtocol.KIND) return@forEach
            val jesterEvent = event.toJesterEvent() ?: return@forEach

            // Check if user is involved (author or tagged)
            val isUserInvolved = jesterEvent.pubKey == userPubkey || jesterEvent.opponentPubkey() == userPubkey
            if (!isUserInvolved) return@forEach

            if (jesterEvent.isStartEvent()) {
                gameIds.add(jesterEvent.id)
            } else if (jesterEvent.isMoveEvent()) {
                jesterEvent.startEventId()?.let { gameIds.add(it) }
            }
        }

        println("[AndroidRelayFetcher] fetchUserGameIds: found ${gameIds.size} game IDs from ${events.size} events")
        return gameIds
    }
}

/**
 * Android implementation of IUserMetadataProvider.
 * Wraps LocalCache.getOrCreateUser() for user metadata lookup.
 */
class AndroidMetadataProvider : IUserMetadataProvider {
    override fun getDisplayName(pubkey: String): String {
        val user = LocalCache.getOrCreateUser(pubkey)
        return user.toBestDisplayName()
    }

    override fun getPictureUrl(pubkey: String): String? {
        val user = LocalCache.getOrCreateUser(pubkey)
        return user.profilePicture()
    }
}
