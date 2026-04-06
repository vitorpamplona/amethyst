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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.marmot

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * EOSE manager for Marmot GroupEvent (kind:445) subscriptions.
 *
 * Uses filters from [MarmotSubscriptionManager.buildFilters()] which
 * include per-group kind:445 filters. These filters are sent to the
 * group's relays on connect/reconnect and when groups are added/removed.
 */
class MarmotGroupEventsEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val manager = key.account.marmotManager ?: return emptyList()
        if (!key.account.isWriteable()) return emptyList()

        // Build Marmot filters (kind:445 per group + kind:30443 own key packages)
        val marmotFilters = manager.subscriptionManager.activeGroupFilters()
        val ownKeyPackageFilter = manager.subscriptionManager.ownKeyPackageFilter()
        val allFilters = marmotFilters + ownKeyPackageFilter

        // Send to the home relays (where group events are relayed)
        val relays = key.account.homeRelays.flow.value
        if (relays.isEmpty()) return emptyList()

        return relays.flatMap { relay ->
            allFilters.map { filter ->
                RelayBasedFilter(
                    relay = relay,
                    filter = filter,
                )
            }
        }
    }

    val userJobMap = mutableMapOf<User, List<Job>>()

    override fun newSub(key: AccountQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.homeRelays.flow.collect {
                        invalidateFilters()
                    }
                },
            )

        return super.newSub(key)
    }

    override fun endSub(
        key: User,
        subId: String,
    ) {
        super.endSub(key, subId)
        userJobMap[key]?.forEach { it.cancel() }
    }
}
