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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.polls

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.polls.datasource.PollsFilterAssemblerSubscription

@Composable
fun PollsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    PollsScreen(
        pollsFeedContentState = accountViewModel.feedStates.pollsFeed,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun PollsScreen(
    pollsFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(pollsFeedContentState)
    WatchAccountForPollsScreen(pollsFeedContentState = pollsFeedContentState, accountViewModel = accountViewModel)
    PollsFilterAssemblerSubscription(accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            PollsTopBar(accountViewModel, nav)
        },
        bottomBar = {
            AppBottomBar(Route.Polls, accountViewModel) { route ->
                if (route == Route.Polls) {
                    pollsFeedContentState.sendToTop()
                } else {
                    nav.newStack(route)
                }
            }
        },
        accountViewModel = accountViewModel,
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            RefresheableBox(pollsFeedContentState, true) {
                SaveableFeedContentState(pollsFeedContentState, scrollStateKey = ScrollStateKeys.POLLS_SCREEN) { listState ->
                    RenderFeedContentState(
                        feedContentState = pollsFeedContentState,
                        accountViewModel = accountViewModel,
                        listState = listState,
                        nav = nav,
                        routeForLastRead = "PollsFeed",
                    )
                }
            }
        }
    }
}

@Composable
fun WatchAccountForPollsScreen(
    pollsFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.livePollsFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers =
        accountViewModel.account.hiddenUsers.flow
            .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        pollsFeedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}
