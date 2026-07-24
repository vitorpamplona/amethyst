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

import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmChannels
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmRegistry
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
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
    override val subscriptionReason get() = "Your notifications"

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
        // Backward-paging boundary: once the feed has filled a page, ask for everything older than
        // its oldest card. It stays null until then — see the note on the missing week floor below,
        // which is what let it stay null forever on a quiet inbox.
        val pagingBoundary = key.feedContentStates.notifications.lastNoteCreatedAtIfFilled()

        val inbox =
            key.account.notificationRelays.flow.value.flatMap {
                // No `since` floor on the first fetch. These filters are scoped by `#p` to my own
                // key and carry a relay-side `limit`, so an all-time query costs one index scan and
                // returns at most `limit` events, newest first — exactly what Home does (it passes
                // `since ?: boundary`, i.e. null on a cold start).
                //
                // This used to fall back to `oneWeekAgo()`, which silently emptied the tab for
                // anyone whose last mention was older than a week: EOSE `since` is in-memory only,
                // so EVERY cold start re-pinned the window to 7 days, and the paging boundary above
                // could never rescue it — it only arms once the feed holds a full page, and the feed
                // could not fill because the query only ever asked for a week. A fresh install of an
                // established account hit the same deadlock.
                val notificationSince = since?.get(it)?.time ?: pagingBoundary

                filterSummaryNotificationsToPubkey(
                    relay = it,
                    pubkey = user(key).pubkeyHex,
                    since = notificationSince,
                ) +
                    filterNotificationsToPubkey(
                        relay = it,
                        pubkey = user(key).pubkeyHex,
                        since = notificationSince,
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
                        // Same reasoning as the inbox filters above: `#p` + `#h` + `limit = 200`
                        // already bound this, so a week floor only hides older group activity.
                        since = since?.get(relay)?.time ?: pagingBoundary,
                    )
                }

        // Buzz DM channels are NOT in the published group list (membership is server-side, tracked in
        // BuzzDmChannels), so the joined-group query above skips them. Poll each DM host relay the same
        // way — `#p` = me + `#h` = my DM channels — so a reaction/zap/repost on my DM message surfaces
        // in notifications, not only as a chip inside the open conversation. Hidden DMs are excluded.
        val myPubkey = user(key).pubkeyHex
        val hiddenDms = BuzzDmRegistry.hiddenFor(myPubkey)
        val dmGroups =
            BuzzDmChannels
                .channelsFor(myPubkey)
                .filterKeys { it !in hiddenDms }
                .entries
                .groupBy({ it.value }, { it.key })
                .flatMap { (relay, channelIds) ->
                    filterGroupNotificationsToPubkey(
                        relay = relay,
                        pubkey = myPubkey,
                        groupIds = channelIds.distinct(),
                        since = since?.get(relay)?.time ?: pagingBoundary,
                    )
                }

        return inbox + groups + dmGroups
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
                // Re-subscribe when a Buzz DM is discovered/hidden so its host relay is polled for
                // reactions/zaps on my DM messages.
                key.account.scope.launch(Dispatchers.IO) {
                    BuzzDmChannels.flow.sample(1000).collectLatest { invalidateFilters() }
                },
                key.account.scope.launch(Dispatchers.IO) {
                    BuzzDmRegistry.hidden.sample(1000).collectLatest { invalidateFilters() }
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
