package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.Amethyst
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object NostrDiscoveryDataSource : NostrDataSource("DiscoveryFeed") {
    lateinit var account: Account

    val scope = Amethyst.instance.applicationIOScope
    val latestEOSEs = EOSEAccount()

    var job: Job? = null

    override fun start() {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            account.liveDiscoveryFollowLists.collect {
                if (this@NostrDiscoveryDataSource::account.isInitialized) {
                    invalidateFilters()
                }
            }
        }
        super.start()
    }

    override fun stop() {
        super.stop()
        job?.cancel()
    }

    fun createLiveStreamFilter(): List<TypedFilter> {
        val follows = account.liveDiscoveryFollowLists.value?.users?.toList()

        return listOfNotNull(
            TypedFilter(
                types = setOf(FeedType.GLOBAL),
                filter = JsonFilter(
                    authors = follows,
                    kinds = listOf(LiveActivitiesChatMessageEvent.kind, LiveActivitiesEvent.kind),
                    limit = 300,
                    since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList.value)?.relayList
                )
            ),
            follows?.let {
                TypedFilter(
                    types = setOf(FeedType.GLOBAL),
                    filter = JsonFilter(
                        tags = mapOf("p" to it),
                        kinds = listOf(LiveActivitiesEvent.kind),
                        limit = 100,
                        since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList.value)?.relayList
                    )
                )
            }
        )
    }

    fun createPublicChatFilter(): List<TypedFilter> {
        val follows = account.liveDiscoveryFollowLists.value?.users?.toList()
        val followChats = account.selectedChatsFollowList().toList()

        return listOfNotNull(
            TypedFilter(
                types = setOf(FeedType.PUBLIC_CHATS),
                filter = JsonFilter(
                    authors = follows,
                    kinds = listOf(ChannelCreateEvent.kind, ChannelMetadataEvent.kind, ChannelMessageEvent.kind),
                    limit = 300,
                    since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList.value)?.relayList
                )
            ),
            if (followChats.isNotEmpty()) {
                TypedFilter(
                    types = setOf(FeedType.PUBLIC_CHATS),
                    filter = JsonFilter(
                        ids = followChats,
                        kinds = listOf(ChannelCreateEvent.kind),
                        limit = 300,
                        since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList.value)?.relayList
                    )
                )
            } else {
                null
            }
        )
    }

    fun createCommunitiesFilter(): TypedFilter {
        val follows = account.liveDiscoveryFollowLists.value?.users?.toList()

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter = JsonFilter(
                authors = follows,
                kinds = listOf(CommunityDefinitionEvent.kind, CommunityPostApprovalEvent.kind),
                limit = 300,
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList.value)?.relayList
            )
        )
    }

    fun createLiveStreamTagsFilter(): TypedFilter? {
        val hashToLoad = account.liveDiscoveryFollowLists.value?.hashtags?.toList()

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
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList.value)?.relayList
            )
        )
    }

    fun createLiveStreamGeohashesFilter(): TypedFilter? {
        val hashToLoad = account.liveDiscoveryFollowLists.value?.geotags?.toList()

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
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList.value)?.relayList
            )
        )
    }

    fun createPublicChatsTagsFilter(): TypedFilter? {
        val hashToLoad = account.liveDiscoveryFollowLists.value?.hashtags?.toList()

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
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList.value)?.relayList
            )
        )
    }

    fun createPublicChatsGeohashesFilter(): TypedFilter? {
        val hashToLoad = account.liveDiscoveryFollowLists.value?.geotags?.toList()

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
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList.value)?.relayList
            )
        )
    }

    fun createCommunitiesTagsFilter(): TypedFilter? {
        val hashToLoad = account.liveDiscoveryFollowLists.value?.hashtags?.toList()

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
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList.value)?.relayList
            )
        )
    }

    fun createCommunitiesGeohashesFilter(): TypedFilter? {
        val hashToLoad = account.liveDiscoveryFollowLists.value?.geotags?.toList()

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
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultDiscoveryFollowList.value)?.relayList
            )
        )
    }

    val discoveryFeedChannel = requestNewChannel() { time, relayUrl ->
        latestEOSEs.addOrUpdate(account.userProfile(), account.defaultDiscoveryFollowList.value, relayUrl, time)
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
