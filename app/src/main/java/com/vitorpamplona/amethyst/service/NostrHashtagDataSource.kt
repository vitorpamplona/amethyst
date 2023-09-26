package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.service.relays.COMMON_FEED_TYPES
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import com.vitorpamplona.quartz.events.AudioHeaderEvent
import com.vitorpamplona.quartz.events.AudioTrackEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.HighlightEvent
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LongTextNoteEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.TextNoteEvent

object NostrHashtagDataSource : NostrDataSource("SingleHashtagFeed") {
    private var hashtagToWatch: String? = null

    fun createLoadHashtagFilter(): TypedFilter? {
        val hashToLoad = hashtagToWatch ?: return null

        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                tags = mapOf(
                    "t" to listOf(
                        hashToLoad,
                        hashToLoad.lowercase(),
                        hashToLoad.uppercase(),
                        hashToLoad.capitalize()
                    )
                ),
                kinds = listOf(TextNoteEvent.kind, ChannelMessageEvent.kind, LongTextNoteEvent.kind, PollNoteEvent.kind, LiveActivitiesChatMessageEvent.kind, ClassifiedsEvent.kind, HighlightEvent.kind, AudioTrackEvent.kind, AudioHeaderEvent.kind),
                limit = 200
            )
        )
    }

    val loadHashtagChannel = requestNewChannel()

    override fun updateChannelFilters() {
        loadHashtagChannel.typedFilters = listOfNotNull(createLoadHashtagFilter()).ifEmpty { null }
    }

    fun loadHashtag(tag: String?) {
        hashtagToWatch = tag

        invalidateFilters()
    }
}
