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
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SinceAuthorPerRelayFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

val SUPPORTED_VIDEO_FEED_MIME_TYPES = listOf("image/jpeg", "image/gif", "image/png", "image/webp", "video/mp4", "video/mpeg", "video/webm", "audio/aac", "audio/mpeg", "audio/webm", "audio/wav", "image/avif")
val SUPPORTED_VIDEO_FEED_MIME_TYPES_SET = SUPPORTED_VIDEO_FEED_MIME_TYPES.toSet()

object NostrVideoDataSource : AmethystNostrDataSource("VideoFeed") {
    lateinit var account: Account

    val scope = Amethyst.instance.applicationIOScope
    val latestEOSEs = EOSEAccount()

    var job: Job? = null

    override fun start() {
        job?.cancel()
        job =
            scope.launch(Dispatchers.IO) {
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

    fun createContextualFilter(): List<TypedFilter> {
        val follows = account.liveStoriesListAuthorsPerRelay.value

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
                            latestEOSEs.users[account.userProfile()]
                                ?.followList
                                ?.get(account.settings.defaultStoriesFollowList.value)
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
                            latestEOSEs.users[account.userProfile()]
                                ?.followList
                                ?.get(account.settings.defaultStoriesFollowList.value)
                                ?.relayList,
                    ),
            ),
        )
    }

    fun createFollowTagsFilter(): List<TypedFilter> {
        val hashToLoad =
            account.liveStoriesFollowLists.value
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
                            latestEOSEs.users[account.userProfile()]
                                ?.followList
                                ?.get(account.settings.defaultStoriesFollowList.value)
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
                            latestEOSEs.users[account.userProfile()]
                                ?.followList
                                ?.get(account.settings.defaultStoriesFollowList.value)
                                ?.relayList,
                    ),
            ),
        )
    }

    fun createFollowGeohashesFilter(): List<TypedFilter> {
        val hashToLoad =
            account.liveStoriesFollowLists.value
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
                            latestEOSEs.users[account.userProfile()]
                                ?.followList
                                ?.get(account.settings.defaultStoriesFollowList.value)
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
                            latestEOSEs.users[account.userProfile()]
                                ?.followList
                                ?.get(account.settings.defaultStoriesFollowList.value)
                                ?.relayList,
                    ),
            ),
        )
    }

    val videoFeedChannel =
        requestNewChannel { time, relayUrl ->
            latestEOSEs.addOrUpdate(
                account.userProfile(),
                account.settings.defaultStoriesFollowList.value,
                relayUrl,
                time,
            )
        }

    override fun updateChannelFilters() {
        videoFeedChannel.typedFilters =
            listOfNotNull(
                createContextualFilter(),
                createFollowTagsFilter(),
                createFollowGeohashesFilter(),
            ).flatten().ifEmpty { null }
    }
}
