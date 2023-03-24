package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter

object NostrChatroomListDataSource : NostrDataSource("MailBoxFeed") {
    lateinit var account: Account

    fun createMessagesToMeFilter() = TypedFilter(
        types = setOf(FeedType.PRIVATE_DMS),
        filter = JsonFilter(
            kinds = listOf(PrivateDmEvent.kind),
            tags = mapOf("p" to listOf(account.userProfile().pubkeyHex()))
        )
    )

    fun createMessagesFromMeFilter() = TypedFilter(
        types = setOf(FeedType.PRIVATE_DMS),
        filter = JsonFilter(
            kinds = listOf(PrivateDmEvent.kind),
            authors = listOf(account.userProfile().pubkeyHex())
        )
    )

    fun createChannelsCreatedbyMeFilter() = TypedFilter(
        types = setOf(FeedType.PUBLIC_CHATS),
        filter = JsonFilter(
            kinds = listOf(ChannelCreateEvent.kind, ChannelMetadataEvent.kind),
            authors = listOf(account.userProfile().pubkeyHex())
        )
    )

    fun createMyChannelsFilter() = TypedFilter(
        types = FeedType.values().toSet(), // Metadata comes from any relay
        filter = JsonFilter(
            kinds = listOf(ChannelCreateEvent.kind),
            ids = account.followingChannels.toList()
        )
    )

    fun createLastChannelInfoFilter(): List<TypedFilter> {
        return account.followingChannels.map {
            TypedFilter(
                types = FeedType.values().toSet(), // Metadata comes from any relay
                filter = JsonFilter(
                    kinds = listOf(ChannelMetadataEvent.kind),
                    tags = mapOf("e" to listOf(it)),
                    limit = 1
                )
            )
        }
    }

    fun createLastMessageOfEachChannelFilter(): List<TypedFilter> {
        return account.followingChannels.map {
            TypedFilter(
                types = setOf(FeedType.PUBLIC_CHATS),
                filter = JsonFilter(
                    kinds = listOf(ChannelMessageEvent.kind),
                    tags = mapOf("e" to listOf(it)),
                    limit = 25 // Remember to consider spam that is being removed from the UI
                )
            )
        }
    }

    val chatroomListChannel = requestNewChannel()

    override fun updateChannelFilters() {
        val list = listOf(
            createMessagesToMeFilter(),
            createMessagesFromMeFilter(),
            createMyChannelsFilter()
        )

        chatroomListChannel.typedFilters = listOfNotNull(
            list,
            createLastChannelInfoFilter(),
            createLastMessageOfEachChannelFilter()
        ).flatten().ifEmpty { null }
    }
}
