package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.service.model.AudioTrackEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ClassifiedsEvent
import com.vitorpamplona.amethyst.service.model.HighlightEvent
import com.vitorpamplona.amethyst.service.model.LiveActivitiesChatMessageEvent
import com.vitorpamplona.amethyst.service.model.LongTextNoteEvent
import com.vitorpamplona.amethyst.service.model.PollNoteEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent
import com.vitorpamplona.amethyst.service.relays.COMMON_FEED_TYPES
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter

object NostrGeohashDataSource : NostrDataSource("SingleGeoHashFeed") {
    private var geohashToWatch: String? = null

    fun createLoadHashtagFilter(): TypedFilter? {
        val hashToLoad = geohashToWatch ?: return null

        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                tags = mapOf(
                    "g" to listOf(
                        hashToLoad
                    )
                ),
                kinds = listOf(TextNoteEvent.kind, ChannelMessageEvent.kind, LongTextNoteEvent.kind, PollNoteEvent.kind, LiveActivitiesChatMessageEvent.kind, ClassifiedsEvent.kind, HighlightEvent.kind, AudioTrackEvent.kind),
                limit = 200
            )
        )
    }

    val loadGeohashChannel = requestNewChannel()

    override fun updateChannelFilters() {
        loadGeohashChannel.typedFilters = listOfNotNull(createLoadHashtagFilter()).ifEmpty { null }
    }

    fun loadHashtag(tag: String?) {
        geohashToWatch = tag

        invalidateFilters()
    }
}
