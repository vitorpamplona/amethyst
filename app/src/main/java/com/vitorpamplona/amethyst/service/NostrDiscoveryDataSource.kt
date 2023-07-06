package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.CommunityDefinitionEvent
import com.vitorpamplona.amethyst.service.model.CommunityPostApprovalEvent
import com.vitorpamplona.amethyst.service.model.LiveActivitiesChatMessageEvent
import com.vitorpamplona.amethyst.service.model.LiveActivitiesEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter

object NostrDiscoveryDataSource : NostrDataSource("DiscoveryFeed") {
    lateinit var account: Account

    fun createLiveStreamFilter(): TypedFilter {
        val follows = account.selectedUsersFollowList(account.defaultDiscoveryFollowList)

        val followKeys = follows?.map {
            it.substring(0, 6)
        }

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter = JsonFilter(
                authors = followKeys,
                kinds = listOf(LiveActivitiesChatMessageEvent.kind, LiveActivitiesEvent.kind),
                limit = 500
            )
        )
    }

    fun createPublicChatFilter(): TypedFilter {
        val follows = account.selectedUsersFollowList(account.defaultDiscoveryFollowList)

        val followKeys = follows?.map {
            it.substring(0, 6)
        }

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter = JsonFilter(
                authors = followKeys,
                kinds = listOf(ChannelCreateEvent.kind, ChannelMetadataEvent.kind, ChannelMessageEvent.kind),
                limit = 500
            )
        )
    }

    fun createCommunitiesFilter(): TypedFilter {
        val follows = account.selectedUsersFollowList(account.defaultDiscoveryFollowList)

        val followKeys = follows?.map {
            it.substring(0, 6)
        }

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter = JsonFilter(
                authors = followKeys,
                kinds = listOf(CommunityDefinitionEvent.kind, CommunityPostApprovalEvent.kind),
                limit = 500
            )
        )
    }

    fun createLiveStreamTagsFilter(): TypedFilter? {
        val hashToLoad = account.selectedTagsFollowList(account.defaultDiscoveryFollowList)

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter = JsonFilter(
                kinds = listOf(LiveActivitiesChatMessageEvent.kind, LiveActivitiesEvent.kind),
                tags = mapOf(
                    "t" to hashToLoad.map {
                        listOf(it, it.lowercase(), it.uppercase(), it.capitalize())
                    }.flatten()
                ),
                limit = 500
            )
        )
    }

    fun createPublicChatsTagsFilter(): TypedFilter? {
        val hashToLoad = account.selectedTagsFollowList(account.defaultDiscoveryFollowList)

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter = JsonFilter(
                kinds = listOf(ChannelCreateEvent.kind, ChannelMetadataEvent.kind, ChannelMessageEvent.kind),
                tags = mapOf(
                    "t" to hashToLoad.map {
                        listOf(it, it.lowercase(), it.uppercase(), it.capitalize())
                    }.flatten()
                ),
                limit = 500
            )
        )
    }

    fun createCommunitiesTagsFilter(): TypedFilter? {
        val hashToLoad = account.selectedTagsFollowList(account.defaultDiscoveryFollowList)

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter = JsonFilter(
                kinds = listOf(CommunityDefinitionEvent.kind, CommunityPostApprovalEvent.kind),
                tags = mapOf(
                    "t" to hashToLoad.map {
                        listOf(it, it.lowercase(), it.uppercase(), it.capitalize())
                    }.flatten()
                ),
                limit = 500
            )
        )
    }

    val discoveryFeedChannel = requestNewChannel()

    override fun updateChannelFilters() {
        discoveryFeedChannel.typedFilters = listOfNotNull(
            createLiveStreamFilter(),
            createPublicChatFilter(),
            createCommunitiesFilter(),
            createLiveStreamTagsFilter(),
            createPublicChatsTagsFilter(),
            createCommunitiesTagsFilter()
        ).ifEmpty { null }
    }
}
