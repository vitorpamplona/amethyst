package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.service.relays.COMMON_FEED_TYPES
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import com.vitorpamplona.quartz.events.AudioTrackEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.HighlightEvent
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LongTextNoteEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.TextNoteEvent

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
