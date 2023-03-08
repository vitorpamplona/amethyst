package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter

object NostrChannelDataSource : NostrDataSource("ChatroomFeed") {
    var channel: Channel? = null

    fun loadMessagesBetween(channelId: String) {
        channel = LocalCache.getOrCreateChannel(channelId)
        resetFilters()
    }

    fun createMessagesToChannelFilter(): TypedFilter? {
        if (channel != null) {
            return TypedFilter(
                types = setOf(FeedType.PUBLIC_CHATS),
                filter = JsonFilter(
                    kinds = listOf(ChannelMessageEvent.kind),
                    tags = mapOf("e" to listOfNotNull(channel?.idHex)),
                    limit = 200
                )
            )
        }
        return null
    }

    val messagesChannel = requestNewChannel()

    override fun updateChannelFilters() {
        messagesChannel.typedFilters = listOfNotNull(createMessagesToChannelFilter()).ifEmpty { null }
    }
}
