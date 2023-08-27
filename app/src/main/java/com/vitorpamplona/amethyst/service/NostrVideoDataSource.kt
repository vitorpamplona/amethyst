package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import com.vitorpamplona.quartz.events.FileHeaderEvent
import com.vitorpamplona.quartz.events.FileStorageHeaderEvent

object NostrVideoDataSource : NostrDataSource("VideoFeed") {
    lateinit var account: Account

    val latestEOSEs = EOSEAccount()

    fun createContextualFilter(): TypedFilter? {
        val follows = account.selectedUsersFollowList(account.defaultStoriesFollowList)?.toList()

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter = JsonFilter(
                authors = follows,
                kinds = listOf(FileHeaderEvent.kind, FileStorageHeaderEvent.kind),
                limit = 200,
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultStoriesFollowList)?.relayList
            )
        )
    }

    fun createFollowTagsFilter(): TypedFilter? {
        val hashToLoad = account.selectedTagsFollowList(account.defaultStoriesFollowList)

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter = JsonFilter(
                kinds = listOf(FileHeaderEvent.kind, FileStorageHeaderEvent.kind),
                tags = mapOf(
                    "t" to hashToLoad.map {
                        listOf(it, it.lowercase(), it.uppercase(), it.capitalize())
                    }.flatten()
                ),
                limit = 100,
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultStoriesFollowList)?.relayList
            )
        )
    }

    fun createFollowGeohashesFilter(): TypedFilter? {
        val hashToLoad = account.selectedGeohashesFollowList(account.defaultStoriesFollowList)

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter = JsonFilter(
                kinds = listOf(FileHeaderEvent.kind, FileStorageHeaderEvent.kind),
                tags = mapOf(
                    "g" to hashToLoad.map {
                        listOf(it, it.lowercase(), it.uppercase(), it.capitalize())
                    }.flatten()
                ),
                limit = 100,
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultStoriesFollowList)?.relayList
            )
        )
    }

    val videoFeedChannel = requestNewChannel() { time, relayUrl ->
        latestEOSEs.addOrUpdate(account.userProfile(), account.defaultStoriesFollowList, relayUrl, time)
    }

    override fun updateChannelFilters() {
        videoFeedChannel.typedFilters = listOfNotNull(
            createContextualFilter(),
            createFollowTagsFilter(),
            createFollowGeohashesFilter()
        ).ifEmpty { null }
    }
}
