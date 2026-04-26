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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.KeyDataSourceSubscription
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent

/**
 * Per-screen state for the per-room kind-7 reaction subscription.
 * Mirror of [RoomChatQueryState] / [RoomPresenceQueryState].
 */
@Stable
class RoomReactionsQueryState(
    val roomATag: String,
    val account: Account,
)

/**
 * REQs `kinds=[7], #a=[roomATag]` — the room's reaction stream the
 * floating-overlay UI consumes. Kind 9735 (zap receipts) is not yet
 * in the filter; the v1 overlay only renders kind-7 reactions.
 * Adding zaps later is a one-line filter change.
 */
class RoomReactionsFilterSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<RoomReactionsQueryState>,
) : PerUniqueIdEoseManager<RoomReactionsQueryState, String>(client, allKeys) {
    override fun updateFilter(
        key: RoomReactionsQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val relays = key.account.outboxRelays.flow.value
        return relays.map { relay ->
            RelayBasedFilter(
                relay = relay,
                filter =
                    Filter(
                        kinds = listOf(ReactionEvent.KIND),
                        tags = mapOf("a" to listOf(key.roomATag)),
                        since = since?.get(relay)?.time,
                    ),
            )
        }
    }

    override fun id(key: RoomReactionsQueryState): String = key.roomATag
}

@Stable
class RoomReactionsFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<RoomReactionsQueryState>() {
    private val sub = RoomReactionsFilterSubAssembler(client, ::allKeys)

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = sub.invalidateFilters()

    override fun destroy() = sub.destroy()
}

/**
 * Lifecycle-aware Composable subscription. Sub stays open while the
 * Composable is in the tree, then closes on dispose.
 */
@Composable
fun RoomReactionsFilterAssemblerSubscription(
    roomATag: String,
    accountViewModel: AccountViewModel,
) = RoomReactionsFilterAssemblerSubscription(
    roomATag,
    accountViewModel.account,
    accountViewModel.dataSources().roomReactions,
)

@Composable
fun RoomReactionsFilterAssemblerSubscription(
    roomATag: String,
    account: Account,
    filterAssembler: RoomReactionsFilterAssembler,
) {
    val state = remember(roomATag) { RoomReactionsQueryState(roomATag, account) }
    KeyDataSourceSubscription(state, filterAssembler)
}
