package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import com.vitorpamplona.quartz.events.AudioHeaderEvent
import com.vitorpamplona.quartz.events.AudioTrackEvent
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.HighlightEvent
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.LongTextNoteEvent
import com.vitorpamplona.quartz.events.PinListEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.RepostEvent
import com.vitorpamplona.quartz.events.TextNoteEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object NostrHomeDataSource : NostrDataSource("HomeFeed") {
    lateinit var account: Account

    val scope = Amethyst.instance.applicationIOScope
    val latestEOSEs = EOSEAccount()

    var job: Job? = null

    override fun start() {
        job?.cancel()
        job = account.scope.launch(Dispatchers.IO) {
            // creates cache on main
            withContext(Dispatchers.Main) {
                account.userProfile().live()
            }
            account.liveHomeFollowLists.collect {
                if (this@NostrHomeDataSource::account.isInitialized) {
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

    fun createFollowAccountsFilter(): TypedFilter {
        val follows = account.liveHomeFollowLists.value?.users
        val followSet = follows?.plus(account.userProfile().pubkeyHex)?.toList()?.ifEmpty { null }

        return TypedFilter(
            types = setOf(FeedType.FOLLOWS),
            filter = JsonFilter(
                kinds = listOf(
                    TextNoteEvent.kind,
                    RepostEvent.kind,
                    GenericRepostEvent.kind,
                    ClassifiedsEvent.kind,
                    LongTextNoteEvent.kind,
                    PollNoteEvent.kind,
                    HighlightEvent.kind,
                    AudioTrackEvent.kind,
                    AudioHeaderEvent.kind,
                    PinListEvent.kind,
                    LiveActivitiesChatMessageEvent.kind,
                    LiveActivitiesEvent.kind
                ),
                authors = followSet,
                limit = 400,
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultHomeFollowList.value)?.relayList
            )
        )
    }

    fun createFollowTagsFilter(): TypedFilter? {
        val hashToLoad = account.liveHomeFollowLists.value?.hashtags ?: return null

        if (hashToLoad.isEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.FOLLOWS),
            filter = JsonFilter(
                kinds = listOf(TextNoteEvent.kind, LongTextNoteEvent.kind, ClassifiedsEvent.kind, HighlightEvent.kind, AudioHeaderEvent.kind, AudioTrackEvent.kind, PinListEvent.kind),
                tags = mapOf(
                    "t" to hashToLoad.map {
                        listOf(it, it.lowercase(), it.uppercase(), it.capitalize())
                    }.flatten()
                ),
                limit = 100,
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultHomeFollowList.value)?.relayList
            )
        )
    }

    fun createFollowGeohashesFilter(): TypedFilter? {
        val hashToLoad = account.liveHomeFollowLists.value?.geotags ?: return null

        if (hashToLoad.isEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.FOLLOWS),
            filter = JsonFilter(
                kinds = listOf(TextNoteEvent.kind, LongTextNoteEvent.kind, ClassifiedsEvent.kind, HighlightEvent.kind, AudioHeaderEvent.kind, AudioTrackEvent.kind, PinListEvent.kind),
                tags = mapOf(
                    "g" to hashToLoad.map {
                        listOf(it, it.lowercase(), it.uppercase(), it.capitalize())
                    }.flatten()
                ),
                limit = 100,
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultHomeFollowList.value)?.relayList
            )
        )
    }

    fun createFollowCommunitiesFilter(): TypedFilter? {
        val communitiesToLoad = account.liveHomeFollowLists.value?.communities ?: return null

        if (communitiesToLoad.isEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.FOLLOWS),
            filter = JsonFilter(
                kinds = listOf(
                    TextNoteEvent.kind,
                    LongTextNoteEvent.kind,
                    ClassifiedsEvent.kind,
                    HighlightEvent.kind,
                    AudioHeaderEvent.kind,
                    AudioTrackEvent.kind,
                    PinListEvent.kind,
                    CommunityPostApprovalEvent.kind
                ),
                tags = mapOf(
                    "a" to communitiesToLoad.toList()
                ),
                limit = 100,
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultHomeFollowList.value)?.relayList
            )
        )
    }

    val followAccountChannel = requestNewChannel { time, relayUrl ->
        latestEOSEs.addOrUpdate(account.userProfile(), account.defaultHomeFollowList.value, relayUrl, time)
    }

    override fun updateChannelFilters() {
        followAccountChannel.typedFilters = listOfNotNull(
            createFollowAccountsFilter(),
            createFollowCommunitiesFilter(),
            createFollowTagsFilter(),
            createFollowGeohashesFilter()
        ).ifEmpty { null }
    }
}
