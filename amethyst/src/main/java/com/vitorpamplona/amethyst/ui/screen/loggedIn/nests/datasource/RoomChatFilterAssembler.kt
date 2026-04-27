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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.datasource

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
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent

/**
 * Per-screen state for the per-room kind-1311 live-chat
 * subscription. Mirror of [RoomPresenceQueryState] — the room's
 * `a`-tag is the address-pointer string (`30312:<host>:<roomId>`).
 */
@Stable
class RoomChatQueryState(
    val roomATag: String,
    val account: Account,
)

/**
 * REQs `kinds=[1311], #a=[roomATag]` against the user's outbox
 * relays so the chat panel sees every message tagged for this
 * room. Events arrive in [com.vitorpamplona.amethyst.model.LocalCache];
 * the room screen pipes them into `NestViewModel.onChatEvent`.
 */
class RoomChatFilterSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<RoomChatQueryState>,
) : PerUniqueIdEoseManager<RoomChatQueryState, String>(client, allKeys) {
    override fun updateFilter(
        key: RoomChatQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val relays = key.account.outboxRelays.flow.value
        return relays.map { relay ->
            RelayBasedFilter(
                relay = relay,
                filter =
                    Filter(
                        kinds = listOf(LiveActivitiesChatMessageEvent.KIND),
                        tags = mapOf("a" to listOf(key.roomATag)),
                        since = since?.get(relay)?.time,
                    ),
            )
        }
    }

    override fun id(key: RoomChatQueryState): String = key.roomATag
}

@Stable
class RoomChatFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<RoomChatQueryState>() {
    private val sub = RoomChatFilterSubAssembler(client, ::allKeys)

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = sub.invalidateFilters()

    override fun destroy() = sub.destroy()
}

/**
 * Lifecycle-aware Composable subscription. Sub stays open while the
 * Composable is in the tree, then closes on dispose.
 */
@Composable
fun RoomChatFilterAssemblerSubscription(
    roomATag: String,
    accountViewModel: AccountViewModel,
) = RoomChatFilterAssemblerSubscription(
    roomATag,
    accountViewModel.account,
    accountViewModel.dataSources().roomChat,
)

@Composable
fun RoomChatFilterAssemblerSubscription(
    roomATag: String,
    account: Account,
    filterAssembler: RoomChatFilterAssembler,
) {
    val state = remember(roomATag) { RoomChatQueryState(roomATag, account) }
    KeyDataSourceSubscription(state, filterAssembler)
}
