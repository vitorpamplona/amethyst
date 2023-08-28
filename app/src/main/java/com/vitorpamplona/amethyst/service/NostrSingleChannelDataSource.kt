package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.PublicChatChannel
import com.vitorpamplona.amethyst.service.relays.COMMON_FEED_TYPES
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
            filter = JsonFilter(
                kinds = listOf(ChannelMetadataEvent.kind),
                tags = mapOf("e" to reactionsToWatch)
            )
        )
    }

    fun createLoadEventsIfNotLoadedFilter(): TypedFilter? {
        val directEventsToLoad = channelsToWatch
            .filter { it.notes.isEmpty() && it is PublicChatChannel }

        val interestedEvents = (directEventsToLoad).map { it.idHex }.toSet()

        if (interestedEvents.isEmpty()) {
            return null
        }

        // downloads linked events to this event.
        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                kinds = listOf(ChannelCreateEvent.kind),
                ids = interestedEvents.toList()
            )
        )
    }

    fun createLoadStreamingIfNotLoadedFilter(): List<TypedFilter>? {
        val directEventsToLoad = channelsToWatch
            .filterIsInstance<LiveActivitiesChannel>()
            .filter { it.info == null }

        val interestedEvents = (directEventsToLoad).map { it.idHex }.toSet()

        if (interestedEvents.isEmpty()) {
            return null
        }

        // downloads linked events to this event.
        return directEventsToLoad.map {
            it.address().let { aTag ->
                TypedFilter(
                    types = COMMON_FEED_TYPES,
                    filter = JsonFilter(
                        kinds = listOf(aTag.kind),
                        tags = mapOf("d" to listOf(aTag.dTag)),
                        authors = listOf(aTag.pubKeyHex)
                    )
                )
            }
        }
    }

    val singleChannelChannel = requestNewChannel()

    override fun updateChannelFilters() {
        val reactions = createMetadataChangeFilter()
        val missing = createLoadEventsIfNotLoadedFilter()
        val missingStreaming = createLoadStreamingIfNotLoadedFilter()

        singleChannelChannel.typedFilters = (
            (listOfNotNull(reactions, missing)) + (missingStreaming ?: emptyList())
            ).ifEmpty { null }
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
