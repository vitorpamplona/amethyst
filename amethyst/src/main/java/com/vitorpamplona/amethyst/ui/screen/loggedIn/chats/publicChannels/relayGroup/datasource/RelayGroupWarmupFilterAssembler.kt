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

import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.datasource.subassemblies.filterMetadataToRelayGroup
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent

/** One on-screen group card's request to warm a single group. */
class RelayGroupWarmupQueryState(
    val channel: RelayGroupChannel,
    /** When true, prefetch only recent content — the caller's screen already streams metadata. */
    val contentOnly: Boolean = false,
    /** How many recent content events to prefetch ahead of a tap. */
    val contentLimit: Int = RELAY_GROUP_WARMUP_LIMIT,
)

/** Newest content kinds we prefetch so opening the card lands on populated screens. */
private val RELAY_GROUP_WARMUP_CONTENT_KINDS =
    listOf(ChatEvent.KIND, PollEvent.KIND, ThreadEvent.KIND, CommentEvent.KIND)

/**
 * Default number of recent events to pull ahead of a tap — enough to fill the first screen AND drive
 * the discovery card's "50+ messages" activity signal (a chat that returns the full page reads as
 * "50+"; fewer shows the exact loaded count). Callers that only need a first-screen preview (e.g. a
 * relay's channel list) pass a smaller [RelayGroupWarmupQueryState.contentLimit].
 */
const val RELAY_GROUP_WARMUP_LIMIT = 50

/**
 * Warms a NIP-29 group referenced inline (a group-link card) without opening it:
 * keeps the relay-signed metadata (name/picture/about + admins/members/roles) live
 * so the card fills in and stays current, and prefetches the newest handful of chat
 * messages and threads so tapping the card lands on already-cached content. Both are
 * pinned to the group's single host relay. Active only while the card is on-screen.
 */
class RelayGroupWarmupFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<RelayGroupWarmupQueryState>() {
    val group =
        listOf(
            RelayGroupWarmupSubAssembler(client, ::allKeys),
        )

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class RelayGroupWarmupSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<RelayGroupWarmupQueryState>,
) : PerUniqueIdEoseManager<RelayGroupWarmupQueryState, GroupId>(client, allKeys) {
    override fun updateFilter(
        key: RelayGroupWarmupQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val groupId = key.channel.groupId
        val metadata = if (key.contentOnly) emptyList() else filterMetadataToRelayGroup(key.channel, since)
        return metadata +
            RelayBasedFilter(
                relay = groupId.relayUrl,
                filter =
                    Filter(
                        kinds = RELAY_GROUP_WARMUP_CONTENT_KINDS,
                        tags = mapOf(GroupIdTag.TAG_NAME to listOf(groupId.id)),
                        limit = key.contentLimit,
                        since = since?.get(groupId.relayUrl)?.time,
                    ),
            )
    }

    override fun id(key: RelayGroupWarmupQueryState) = key.channel.groupId
}
