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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.badges

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.ui.feeds.PagerStateKeys
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.feeds.rememberForeverPagerState
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.badges.datasource.BadgesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Composable
fun BadgesScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedStates = accountViewModel.feedStates

    WatchLifecycleAndUpdateModel(feedStates.badgesReceived)
    WatchLifecycleAndUpdateModel(feedStates.badgesMine)
    WatchLifecycleAndUpdateModel(feedStates.badgesAwarded)
    WatchLifecycleAndUpdateModel(feedStates.badgesDiscover)

    BadgesFilterAssemblerSubscription(accountViewModel)

    AssembleBadgesTabs(
        received = feedStates.badgesReceived,
        mine = feedStates.badgesMine,
        awarded = feedStates.badgesAwarded,
        discover = feedStates.badgesDiscover,
    ) { pagerState, tabs ->
        BadgesPages(pagerState, tabs, accountViewModel, nav)
    }
}

@Composable
private fun AssembleBadgesTabs(
    received: FeedContentState,
    mine: FeedContentState,
    awarded: FeedContentState,
    discover: FeedContentState,
    inner: @Composable (PagerState, ImmutableList<BadgesTabItem>) -> Unit,
) {
    val pagerState = rememberForeverPagerState(key = PagerStateKeys.BADGES_SCREEN) { 4 }

    val tabs by
        remember(received, mine, awarded, discover) {
            mutableStateOf(
                listOf(
                    BadgesTabItem(
                        resource = R.string.received_badges,
                        feedState = received,
                        routeForLastRead = "BadgesReceivedFeed",
                        scrollStateKey = ScrollStateKeys.BADGES_RECEIVED,
                    ),
                    BadgesTabItem(
                        resource = R.string.my_badges,
                        feedState = mine,
                        routeForLastRead = "BadgesMineFeed",
                        scrollStateKey = ScrollStateKeys.BADGES_MINE,
                    ),
                    BadgesTabItem(
                        resource = R.string.awarded_badges,
                        feedState = awarded,
                        routeForLastRead = "BadgesAwardedFeed",
                        scrollStateKey = ScrollStateKeys.BADGES_AWARDED,
                    ),
                    BadgesTabItem(
                        resource = R.string.discover_badges,
                        feedState = discover,
                        routeForLastRead = "BadgesDiscoverFeed",
                        scrollStateKey = ScrollStateKeys.BADGES_DISCOVER,
                    ),
                ).toImmutableList(),
            )
        }

    inner(pagerState, tabs)
}

@Composable
private fun BadgesPages(
    pagerState: PagerState,
    tabs: ImmutableList<BadgesTabItem>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                BadgesTopBar(accountViewModel, nav)
                SecondaryTabRow(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    modifier = TabRowHeight,
                    selectedTabIndex = pagerState.currentPage,
                ) {
                    val coroutineScope = rememberCoroutineScope()
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            text = { Text(text = stringRes(tab.resource)) },
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        )
                    }
                }
            }
        },
        bottomBar = {
            AppBottomBar(Route.Badges, accountViewModel) { route ->
                if (route == Route.Badges) {
                    tabs[pagerState.currentPage].feedState.sendToTop()
                } else {
                    nav.newStack(route)
                }
            }
        },
        floatingButton = {
            NewBadgeButton(nav)
        },
        accountViewModel = accountViewModel,
    ) {
        HorizontalPager(
            contentPadding = it,
            state = pagerState,
            userScrollEnabled = true,
        ) { page ->
            RefresheableBox(tabs[page].feedState, true) {
                SaveableFeedContentState(tabs[page].feedState, scrollStateKey = tabs[page].scrollStateKey) { listState ->
                    RenderFeedContentState(
                        feedContentState = tabs[page].feedState,
                        accountViewModel = accountViewModel,
                        listState = listState,
                        nav = nav,
                        routeForLastRead = tabs[page].routeForLastRead,
                    )
                }
            }
        }
    }
}

@Immutable
class BadgesTabItem(
    val resource: Int,
    val feedState: FeedContentState,
    val routeForLastRead: String,
    val scrollStateKey: String,
)
