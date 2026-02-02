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
package com.vitorpamplona.amethyst.commons.chess.subscription

import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessMoveEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Shared filter builder for chess subscriptions.
 * Used by both Android and Desktop to ensure identical subscription behavior.
 *
 * Builds 4 types of filters:
 * 1. Personal filters - Events tagged with user's pubkey
 * 2. Challenge filters - All challenges (like jesterui pattern)
 * 3. Active game filters - Game-specific move/end subscriptions
 * 4. Recent game filters - For spectating discovery (7 day window)
 */
object ChessFilterBuilder {
    /** Challenge and accept event kinds (for lobby display) */
    private val CHALLENGE_KINDS =
        listOf(
            LiveChessGameChallengeEvent.KIND,
            LiveChessGameAcceptEvent.KIND,
        )

    /** Move and end event kinds (for active games) */
    private val GAME_EVENT_KINDS =
        listOf(
            LiveChessMoveEvent.KIND,
            LiveChessGameEndEvent.KIND,
        )

    /** All chess event kinds */
    private val ALL_CHESS_KINDS = CHALLENGE_KINDS + GAME_EVENT_KINDS

    /**
     * Build all chess filters for a given subscription state.
     * This is the main entry point - platforms call this to get filters.
     *
     * @param state The current subscription state with user info and active games
     * @param sinceForRelay Function to get cached EOSE time for a relay (null if no cache)
     * @return List of relay-specific filters to subscribe with
     */
    fun buildAllFilters(
        state: ChessSubscriptionState,
        sinceForRelay: (NormalizedRelayUrl) -> Long?,
    ): List<RelayBasedFilter> {
        val filters = mutableListOf<RelayBasedFilter>()
        val now = TimeUtils.now()

        // Filter 1: Personal events (challenges to us, moves in our games)
        filters.addAll(
            buildPersonalFilters(state.userPubkey, state.relays, sinceForRelay, now),
        )

        // Filter 2: All challenges (for lobby display)
        filters.addAll(
            buildChallengeFilters(state.relays, sinceForRelay, now),
        )

        // Filter 3: Active game subscriptions (game-specific)
        if (state.hasActiveGames) {
            filters.addAll(
                buildActiveGameFilters(
                    state.allGameIds,
                    state.userPubkey,
                    state.opponentPubkeys,
                    state.relays,
                ),
            )
        }

        // Filter 4: Recent game events (for spectating discovery)
        filters.addAll(
            buildRecentGameFilters(state.relays, sinceForRelay, now),
        )

        return filters
    }

    /**
     * Personal events - tagged with user's pubkey.
     * Catches: challenges directed at us, moves in our games, game ends.
     */
    fun buildPersonalFilters(
        userPubkey: String,
        relays: Set<NormalizedRelayUrl>,
        sinceForRelay: (NormalizedRelayUrl) -> Long?,
        now: Long = TimeUtils.now(),
    ): List<RelayBasedFilter> {
        val filter =
            Filter(
                kinds = ALL_CHESS_KINDS,
                tags = mapOf("p" to listOf(userPubkey)),
                limit = 100,
            )

        return relays.map { relay ->
            val sinceTime =
                sinceForRelay(relay)
                    ?: (now - ChessTimeWindows.CHALLENGE_WINDOW_SECONDS)
            RelayBasedFilter(
                relay = relay,
                filter = filter.copy(since = sinceTime),
            )
        }
    }

    /**
     * All challenge events (like jesterui pattern).
     * No author/tag restriction - fetch everything, filter client-side.
     * This ensures we see open challenges and public games.
     */
    fun buildChallengeFilters(
        relays: Set<NormalizedRelayUrl>,
        sinceForRelay: (NormalizedRelayUrl) -> Long?,
        now: Long = TimeUtils.now(),
    ): List<RelayBasedFilter> {
        val filter =
            Filter(
                kinds = CHALLENGE_KINDS,
                limit = 100,
            )

        return relays.map { relay ->
            val sinceTime =
                sinceForRelay(relay)
                    ?: (now - ChessTimeWindows.CHALLENGE_WINDOW_SECONDS)
            RelayBasedFilter(
                relay = relay,
                filter = filter.copy(since = sinceTime),
            )
        }
    }

