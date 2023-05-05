package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.model.FileHeaderEvent
import com.vitorpamplona.amethyst.service.model.FileStorageHeaderEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter

object NostrVideoDataSource : NostrDataSource("VideoFeed") {
    lateinit var account: Account

    fun createContextualFilter(): TypedFilter? {
        val follows = account.selectedUsersFollowList(account.defaultStoriesFollowList)

        val followKeys = follows?.map {
            it.substring(0, 6)
        }

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter = JsonFilter(
                authors = followKeys,
                kinds = listOf(FileHeaderEvent.kind, FileStorageHeaderEvent.kind),
                limit = 200
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
                limit = 100
            )
        )
    }

    val videoFeedChannel = requestNewChannel()

    override fun updateChannelFilters() {
        videoFeedChannel.typedFilters = listOfNotNull(createContextualFilter(), createFollowTagsFilter()).ifEmpty { null }
    }
}
