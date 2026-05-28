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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource

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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource.subassemblies.PODCAST_EPISODE_KINDS
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource.subassemblies.PODCAST_KINDS
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource.subassemblies.filterPodcastEventsByAllCommunities
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource.subassemblies.filterPodcastEventsByAuthors
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource.subassemblies.filterPodcastEventsByCommunity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource.subassemblies.filterPodcastEventsByFollows
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource.subassemblies.filterPodcastEventsByGeohashes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource.subassemblies.filterPodcastEventsByHashtag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource.subassemblies.filterPodcastEventsByMutedAuthors
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource.subassemblies.filterPodcastEventsGlobal
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

fun makePodcastEpisodesFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> = makePodcastFilter(feedSettings, PODCAST_EPISODE_KINDS, since, defaultSince)

fun makePodcastsFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> = makePodcastFilter(feedSettings, PODCAST_KINDS, since, defaultSince)

private fun makePodcastFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    kinds: List<Int>,
    since: SincePerRelayMap?,
    defaultSince: Long?,
): List<RelayBasedFilter> =
    when (feedSettings) {
        is AllCommunitiesTopNavPerRelayFilterSet -> filterPodcastEventsByAllCommunities(feedSettings, kinds, since, defaultSince)
        is AllFollowsTopNavPerRelayFilterSet -> filterPodcastEventsByFollows(feedSettings, kinds, since, defaultSince)
        is AuthorsTopNavPerRelayFilterSet -> filterPodcastEventsByAuthors(feedSettings, kinds, since, defaultSince)
        is GlobalTopNavPerRelayFilterSet -> filterPodcastEventsGlobal(feedSettings, kinds, since, defaultSince)
        is HashtagTopNavPerRelayFilterSet -> filterPodcastEventsByHashtag(feedSettings, kinds, since, defaultSince)
        is LocationTopNavPerRelayFilterSet -> filterPodcastEventsByGeohashes(feedSettings, kinds, since, defaultSince)
        is MutedAuthorsTopNavPerRelayFilterSet -> filterPodcastEventsByMutedAuthors(feedSettings, kinds, since, defaultSince)
        is SingleCommunityTopNavPerRelayFilterSet -> filterPodcastEventsByCommunity(feedSettings, kinds, since, defaultSince)
        else -> emptyList()
    }
