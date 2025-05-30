/**
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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.mixChatsLive

import com.vitorpamplona.amethyst.model.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.PublicChatChannel
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.ChannelFinderQueryState
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.nip28PublicChats.filterChannelMetadataUpdatesById
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.nip53LiveActivities.filterLiveStreamUpdatesByAddress
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime

/**
 * This assembler observes modifications to the LiveActivity root events
 * since they are replaceable and updates to the public chat metadata events.
 *
 * They are all cramped as multiple filters in a single subscription with
 * only one EOSE for everybody.
 */
class ChannelMetadataAndLiveActivityWatcherSubAssembler(
    client: NostrClient,
    allKeys: () -> Set<ChannelFinderQueryState>,
) : SingleSubEoseManager<ChannelFinderQueryState>(client, allKeys) {
    override fun updateFilter(
        keys: List<ChannelFinderQueryState>,
        since: Map<String, EOSETime>?,
    ): List<TypedFilter>? =
        keys
            .mapNotNull { key ->
                if (key.channel is PublicChatChannel) {
                    filterChannelMetadataUpdatesById(key.channel, since)
                } else if (key.channel is LiveActivitiesChannel) {
                    filterLiveStreamUpdatesByAddress(key.channel, since)
                } else {
                    null
                }
            }.flatten()

    override fun distinct(key: ChannelFinderQueryState) = key.channel.idHex
}
