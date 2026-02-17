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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chess.subscription.ChessFilterBuilder
import com.vitorpamplona.amethyst.commons.chess.subscription.ChessSubscriptionState
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.KeyDataSourceSubscription
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Subscribe to chess events when the Chess screen is active.
 *
 * Uses Amethyst's subscription system to fetch events into LocalCache,
 * then triggers ViewModel refresh to process them.
 */
@Composable
fun ChessSubscription(
    accountViewModel: AccountViewModel,
    chessViewModel: ChessViewModelNew,
) {
    // Get active game IDs from the view model for game-specific subscriptions
    val activeGames by chessViewModel.activeGames.collectAsStateWithLifecycle()
    val spectatingGames by chessViewModel.spectatingGames.collectAsStateWithLifecycle()
    val activeGameIds = activeGames.keys + spectatingGames.keys

    // Extract opponent pubkeys using stable keys (avoid recomposition from LiveChessGameState identity)
    val opponentPubkeys =
        remember(activeGameIds) {
            activeGames.values.map { it.opponentPubkey }.toSet()
        }

    val state =
        remember(accountViewModel.account, activeGameIds, opponentPubkeys) {
            ChessQueryState(
                userPubkey = accountViewModel.account.userProfile().pubkeyHex,
                // Use notification inbox relays - where others send events TO us
                inboxRelays = accountViewModel.account.notificationRelays.flow.value,
                // Always use global relays for chess - we want to see ALL games
                globalRelays = accountViewModel.account.defaultGlobalRelays.flow.value,
                // Always treat as global for chess
                isGlobal = true,
                activeGameIds = activeGameIds,
                opponentPubkeys = opponentPubkeys,
            )
        }

    // Register subscription with Amethyst's subscription system
    KeyDataSourceSubscription(state, accountViewModel.dataSources().chess)

    // Trigger ViewModel refresh when subscription state changes
    // This fetches challenges from LocalCache after events arrive
    DisposableEffect(state) {
        chessViewModel.forceRefresh()
        onDispose {
            // Subscription cleanup handled by KeyDataSourceSubscription
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
    val opponentPubkeys: Set<String> = emptySet(),
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
 * Create relay filters for chess events using the shared [ChessFilterBuilder].
 *
 * Following jesterui's approach:
 * 1. Fetch ALL challenges from ALL connected relays (no author/tag restriction)
 * 2. Filter client-side based on user's preferences (Global/Follows)
 * 3. Game-specific subscriptions for active games
 *
 * This ensures we see:
 * - Open challenges from anyone
 * - Challenges directed at us
 * - Public games to spectate
 */
fun filterChessEvents(
    key: ChessQueryState,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    // Convert Android ChessQueryState to shared ChessSubscriptionState
    val state =
        ChessSubscriptionState(
            userPubkey = key.userPubkey,
            relays = key.inboxRelays + key.globalRelays,
            activeGameIds = key.activeGameIds,
            opponentPubkeys = key.opponentPubkeys,
        )

    // Use shared filter builder for consistent behavior with Desktop
    return ChessFilterBuilder.buildAllFilters(state) { relay ->
        since?.get(relay)?.time
    }
}
