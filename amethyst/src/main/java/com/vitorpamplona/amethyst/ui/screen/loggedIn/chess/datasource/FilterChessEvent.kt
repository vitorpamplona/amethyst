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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chess.datasource

import com.vitorpamplona.amethyst.commons.chess.subscription.ChessFilterBuilder
import com.vitorpamplona.amethyst.commons.chess.subscription.ChessSubscriptionState
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

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
