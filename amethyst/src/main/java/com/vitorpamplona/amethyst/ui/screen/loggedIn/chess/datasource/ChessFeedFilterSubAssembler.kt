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

import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerKeyEoseManager
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils

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
 * Sub-assembler that creates the actual relay filters.
 * Intercepts incoming chess events from relays and forwards them to the ViewModel.
 */
class ChessFeedFilterSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChessQueryState>,
) : PerKeyEoseManager<ChessQueryState, String>(client, allKeys) {
    var onChessEvent: ((Event) -> Unit)? = null

    override fun updateFilter(
        queryState: ChessQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> = filterChessEvents(queryState, since)

    override fun extractKey(queryState: ChessQueryState): String = queryState.userPubkey

    override fun newSub(queryState: ChessQueryState): Subscription =
        requestNewSubscription(
            object : SubscriptionListener {
                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    newEose(queryState, relay, TimeUtils.now(), forFilters)
                }

                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (isLive) {
                        newEose(queryState, relay, TimeUtils.now(), forFilters)
                    }
                    onChessEvent?.invoke(event)
                }
            },
        )
}
