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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities

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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities.subassemblies.filterLiveActivitiesByAllCommunities
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities.subassemblies.filterLiveActivitiesByAuthors
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities.subassemblies.filterLiveActivitiesByCommunity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities.subassemblies.filterLiveActivitiesByFollows
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities.subassemblies.filterLiveActivitiesByGeohash
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities.subassemblies.filterLiveActivitiesByHashtag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities.subassemblies.filterLiveActivitiesGlobal
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

fun makeLiveActivitiesFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> =
    when (feedSettings) {
        is AllCommunitiesTopNavPerRelayFilterSet -> filterLiveActivitiesByAllCommunities(feedSettings, since, defaultSince)
        is AllFollowsTopNavPerRelayFilterSet -> filterLiveActivitiesByFollows(feedSettings, since, defaultSince)
        is AuthorsTopNavPerRelayFilterSet -> filterLiveActivitiesByAuthors(feedSettings, since, defaultSince)
        is GlobalTopNavPerRelayFilterSet -> filterLiveActivitiesGlobal(feedSettings, since, defaultSince)
        is HashtagTopNavPerRelayFilterSet -> filterLiveActivitiesByHashtag(feedSettings, since, defaultSince)
        is LocationTopNavPerRelayFilterSet -> filterLiveActivitiesByGeohash(feedSettings, since, defaultSince)
        is MutedAuthorsTopNavPerRelayFilterSet -> filterLiveActivitiesByAuthors(feedSettings, since, defaultSince)
        is SingleCommunityTopNavPerRelayFilterSet -> filterLiveActivitiesByCommunity(feedSettings, since, defaultSince)
        else -> emptyList()
    }
