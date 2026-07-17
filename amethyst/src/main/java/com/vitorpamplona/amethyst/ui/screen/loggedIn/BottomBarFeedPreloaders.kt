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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.ui.navigation.bottombars.BottomBarEntry
import com.vitorpamplona.amethyst.ui.navigation.bottombars.NavBarItem
import com.vitorpamplona.amethyst.ui.screen.loggedIn.articles.datasource.ArticlesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.badges.datasource.BadgesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.datasource.CalendarsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupMyJoinedGroupsSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.datasource.ChatroomListFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.communities.list.datasource.CommunitiesListFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.datasource.DiscoveryFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.browse.datasource.BrowseEmojiSetsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.followPacks.list.datasource.FollowPacksFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepositories.datasource.GitRepositoriesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.HomeFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.livestreams.datasource.LiveStreamsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.longs.datasource.LongsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.datasource.MusicTracksFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.datasource.NestsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.pictures.datasource.PicturesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource.PodcastEpisodesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource.PodcastsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.polls.datasource.PollsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.products.datasource.ProductsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.publicChats.datasource.PublicChatsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.shorts.datasource.ShortsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.softwareapps.datasource.SoftwareAppsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.VideoFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.datasource.WorkoutsFilterAssemblerSubscription

/**
 * Activates the relay subscription for each feed the user has pinned to the bottom
 * bar so its data is preloaded before the tab is opened.
 *
 * The subscriptions track the user's chosen list reactively: removing an icon disposes
 * its subscription, and adding one starts it. Items without a feed (Profile, Wallet,
 * Settings, etc.) and items already covered by [com.vitorpamplona.amethyst.service
 * .relayClient.reqCommand.account.AccountFilterAssemblerSubscription] (Notifications,
 * DM gift wraps) intentionally have no entry here.
 */
@Composable
fun BottomBarFeedPreloaders(accountViewModel: AccountViewModel) {
    val items by accountViewModel.settings.uiSettingsFlow.bottomBarItems
        .collectAsStateWithLifecycle()

    // Only built-in destinations have feeds to preload; favorite-app entries embed their own content.
    items.forEach { entry ->
        val item = (entry as? BottomBarEntry.BuiltIn)?.item ?: return@forEach
        key(item) {
            PreloadFor(item, accountViewModel)
        }
    }
}

@Composable
private fun PreloadFor(
    item: NavBarItem,
    accountViewModel: AccountViewModel,
) {
    when (item) {
        NavBarItem.HOME -> HomeFilterAssemblerSubscription(accountViewModel)

        NavBarItem.MESSAGES -> ChatroomListFilterAssemblerSubscription(accountViewModel)

        NavBarItem.VIDEO -> VideoFilterAssemblerSubscription(accountViewModel)

        NavBarItem.DISCOVER -> DiscoveryFilterAssemblerSubscription(accountViewModel)

        NavBarItem.COMMUNITIES -> CommunitiesListFilterAssemblerSubscription(accountViewModel)

        NavBarItem.ARTICLES -> ArticlesFilterAssemblerSubscription(accountViewModel)

        NavBarItem.PICTURES -> PicturesFilterAssemblerSubscription(accountViewModel)

        NavBarItem.WORKOUTS -> WorkoutsFilterAssemblerSubscription(accountViewModel)

        NavBarItem.GIT_REPOSITORIES -> GitRepositoriesFilterAssemblerSubscription(accountViewModel)

        NavBarItem.SOFTWARE_APPS -> SoftwareAppsFilterAssemblerSubscription(accountViewModel)

        // Napplets & nSites read directly from the local cache; their screens open the discovery
        // subscription themselves, so there's nothing to preload here.
        NavBarItem.NAPPLETS -> {}

        NavBarItem.NSITES -> {}

        // The browser is a "new tab" launcher with no feed to preload.
        NavBarItem.BROWSER -> {}

        // Favorite apps is a device-local launcher grid — nothing to preload from relays.
        NavBarItem.FAVORITE_APPS -> {}

        NavBarItem.CALENDARS,
        NavBarItem.CALENDAR_COLLECTIONS,
        -> CalendarsFilterAssemblerSubscription(accountViewModel)

        NavBarItem.SHORTS -> ShortsFilterAssemblerSubscription(accountViewModel)

        NavBarItem.MUSIC_TRACKS,
        NavBarItem.MUSIC_PLAYLISTS,
        // Same REQ fetches both kinds 36787 + 34139, so one subscription serves both
        // tabs no matter which one the user pinned.
        -> MusicTracksFilterAssemblerSubscription(accountViewModel)

        NavBarItem.PODCAST_EPISODES -> PodcastEpisodesFilterAssemblerSubscription(accountViewModel)

        NavBarItem.PODCASTS -> PodcastsFilterAssemblerSubscription(accountViewModel)

        NavBarItem.PUBLIC_CHATS -> PublicChatsFilterAssemblerSubscription(accountViewModel)

        NavBarItem.RELAY_GROUPS -> RelayGroupMyJoinedGroupsSubscription(accountViewModel.dataSources().relayGroupMyJoinedGroups, accountViewModel)

        NavBarItem.CONCORD -> ConcordChannelSubscription(accountViewModel.dataSources().concordChannels, accountViewModel)

        NavBarItem.FOLLOW_PACKS -> FollowPacksFilterAssemblerSubscription(accountViewModel)

        NavBarItem.LIVE_STREAMS -> LiveStreamsFilterAssemblerSubscription(accountViewModel)

        NavBarItem.NESTS -> NestsFilterAssemblerSubscription(accountViewModel)

        NavBarItem.LONGS -> LongsFilterAssemblerSubscription(accountViewModel)

        NavBarItem.POLLS -> PollsFilterAssemblerSubscription(accountViewModel)

        NavBarItem.BADGES -> BadgesFilterAssemblerSubscription(accountViewModel)

        NavBarItem.PRODUCTS -> ProductsFilterAssemblerSubscription(accountViewModel)

        NavBarItem.EMOJI_SETS -> BrowseEmojiSetsFilterAssemblerSubscription(accountViewModel)

        // Covered by AccountFilterAssemblerSubscription (always-on).
        NavBarItem.NOTIFICATIONS -> Unit

        // Anonymous location channels — the pinned avatar carries no metadata, and the cell's
        // message subscription opens from the chat screen itself, so there's nothing to preload.
        NavBarItem.GEOHASH_CHATS -> Unit

        // No relay feed to preload (loaded by the destination screen on demand).
        NavBarItem.PROFILE,
        NavBarItem.MY_LISTS,
        NavBarItem.BOOKMARKS,
        NavBarItem.WEB_BOOKMARKS,
        NavBarItem.DRAFTS,
        NavBarItem.SCHEDULED_POSTS,
        NavBarItem.INTEREST_SETS,
        NavBarItem.EMOJI_PACKS,
        NavBarItem.WALLET,
        NavBarItem.NOSTR_SIGNER,
        NavBarItem.FAVORITE_ALGO_FEEDS,
        NavBarItem.SETTINGS,
        -> Unit
    }
}
