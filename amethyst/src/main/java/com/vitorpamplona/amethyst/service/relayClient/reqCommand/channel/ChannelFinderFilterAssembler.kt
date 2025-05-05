/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel

import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.PublicChatChannel
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.QueryBasedSubscriptionOrchestrator
import com.vitorpamplona.ammolite.relays.EVENT_FINDER_TYPES
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent

// This allows multiple screen to be listening to tags, even the same tag
class ChannelFinderQueryState(
    val channel: Channel,
)

class ChannelFinderFilterAssembler(
    client: NostrClient,
) : QueryBasedSubscriptionOrchestrator<ChannelFinderQueryState>(client) {
    private fun createMetadataChangeFilter(keys: Set<ChannelFinderQueryState>): TypedFilter? {
        val reactionsToWatch = keys.filter { it.channel is PublicChatChannel }.map { it.channel.idHex }

        if (reactionsToWatch.isEmpty()) {
            return null
        }

        // downloads all the reactions to a given event.
        return TypedFilter(
            types = setOf(FeedType.PUBLIC_CHATS),
            filter =
                SincePerRelayFilter(
                    kinds = listOf(ChannelMetadataEvent.KIND),
                    tags = mapOf("e" to reactionsToWatch),
                    limit = 3,
                ),
        )
    }

    fun createLoadEventsIfNotLoadedFilter(keys: Set<ChannelFinderQueryState>): TypedFilter? {
        val interestedEvents =
            keys.mapNotNullTo(mutableSetOf()) {
                if (it.channel is PublicChatChannel && it.channel.event == null) {
                    it.channel.idHex
                } else {
                    null
                }
            }

        if (interestedEvents.isEmpty()) {
            return null
        }

        // downloads linked events to this event.
        return TypedFilter(
            types = EVENT_FINDER_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds = listOf(ChannelCreateEvent.KIND),
                    ids = interestedEvents.toList(),
                ),
        )
    }

    fun createLoadStreamingIfNotLoadedFilter(keys: Set<ChannelFinderQueryState>): List<TypedFilter>? {
        val directEventsToLoad =
            keys.mapNotNull {
                if (it.channel is LiveActivitiesChannel && it.channel.info == null) {
                    it.channel
                } else {
                    null
                }
            }

        val interestedEvents = directEventsToLoad.map { it.idHex }.toSet()

        if (interestedEvents.isEmpty()) {
            return null
        }

        // downloads linked events to this event.
        return directEventsToLoad.map {
            it.address().let { aTag ->
                TypedFilter(
                    types = EVENT_FINDER_TYPES,
                    filter =
                        SincePerRelayFilter(
                            kinds = listOf(aTag.kind),
                            tags = mapOf("d" to listOf(aTag.dTag)),
                            authors = listOf(aTag.pubKeyHex),
                        ),
                )
            }
        }
    }

    val singleChannelChannel = requestNewSubscription()

    override fun updateSubscriptions(keys: Set<ChannelFinderQueryState>) {
        val reactions = createMetadataChangeFilter(keys)
        val missing = createLoadEventsIfNotLoadedFilter(keys)
        val missingStreaming = createLoadStreamingIfNotLoadedFilter(keys)

        singleChannelChannel.typedFilters = ((listOfNotNull(reactions, missing)) + (missingStreaming ?: emptyList())).ifEmpty { null }
    }
}
