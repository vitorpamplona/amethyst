/**
 * Copyright (c) 2024 Vitor Pamplona
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.MainTopBar
import com.vitorpamplona.amethyst.ui.navigation.Route
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

    val markKnownAsRead = remember { mutableStateOf(false) }
    val markNewAsRead = remember { mutableStateOf(false) }

    WatchLifecycleAndUpdateModel(knownFeedContentState)
    WatchLifecycleAndUpdateModel(newFeedContentState)

    ChatroomListFilterAssemblerSubscription(accountViewModel.dataSources().chatroomList, accountViewModel)

    val tabs by
        remember(knownFeedContentState, markKnownAsRead) {
            derivedStateOf {
                listOf(
                    MessagesTabItem(R.string.known, knownFeedContentState, markKnownAsRead),
                    MessagesTabItem(R.string.new_requests, newFeedContentState, markNewAsRead),
                )
            }
        }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                MainTopBar(accountViewModel, nav)
                MessagesTabHeader(
                    pagerState,
                    tabs,
                    { markKnownAsRead.value = true },
                    { markNewAsRead.value = true },
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
