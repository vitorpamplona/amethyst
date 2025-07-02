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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip51FollowSets

import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.aroundMe.LocationTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.global.GlobalTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.hashtag.HashtagTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.allcommunities.AllCommunitiesTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.community.SingleCommunityTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip51FollowSets.subassemblies.filterFollowSetsByAllCommunities
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip51FollowSets.subassemblies.filterFollowSetsByAuthors
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip51FollowSets.subassemblies.filterFollowSetsByCommunity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip51FollowSets.subassemblies.filterFollowSetsByFollows
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip51FollowSets.subassemblies.filterFollowSetsByGeohash
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip51FollowSets.subassemblies.filterFollowSetsByHashtag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip51FollowSets.subassemblies.filterFollowSetsGlobal
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

fun makeFollowSetsFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> =
    when (feedSettings) {
        is AllCommunitiesTopNavPerRelayFilterSet -> filterFollowSetsByAllCommunities(feedSettings, since)
        is AllFollowsByOutboxTopNavPerRelayFilterSet -> filterFollowSetsByFollows(feedSettings, since)
        is AuthorsByOutboxTopNavPerRelayFilterSet -> filterFollowSetsByAuthors(feedSettings, since)
        is GlobalTopNavPerRelayFilterSet -> filterFollowSetsGlobal(feedSettings, since)
        is HashtagTopNavPerRelayFilterSet -> filterFollowSetsByHashtag(feedSettings, since)
        is LocationTopNavPerRelayFilterSet -> filterFollowSetsByGeohash(feedSettings, since)
        is MutedAuthorsByOutboxTopNavPerRelayFilterSet -> filterFollowSetsByAuthors(feedSettings, since)
        is SingleCommunityTopNavPerRelayFilterSet -> filterFollowSetsByCommunity(feedSettings, since)
        else -> emptyList()
    }
