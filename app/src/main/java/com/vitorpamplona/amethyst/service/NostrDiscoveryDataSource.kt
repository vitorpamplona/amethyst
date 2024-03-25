/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
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
import com.vitorpamplona.quartz.events.ClassifiedsEvent
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
        job =
            scope.launch(Dispatchers.IO) {
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

    fun createMarketplaceFilter(): List<TypedFilter> {
        val follows = account.liveDiscoveryFollowLists.value?.users?.toList()
        val hashToLoad = account.liveDiscoveryFollowLists.value?.hashtags?.toList()
        val geohashToLoad = account.liveDiscoveryFollowLists.value?.geotags?.toList()

        return listOfNotNull(
            TypedFilter(
                types = setOf(FeedType.GLOBAL),
                filter =
                    JsonFilter(
                        authors = follows,
                        kinds = listOf(ClassifiedsEvent.KIND),
                        limit = 300,
                        since =
                            latestEOSEs.users[account.userProfile()]
                                ?.followList
                                ?.get(account.defaultDiscoveryFollowList.value)
                                ?.relayList,
                    ),
            ),
            hashToLoad?.let {
                TypedFilter(
                    types = setOf(FeedType.GLOBAL),
                    filter =
                        JsonFilter(
                            kinds = listOf(ClassifiedsEvent.KIND),
                            tags =
                                mapOf(
                                    "t" to
                                        it
                                            .map { listOf(it, it.lowercase(), it.uppercase(), it.capitalize()) }
                                            .flatten(),
                                ),
                            limit = 300,
                            since =
                                latestEOSEs.users[account.userProfile()]
                                    ?.followList
                                    ?.get(account.defaultDiscoveryFollowList.value)
                                    ?.relayList,
                        ),
                )
            },
            geohashToLoad?.let {
                TypedFilter(
                    types = setOf(FeedType.GLOBAL),
                    filter =
                        JsonFilter(
                            kinds = listOf(ClassifiedsEvent.KIND),
                            tags =
                                mapOf(
                                    "g" to
                                        it
                                            .map { listOf(it, it.lowercase(), it.uppercase(), it.capitalize()) }
                                            .flatten(),
                                ),
                            limit = 300,
                            since =
                                latestEOSEs.users[account.userProfile()]
                                    ?.followList
                                    ?.get(account.defaultDiscoveryFollowList.value)
                                    ?.relayList,
                        ),
                )
            },
        )
    }

    fun createLiveStreamFilter(): List<TypedFilter> {
        val follows = account.liveDiscoveryFollowLists.value?.users?.toList()

        return listOfNotNull(
            TypedFilter(
                types = setOf(FeedType.GLOBAL),
                filter =
                    JsonFilter(
                        authors = follows,
                        kinds = listOf(LiveActivitiesChatMessageEvent.KIND, LiveActivitiesEvent.KIND),
                        limit = 300,
                        since =
                            latestEOSEs.users[account.userProfile()]
                                ?.followList
                                ?.get(account.defaultDiscoveryFollowList.value)
                                ?.relayList,
                    ),
            ),
            follows?.let {
                TypedFilter(
                    types = setOf(FeedType.GLOBAL),
                    filter =
                        JsonFilter(
                            tags = mapOf("p" to it),
                            kinds = listOf(LiveActivitiesEvent.KIND),
                            limit = 100,
                            since =
                                latestEOSEs.users[account.userProfile()]
                                    ?.followList
                                    ?.get(account.defaultDiscoveryFollowList.value)
                                    ?.relayList,
                        ),
                )
            },
        )
    }

    fun createPublicChatFilter(): List<TypedFilter> {
        val follows = account.liveDiscoveryFollowLists.value?.users?.toList()
        val followChats = account.selectedChatsFollowList().toList()

        return listOfNotNull(
            TypedFilter(
                types = setOf(FeedType.PUBLIC_CHATS),
                filter =
                    JsonFilter(
                        authors = follows,
                        kinds = listOf(ChannelMessageEvent.KIND),
                        limit = 500,
                        since =
                            latestEOSEs.users[account.userProfile()]
                                ?.followList
                                ?.get(account.defaultDiscoveryFollowList.value)
                                ?.relayList,
                    ),
            ),
            if (followChats.isNotEmpty()) {
                TypedFilter(
                    types = setOf(FeedType.PUBLIC_CHATS),
                    filter =
                        JsonFilter(
                            ids = followChats,
                            kinds = listOf(ChannelCreateEvent.KIND, ChannelMessageEvent.KIND),
                            limit = 300,
                            since =
                                latestEOSEs.users[account.userProfile()]
                                    ?.followList
                                    ?.get(account.defaultDiscoveryFollowList.value)
                                    ?.relayList,
                        ),
                )
            } else {
                null
            },
        )
    }

    fun createCommunitiesFilter(): TypedFilter {
        val follows = account.liveDiscoveryFollowLists.value?.users?.toList()

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter =
                JsonFilter(
                    authors = follows,
                    kinds = listOf(CommunityDefinitionEvent.KIND, CommunityPostApprovalEvent.KIND),
                    limit = 300,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.defaultDiscoveryFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createLiveStreamTagsFilter(): TypedFilter? {
        val hashToLoad = account.liveDiscoveryFollowLists.value?.hashtags?.toList()

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter =
                JsonFilter(
                    kinds = listOf(LiveActivitiesChatMessageEvent.KIND, LiveActivitiesEvent.KIND),
                    tags =
                        mapOf(
                            "t" to
                                hashToLoad
                                    .map { listOf(it, it.lowercase(), it.uppercase(), it.capitalize()) }
                                    .flatten(),
                        ),
                    limit = 300,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.defaultDiscoveryFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createLiveStreamGeohashesFilter(): TypedFilter? {
        val hashToLoad = account.liveDiscoveryFollowLists.value?.geotags?.toList()

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter =
                JsonFilter(
                    kinds = listOf(LiveActivitiesChatMessageEvent.KIND, LiveActivitiesEvent.KIND),
                    tags =
                        mapOf(
                            "g" to
                                hashToLoad
                                    .map { listOf(it, it.lowercase(), it.uppercase(), it.capitalize()) }
                                    .flatten(),
                        ),
                    limit = 300,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.defaultDiscoveryFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createPublicChatsTagsFilter(): TypedFilter? {
        val hashToLoad = account.liveDiscoveryFollowLists.value?.hashtags?.toList()

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.PUBLIC_CHATS),
            filter =
                JsonFilter(
                    kinds =
                        listOf(ChannelCreateEvent.KIND, ChannelMetadataEvent.KIND, ChannelMessageEvent.KIND),
                    tags =
                        mapOf(
                            "t" to
                                hashToLoad
                                    .map { listOf(it, it.lowercase(), it.uppercase(), it.capitalize()) }
                                    .flatten(),
                        ),
                    limit = 300,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.defaultDiscoveryFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createPublicChatsGeohashesFilter(): TypedFilter? {
        val hashToLoad = account.liveDiscoveryFollowLists.value?.geotags?.toList()

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.PUBLIC_CHATS),
            filter =
                JsonFilter(
                    kinds =
                        listOf(ChannelCreateEvent.KIND, ChannelMetadataEvent.KIND, ChannelMessageEvent.KIND),
                    tags =
                        mapOf(
                            "g" to
                                hashToLoad
                                    .map { listOf(it, it.lowercase(), it.uppercase(), it.capitalize()) }
                                    .flatten(),
                        ),
                    limit = 300,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.defaultDiscoveryFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createCommunitiesTagsFilter(): TypedFilter? {
        val hashToLoad = account.liveDiscoveryFollowLists.value?.hashtags?.toList()

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter =
                JsonFilter(
                    kinds = listOf(CommunityDefinitionEvent.KIND, CommunityPostApprovalEvent.KIND),
                    tags =
                        mapOf(
                            "t" to
                                hashToLoad
                                    .map { listOf(it, it.lowercase(), it.uppercase(), it.capitalize()) }
                                    .flatten(),
                        ),
                    limit = 300,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.defaultDiscoveryFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createCommunitiesGeohashesFilter(): TypedFilter? {
        val hashToLoad = account.liveDiscoveryFollowLists.value?.geotags?.toList()

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter =
                JsonFilter(
                    kinds = listOf(CommunityDefinitionEvent.KIND, CommunityPostApprovalEvent.KIND),
                    tags =
                        mapOf(
                            "g" to
                                hashToLoad
                                    .map { listOf(it, it.lowercase(), it.uppercase(), it.capitalize()) }
                                    .flatten(),
                        ),
                    limit = 300,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.defaultDiscoveryFollowList.value)
                            ?.relayList,
                ),
        )
    }

    val discoveryFeedChannel =
        requestNewChannel { time, relayUrl ->
            latestEOSEs.addOrUpdate(
                account.userProfile(),
                account.defaultDiscoveryFollowList.value,
                relayUrl,
                time,
            )
        }

    override fun updateChannelFilters() {
        discoveryFeedChannel.typedFilters =
            createLiveStreamFilter()
                .plus(createPublicChatFilter())
                .plus(createMarketplaceFilter())
                .plus(
                    listOfNotNull(
                        createLiveStreamTagsFilter(),
                        createLiveStreamGeohashesFilter(),
                        createCommunitiesFilter(),
                        createCommunitiesTagsFilter(),
                        createCommunitiesGeohashesFilter(),
                        createPublicChatsTagsFilter(),
                        createPublicChatsGeohashesFilter(),
                    ),
                )
                .ifEmpty { null }
    }
}
