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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource

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
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.collections.flatten

val SUPPORTED_VIDEO_FEED_MIME_TYPES = listOf("image/jpeg", "image/gif", "image/png", "image/webp", "video/mp4", "video/mpeg", "video/webm", "audio/aac", "audio/mpeg", "audio/webm", "audio/wav", "image/avif")
val SUPPORTED_VIDEO_FEED_MIME_TYPES_SET = SUPPORTED_VIDEO_FEED_MIME_TYPES.toSet()

// This allows multiple screen to be listening to tags, even the same tag
class VideoQueryState(
    val account: Account,
    val scope: CoroutineScope,
)

class VideoFilterAssembler(
    client: NostrClient,
) : QueryBasedSubscriptionOrchestrator<VideoQueryState>(client) {
    val latestEOSEs = EOSEAccount()

    fun createContextualFilter(key: VideoQueryState): List<TypedFilter> {
        val follows = key.account.liveStoriesListAuthorsPerRelay.value

        val types = if (follows == null) setOf(FeedType.GLOBAL) else setOf(FeedType.FOLLOWS)

        return listOf(
            TypedFilter(
                types = types,
                filter =
                    SinceAuthorPerRelayFilter(
                        authors = follows,
                        kinds = listOf(PictureEvent.KIND, VideoHorizontalEvent.KIND, VideoVerticalEvent.KIND),
                        limit = 200,
                        since =
                            latestEOSEs.users[key.account.userProfile()]
                                ?.followList
                                ?.get(key.account.settings.defaultStoriesFollowList.value)
                                ?.relayList,
                    ),
            ),
            TypedFilter(
                types = types,
                filter =
                    SinceAuthorPerRelayFilter(
                        authors = follows,
                        kinds = listOf(FileHeaderEvent.KIND, FileStorageHeaderEvent.KIND),
                        limit = 200,
                        tags = mapOf("m" to SUPPORTED_VIDEO_FEED_MIME_TYPES),
                        since =
                            latestEOSEs.users[key.account.userProfile()]
                                ?.followList
                                ?.get(key.account.settings.defaultStoriesFollowList.value)
                                ?.relayList,
                    ),
            ),
        )
    }

    fun createFollowTagsFilter(key: VideoQueryState): List<TypedFilter> {
        val hashToLoad =
            key.account.liveStoriesFollowLists.value
                ?.hashtags
                ?.toList() ?: return emptyList()

        if (hashToLoad.isEmpty()) return emptyList()

        val hashtags =
            hashToLoad
                .map { listOf(it, it.lowercase(), it.uppercase(), it.capitalize()) }
                .flatten()

        return listOf(
            TypedFilter(
                types = setOf(FeedType.GLOBAL),
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(PictureEvent.KIND, VideoHorizontalEvent.KIND, VideoVerticalEvent.KIND),
                        tags = mapOf("t" to hashtags),
                        limit = 100,
                        since =
                            latestEOSEs.users[key.account.userProfile()]
                                ?.followList
                                ?.get(key.account.settings.defaultStoriesFollowList.value)
                                ?.relayList,
                    ),
            ),
            TypedFilter(
                types = setOf(FeedType.GLOBAL),
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(FileHeaderEvent.KIND, FileStorageHeaderEvent.KIND),
                        tags =
                            mapOf(
                                "t" to hashtags,
                                "m" to SUPPORTED_VIDEO_FEED_MIME_TYPES,
                            ),
                        limit = 100,
                        since =
                            latestEOSEs.users[key.account.userProfile()]
                                ?.followList
                                ?.get(key.account.settings.defaultStoriesFollowList.value)
                                ?.relayList,
                    ),
            ),
        )
    }

    fun createFollowGeohashesFilter(key: VideoQueryState): List<TypedFilter> {
        val hashToLoad =
            key.account.liveStoriesFollowLists.value
                ?.geotags
                ?.toList() ?: return emptyList()

        if (hashToLoad.isEmpty()) return emptyList()

        val geoHashes = hashToLoad

        return listOf(
            TypedFilter(
                types = setOf(FeedType.GLOBAL),
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(PictureEvent.KIND, VideoHorizontalEvent.KIND, VideoVerticalEvent.KIND),
                        tags = mapOf("g" to geoHashes),
                        limit = 100,
                        since =
                            latestEOSEs.users[key.account.userProfile()]
                                ?.followList
                                ?.get(key.account.settings.defaultStoriesFollowList.value)
                                ?.relayList,
                    ),
            ),
            TypedFilter(
                types = setOf(FeedType.GLOBAL),
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(FileHeaderEvent.KIND, FileStorageHeaderEvent.KIND),
                        tags =
                            mapOf(
                                "g" to geoHashes,
                                "m" to SUPPORTED_VIDEO_FEED_MIME_TYPES,
                            ),
                        limit = 100,
                        since =
                            latestEOSEs.users[key.account.userProfile()]
                                ?.followList
                                ?.get(key.account.settings.defaultStoriesFollowList.value)
                                ?.relayList,
                    ),
            ),
        )
    }

    fun mergeAllFilters(key: VideoQueryState): List<TypedFilter>? =
        listOfNotNull(
            createContextualFilter(key),
            createFollowTagsFilter(key),
            createFollowGeohashesFilter(key),
        ).flatten().ifEmpty { null }

    val userJobMap = mutableMapOf<User, Job>()
    val userSubscriptionMap = mutableMapOf<User, String>()

    fun newSub(key: VideoQueryState): Subscription {
        userJobMap[key.account.userProfile()]?.cancel()
        userJobMap[key.account.userProfile()] =
            key.scope.launch(Dispatchers.Default) {
                key.account.liveStoriesFollowLists.collect {
                    invalidateFilters()
                }
            }

        return requestNewSubscription { time, relayUrl ->
            latestEOSEs.addOrUpdate(
                key.account.userProfile(),
                key.account.settings.defaultStoriesFollowList.value,
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
    }

    fun findOrCreateSubFor(key: VideoQueryState): Subscription {
        var subId = userSubscriptionMap[key.account.userProfile()]
        return if (subId == null) {
            newSub(key).also { userSubscriptionMap[key.account.userProfile()] = it.id }
        } else {
            getSub(subId) ?: newSub(key).also { userSubscriptionMap[key.account.userProfile()] = it.id }
        }
    }

    // One sub per subscribed account
    override fun updateSubscriptions(keys: Set<VideoQueryState>) {
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
