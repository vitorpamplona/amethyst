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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.music.datasource

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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.datasource.subassemblies.MUSIC_PLAYLIST_KINDS
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.datasource.subassemblies.MUSIC_TRACK_KINDS
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.datasource.subassemblies.filterMusicEventsByAllCommunities
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.datasource.subassemblies.filterMusicEventsByAuthors
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.datasource.subassemblies.filterMusicEventsByCommunity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.datasource.subassemblies.filterMusicEventsByFollows
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.datasource.subassemblies.filterMusicEventsByGeohashes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.datasource.subassemblies.filterMusicEventsByHashtag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.datasource.subassemblies.filterMusicEventsByMutedAuthors
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.datasource.subassemblies.filterMusicEventsGlobal
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

/**
 * Builds a per-relay filter set for the music *tracks* feed (kind 36787).
 * Mirrors `makeLongsFilter`/`makeArticlesFilter` — every supported top-bar list selector
 * (follows, authors, global, mute, hashtag, geohash, communities) maps to a dedicated
 * sub-assembly. Anything not listed (e.g. future selectors we don't yet support) returns
 * `emptyList` so we don't subscribe to junk.
 */
fun makeMusicTracksFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> = makeMusicFilter(feedSettings, MUSIC_TRACK_KINDS, since, defaultSince)

/**
 * Builds a per-relay filter set for the music *playlists* feed (kind 34139).
 * Same routing as [makeMusicTracksFilter] — only the kind list differs, so each screen's
 * REQ asks for just the kind it actually renders instead of one combined REQ.
 */
fun makeMusicPlaylistsFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> = makeMusicFilter(feedSettings, MUSIC_PLAYLIST_KINDS, since, defaultSince)

private fun makeMusicFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    kinds: List<Int>,
    since: SincePerRelayMap?,
    defaultSince: Long?,
): List<RelayBasedFilter> =
    when (feedSettings) {
        is AllCommunitiesTopNavPerRelayFilterSet -> filterMusicEventsByAllCommunities(feedSettings, kinds, since, defaultSince)
        is AllFollowsTopNavPerRelayFilterSet -> filterMusicEventsByFollows(feedSettings, kinds, since, defaultSince)
        is AuthorsTopNavPerRelayFilterSet -> filterMusicEventsByAuthors(feedSettings, kinds, since, defaultSince)
        is GlobalTopNavPerRelayFilterSet -> filterMusicEventsGlobal(feedSettings, kinds, since, defaultSince)
        is HashtagTopNavPerRelayFilterSet -> filterMusicEventsByHashtag(feedSettings, kinds, since, defaultSince)
        is LocationTopNavPerRelayFilterSet -> filterMusicEventsByGeohashes(feedSettings, kinds, since, defaultSince)
        is MutedAuthorsTopNavPerRelayFilterSet -> filterMusicEventsByMutedAuthors(feedSettings, kinds, since, defaultSince)
        is SingleCommunityTopNavPerRelayFilterSet -> filterMusicEventsByCommunity(feedSettings, kinds, since, defaultSince)
        else -> emptyList()
    }
