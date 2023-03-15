package com.vitorpamplona.amethyst.service

import androidx.compose.ui.text.capitalize
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.LongTextNoteEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter

object NostrHashtagDataSource : NostrDataSource("SingleHashtagFeed") {
    private var hashtagToWatch: String? = null

    fun createLoadHashtagFilter(): TypedFilter? {
        val hashToLoad = hashtagToWatch ?: return null

        return TypedFilter(
            types = FeedType.values().toSet(),
            filter = JsonFilter(
                tags = mapOf("t" to listOf(hashToLoad, hashToLoad.lowercase(), hashToLoad.uppercase(), hashToLoad.capitalize())),
                kinds = listOf(TextNoteEvent.kind, ChannelMessageEvent.kind, LongTextNoteEvent.kind),
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
