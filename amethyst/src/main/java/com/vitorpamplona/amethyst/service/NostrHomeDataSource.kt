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
import com.vitorpamplona.quartz.events.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.events.AudioHeaderEvent
import com.vitorpamplona.quartz.events.AudioTrackEvent
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.HighlightEvent
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.LongTextNoteEvent
import com.vitorpamplona.quartz.events.MetadataEvent
import com.vitorpamplona.quartz.events.PinListEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.RepostEvent
import com.vitorpamplona.quartz.events.TextNoteEvent
import com.vitorpamplona.quartz.events.WikiNoteEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object NostrHomeDataSource : AmethystNostrDataSource("HomeFeed") {
    lateinit var account: Account

    val scope = Amethyst.instance.applicationIOScope
    val latestEOSEs = EOSEAccount()

    var job: Job? = null
    var job2: Job? = null

    override fun start() {
        job?.cancel()
        job =
            scope.launch(Dispatchers.IO) {
                account.liveHomeFollowLists.collect {
                    if (this@NostrHomeDataSource::account.isInitialized) {
                        invalidateFilters()
                    }
                }
            }

        job2?.cancel()
        job2 =
            scope.launch(Dispatchers.IO) {
                account.liveHomeListAuthorsPerRelay.collect {
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
        job2?.cancel()
    }

    fun createFollowAccountsFilter(): TypedFilter {
        val follows =
            account.liveHomeListAuthorsPerRelay.value

        return TypedFilter(
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
                            PollNoteEvent.KIND,
                            HighlightEvent.KIND,
                            AudioTrackEvent.KIND,
                            AudioHeaderEvent.KIND,
                            PinListEvent.KIND,
                            LiveActivitiesChatMessageEvent.KIND,
                            LiveActivitiesEvent.KIND,
                            WikiNoteEvent.KIND,
                        ),
                    authors = follows,
                    limit = 400,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.settings.defaultHomeFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createFollowMetadataAndReleaseFilter(): TypedFilter? {
        val follows = account.liveHomeListAuthorsPerRelay.value

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
                            latestEOSEs.users[account.userProfile()]
                                ?.followList
                                ?.get(account.settings.defaultHomeFollowList.value)
                                ?.relayList,
                    ),
            )
        } else {
            null
        }
    }

    fun createFollowTagsFilter(): TypedFilter? {
        val hashToLoad = account.liveHomeFollowLists.value?.hashtags ?: return null

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
                            AudioTrackEvent.KIND,
                            PinListEvent.KIND,
                            WikiNoteEvent.KIND,
                        ),
                    tags =
                        mapOf(
                            "t" to
                                hashToLoad
                                    .map { listOf(it, it.lowercase(), it.uppercase(), it.capitalize()) }
                                    .flatten(),
                        ),
                    limit = 100,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.settings.defaultHomeFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createFollowGeohashesFilter(): TypedFilter? {
        val hashToLoad = account.liveHomeFollowLists.value?.geotags ?: return null

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
                            AudioTrackEvent.KIND,
                            PinListEvent.KIND,
                            WikiNoteEvent.KIND,
                        ),
                    tags =
                        mapOf(
                            "g" to
                                hashToLoad
                                    .map { listOf(it.lowercase()) }
                                    .flatten(),
                        ),
                    limit = 100,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.settings.defaultHomeFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createFollowCommunitiesFilter(): TypedFilter? {
        val communitiesToLoad = account.liveHomeFollowLists.value?.addresses ?: return null

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
                            AudioHeaderEvent.KIND,
                            AudioTrackEvent.KIND,
                            PinListEvent.KIND,
                            WikiNoteEvent.KIND,
                            CommunityPostApprovalEvent.KIND,
                        ),
                    tags =
                        mapOf(
                            "a" to communitiesToLoad.toList(),
                        ),
                    limit = 100,
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.settings.defaultHomeFollowList.value)
                            ?.relayList,
                ),
        )
    }

    val followAccountChannel =
        requestNewChannel { time, relayUrl ->
            latestEOSEs.addOrUpdate(
                account.userProfile(),
                account.settings.defaultHomeFollowList.value,
                relayUrl,
                time,
            )
        }

    override fun updateChannelFilters() {
        followAccountChannel.typedFilters =
            listOfNotNull(
                createFollowAccountsFilter(),
                createFollowMetadataAndReleaseFilter(),
                createFollowCommunitiesFilter(),
                createFollowTagsFilter(),
                createFollowGeohashesFilter(),
            ).ifEmpty { null }
    }
}
