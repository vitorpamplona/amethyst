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
import com.vitorpamplona.amethyst.ui.navigation.bottombars.NavBarItem
import com.vitorpamplona.amethyst.ui.screen.loggedIn.articles.datasource.ArticlesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.badges.datasource.BadgesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.datasource.ChatroomListFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.communities.list.datasource.CommunitiesListFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.datasource.DiscoveryFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.browse.datasource.BrowseEmojiSetsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.followPacks.list.datasource.FollowPacksFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.HomeFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.livestreams.datasource.LiveStreamsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.longs.datasource.LongsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.datasource.NestsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.pictures.datasource.PicturesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.polls.datasource.PollsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.products.datasource.ProductsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.publicChats.datasource.PublicChatsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.shorts.datasource.ShortsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.VideoFilterAssemblerSubscription

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

    items.forEach { item ->
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

        NavBarItem.SHORTS -> ShortsFilterAssemblerSubscription(accountViewModel)

        NavBarItem.PUBLIC_CHATS -> PublicChatsFilterAssemblerSubscription(accountViewModel)

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

        // No relay feed to preload (loaded by the destination screen on demand).
        NavBarItem.PROFILE,
        NavBarItem.MY_LISTS,
        NavBarItem.BOOKMARKS,
        NavBarItem.WEB_BOOKMARKS,
        NavBarItem.DRAFTS,
        NavBarItem.INTEREST_SETS,
        NavBarItem.EMOJI_PACKS,
        NavBarItem.WALLET,
        NavBarItem.SETTINGS,
        -> Unit
    }
}
