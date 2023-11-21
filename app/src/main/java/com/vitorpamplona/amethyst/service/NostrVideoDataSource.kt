package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import com.vitorpamplona.quartz.events.FileHeaderEvent
import com.vitorpamplona.quartz.events.FileStorageHeaderEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object NostrVideoDataSource : NostrDataSource("VideoFeed") {
    lateinit var account: Account

    val scope = Amethyst.instance.applicationIOScope
    val latestEOSEs = EOSEAccount()

    var job: Job? = null

    override fun start() {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            account.liveStoriesFollowLists.collect {
                if (this@NostrVideoDataSource::account.isInitialized) {
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

    fun createContextualFilter(): TypedFilter {
        val follows = account.liveStoriesFollowLists.value?.users?.toList()

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter = JsonFilter(
                authors = follows,
                kinds = listOf(FileHeaderEvent.kind, FileStorageHeaderEvent.kind),
                limit = 200,
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultStoriesFollowList.value)?.relayList
            )
        )
    }

    fun createFollowTagsFilter(): TypedFilter? {
        val hashToLoad = account.liveStoriesFollowLists.value?.hashtags?.toList() ?: return null

        if (hashToLoad.isEmpty()) return null

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
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultStoriesFollowList.value)?.relayList
            )
        )
    }

    fun createFollowGeohashesFilter(): TypedFilter? {
        val hashToLoad = account.liveStoriesFollowLists.value?.geotags?.toList() ?: return null

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
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultStoriesFollowList.value)?.relayList
            )
        )
    }

    val videoFeedChannel = requestNewChannel() { time, relayUrl ->
        latestEOSEs.addOrUpdate(account.userProfile(), account.defaultStoriesFollowList.value, relayUrl, time)
    }

    override fun updateChannelFilters() {
        videoFeedChannel.typedFilters = listOfNotNull(
            createContextualFilter(),
            createFollowTagsFilter(),
            createFollowGeohashesFilter()
        ).ifEmpty { null }
    }
}
