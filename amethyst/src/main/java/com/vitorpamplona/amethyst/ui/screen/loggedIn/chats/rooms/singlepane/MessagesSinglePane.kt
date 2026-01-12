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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.singlepane

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.AmethystClickableIcon
import com.vitorpamplona.amethyst.ui.navigation.topbars.UserDrawerSearchTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.ChannelFabColumn
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.datasource.ChatroomListFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.feed.MessagesPager
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.feed.MessagesTabHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.feed.MessagesTabItem

@Composable
fun MessagesSinglePane(
    knownFeedContentState: FeedContentState,
    newFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pagerState = rememberPagerState { 2 }

    WatchLifecycleAndUpdateModel(knownFeedContentState)
    WatchLifecycleAndUpdateModel(newFeedContentState)

    ChatroomListFilterAssemblerSubscription(accountViewModel)

    val tabs by
        remember(knownFeedContentState) {
            derivedStateOf {
                listOf(
                    MessagesTabItem(R.string.known, ScrollStateKeys.MESSAGES_KNOWN, knownFeedContentState),
                    MessagesTabItem(R.string.new_requests, ScrollStateKeys.MESSAGES_NEW, newFeedContentState),
                )
            }
        }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                UserDrawerSearchTopBar(accountViewModel, nav) { AmethystClickableIcon() }
                MessagesTabHeader(
                    pagerState,
                    tabs,
                    { accountViewModel.markAllChatNotesAsRead(knownFeedContentState.visibleNotes()) },
                    { accountViewModel.markAllChatNotesAsRead(newFeedContentState.visibleNotes()) },
                )
            }
        },
        bottomBar = {
            AppBottomBar(Route.Message, accountViewModel) { route ->
                if (route == Route.Message) {
                    tabs[pagerState.currentPage].feedContentState.sendToTop()
                } else {
                    nav.newStack(route)
                }
            }
        },
        floatingButton = {
            ChannelFabColumn(nav)
        },
        accountViewModel = accountViewModel,
    ) {
        MessagesPager(
            pagerState,
            tabs,
            it,
            accountViewModel,
            nav,
        )
    }
}
