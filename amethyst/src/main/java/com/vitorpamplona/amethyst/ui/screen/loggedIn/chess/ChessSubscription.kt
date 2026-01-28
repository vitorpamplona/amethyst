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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.service.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessMoveEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Default "since" window for challenges (24 hours)
 * Challenges older than this are considered expired anyway
 */
private const val CHALLENGE_WINDOW_SECONDS = 24 * 60 * 60L

/**
 * Default "since" window for game events when no EOSE cache exists (7 days)
 * This prevents loading ancient game history on first connection
 */
private const val GAME_EVENT_WINDOW_SECONDS = 7 * 24 * 60 * 60L

/**
 * Subscribe to chess events when the Chess tab is active.
 * Respects the Global/Follows filter from the discovery top bar.
 */
@Composable
fun ChessSubscription(
    accountViewModel: AccountViewModel,
    chessViewModel: ChessViewModel,
) {
    // Observe the current filter selection (Global, All Follows, etc.)
    val filterSelection by accountViewModel.account.settings.defaultDiscoveryFollowList
        .collectAsStateWithLifecycle()

    val isGlobal = filterSelection == GLOBAL_FOLLOWS

    // Get active game IDs from the view model for game-specific subscriptions
    val activeGames by chessViewModel.activeGames.collectAsStateWithLifecycle()
    val activeGameIds = activeGames.keys

    val state =
        remember(accountViewModel.account, isGlobal, activeGameIds) {
            ChessQueryState(
                userPubkey = accountViewModel.account.userProfile().pubkeyHex,
                // Use notification inbox relays - where others send events TO us
                inboxRelays = accountViewModel.account.notificationRelays.flow.value,
                // Use proxy relays for global, outbox relays for follows
                globalRelays =
                    if (isGlobal) {
                        accountViewModel.account.defaultGlobalRelays.flow.value
                    } else {
                        accountViewModel.account.followOutboxesOrProxy.flow.value
                    },
                isGlobal = isGlobal,
                activeGameIds = activeGameIds,
            )
        }

    DisposableEffect(state) {
        accountViewModel.dataSources().chess.subscribe(state)
        chessViewModel.refreshChallenges()
        onDispose {
            accountViewModel.dataSources().chess.unsubscribe(state)
        }
    }
}

/**
 * Query state for chess subscription
 */
data class ChessQueryState(
    val userPubkey: String,
    val inboxRelays: Set<NormalizedRelayUrl>,
    val globalRelays: Set<NormalizedRelayUrl>,
    val isGlobal: Boolean,
    val activeGameIds: Set<String> = emptySet(),
)

/**
 * Filter assembler for chess events
 */
class ChessFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<ChessQueryState>() {
    val group =
        listOf(
            ChessFeedFilterSubAssembler(client, ::allKeys),
        )

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

/**
 * Sub-assembler that creates the actual relay filters
 */
class ChessFeedFilterSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChessQueryState>,
) : PerUniqueIdEoseManager<ChessQueryState, String>(client, allKeys) {
    override fun updateFilter(
        key: ChessQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> = filterChessEvents(key, since)

    override fun id(key: ChessQueryState): String = "chess-${key.userPubkey}-${key.isGlobal}-${key.activeGameIds.hashCode()}"
}

/**
 * Create relay filters for chess events.
 *
 * Creates filters based on the Global/Follows setting:
 * - Global: Fetch all chess events from global relays
 * - Follows: Fetch from followed users' outbox relays
 *
 * Also always fetches challenges directed at the user from inbox relays.
 *
 * Improvements over basic implementation:
 * 1. Default "since" timestamps to avoid loading very old events on first connection
 * 2. Separate challenge filter (shorter window) from game events (longer window)
 * 3. Game-specific subscriptions for active games using #a tag references
 * 4. Grouped filters by relay to reduce subscription overhead
 */
fun filterChessEvents(
    key: ChessQueryState,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val filters = mutableListOf<RelayBasedFilter>()
    val now = TimeUtils.now()

    // Challenge kinds only (for lobby display)
    val challengeKinds =
        listOf(
            LiveChessGameChallengeEvent.KIND,
            LiveChessGameAcceptEvent.KIND,
        )

    // Move/end kinds (for active games)
    val gameEventKinds =
        listOf(
            LiveChessMoveEvent.KIND,
            LiveChessGameEndEvent.KIND,
        )

    // All chess kinds
    val allChessKinds = challengeKinds + gameEventKinds

    // ========================================
    // Filter 1: Personal challenges from inbox relays
    // Uses 24h window for challenges (they expire anyway)
    // ========================================
    val inboxFilter =
        Filter(
            kinds = allChessKinds,
            tags = mapOf("p" to listOf(key.userPubkey)),
            limit = 100,
        )

    for (relay in key.inboxRelays) {
        val sinceTime = since?.get(relay)?.time ?: (now - CHALLENGE_WINDOW_SECONDS)
        filters.add(
            RelayBasedFilter(
                relay = relay,
                filter = inboxFilter.copy(since = sinceTime),
            ),
        )
    }

    // ========================================
    // Filter 2: General challenges from global/outbox relays
    // Uses 24h window to show recent open challenges
    // ========================================
    val globalChallengeFilter =
        Filter(
            kinds = challengeKinds,
            limit = 50,
        )

    for (relay in key.globalRelays) {
        val sinceTime = since?.get(relay)?.time ?: (now - CHALLENGE_WINDOW_SECONDS)
        filters.add(
            RelayBasedFilter(
                relay = relay,
                filter = globalChallengeFilter.copy(since = sinceTime),
            ),
        )
    }

    // ========================================
    // Filter 3: Active game events
    // Uses longer window (7 days) or no window for active games
    // Follows jesterui pattern: subscribe to game-specific events via #d tag
    // ========================================
    if (key.activeGameIds.isNotEmpty()) {
        // Create filters for each active game's events
        // Using #d tag to match the game_id in addressable events
        val gameSpecificFilter =
            Filter(
                kinds = gameEventKinds,
                tags = mapOf("d" to key.activeGameIds.toList()),
                limit = 200, // More moves per game
            )

        // Query all relays for active game events (no since - need full history)
        val allRelays = key.inboxRelays + key.globalRelays
        for (relay in allRelays) {
            filters.add(
                RelayBasedFilter(
                    relay = relay,
                    filter = gameSpecificFilter,
                ),
            )
        }
    } else {
        // No active games - use general game event filter with window
        val generalGameFilter =
            Filter(
                kinds = gameEventKinds,
                limit = 100,
            )

        for (relay in key.globalRelays) {
            val sinceTime = since?.get(relay)?.time ?: (now - GAME_EVENT_WINDOW_SECONDS)
            filters.add(
                RelayBasedFilter(
                    relay = relay,
                    filter = generalGameFilter.copy(since = sinceTime),
                ),
            )
        }
    }

    return filters
}
