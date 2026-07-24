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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource

import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.model.privateChats.DmHistoryTuning
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.paging.WindowLoadTracker
import com.vitorpamplona.amethyst.commons.relayClient.paging.trackingListener
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.launchChatFeedToggleObserver
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

/** One screen's request to keep the user's joined groups' recent chat live. */
class RelayGroupJoinedChatTailQueryState(
    val account: Account,
)

/**
 * Always-on **live tail** for the recent chat of every NIP-29 group the user has joined — the group
 * analog of the NIP-04 rooms-list live tail
 * ([com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.datasource.ChatroomListNip04SubAssembler]).
 *
 * One `#h`-scoped filter **per host relay** carries every joined group id on that relay at once,
 * `since = recentBoundary()` and **no per-group limit** — a time floor bounds it, so unlike the fixed
 * `limit=50` window it can batch all of a relay's groups into a single REQ. This keeps the Messages-list
 * previews reflecting the true newest message and joined groups' recent chat warm in cache without
 * opening each one. Older history is the [RelayGroupOpenChatHistorySubAssembler]'s job (below the floor).
 *
 * Batching by a shared time floor (rather than a per-group `limit` + shared per-relay EOSE `since`) is
 * what makes this both correct — every joined group is covered the moment it joins — and reconnect-safe:
 * a reconnect re-issues one `since=window` REQ per relay, never a per-group page replay.
 */
class RelayGroupJoinedChatTailFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<RelayGroupJoinedChatTailQueryState>() {
    val tail = RelayGroupJoinedChatTailSubAssembler(client, ::allKeys)

    val group = listOf(tail)

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class RelayGroupJoinedChatTailSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<RelayGroupJoinedChatTailQueryState>,
) : PerUniqueIdEoseManager<RelayGroupJoinedChatTailQueryState, Account>(client, allKeys) {
    override val subscriptionReason get() = "Your group chats"

    private val windowLoad = WindowLoadTracker("relayGroup.preview.live")
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    override fun updateFilter(
        key: RelayGroupJoinedChatTailQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        if (!key.account.settings.isChatFeedEnabled(ChatFeedType.NIP29)) {
            windowLoad.setExpectedRelays(emptySet())
            return null
        }
        val joined = key.account.relayGroupList.liveRelayGroupList.value
        if (joined.isEmpty()) {
            windowLoad.setExpectedRelays(emptySet())
            return null
        }

        // One #h filter per host relay carrying every joined group id on it; bounded by the shared
        // recent-tail floor, so no per-group limit and no per-group re-subscribe on join.
        val filters = buildRelayGroupJoinedChatTailFilters(joined, DmHistoryTuning.recentBoundary())
        windowLoad.setExpectedRelays(filters.mapTo(mutableSetOf()) { it.relay })
        return filters
    }

    override fun id(key: RelayGroupJoinedChatTailQueryState) = key.account

    private val toggleJobs = mutableMapOf<Account, Job>()

    override fun newSub(key: RelayGroupJoinedChatTailQueryState): Subscription {
        windowLoad.startLoading(key.account.scope)
        toggleJobs.remove(key.account)?.cancel()
        toggleJobs[key.account] =
            key.account.scope.launchChatFeedToggleObserver(key.account, ChatFeedType.NIP29) { invalidateFilters() }
        return requestNewSubscription(
            windowLoad.trackingListener { relay: NormalizedRelayUrl, filters -> newEose(key, relay, TimeUtils.now(), filters) },
        )
    }

    override fun endSub(
        key: Account,
        subId: String,
    ) {
        super.endSub(key, subId)
        toggleJobs.remove(key)?.cancel()
    }
}
