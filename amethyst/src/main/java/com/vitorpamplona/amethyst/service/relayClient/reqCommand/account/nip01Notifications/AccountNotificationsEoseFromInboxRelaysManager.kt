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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip01Notifications

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

class AccountNotificationsEoseFromInboxRelaysManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    /**
     * Downloads most notifications from the user's own inbox relays.
     * But also connects to all the follows relays to check for new notifications that are not in the user's
     * own inbox.
     */
    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val inbox =
            key.account.notificationRelays.flow.value.flatMap {
                filterSummaryNotificationsToPubkey(
                    relay = it,
                    pubkey = user(key).pubkeyHex,
                    since = since?.get(it)?.time ?: TimeUtils.oneWeekAgo(),
                ) +
                    filterNotificationsToPubkey(
                        relay = it,
                        pubkey = user(key).pubkeyHex,
                        since = since?.get(it)?.time ?: key.feedContentStates.notifications.lastNoteCreatedAtIfFilled() ?: TimeUtils.oneWeekAgo(),
                    )
            }

        // NIP-29 group activity (reactions/replies to my messages) lives on the group's host relay,
        // not my inbox relays — poll each host relay for those, scoped by `#h` to my joined groups.
        val groups =
            key.account.relayGroupList.liveRelayGroupList.value
                .groupBy({ it.relayUrl }, { it.groupId })
                .flatMap { (relayUrl, groupIds) ->
                    val relay = RelayUrlNormalizer.normalizeOrNull(relayUrl) ?: return@flatMap emptyList()
                    filterGroupNotificationsToPubkey(
                        relay = relay,
                        pubkey = user(key).pubkeyHex,
                        groupIds = groupIds.distinct(),
                        since = since?.get(relay)?.time ?: TimeUtils.oneWeekAgo(),
                    )
                }

        return inbox + groups
    }

    val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: AccountQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.notificationRelays.flow.sample(1000).collectLatest {
                        invalidateFilters()
                    }
                },
                // Re-subscribe when I join/leave a group so its host relay is added to (or dropped
                // from) the notification query.
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.relayGroupList.liveRelayGroupList.sample(1000).collectLatest {
                        invalidateFilters()
                    }
                },
                key.account.scope.launch(Dispatchers.IO) {
                    key.feedContentStates.notifications.lastNoteCreatedAtWhenFullyLoaded.sample(5000).collectLatest {
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
