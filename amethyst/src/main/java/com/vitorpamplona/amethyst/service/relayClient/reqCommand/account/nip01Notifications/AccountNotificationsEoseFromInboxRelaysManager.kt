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
        // A cold-start floor, NOT paging — backward paging lives in
        // [AccountNotificationsHistoryEoseManager]. Read only when a relay has no EOSE yet; once it
        // does, the EOSE time wins and this is never consulted.
        //
        // `since` means *newer than*, so this floors the query at the depth the feed already reaches
        // rather than asking all-time again. It stays null until the feed holds a full page — and is
        // therefore always null for an account with no UI, since nothing fills that feed.
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

        // NIP-29 group activity (reactions/replies to my messages) is deliberately NOT requested here.
        // It lives on the group's host relay and used to be one `#h` filter per relay carrying every
        // joined group id — but this subscription also carries the inbox filters above, which have no
        // `#h` at all, and `block/buzz` downgrades any subscription with a channel-less (or multi-
        // channel) filter to "global", a class that by design never receives channel-scoped events. So
        // those filters answered the stored query at EOSE and then went deaf until the next launch.
        //
        // Group and Buzz-DM activity now rides the per-channel subscriptions that are already scoped to
        // exactly one channel — RelayGroupJoinedChatTailSubAssembler and BuzzDmJoinedChatTailSubAssembler,
        // both mounted app-wide from LoggedInPage, so coverage is unchanged and delivery is live.
        return inbox
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
                // No group/Buzz-DM watchers here any more: those filters moved to the per-channel
                // subscriptions, which mount and unmount with the channel itself.
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
