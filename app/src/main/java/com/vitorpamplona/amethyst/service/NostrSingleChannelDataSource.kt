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
package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.PublicChatChannel
import com.vitorpamplona.amethyst.service.relays.EVENT_FINDER_TYPES
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent

object NostrSingleChannelDataSource : NostrDataSource("SingleChannelFeed") {
    private var channelsToWatch = setOf<Channel>()

    private fun createMetadataChangeFilter(): TypedFilter? {
        val reactionsToWatch = channelsToWatch.filter { it is PublicChatChannel }.map { it.idHex }

        if (reactionsToWatch.isEmpty()) {
            return null
        }

        // downloads all the reactions to a given event.
        return TypedFilter(
            types = setOf(FeedType.PUBLIC_CHATS),
            filter =
                JsonFilter(
                    kinds = listOf(ChannelMetadataEvent.KIND),
                    tags = mapOf("e" to reactionsToWatch),
                ),
        )
    }

    fun createLoadEventsIfNotLoadedFilter(): TypedFilter? {
        val directEventsToLoad =
            channelsToWatch.filter { it.notes.isEmpty() && it is PublicChatChannel }

        val interestedEvents = (directEventsToLoad).map { it.idHex }.toSet()

        if (interestedEvents.isEmpty()) {
            return null
        }

        // downloads linked events to this event.
        return TypedFilter(
            types = EVENT_FINDER_TYPES,
            filter =
                JsonFilter(
                    kinds = listOf(ChannelCreateEvent.KIND),
                    ids = interestedEvents.toList(),
                ),
        )
    }

    fun createLoadStreamingIfNotLoadedFilter(): List<TypedFilter>? {
        val directEventsToLoad =
            channelsToWatch.filterIsInstance<LiveActivitiesChannel>().filter { it.info == null }

        val interestedEvents = (directEventsToLoad).map { it.idHex }.toSet()

        if (interestedEvents.isEmpty()) {
            return null
        }

        // downloads linked events to this event.
        return directEventsToLoad.map {
            it.address().let { aTag ->
                TypedFilter(
                    types = EVENT_FINDER_TYPES,
                    filter =
                        JsonFilter(
                            kinds = listOf(aTag.kind),
                            tags = mapOf("d" to listOf(aTag.dTag)),
                            authors = listOf(aTag.pubKeyHex),
                        ),
                )
            }
        }
    }

    val singleChannelChannel = requestNewChannel()

    override fun updateChannelFilters() {
        val reactions = createMetadataChangeFilter()
        val missing = createLoadEventsIfNotLoadedFilter()
        val missingStreaming = createLoadStreamingIfNotLoadedFilter()

        singleChannelChannel.typedFilters =
            ((listOfNotNull(reactions, missing)) + (missingStreaming ?: emptyList())).ifEmpty { null }
    }

    fun add(eventId: Channel) {
        if (eventId !in channelsToWatch) {
            channelsToWatch = channelsToWatch.plus(eventId)
            invalidateFilters()
        }
    }

    fun remove(eventId: Channel) {
        if (eventId in channelsToWatch) {
            channelsToWatch = channelsToWatch.minus(eventId)
            invalidateFilters()
        }
    }
}
