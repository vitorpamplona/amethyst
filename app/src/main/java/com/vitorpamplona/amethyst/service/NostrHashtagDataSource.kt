package com.vitorpamplona.amethyst.service

import androidx.compose.ui.text.capitalize
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
                kinds = listOf(TextNoteEvent.kind, ChannelMessageEvent.kind, LongTextNoteEvent.kind, PollNoteEvent.kind, LiveActivitiesChatMessageEvent.kind, ClassifiedsEvent.kind, HighlightEvent.kind, AudioTrackEvent.kind),
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
