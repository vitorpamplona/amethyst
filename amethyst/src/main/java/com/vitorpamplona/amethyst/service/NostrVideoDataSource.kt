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
import com.vitorpamplona.ammolite.relays.Filter
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.quartz.events.FileHeaderEvent
import com.vitorpamplona.quartz.events.FileStorageHeaderEvent
import com.vitorpamplona.quartz.events.VideoHorizontalEvent
import com.vitorpamplona.quartz.events.VideoVerticalEvent
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

    fun createContextualFilter(): TypedFilter {
        val follows =
            account.liveStoriesFollowLists.value
                ?.users
                ?.toList()

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter =
                Filter(
                    authors = follows,
                    kinds = listOf(FileHeaderEvent.KIND, FileStorageHeaderEvent.KIND, VideoHorizontalEvent.KIND, VideoVerticalEvent.KIND),
                    limit = 200,
                    tags = mapOf("m" to SUPPORTED_VIDEO_FEED_MIME_TYPES),
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.defaultStoriesFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createFollowTagsFilter(): TypedFilter? {
        val hashToLoad =
            account.liveStoriesFollowLists.value
                ?.hashtags
                ?.toList() ?: return null

        if (hashToLoad.isEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter =
                Filter(
                    kinds = listOf(FileHeaderEvent.KIND, FileStorageHeaderEvent.KIND, VideoHorizontalEvent.KIND, VideoVerticalEvent.KIND),
                    tags =
                        mapOf(
                            "t" to
                                hashToLoad
                                    .map { listOf(it, it.lowercase(), it.uppercase(), it.capitalize()) }
                                    .flatten(),
                            "m" to SUPPORTED_VIDEO_FEED_MIME_TYPES,
                        ),
                    limit = 100,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.defaultStoriesFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createFollowGeohashesFilter(): TypedFilter? {
        val hashToLoad =
            account.liveStoriesFollowLists.value
                ?.geotags
                ?.toList() ?: return null

        if (hashToLoad.isEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter =
                Filter(
                    kinds = listOf(FileHeaderEvent.KIND, FileStorageHeaderEvent.KIND, VideoHorizontalEvent.KIND, VideoVerticalEvent.KIND),
                    tags =
                        mapOf(
                            "g" to
                                hashToLoad
                                    .map { listOf(it, it.lowercase(), it.uppercase(), it.capitalize()) }
                                    .flatten(),
                            "m" to SUPPORTED_VIDEO_FEED_MIME_TYPES,
                        ),
                    limit = 100,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.defaultStoriesFollowList.value)
                            ?.relayList,
                ),
        )
    }

    val videoFeedChannel =
        requestNewChannel { time, relayUrl ->
            latestEOSEs.addOrUpdate(
                account.userProfile(),
                account.defaultStoriesFollowList.value,
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
            ).ifEmpty { null }
    }
}
