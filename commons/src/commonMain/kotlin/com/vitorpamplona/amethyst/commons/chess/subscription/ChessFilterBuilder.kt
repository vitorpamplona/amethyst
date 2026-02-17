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
package com.vitorpamplona.amethyst.commons.chess.subscription

import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip64Chess.JesterProtocol
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Shared filter builder for chess subscriptions using Jester protocol.
 * Used by both Android and Desktop to ensure identical subscription behavior.
 *
 * Jester Protocol (kind 30):
 * - All chess events use kind 30
 * - Start events: e-tag references START_POSITION_HASH
 * - Move events: e-tags [startEventId, headEventId]
 * - Content JSON determines event type (kind: 0=start, 1=move, 2=chat)
 *
 * Builds 4 types of filters:
 * 1. Personal filters - Events tagged with user's pubkey
 * 2. Start/Challenge filters - Events referencing START_POSITION_HASH
 * 3. Active game filters - Events referencing specific game startEventIds
 * 4. Recent game filters - For spectating discovery
 */
object ChessFilterBuilder {
    /** Jester protocol kind for all chess events */
    private const val JESTER_KIND = JesterProtocol.KIND

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

        // Filter 2: All start/challenge events (for lobby display)
        filters.addAll(
            buildStartEventFilters(state.relays, sinceForRelay, now),
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
     * Catches: challenges directed at us, moves in our games.
     */
    fun buildPersonalFilters(
        userPubkey: String,
        relays: Set<NormalizedRelayUrl>,
        sinceForRelay: (NormalizedRelayUrl) -> Long?,
        now: Long = TimeUtils.now(),
    ): List<RelayBasedFilter> {
        val filter =
            Filter(
                kinds = listOf(JESTER_KIND),
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
     * Start/challenge events (Jester pattern).
     * In Jester protocol, start events reference the START_POSITION_HASH.
     * This fetches all game starts for lobby display.
     */
    fun buildStartEventFilters(
        relays: Set<NormalizedRelayUrl>,
        sinceForRelay: (NormalizedRelayUrl) -> Long?,
        now: Long = TimeUtils.now(),
    ): List<RelayBasedFilter> {
        val filter =
            Filter(
                kinds = listOf(JESTER_KIND),
                tags = mapOf("e" to listOf(JesterProtocol.START_POSITION_HASH)),
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
     * In Jester protocol:
     * - Move events have e-tags: [startEventId, headEventId]
     * - We filter by the first e-tag (startEventId) to get all moves for a game
     * - We also filter by opponent authors and p-tag for redundancy
     */
    fun buildActiveGameFilters(
        startEventIds: Set<String>,
        userPubkey: String,
        opponentPubkeys: Set<String>,
        relays: Set<NormalizedRelayUrl>,
    ): List<RelayBasedFilter> {
        if (startEventIds.isEmpty()) return emptyList()

        val filters = mutableListOf<RelayBasedFilter>()

        // Game events: filter by e-tag (startEventId)
        // This catches all moves/events for these games
        val gameFilter =
            Filter(
                kinds = listOf(JESTER_KIND),
                tags = mapOf("e" to startEventIds.toList()),
                limit = 500,
            )

        relays.forEach { relay ->
            filters.add(RelayBasedFilter(relay = relay, filter = gameFilter))
        }

        // Also filter by p-tag (opponent tagged us) for redundancy
        val tagFilter =
            Filter(
                kinds = listOf(JESTER_KIND),
                tags = mapOf("p" to listOf(userPubkey)),
                limit = 200,
            )

        relays.forEach { relay ->
            filters.add(RelayBasedFilter(relay = relay, filter = tagFilter))
        }

        // Also filter by authors (opponent pubkeys) for redundancy
        if (opponentPubkeys.isNotEmpty()) {
            val authorFilter =
                Filter(
                    kinds = listOf(JESTER_KIND),
                    authors = opponentPubkeys.toList(),
                    limit = 200,
                )

            relays.forEach { relay ->
                filters.add(RelayBasedFilter(relay = relay, filter = authorFilter))
            }
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
                kinds = listOf(JESTER_KIND),
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
     *
     * In Jester protocol, all game events reference the startEventId via e-tag.
     */
    fun gameEventsFilter(startEventId: String): Filter =
        Filter(
            kinds = listOf(JESTER_KIND),
            tags = mapOf("e" to listOf(startEventId)),
            limit = 500,
        )

    /**
     * Filter for start/challenge events in the last 24 hours.
     * Used for one-shot fetch to populate lobby.
     *
     * Start events reference the START_POSITION_HASH.
     */
    fun challengesFilter(userPubkey: String? = null): Filter {
        val now = TimeUtils.now()
        return Filter(
            kinds = listOf(JESTER_KIND),
            tags = mapOf("e" to listOf(JesterProtocol.START_POSITION_HASH)),
            since = now - ChessTimeWindows.CHALLENGE_WINDOW_SECONDS,
            limit = 100,
        )
    }

    /**
     * Filter for recent game activity for spectating discovery.
     * Fetches events from the last 7 days.
     */
    fun recentGamesFilter(): Filter {
        val now = TimeUtils.now()
        return Filter(
            kinds = listOf(JESTER_KIND),
            since = now - ChessTimeWindows.GAME_EVENT_WINDOW_SECONDS,
            limit = 200,
        )
    }

    /**
     * Filter for user's own chess events (events they authored).
     * Used to discover games the user is participating in.
     */
    fun userGamesFilter(userPubkey: String): Filter {
        val now = TimeUtils.now()
        return Filter(
            kinds = listOf(JESTER_KIND),
            authors = listOf(userPubkey),
            since = now - ChessTimeWindows.GAME_EVENT_WINDOW_SECONDS,
            limit = 200,
        )
    }

    /**
     * Filter for events tagged with user's pubkey (games they're participating in).
     * Complements userGamesFilter to find games where user is the opponent.
     */
    fun userTaggedFilter(userPubkey: String): Filter {
        val now = TimeUtils.now()
        return Filter(
            kinds = listOf(JESTER_KIND),
            tags = mapOf("p" to listOf(userPubkey)),
            since = now - ChessTimeWindows.GAME_EVENT_WINDOW_SECONDS,
            limit = 200,
        )
    }
}
