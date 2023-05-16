package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.service.model.AudioTrackEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.HighlightEvent
import com.vitorpamplona.amethyst.service.model.LongTextNoteEvent
import com.vitorpamplona.amethyst.service.model.PinListEvent
import com.vitorpamplona.amethyst.service.model.PollNoteEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter

object NostrGlobalDataSource : NostrDataSource("GlobalFeed") {
    fun createGlobalFilter() = TypedFilter(
        types = setOf(FeedType.GLOBAL),
        filter = JsonFilter(
            kinds = listOf(TextNoteEvent.kind, PollNoteEvent.kind, ChannelMessageEvent.kind, AudioTrackEvent.kind, PinListEvent.kind, LongTextNoteEvent.kind, HighlightEvent.kind),
            limit = 200
        )
    )

    val globalFeedChannel = requestNewChannel()

    override fun updateChannelFilters() {
        globalFeedChannel.typedFilters = listOf(createGlobalFilter()).ifEmpty { null }
    }
}
