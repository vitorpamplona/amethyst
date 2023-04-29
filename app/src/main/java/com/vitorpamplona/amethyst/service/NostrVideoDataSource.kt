package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.service.model.FileHeaderEvent
import com.vitorpamplona.amethyst.service.model.FileStorageEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter

object NostrVideoDataSource : NostrDataSource("VideoFeed") {
    fun createGlobalFilter() = TypedFilter(
        types = setOf(FeedType.GLOBAL),
        filter = JsonFilter(
            kinds = listOf(FileHeaderEvent.kind, FileStorageEvent.kind),
            limit = 200
        )
    )

    val videoFeedChannel = requestNewChannel()

    override fun updateChannelFilters() {
        videoFeedChannel.typedFilters = listOf(createGlobalFilter()).ifEmpty { null }
    }
}
