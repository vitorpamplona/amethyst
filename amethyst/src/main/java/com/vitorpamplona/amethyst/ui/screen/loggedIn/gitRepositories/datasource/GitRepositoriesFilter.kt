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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepositories.datasource

import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.aroundMe.LocationTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.global.GlobalTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.hashtag.HashtagTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.allcommunities.AllCommunitiesTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.community.SingleCommunityTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepositories.datasource.subassemblies.filterGitRepositoriesByAllCommunities
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepositories.datasource.subassemblies.filterGitRepositoriesByAuthors
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepositories.datasource.subassemblies.filterGitRepositoriesByCommunity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepositories.datasource.subassemblies.filterGitRepositoriesByFollows
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepositories.datasource.subassemblies.filterGitRepositoriesByGeohashes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepositories.datasource.subassemblies.filterGitRepositoriesByHashtag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepositories.datasource.subassemblies.filterGitRepositoriesByMutedAuthors
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepositories.datasource.subassemblies.filterGitRepositoriesGlobal
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

fun makeGitRepositoriesFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> =
    when (feedSettings) {
        is AllCommunitiesTopNavPerRelayFilterSet -> filterGitRepositoriesByAllCommunities(feedSettings, since, defaultSince)
        is AllFollowsTopNavPerRelayFilterSet -> filterGitRepositoriesByFollows(feedSettings, since, defaultSince)
        is AuthorsTopNavPerRelayFilterSet -> filterGitRepositoriesByAuthors(feedSettings, since, defaultSince)
        is GlobalTopNavPerRelayFilterSet -> filterGitRepositoriesGlobal(feedSettings, since, defaultSince)
        is HashtagTopNavPerRelayFilterSet -> filterGitRepositoriesByHashtag(feedSettings, since, defaultSince)
        is LocationTopNavPerRelayFilterSet -> filterGitRepositoriesByGeohashes(feedSettings, since, defaultSince)
        is MutedAuthorsTopNavPerRelayFilterSet -> filterGitRepositoriesByMutedAuthors(feedSettings, since, defaultSince)
        is SingleCommunityTopNavPerRelayFilterSet -> filterGitRepositoriesByCommunity(feedSettings, since, defaultSince)
        else -> emptyList()
    }
