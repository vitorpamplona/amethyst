package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter

object NostrChannelDataSource : NostrDataSource("ChatroomFeed") {
    var account: Account? = null
    var channel: Channel? = null

    fun loadMessagesBetween(account: Account, channel: Channel) {
        this.account = account
        this.channel = channel
        resetFilters()
    }

    fun clear() {
        account = null
        channel = null
    }

    fun createMessagesByMeToChannelFilter(): TypedFilter? {
        val myAccount = account ?: return null

        if (channel != null) {
            // Brings on messages by the user from all other relays.
            // Since we ship with write to public, read from private only
            // this guarantees that messages from the author do not disappear.
            return TypedFilter(
                types = setOf(FeedType.FOLLOWS, FeedType.PRIVATE_DMS, FeedType.GLOBAL, FeedType.SEARCH),
                filter = JsonFilter(
                    kinds = listOf(ChannelMessageEvent.kind),
                    authors = listOf(myAccount.userProfile().pubkeyHex),
                    limit = 50
                )
            )
        }
        return null
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
        messagesChannel.typedFilters = listOfNotNull(
            createMessagesToChannelFilter(),
            createMessagesByMeToChannelFilter()
        ).ifEmpty { null }
    }
}