    /**
     * Active game events - game-specific subscriptions.
     * These filters ensure moves are received for games the user is playing.
     *
     * Note: Move d-tags are "gameId-moveNumber" so we can't filter by #d for moves.
     * We use two strategies:
     * 1. Filter by authors (opponent pubkeys) - catches moves they make
     * 2. Filter by #p tag (opponent tagged us) - catches moves tagged with us
     *
     * End events use gameId as d-tag so we can filter those directly.
     */
    fun buildActiveGameFilters(
        gameIds: Set<String>,
        userPubkey: String,
        opponentPubkeys: Set<String>,
        relays: Set<NormalizedRelayUrl>,
    ): List<RelayBasedFilter> {
        if (gameIds.isEmpty()) return emptyList()

        println("[ChessFilterBuilder] Building filters for ${gameIds.size} games, ${opponentPubkeys.size} opponents: $opponentPubkeys")

        val filters = mutableListOf<RelayBasedFilter>()

        // End events: filter by d-tag (gameId)
        val endFilter =
            Filter(
                kinds = listOf(LiveChessGameEndEvent.KIND),
                tags = mapOf("d" to gameIds.toList()),
                limit = 50,
            )

        // Move events: filter by p-tag (opponent tagged us)
        // This catches all moves for games where we're a participant
        val moveFilterByTag =
            Filter(
                kinds = listOf(LiveChessMoveEvent.KIND),
                tags = mapOf("p" to listOf(userPubkey)),
                limit = 200,
            )

        relays.forEach { relay ->
            filters.add(RelayBasedFilter(relay = relay, filter = endFilter))
            filters.add(RelayBasedFilter(relay = relay, filter = moveFilterByTag))
        }

        // Move events: filter by authors (opponent pubkeys)
        // This directly catches moves made by our opponents
        if (opponentPubkeys.isNotEmpty()) {
            println("[ChessFilterBuilder] Adding author filter for opponents: $opponentPubkeys")
            val moveFilterByAuthor =
                Filter(
                    kinds = listOf(LiveChessMoveEvent.KIND),
                    authors = opponentPubkeys.toList(),
                    limit = 200,
                )

            relays.forEach { relay ->
                filters.add(RelayBasedFilter(relay = relay, filter = moveFilterByAuthor))
            }
        } else {
            println("[ChessFilterBuilder] WARNING: No opponent pubkeys provided!")
        }

        return filters
    }

    /**
     * Recent game events for spectating discovery.
     * Uses longer time window (7 days) to catch ongoing games.
     */
    fun buildRecentGameFilters(
        relays: Set<NormalizedRelayUrl>,
        sinceForRelay: (NormalizedRelayUrl) -> Long?,
        now: Long = TimeUtils.now(),
    ): List<RelayBasedFilter> {
        val filter =
            Filter(
                kinds = GAME_EVENT_KINDS,
                limit = 100,
            )

        return relays.map { relay ->
            val sinceTime =
                sinceForRelay(relay)
                    ?: (now - ChessTimeWindows.GAME_EVENT_WINDOW_SECONDS)
            RelayBasedFilter(
                relay = relay,
                filter = filter.copy(since = sinceTime),
            )
        }
    }

    // ===================================================
    // Simple filters for one-shot fetches (ChessRelayFetcher)
    // ===================================================

    /**
     * Filter for all events related to a specific game.
     * Used for one-shot fetch when loading a game.
     */
    fun gameEventsFilter(gameId: String): Filter =
        Filter(
            kinds = ALL_CHESS_KINDS + listOf(com.vitorpamplona.quartz.nip64Chess.LiveChessDrawOfferEvent.KIND),
            tags = mapOf("d" to listOf(gameId)),
            limit = 500,
        )

    /**
     * Filter for challenge events in the last 24 hours.
     * Used for one-shot fetch to populate lobby.
     */
    fun challengesFilter(userPubkey: String): Filter {
        val now = TimeUtils.now()
        return Filter(
            kinds = CHALLENGE_KINDS,
            since = now - ChessTimeWindows.CHALLENGE_WINDOW_SECONDS,
            limit = 100,
        )
    }

    /**
     * Filter for recent game activity for spectating discovery.
     * Fetches move events from the last 7 days.
     */
    fun recentGamesFilter(): Filter {
        val now = TimeUtils.now()
        return Filter(
            kinds = ALL_CHESS_KINDS,
            since = now - ChessTimeWindows.GAME_EVENT_WINDOW_SECONDS,
            limit = 200,
        )
    }
}
