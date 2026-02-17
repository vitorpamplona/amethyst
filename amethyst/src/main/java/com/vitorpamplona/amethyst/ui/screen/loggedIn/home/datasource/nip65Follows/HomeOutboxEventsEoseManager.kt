/*
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
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.aroundMe.LocationTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.chess.ChessTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.global.GlobalTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.hashtag.HashtagTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.allcommunities.AllCommunitiesTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.community.SingleCommunityTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserAndFollowListEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.HomeQueryState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip01Core.filterHomePostsByGeohashes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip01Core.filterHomePostsByGlobal
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip01Core.filterHomePostsByHashtags
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip64Chess.filterHomePostsByChess
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip72Communities.filterHomePostsByAllCommunities
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip72Communities.filterHomePostsByCommunity
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

class HomeOutboxEventsEoseManager(
    client: INostrClient,
    allKeys: () -> Set<HomeQueryState>,
) : PerUserAndFollowListEoseManager<HomeQueryState, String>(client, allKeys) {
    override fun updateFilter(
        key: HomeQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val feedSettings = key.followsPerRelay()
        val newThreadSince = key.feedState.homeNewThreads.lastNoteCreatedAtIfFilled()
        val repliesSince = key.feedState.homeReplies.lastNoteCreatedAtIfFilled()
        return when (feedSettings) {
            is AllCommunitiesTopNavPerRelayFilterSet -> filterHomePostsByAllCommunities(feedSettings, since, newThreadSince)
            is AllFollowsTopNavPerRelayFilterSet -> filterHomePostsByAllFollows(feedSettings, since, newThreadSince, repliesSince)
            is AuthorsTopNavPerRelayFilterSet -> filterHomePostsByAuthors(feedSettings, since, newThreadSince, repliesSince)
            is ChessTopNavPerRelayFilterSet -> filterHomePostsByChess(feedSettings, since, newThreadSince)
            is GlobalTopNavPerRelayFilterSet -> filterHomePostsByGlobal(feedSettings, since, newThreadSince, repliesSince)
            is HashtagTopNavPerRelayFilterSet -> filterHomePostsByHashtags(feedSettings, since, newThreadSince)
            is LocationTopNavPerRelayFilterSet -> filterHomePostsByGeohashes(feedSettings, since, newThreadSince)
            is MutedAuthorsTopNavPerRelayFilterSet -> filterHomePostsByAuthors(feedSettings, since, newThreadSince, repliesSince)
            is SingleCommunityTopNavPerRelayFilterSet -> filterHomePostsByCommunity(feedSettings, since, newThreadSince)
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
                key.scope.launch(Dispatchers.IO) {
                    key.listNameFlow().collectLatest {
                        invalidateFilters()
                    }
                },
                key.scope.launch(Dispatchers.IO) {
                    key.followRelayFlow().sample(1000).collectLatest {
                        invalidateFilters()
                    }
                },
                key.account.scope.launch(Dispatchers.IO) {
                    key.feedState.homeNewThreads.lastNoteCreatedAtWhenFullyLoaded.sample(5000).collectLatest {
                        invalidateFilters()
                    }
                },
                key.account.scope.launch(Dispatchers.IO) {
                    key.feedState.homeReplies.lastNoteCreatedAtWhenFullyLoaded.sample(5000).collectLatest {
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
