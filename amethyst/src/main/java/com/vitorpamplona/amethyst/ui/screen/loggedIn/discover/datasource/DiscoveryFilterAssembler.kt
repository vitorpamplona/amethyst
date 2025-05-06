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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.datasource

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.QueryBasedSubscriptionOrchestrator
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.datasources.Subscription
import com.vitorpamplona.ammolite.relays.filters.SinceAuthorPerRelayFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip51Lists.FollowListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

// This allows multiple screen to be listening to tags, even the same tag
class DiscoveryQueryState(
    val account: Account,
    val scope: CoroutineScope,
)

class DiscoveryFilterAssembler(
    client: NostrClient,
) : QueryBasedSubscriptionOrchestrator<DiscoveryQueryState>(client) {
    val latestEOSEs = EOSEAccount()

    fun since(key: DiscoveryQueryState) =
        latestEOSEs.users[key.account.userProfile()]
            ?.followList
            ?.get(key.account.settings.defaultDiscoveryFollowList.value)
            ?.relayList

    fun newEose(
        key: DiscoveryQueryState,
        relayUrl: String,
        time: Long,
    ) {
        latestEOSEs.addOrUpdate(
            key.account.userProfile(),
            key.account.settings.defaultDiscoveryFollowList.value,
            relayUrl,
            time,
        )
        invalidateFilters()
    }

    fun createMarketplaceFilter(key: DiscoveryQueryState): List<TypedFilter> {
        val follows =
            key.account.liveDiscoveryListAuthorsPerRelay.value
                ?.ifEmpty { null }
        val hashToLoad =
            key.account.liveDiscoveryFollowLists.value
                ?.hashtags
                ?.toList()
                ?.ifEmpty { null }
        val geohashToLoad =
            key.account.liveDiscoveryFollowLists.value
                ?.geotags
                ?.toList()
                ?.ifEmpty { null }

        return listOfNotNull(
            TypedFilter(
                types = if (follows == null) setOf(FeedType.GLOBAL) else setOf(FeedType.FOLLOWS),
                filter =
                    SinceAuthorPerRelayFilter(
                        authors = follows,
                        kinds = listOf(ClassifiedsEvent.KIND),
                        limit = 300,
                        since = since(key),
                    ),
            ),
            hashToLoad?.let {
                TypedFilter(
                    types = setOf(FeedType.GLOBAL),
                    filter =
                        SincePerRelayFilter(
                            kinds = listOf(ClassifiedsEvent.KIND),
                            tags =
                                mapOf(
                                    "t" to
                                        it
                                            .map {
                                                listOf(
                                                    it,
                                                    it.lowercase(),
                                                    it.uppercase(),
                                                    it.replaceFirstChar {
                                                        if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                                                    },
                                                )
                                            }.flatten(),
                                ),
                            limit = 300,
                            since = since(key),
                        ),
                )
            },
            geohashToLoad?.let {
                TypedFilter(
                    types = setOf(FeedType.GLOBAL),
                    filter =
                        SincePerRelayFilter(
                            kinds = listOf(ClassifiedsEvent.KIND),
                            tags =
                                mapOf(
                                    "g" to it,
                                ),
                            limit = 300,
                            since = since(key),
                        ),
                )
            },
        )
    }

    fun createNIP89Filter(key: DiscoveryQueryState): List<TypedFilter> =
        listOfNotNull(
            TypedFilter(
                types = setOf(FeedType.GLOBAL),
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(AppDefinitionEvent.KIND),
                        limit = 300,
                        tags = mapOf("k" to listOf("5300")),
                        since = since(key),
                    ),
            ),
        )

    fun createLiveStreamFilter(key: DiscoveryQueryState): List<TypedFilter> {
        val follows =
            key.account.liveDiscoveryFollowLists.value
                ?.authors
                ?.toList()
                ?.ifEmpty { null }

        val followsRelays = key.account.liveDiscoveryListAuthorsPerRelay.value

        return listOfNotNull(
            TypedFilter(
                types = if (follows == null) setOf(FeedType.GLOBAL) else setOf(FeedType.FOLLOWS),
                filter =
                    SinceAuthorPerRelayFilter(
                        authors = followsRelays,
                        kinds =
                            listOf(
                                LiveActivitiesChatMessageEvent.KIND,
                                LiveActivitiesEvent.KIND,
                            ),
                        limit = 300,
                        since = since(key),
                    ),
            ),
            follows?.let {
                TypedFilter(
                    types = setOf(FeedType.FOLLOWS),
                    filter =
                        SincePerRelayFilter(
                            tags = mapOf("p" to it),
                            kinds = listOf(LiveActivitiesEvent.KIND),
                            limit = 100,
                            since = since(key),
                        ),
                )
            },
        )
    }

    fun createPublicChatFilter(key: DiscoveryQueryState): List<TypedFilter> {
        val follows =
            key.account.liveDiscoveryListAuthorsPerRelay.value
                ?.ifEmpty { null }
        val followChats =
            key.account.publicChatList.livePublicChatEventIdSet.value

        return listOfNotNull(
            TypedFilter(
                types = setOf(FeedType.PUBLIC_CHATS),
                filter =
                    SinceAuthorPerRelayFilter(
                        authors = follows,
                        kinds = listOf(ChannelMessageEvent.KIND),
                        limit = 500,
                        since = since(key),
                    ),
            ),
            if (followChats.isNotEmpty()) {
                TypedFilter(
                    types = setOf(FeedType.PUBLIC_CHATS),
                    filter =
                        SincePerRelayFilter(
                            ids = followChats.toList(),
                            kinds =
                                listOf(
                                    ChannelCreateEvent.KIND,
                                    ChannelMessageEvent.KIND,
                                ),
                            limit = 300,
                            since = since(key),
                        ),
                )
            } else {
                null
            },
        )
    }

    fun createFollowSetFilter(key: DiscoveryQueryState): List<TypedFilter> {
        val follows =
            key.account.liveDiscoveryListAuthorsPerRelay.value
                ?.ifEmpty { null }

        return listOfNotNull(
            TypedFilter(
                types = setOf(FeedType.FOLLOWS),
                filter =
                    SinceAuthorPerRelayFilter(
                        authors = follows,
                        kinds = listOf(FollowListEvent.KIND),
                        limit = 300,
                        since = since(key),
                    ),
            ),
        )
    }

    fun createLongFormFilter(key: DiscoveryQueryState): List<TypedFilter> {
        val follows =
            key.account.liveDiscoveryListAuthorsPerRelay.value
                ?.ifEmpty { null }

        return listOfNotNull(
            TypedFilter(
                types = setOf(FeedType.FOLLOWS),
                filter =
                    SinceAuthorPerRelayFilter(
                        authors = follows,
                        kinds = listOf(LongTextNoteEvent.KIND),
                        limit = 300,
                        since = since(key),
                    ),
            ),
        )
    }

    fun createCommunitiesFilter(key: DiscoveryQueryState): TypedFilter {
        val follows = key.account.liveDiscoveryListAuthorsPerRelay.value

        return TypedFilter(
            types = if (follows == null) setOf(FeedType.GLOBAL) else setOf(FeedType.FOLLOWS),
            filter =
                SinceAuthorPerRelayFilter(
                    authors = follows,
                    kinds =
                        listOf(
                            CommunityDefinitionEvent.KIND,
                            CommunityPostApprovalEvent.KIND,
                        ),
                    limit = 300,
                    since = since(key),
                ),
        )
    }

    fun createLiveStreamTagsFilter(key: DiscoveryQueryState): TypedFilter? {
        val hashToLoad =
            key.account.liveDiscoveryFollowLists.value
                ?.hashtags
                ?.toList()

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            LiveActivitiesChatMessageEvent.KIND,
                            LiveActivitiesEvent.KIND,
                        ),
                    tags =
                        mapOf(
                            "t" to
                                hashToLoad
                                    .map {
                                        listOf(
                                            it,
                                            it.lowercase(),
                                            it.uppercase(),
                                            it.replaceFirstChar {
                                                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                                            },
                                        )
                                    }.flatten(),
                        ),
                    limit = 300,
                    since = since(key),
                ),
        )
    }

    fun createLiveStreamGeohashesFilter(key: DiscoveryQueryState): TypedFilter? {
        val hashToLoad =
            key.account.liveDiscoveryFollowLists.value
                ?.geotags
                ?.toList()

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            LiveActivitiesChatMessageEvent.KIND,
                            LiveActivitiesEvent.KIND,
                        ),
                    tags = mapOf("g" to hashToLoad),
                    limit = 300,
                    since = since(key),
                ),
        )
    }

    fun createPublicChatsTagsFilter(key: DiscoveryQueryState): TypedFilter? {
        val hashToLoad =
            key.account.liveDiscoveryFollowLists.value
                ?.hashtags
                ?.toList()

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.PUBLIC_CHATS),
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            ChannelCreateEvent.KIND,
                            ChannelMetadataEvent.KIND,
                            ChannelMessageEvent.KIND,
                        ),
                    tags =
                        mapOf(
                            "t" to
                                hashToLoad
                                    .map {
                                        listOf(
                                            it,
                                            it.lowercase(),
                                            it.uppercase(),
                                            it.replaceFirstChar {
                                                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                                            },
                                        )
                                    }.flatten(),
                        ),
                    limit = 300,
                    since = since(key),
                ),
        )
    }

    fun createPublicChatsGeohashesFilter(key: DiscoveryQueryState): TypedFilter? {
        val hashToLoad =
            key.account.liveDiscoveryFollowLists.value
                ?.geotags
                ?.toList()

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.PUBLIC_CHATS),
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            ChannelCreateEvent.KIND,
                            ChannelMetadataEvent.KIND,
                            ChannelMessageEvent.KIND,
                        ),
                    tags =
                        mapOf("g" to hashToLoad),
                    limit = 300,
                    since = since(key),
                ),
        )
    }

    fun createCommunitiesTagsFilter(key: DiscoveryQueryState): TypedFilter? {
        val hashToLoad =
            key.account.liveDiscoveryFollowLists.value
                ?.hashtags
                ?.toList()

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            CommunityDefinitionEvent.KIND,
                            CommunityPostApprovalEvent.KIND,
                        ),
                    tags =
                        mapOf(
                            "t" to
                                hashToLoad
                                    .map {
                                        listOf(
                                            it,
                                            it.lowercase(),
                                            it.uppercase(),
                                            it.replaceFirstChar {
                                                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                                            },
                                        )
                                    }.flatten(),
                        ),
                    limit = 300,
                    since = since(key),
                ),
        )
    }

    fun createCommunitiesGeohashesFilter(key: DiscoveryQueryState): TypedFilter? {
        val hashToLoad =
            key.account.liveDiscoveryFollowLists.value
                ?.geotags
                ?.toList()

        if (hashToLoad.isNullOrEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            CommunityDefinitionEvent.KIND,
                            CommunityPostApprovalEvent.KIND,
                        ),
                    tags = mapOf("g" to hashToLoad),
                    limit = 300,
                    since = since(key),
                ),
        )
    }

    fun mergeAllFilters(key: DiscoveryQueryState): List<TypedFilter>? =
        createLiveStreamFilter(key)
            .plus(createNIP89Filter(key))
            .plus(createPublicChatFilter(key))
            .plus(createFollowSetFilter(key))
            .plus(createLongFormFilter(key))
            .plus(createMarketplaceFilter(key))
            .plus(
                listOfNotNull(
                    createLiveStreamTagsFilter(key),
                    createLiveStreamGeohashesFilter(key),
                    createCommunitiesFilter(key),
                    createCommunitiesTagsFilter(key),
                    createCommunitiesGeohashesFilter(key),
                    createPublicChatsTagsFilter(key),
                    createPublicChatsGeohashesFilter(key),
                ),
            ).toList()
            .ifEmpty { null }

    val userJobMap = mutableMapOf<User, Job>()
    val userSubscriptionMap = mutableMapOf<User, String>()

    fun newSub(key: DiscoveryQueryState): Subscription {
        userJobMap[key.account.userProfile()]?.cancel()
        userJobMap[key.account.userProfile()] =
            key.scope.launch(Dispatchers.Default) {
                key.account.liveDiscoveryFollowLists.collect {
                    invalidateFilters()
                }
            }

        return requestNewSubscription { time, relayUrl ->
            newEose(key, relayUrl, time)
        }
    }

    fun endSub(
        key: User,
        subId: String,
    ) {
        dismissSubscription(subId)
        userJobMap[key]?.cancel()
    }

    fun findOrCreateSubFor(key: DiscoveryQueryState): Subscription {
        var subId = userSubscriptionMap[key.account.userProfile()]
        return if (subId == null) {
            newSub(key).also { userSubscriptionMap[key.account.userProfile()] = it.id }
        } else {
            getSub(subId) ?: newSub(key).also { userSubscriptionMap[key.account.userProfile()] = it.id }
        }
    }

    // One sub per subscribed account
    override fun updateSubscriptions(keys: Set<DiscoveryQueryState>) {
        val uniqueSubscribedAccounts = keys.distinctBy { it.account }

        val updated = mutableSetOf<User>()

        uniqueSubscribedAccounts.forEach {
            val user = it.account.userProfile()
            val sub = findOrCreateSubFor(it)
            sub.typedFilters = mergeAllFilters(it)

            updated.add(user)
        }

        userSubscriptionMap.forEach {
            if (it.key !in updated) {
                endSub(it.key, it.value)
            }
        }
    }
}
