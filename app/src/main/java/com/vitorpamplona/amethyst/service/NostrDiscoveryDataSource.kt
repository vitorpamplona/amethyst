package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.CommunityDefinitionEvent
import com.vitorpamplona.quartz.events.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent

object NostrDiscoveryDataSource : NostrDataSource("DiscoveryFeed") {
    lateinit var account: Account

    val latestEOSEs = EOSEAccount()

    fun createLiveStreamFilter(): List<TypedFilter> {
        val follows = account.selectedUsersFollowList(account.defaultDiscoveryFollowList)?.toList()

        return listOfNotNull(
            TypedFilter(
                types = setOf(FeedType.GLOBAL),
                filter = JsonFilter(
                    authors = follows,
                    kinds = listOf(LiveActivitiesChatMessageEvent.kind, LiveActivitiesEvent.kind),
                    limit = 300,
                    since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList)?.relayList
                )
            ),
            follows?.let {
                TypedFilter(
                    types = setOf(FeedType.GLOBAL),
                    filter = JsonFilter(
                        tags = mapOf("p" to it),
                        kinds = listOf(LiveActivitiesEvent.kind),
                        limit = 100,
                        since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList)?.relayList
                    )
                )
            }
        )
    }

    fun createPublicChatFilter(): List<TypedFilter> {
        val follows = account.selectedUsersFollowList(account.defaultDiscoveryFollowList)?.toList()
        val followChats = account.selectedChatsFollowList().toList()

        return listOf(
            TypedFilter(
                types = setOf(FeedType.PUBLIC_CHATS),
                filter = JsonFilter(
                    authors = follows,
                    kinds = listOf(ChannelCreateEvent.kind, ChannelMetadataEvent.kind, ChannelMessageEvent.kind),
                    limit = 300,
                    since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList)?.relayList
                )
            ),
            TypedFilter(
                types = setOf(FeedType.PUBLIC_CHATS),
                filter = JsonFilter(
                    ids = followChats,
                    kinds = listOf(ChannelCreateEvent.kind),
                    limit = 300,
                    since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList)?.relayList
                )
            )
        )
    }

    fun createCommunitiesFilter(): TypedFilter {
        val follows = account.selectedUsersFollowList(account.defaultDiscoveryFollowList)?.toList()

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter = JsonFilter(
                authors = follows,
                kinds = listOf(CommunityDefinitionEvent.kind, CommunityPostApprovalEvent.kind),
                limit = 300,
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList)?.relayList
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
                limit = 300,
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList)?.relayList
            )
        )
    }

    fun createLiveStreamGeohashesFilter(): TypedFilter? {
        val hashToLoad = account.selectedGeohashesFollowList(account.defaultDiscoveryFollowList)

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter = JsonFilter(
                kinds = listOf(LiveActivitiesChatMessageEvent.kind, LiveActivitiesEvent.kind),
                tags = mapOf(
                    "g" to hashToLoad.map {
                        listOf(it, it.lowercase(), it.uppercase(), it.capitalize())
                    }.flatten()
                ),
                limit = 300,
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList)?.relayList
            )
        )
    }

    fun createPublicChatsTagsFilter(): TypedFilter? {
        val hashToLoad = account.selectedTagsFollowList(account.defaultDiscoveryFollowList)

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.PUBLIC_CHATS),
            filter = JsonFilter(
                kinds = listOf(ChannelCreateEvent.kind, ChannelMetadataEvent.kind, ChannelMessageEvent.kind),
                tags = mapOf(
                    "t" to hashToLoad.map {
                        listOf(it, it.lowercase(), it.uppercase(), it.capitalize())
                    }.flatten()
                ),
                limit = 300,
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList)?.relayList
            )
        )
    }

    fun createPublicChatsGeohashesFilter(): TypedFilter? {
        val hashToLoad = account.selectedGeohashesFollowList(account.defaultDiscoveryFollowList)

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.PUBLIC_CHATS),
            filter = JsonFilter(
                kinds = listOf(ChannelCreateEvent.kind, ChannelMetadataEvent.kind, ChannelMessageEvent.kind),
                tags = mapOf(
                    "g" to hashToLoad.map {
                        listOf(it, it.lowercase(), it.uppercase(), it.capitalize())
                    }.flatten()
                ),
                limit = 300,
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList)?.relayList
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
                limit = 300,
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList)?.relayList
            )
        )
    }

    fun createCommunitiesGeohashesFilter(): TypedFilter? {
        val hashToLoad = account.selectedGeohashesFollowList(account.defaultDiscoveryFollowList)

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter = JsonFilter(
                kinds = listOf(CommunityDefinitionEvent.kind, CommunityPostApprovalEvent.kind),
                tags = mapOf(
                    "g" to hashToLoad.map {
                        listOf(it, it.lowercase(), it.uppercase(), it.capitalize())
                    }.flatten()
                ),
                limit = 300,
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList)?.relayList
            )
        )
    }

    val discoveryFeedChannel = requestNewChannel() { time, relayUrl ->
        latestEOSEs.addOrUpdate(account.userProfile(), account.defaultDiscoveryFollowList, relayUrl, time)
    }

    override fun updateChannelFilters() {
        discoveryFeedChannel.typedFilters = createLiveStreamFilter().plus(createPublicChatFilter()).plus(
            listOfNotNull(
                createLiveStreamTagsFilter(),
                createLiveStreamGeohashesFilter(),
                createCommunitiesFilter(),
                createPublicChatsTagsFilter(),
                createCommunitiesTagsFilter(),
                createCommunitiesGeohashesFilter(),
                createPublicChatsGeohashesFilter()
            )
        ).ifEmpty { null }
    }
}
