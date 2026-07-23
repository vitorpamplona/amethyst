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

import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmChannels
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmRegistry
import com.vitorpamplona.amethyst.commons.model.privateChats.DmHistoryTuning
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.paging.WindowLoadTracker
import com.vitorpamplona.amethyst.commons.relayClient.paging.trackingListener
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.GroupTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.StateFlow

/** One screen's request to keep the viewer's Buzz DM channels' recent chat live. */
class BuzzDmJoinedChatTailQueryState(
    val account: Account,
)

/**
 * Always-on **live tail** for the recent chat of every Buzz DM channel the viewer belongs to — the DM
 * analog of [RelayGroupJoinedChatTailFilterAssembler]. A Buzz DM is a relay-authoritative NIP-29 group
 * (UUID `h`), so its messages ride the exact same `#h`-scoped batched tail; only the channel *source*
 * differs — [BuzzDmChannels] (populated by
 * [com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.BuzzDmDiscoveryPreload]) rather than the published
 * kind-10009 group list, because DM memberships are server-side and deliberately never published.
 *
 * Keeping these warm app-wide is what lets a Buzz DM surface on the Notifications tab and in push without
 * the viewer opening the conversation. Hidden DMs (per the 30622 snapshot in [BuzzDmRegistry]) are
 * excluded so a hidden conversation neither streams nor notifies.
 */
class BuzzDmJoinedChatTailFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<BuzzDmJoinedChatTailQueryState>() {
    val tail = BuzzDmJoinedChatTailSubAssembler(client, ::allKeys)

    val group = listOf(tail)

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class BuzzDmJoinedChatTailSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<BuzzDmJoinedChatTailQueryState>,
) : PerUniqueIdEoseManager<BuzzDmJoinedChatTailQueryState, Account>(client, allKeys) {
    private val windowLoad = WindowLoadTracker("buzzDm.preview.live")
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    override fun updateFilter(
        key: BuzzDmJoinedChatTailQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val me = key.account.userProfile().pubkeyHex
        val hidden = BuzzDmRegistry.hiddenFor(me)
        val channels = BuzzDmChannels.channelsFor(me).filterKeys { it !in hidden }
        if (channels.isEmpty()) {
            windowLoad.setExpectedRelays(emptySet())
            return null
        }

        // Reuse the joined-group tail builder: one #h filter per host relay carrying every DM channel id
        // on it, bounded by the shared recent floor (no per-channel limit, reconnect-safe).
        val asTags = channels.map { (channelId, relay) -> GroupTag(channelId, relay.url) }
        val filters = buildRelayGroupJoinedChatTailFilters(asTags, DmHistoryTuning.recentBoundary())
        windowLoad.setExpectedRelays(filters.mapTo(mutableSetOf()) { it.relay })
        return filters
    }

    override fun id(key: BuzzDmJoinedChatTailQueryState) = key.account

    override fun newSub(key: BuzzDmJoinedChatTailQueryState): Subscription {
        windowLoad.startLoading(key.account.scope)
        return requestNewSubscription(
            windowLoad.trackingListener { relay: NormalizedRelayUrl, filters -> newEose(key, relay, TimeUtils.now(), filters) },
        )
    }
}
