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
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.StateFlow

/** Timeline kinds shown in a group's chat — chat messages and polls. */
private val RELAY_GROUP_TIMELINE_KINDS = listOf(ChatEvent.KIND, PollEvent.KIND)

/** One open NIP-29 group whose recent chat the screen wants live. */
class RelayGroupChatTailQueryState(
    val account: Account,
    val groupId: GroupId,
)

/**
 * Per-open-group **live tail**: the recent chat window of the *currently open* group, `#h`-scoped on
 * its host relay, `since = recentBoundary()`. The group analog of the NIP-04 per-conversation live tail.
 *
 * The batched [RelayGroupPreviewTailFilterAssembler] already covers every *joined* group; this exists so
 * an open **non-joined** group (opened by link before joining, not in `liveRelayGroupList`) still gets
 * its recent messages and live updates. For a joined open group it simply overlaps the batched tail
 * (harmless — events dedup by id on ingest). Older history is the [RelayGroupChatHistorySubAssembler]'s job.
 */
class RelayGroupChatTailFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<RelayGroupChatTailQueryState>() {
    val tail = RelayGroupChatTailSubAssembler(client, ::allKeys)

    val group = listOf(tail)

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class RelayGroupChatTailSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<RelayGroupChatTailQueryState>,
) : PerUniqueIdEoseManager<RelayGroupChatTailQueryState, GroupId>(client, allKeys) {
    private val windowLoad = WindowLoadTracker("relayGroup.chat.live")
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    override fun updateFilter(
        key: RelayGroupChatTailQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val relay = key.groupId.relayUrl
        windowLoad.setExpectedRelays(setOf(relay))
        return listOf(
            RelayBasedFilter(
                relay = relay,
                filter =
                    Filter(
                        kinds = RELAY_GROUP_TIMELINE_KINDS,
                        tags = mapOf(GroupIdTag.TAG_NAME to listOf(key.groupId.id)),
                        since = DmHistoryTuning.recentBoundary(),
                    ),
            ),
        )
    }

    override fun id(key: RelayGroupChatTailQueryState) = key.groupId

    override fun newSub(key: RelayGroupChatTailQueryState): Subscription {
        windowLoad.startLoading(key.account.scope)
        return requestNewSubscription(
            windowLoad.trackingListener { relay: NormalizedRelayUrl, filters -> newEose(key, relay, TimeUtils.now(), filters) },
        )
    }
}
