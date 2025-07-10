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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip65Follows

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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.HomeQueryState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip01Core.filterHomePostsByGeohashes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip01Core.filterHomePostsByGlobal
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip01Core.filterHomePostsByHashtags
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip72Communities.filterHomePostsByAllCommunities
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip72Communities.filterHomePostsByCommunity
import com.vitorpamplona.ammolite.relays.datasources.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeOutboxEventsEoseManager(
    client: NostrClient,
    allKeys: () -> Set<HomeQueryState>,
) : PerUserAndFollowListEoseManager<HomeQueryState, String>(client, allKeys) {
    override fun updateFilter(
        key: HomeQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val feedSettings = key.followsPerRelay()
        return when (feedSettings) {
            is AllCommunitiesTopNavPerRelayFilterSet -> filterHomePostsByAllCommunities(feedSettings, since)
            is AllFollowsByOutboxTopNavPerRelayFilterSet -> filterHomePostsByAllFollows(feedSettings, since)
            is AuthorsByOutboxTopNavPerRelayFilterSet -> filterHomePostsByAuthors(feedSettings, since)
            is GlobalTopNavPerRelayFilterSet -> filterHomePostsByGlobal(feedSettings, since)
            is HashtagTopNavPerRelayFilterSet -> filterHomePostsByHashtags(feedSettings, since)
            is LocationTopNavPerRelayFilterSet -> filterHomePostsByGeohashes(feedSettings, since)
            is MutedAuthorsByOutboxTopNavPerRelayFilterSet -> filterHomePostsByAuthors(feedSettings, since)
            is SingleCommunityTopNavPerRelayFilterSet -> filterHomePostsByCommunity(feedSettings, since)
            else -> emptyList()
        }
    }

    override fun user(key: HomeQueryState) = key.account.userProfile()

    override fun list(key: HomeQueryState) = key.listName()

    fun HomeQueryState.listNameFlow() = account.settings.defaultHomeFollowList

    fun HomeQueryState.listName() = listNameFlow().value

    fun HomeQueryState.followRelayFlow() = account.liveHomeFollowListsPerRelay

    fun HomeQueryState.followsPerRelay() = followRelayFlow().value

    val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: HomeQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.scope.launch(Dispatchers.Default) {
                    key.listNameFlow().collectLatest {
                        invalidateFilters()
                    }
                },
                key.scope.launch(Dispatchers.Default) {
                    key.followRelayFlow().collectLatest {
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
