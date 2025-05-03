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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource

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
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

// This allows multiple screen to be listening to tags, even the same tag
class HomeQueryState(
    val account: Account,
    val scope: CoroutineScope,
)

class HomeFilterAssembler(
    client: NostrClient,
) : QueryBasedSubscriptionOrchestrator<HomeQueryState>(client) {
    val latestEOSEs = EOSEAccount()

    fun createFollowAccountsFilter(key: HomeQueryState): List<TypedFilter> {
        val follows = key.account.liveHomeListAuthorsPerRelay.value

        return listOf(
            TypedFilter(
                types = setOf(if (follows == null) FeedType.GLOBAL else FeedType.FOLLOWS),
                filter =
                    SinceAuthorPerRelayFilter(
                        kinds =
                            listOf(
                                TextNoteEvent.KIND,
                                RepostEvent.KIND,
                                GenericRepostEvent.KIND,
                                ClassifiedsEvent.KIND,
                                LongTextNoteEvent.KIND,
                                EphemeralChatEvent.KIND,
                                HighlightEvent.KIND,
                            ),
                        authors = follows,
                        limit = 400,
                        since =
                            latestEOSEs.users[key.account.userProfile()]
                                ?.followList
                                ?.get(key.account.settings.defaultHomeFollowList.value)
                                ?.relayList,
                    ),
            ),
            TypedFilter(
                types = setOf(if (follows == null) FeedType.GLOBAL else FeedType.FOLLOWS),
                filter =
                    SinceAuthorPerRelayFilter(
                        kinds =
                            listOf(
                                PollNoteEvent.KIND,
                                AudioTrackEvent.KIND,
                                AudioHeaderEvent.KIND,
                                PinListEvent.KIND,
                                InteractiveStoryPrologueEvent.KIND,
                                LiveActivitiesChatMessageEvent.KIND,
                                LiveActivitiesEvent.KIND,
                                WikiNoteEvent.KIND,
                            ),
                        authors = follows,
                        limit = 400,
                        since =
                            latestEOSEs.users[key.account.userProfile()]
                                ?.followList
                                ?.get(key.account.settings.defaultHomeFollowList.value)
                                ?.relayList,
                    ),
            ),
        )
    }

    fun createFollowMetadataAndReleaseFilter(key: HomeQueryState): TypedFilter? {
        val follows = key.account.liveHomeListAuthorsPerRelay.value

        return if (!follows.isNullOrEmpty()) {
            TypedFilter(
                types = setOf(FeedType.FOLLOWS),
                filter =
                    SinceAuthorPerRelayFilter(
                        kinds =
                            listOf(
                                MetadataEvent.KIND,
                                AdvertisedRelayListEvent.KIND,
                            ),
                        authors = follows,
                        since =
                            latestEOSEs.users[key.account.userProfile()]
                                ?.followList
                                ?.get(key.account.settings.defaultHomeFollowList.value)
                                ?.relayList,
                    ),
            )
        } else {
            null
        }
    }

    fun createFollowTagsFilter(key: HomeQueryState): TypedFilter? {
        val hashToLoad =
            key.account.liveHomeFollowLists.value
                ?.hashtags ?: return null

        if (hashToLoad.isEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.FOLLOWS),
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            TextNoteEvent.KIND,
                            RepostEvent.KIND,
                            GenericRepostEvent.KIND,
                            LongTextNoteEvent.KIND,
                            ClassifiedsEvent.KIND,
                            HighlightEvent.KIND,
                            AudioHeaderEvent.KIND,
                            InteractiveStoryPrologueEvent.KIND,
                            CommentEvent.KIND,
                            WikiNoteEvent.KIND,
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
                    limit = 100,
                    since =
                        latestEOSEs.users[key.account.userProfile()]
                            ?.followList
                            ?.get(key.account.settings.defaultHomeFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createFollowGeohashesFilter(key: HomeQueryState): TypedFilter? {
        val hashToLoad =
            key.account.liveHomeFollowLists.value
                ?.geotags ?: return null

        if (hashToLoad.isEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.FOLLOWS),
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            TextNoteEvent.KIND,
                            RepostEvent.KIND,
                            GenericRepostEvent.KIND,
                            LongTextNoteEvent.KIND,
                            ClassifiedsEvent.KIND,
                            HighlightEvent.KIND,
                            InteractiveStoryPrologueEvent.KIND,
                            WikiNoteEvent.KIND,
                            CommentEvent.KIND,
                        ),
                    tags =
                        mapOf(
                            "g" to hashToLoad.toList(),
                        ),
                    limit = 100,
                    since =
                        latestEOSEs.users[key.account.userProfile()]
                            ?.followList
                            ?.get(key.account.settings.defaultHomeFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createFollowCommunitiesFilter(key: HomeQueryState): TypedFilter? {
        val communitiesToLoad =
            key.account.liveHomeFollowLists.value
                ?.addresses ?: return null

        if (communitiesToLoad.isEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.FOLLOWS),
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            TextNoteEvent.KIND,
                            LongTextNoteEvent.KIND,
                            ClassifiedsEvent.KIND,
                            HighlightEvent.KIND,
                            WikiNoteEvent.KIND,
                            CommunityPostApprovalEvent.KIND,
                            CommentEvent.KIND,
                            InteractiveStoryPrologueEvent.KIND,
                        ),
                    tags =
                        mapOf(
                            "a" to communitiesToLoad.toList(),
                        ),
                    limit = 100,
                    since =
                        latestEOSEs.users[key.account.userProfile()]
                            ?.followList
                            ?.get(key.account.settings.defaultHomeFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun mergeAllFilters(key: HomeQueryState): List<TypedFilter>? =
        (
            createFollowAccountsFilter(key) +
                listOfNotNull(
                    createFollowMetadataAndReleaseFilter(key),
                    createFollowCommunitiesFilter(key),
                    createFollowTagsFilter(key),
                    createFollowGeohashesFilter(key),
                )
        ).ifEmpty { null }

    val userJobMap = mutableMapOf<User, Job>()
    val userJobMap2 = mutableMapOf<User, Job>()
    val userSubscriptionMap = mutableMapOf<User, String>()

    fun newSub(key: HomeQueryState): Subscription {
        userJobMap[key.account.userProfile()]?.cancel()
        userJobMap[key.account.userProfile()] =
            key.scope.launch(Dispatchers.Default) {
                key.account.liveHomeFollowLists.collect {
                    invalidateFilters()
                }
            }

        userJobMap2[key.account.userProfile()]?.cancel()
        userJobMap2[key.account.userProfile()] =
            key.scope.launch(Dispatchers.Default) {
                key.account.liveHomeListAuthorsPerRelay.collect {
                    invalidateFilters()
                }
            }

        return requestNewSubscription { time, relayUrl ->
            latestEOSEs.addOrUpdate(
                key.account.userProfile(),
                key.account.settings.defaultHomeFollowList.value,
                relayUrl,
                time,
            )
        }
    }

    fun endSub(
        key: User,
        subId: String,
    ) {
        dismissSubscription(subId)
        userJobMap[key]?.cancel()
        userJobMap2[key]?.cancel()
    }

    fun findOrCreateSubFor(key: HomeQueryState): Subscription {
        var subId = userSubscriptionMap[key.account.userProfile()]
        return if (subId == null) {
            newSub(key).also { userSubscriptionMap[key.account.userProfile()] = it.id }
        } else {
            getSub(subId) ?: newSub(key).also { userSubscriptionMap[key.account.userProfile()] = it.id }
        }
    }

    // One sub per subscribed account
    override fun updateSubscriptions(keys: Set<HomeQueryState>) {
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
