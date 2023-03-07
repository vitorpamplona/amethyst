package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter

object NostrSingleChannelDataSource : NostrDataSource("SingleChannelFeed") {
    private var channelsToWatch = setOf<String>()

    private fun createRepliesAndReactionsFilter(): TypedFilter? {
        val reactionsToWatch = channelsToWatch.map { it }

        if (reactionsToWatch.isEmpty()) {
            return null
        }

        // downloads all the reactions to a given event.
        return TypedFilter(
            types = setOf(FeedType.PUBLIC_CHATS),
            filter = JsonFilter(
                kinds = listOf(ChannelMetadataEvent.kind),
                tags = mapOf("e" to reactionsToWatch)
            )
        )
    }

    fun createLoadEventsIfNotLoadedFilter(): TypedFilter? {
        val directEventsToLoad = channelsToWatch
            .map { LocalCache.getOrCreateChannel(it) }
            .filter { it.notes.isEmpty() }

        val interestedEvents = (directEventsToLoad).map { it.idHex }.toSet()

        if (interestedEvents.isEmpty()) {
            return null
        }

        // downloads linked events to this event.
        return TypedFilter(
            types = FeedType.values().toSet(),
            filter = JsonFilter(
                kinds = listOf(ChannelCreateEvent.kind),
                ids = interestedEvents.toList()
            )
        )
    }

    val singleChannelChannel = requestNewChannel()

    override fun updateChannelFilters() {
        val reactions = createRepliesAndReactionsFilter()
        val missing = createLoadEventsIfNotLoadedFilter()

        singleChannelChannel.typedFilters = listOfNotNull(reactions, missing).ifEmpty { null }
    }

    fun add(eventId: String) {
        channelsToWatch = channelsToWatch.plus(eventId)
        invalidateFilters()
    }

    fun remove(eventId: String) {
        channelsToWatch = channelsToWatch.minus(eventId)
        invalidateFilters()
    }
}
