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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.service.OnlineChecker
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.ui.feeds.PagerStateKeys
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.feeds.rememberForeverPagerState
import com.vitorpamplona.amethyst.ui.navigation.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.note.UpdateZapAmountDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
    nip47: String? = null,
) {
    ResolveNIP47(nip47, accountViewModel)

    HomeScreen(
        newThreadsFeedState = accountViewModel.feedStates.homeNewThreads,
        repliesFeedState = accountViewModel.feedStates.homeReplies,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    newThreadsFeedState: FeedContentState,
    repliesFeedState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchAccountForHomeScreen(newThreadsFeedState, repliesFeedState, accountViewModel)

    WatchLifeCycleChanges(accountViewModel)

    AssembleHomeTabs(newThreadsFeedState, repliesFeedState) { pagerState, tabItems ->
        HomePages(pagerState, tabItems, accountViewModel, nav)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssembleHomeTabs(
    newThreadsFeedState: FeedContentState,
    repliesFeedState: FeedContentState,
    inner: @Composable (PagerState, ImmutableList<TabItem>) -> Unit,
) {
    val pagerState = rememberForeverPagerState(key = PagerStateKeys.HOME_SCREEN) { 2 }

    val tabs by
        remember(newThreadsFeedState, repliesFeedState) {
            mutableStateOf(
                listOf(
                    TabItem(
                        resource = R.string.new_threads,
                        feedState = newThreadsFeedState,
                        routeForLastRead = Route.Home.base + "Follows",
                        scrollStateKey = ScrollStateKeys.HOME_FOLLOWS,
                    ),
                    TabItem(
                        resource = R.string.conversations,
                        feedState = repliesFeedState,
                        routeForLastRead = Route.Home.base + "FollowsReplies",
                        scrollStateKey = ScrollStateKeys.HOME_REPLIES,
                    ),
                ).toImmutableList(),
            )
        }

    inner(pagerState, tabs)
}

@Composable
fun ResolveNIP47(
    nip47: String?,
    accountViewModel: AccountViewModel,
) {
    var wantsToAddNip47 by remember(nip47) { mutableStateOf(nip47) }

    if (wantsToAddNip47 != null) {
        UpdateZapAmountDialog({ wantsToAddNip47 = null }, wantsToAddNip47, accountViewModel)
    }
}

@Composable
private fun WatchLifeCycleChanges(accountViewModel: AccountViewModel) {
    val lifeCycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    NostrHomeDataSource.account = accountViewModel.account
                    NostrHomeDataSource.invalidateFilters()
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HomePages(
    pagerState: PagerState,
    tabs: ImmutableList<TabItem>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                HomeTopBar(accountViewModel, nav)
                TabRow(
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
            AppBottomBar(Route.Home, accountViewModel) { route, _ ->
                if (route == Route.Home) {
                    tabs[pagerState.currentPage].feedState.sendToTop()
                } else {
                    nav.newStack(route.base)
                }
            }
        },
        floatingButton = {
            NewNoteButton(accountViewModel, nav)
        },
        accountViewModel = accountViewModel,
    ) {
        HorizontalPager(
            contentPadding = it,
            state = pagerState,
            userScrollEnabled = false,
        ) { page ->
            HomeFeeds(
                feedState = tabs[page].feedState,
                routeForLastRead = tabs[page].routeForLastRead,
                scrollStateKey = tabs[page].scrollStateKey,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun HomeFeeds(
    feedState: FeedContentState,
    routeForLastRead: String?,
    enablePullRefresh: Boolean = true,
    scrollStateKey: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RefresheableBox(feedState, enablePullRefresh) {
        SaveableFeedContentState(feedState, scrollStateKey) { listState ->
            RenderFeedContentState(
                feedContentState = feedState,
                accountViewModel = accountViewModel,
                listState = listState,
                nav = nav,
                routeForLastRead = routeForLastRead,
                onEmpty = { HomeFeedEmpty(feedState::invalidateData) },
            )
        }
    }
}

@Preview
@Composable
fun HomeFeedEmptyPreview() {
    ThemeComparisonRow(
        toPreview = { HomeFeedEmpty {} },
    )
}

@Composable
fun HomeFeedEmpty(onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringRes(R.string.feed_is_empty))
        Spacer(modifier = StdVertSpacer)
        OutlinedButton(onClick = onRefresh) { Text(text = stringRes(R.string.refresh)) }
    }
}

@Composable
fun CheckIfVideoIsOnline(
    url: String,
    accountViewModel: AccountViewModel,
    whenOnline: @Composable (Boolean) -> Unit,
) {
    var online by remember {
        mutableStateOf(
            OnlineChecker.isOnlineCached(url),
        )
    }

    LaunchedEffect(key1 = url) {
        accountViewModel.checkVideoIsOnline(url) { isOnline ->
            if (online != isOnline) {
                online = isOnline
            }
        }
    }

    whenOnline(online)
}

@Composable
fun CrossfadeCheckIfVideoIsOnline(
    url: String,
    accountViewModel: AccountViewModel,
    whenOnline: @Composable () -> Unit,
) {
    var online by remember {
        mutableStateOf(
            OnlineChecker.isOnlineCached(url),
        )
    }

    LaunchedEffect(key1 = url) {
        accountViewModel.checkVideoIsOnline(url) { isOnline ->
            if (online != isOnline) {
                online = isOnline
            }
        }
    }

    CrossfadeIfEnabled(
        targetState = online,
        label = "CheckIfUrlIsOnline",
        accountViewModel = accountViewModel,
    ) {
        if (it) {
            whenOnline()
        }
    }
}

@Composable
fun WatchAccountForHomeScreen(
    newThreadsFeedState: FeedContentState,
    repliesFeedState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val homeFollowList by accountViewModel.account.liveHomeFollowLists.collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, homeFollowList) {
        NostrHomeDataSource.account = accountViewModel.account
        NostrHomeDataSource.invalidateFilters()
        newThreadsFeedState.checkKeysInvalidateDataAndSendToTop()
        repliesFeedState.checkKeysInvalidateDataAndSendToTop()
    }
}

@Immutable
class TabItem(
    val resource: Int,
    val feedState: FeedContentState,
    val routeForLastRead: String,
    val scrollStateKey: String,
    val forceEventKind: Int? = null,
    val useGridLayout: Boolean = false,
)
