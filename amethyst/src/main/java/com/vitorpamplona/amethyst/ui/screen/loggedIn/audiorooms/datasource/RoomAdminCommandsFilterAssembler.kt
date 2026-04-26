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
import com.vitorpamplona.quartz.experimental.audiorooms.admin.AdminCommandEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

/**
 * Per-screen state for the per-room kind-4312 admin command sub.
 * Filtered by both `#a` (the room) and `#p` (the local pubkey) so
 * the relay only sends commands actually targeting the local user.
 * Authority enforcement happens client-side in the room screen.
 */
@Stable
class RoomAdminCommandsQueryState(
    val roomATag: String,
    val localPubkey: String,
    val account: Account,
)

class RoomAdminCommandsFilterSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<RoomAdminCommandsQueryState>,
) : PerUniqueIdEoseManager<RoomAdminCommandsQueryState, String>(client, allKeys) {
    override fun updateFilter(
        key: RoomAdminCommandsQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val relays = key.account.outboxRelays.flow.value
        return relays.map { relay ->
            RelayBasedFilter(
                relay = relay,
                filter =
                    Filter(
                        kinds = listOf(AdminCommandEvent.KIND),
                        tags = mapOf("a" to listOf(key.roomATag), "p" to listOf(key.localPubkey)),
                        since = since?.get(relay)?.time,
                    ),
            )
        }
    }

    override fun id(key: RoomAdminCommandsQueryState): String = "${key.roomATag}|${key.localPubkey}"
}

@Stable
class RoomAdminCommandsFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<RoomAdminCommandsQueryState>() {
    private val sub = RoomAdminCommandsFilterSubAssembler(client, ::allKeys)

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = sub.invalidateFilters()

    override fun destroy() = sub.destroy()
}

@Composable
fun RoomAdminCommandsFilterAssemblerSubscription(
    roomATag: String,
    localPubkey: String,
    accountViewModel: AccountViewModel,
) = RoomAdminCommandsFilterAssemblerSubscription(
    roomATag,
    localPubkey,
    accountViewModel.account,
    accountViewModel.dataSources().roomAdminCommands,
)

@Composable
fun RoomAdminCommandsFilterAssemblerSubscription(
    roomATag: String,
    localPubkey: String,
    account: Account,
    filterAssembler: RoomAdminCommandsFilterAssembler,
) {
    val state = remember(roomATag, localPubkey) { RoomAdminCommandsQueryState(roomATag, localPubkey, account) }
    KeyDataSourceSubscription(state, filterAssembler)
}
