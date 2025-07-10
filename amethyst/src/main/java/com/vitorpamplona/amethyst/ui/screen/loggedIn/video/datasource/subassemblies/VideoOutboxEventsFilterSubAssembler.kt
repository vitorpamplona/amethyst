/**
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.subassemblies

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.aroundMe.LocationTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.global.GlobalTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.hashtag.HashtagTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.allcommunities.AllCommunitiesTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.community.SingleCommunityTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserAndFollowListEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.VideoQueryState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.subassemblies.nip01Core.filterPictureAndVideoByGeohash
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.subassemblies.nip01Core.filterPictureAndVideoByHashtag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.subassemblies.nip01Core.filterPictureAndVideoGlobal
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.subassemblies.nip65Follows.filterPictureAndVideoByAuthors
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.subassemblies.nip65Follows.filterPictureAndVideoByFollows
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.subassemblies.nip72Communities.filterPictureAndVideoByAllCommunities
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.subassemblies.nip72Communities.filterPictureAndVideoByCommunity
import com.vitorpamplona.ammolite.relays.datasources.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

class VideoOutboxEventsFilterSubAssembler(
    client: NostrClient,
    allKeys: () -> Set<VideoQueryState>,
) : PerUserAndFollowListEoseManager<VideoQueryState, String>(client, allKeys) {
    override fun updateFilter(
        key: VideoQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val feedSettings = key.followsPerRelay()
        return when (feedSettings) {
            is AllCommunitiesTopNavPerRelayFilterSet -> filterPictureAndVideoByAllCommunities(feedSettings, since)
            is AllFollowsByOutboxTopNavPerRelayFilterSet -> filterPictureAndVideoByFollows(feedSettings, since)
            is AuthorsByOutboxTopNavPerRelayFilterSet -> filterPictureAndVideoByAuthors(feedSettings, since)
            is GlobalTopNavPerRelayFilterSet -> filterPictureAndVideoGlobal(feedSettings, since)
            is HashtagTopNavPerRelayFilterSet -> filterPictureAndVideoByHashtag(feedSettings, since)
            is LocationTopNavPerRelayFilterSet -> filterPictureAndVideoByGeohash(feedSettings, since)
            is MutedAuthorsByOutboxTopNavPerRelayFilterSet -> filterPictureAndVideoByAuthors(feedSettings, since)
            is SingleCommunityTopNavPerRelayFilterSet -> filterPictureAndVideoByCommunity(feedSettings, since)
            else -> emptyList()
        }
    }

    override fun user(key: VideoQueryState) = key.account.userProfile()

    override fun list(key: VideoQueryState) = key.listName()

    fun VideoQueryState.listNameFlow() = account.settings.defaultStoriesFollowList

    fun VideoQueryState.listName() = listNameFlow().value

    fun VideoQueryState.followsPerRelayFlow() = account.liveStoriesFollowListsPerRelay

    fun VideoQueryState.followsPerRelay() = followsPerRelayFlow().value

    val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: VideoQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.scope.launch(Dispatchers.Default) {
                    key.followsPerRelayFlow().sample(500).collectLatest {
                        invalidateFilters()
                    }
                },
            )

        return super.newSub(key)
    }

    override fun endSub(
        key: User,
        subId: String,
    ) {
        super.endSub(key, subId)
        userJobMap[key]?.forEach { it.cancel() }
    }
}
