package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.ThreadAssembler
import com.vitorpamplona.amethyst.service.relays.COMMON_FEED_TYPES
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter

object NostrThreadDataSource : NostrDataSource("SingleThreadFeed") {
    private var eventToWatch: String? = null

    fun createLoadEventsIfNotLoadedFilter(): TypedFilter? {
        val threadToLoad = eventToWatch ?: return null

        val eventsToLoad = ThreadAssembler().findThreadFor(threadToLoad)
            .filter { it.event == null }
            .map { it.idHex }
            .toSet()
            .ifEmpty { null } ?: return null

        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                ids = eventsToLoad.map { it.substring(0, 8) }
            )
        )
    }

    val loadEventsChannel = requestNewChannel() { _, _ ->
        // Many relays operate with limits in the amount of filters.
        // As information comes, the filters will be rotated to get more data.
        invalidateFilters()
    }

    override fun updateChannelFilters() {
        loadEventsChannel.typedFilters = listOfNotNull(createLoadEventsIfNotLoadedFilter()).ifEmpty { null }
    }

    fun loadThread(noteId: String?) {
        eventToWatch = noteId

        invalidateFilters()
    }
}
