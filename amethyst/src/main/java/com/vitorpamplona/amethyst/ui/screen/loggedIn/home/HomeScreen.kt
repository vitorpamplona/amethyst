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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.AROUND_ME
import com.vitorpamplona.amethyst.model.CHESS
import com.vitorpamplona.amethyst.service.OnlineChecker
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.feeds.ChannelFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.ChannelFeedState
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
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.geohash.NewGeoPostButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.HomeFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.live.RenderEphemeralBubble
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.live.RenderLiveActivityBubble
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.HorzPadding
import com.vitorpamplona.amethyst.ui.theme.Size5dp
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
) {
    HomeScreen(
        liveFeedState = accountViewModel.feedStates.homeLive,
        newThreadsFeedState = accountViewModel.feedStates.homeNewThreads,
        repliesFeedState = accountViewModel.feedStates.homeReplies,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    liveFeedState: ChannelFeedContentState,
    newThreadsFeedState: FeedContentState,
    repliesFeedState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchAccountForHomeScreen(newThreadsFeedState, repliesFeedState, accountViewModel)

    WatchLifecycleAndUpdateModel(liveFeedState)
    WatchLifecycleAndUpdateModel(newThreadsFeedState)
    WatchLifecycleAndUpdateModel(repliesFeedState)

    HomeFilterAssemblerSubscription(accountViewModel)

    AssembleHomeTabs(newThreadsFeedState, repliesFeedState, liveFeedState) { pagerState, tabItems ->
        HomePages(pagerState, tabItems, accountViewModel, nav)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssembleHomeTabs(
    newThreadsFeedState: FeedContentState,
    repliesFeedState: FeedContentState,
    liveFeedState: ChannelFeedContentState,
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
                        routeForLastRead = "HomeFollows",
                        scrollStateKey = ScrollStateKeys.HOME_FOLLOWS,
                        liveSection = liveFeedState,
                    ),
                    TabItem(
                        resource = R.string.conversations,
                        feedState = repliesFeedState,
                        routeForLastRead = "HomeFollowsReplies",
                        scrollStateKey = ScrollStateKeys.HOME_REPLIES,
                        liveSection = liveFeedState,
                    ),
                ).toImmutableList(),
            )
        }

    inner(pagerState, tabs)
}

@Composable
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
            AppBottomBar(Route.Home, accountViewModel) { route ->
                if (route == Route.Home) {
                    tabs[pagerState.currentPage].feedState.sendToTop()
                } else {
                    nav.newStack(route)
                }
            }
        },
        floatingButton = {
            HomeScreenFloatingButton(accountViewModel, nav)
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
                liveSection = tabs[page].liveSection,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun HomeScreenFloatingButton(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val list =
        accountViewModel.account.settings.defaultHomeFollowList
            .collectAsStateWithLifecycle()

    when (list.value) {
        AROUND_ME -> {
            val location by Amethyst.instance.locationManager.geohashStateFlow
                .collectAsStateWithLifecycle()

            when (val myLocation = location) {
                is LocationState.LocationResult.Success -> {
                    NewGeoPostButton(myLocation.geoHash.toString(), accountViewModel, nav)
                }

                is LocationState.LocationResult.LackPermission -> { }

                is LocationState.LocationResult.Loading -> { }
            }
        }

        CHESS -> {
            NewChessGameButton(accountViewModel, nav)
        }

        else -> {
            NewNoteButton(nav)
        }
    }
}

@Composable
fun HomeFeeds(
    feedState: FeedContentState,
    routeForLastRead: String?,
    enablePullRefresh: Boolean = true,
    scrollStateKey: String? = null,
    liveSection: ChannelFeedContentState? = null,
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
                onLoaded = { FeedLoaded(it, listState, routeForLastRead, liveSection, accountViewModel, nav) },
                onEmpty = { HomeFeedEmpty(feedState::invalidateData) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedLoaded(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    routeForLastRead: String?,
    liveSection: ChannelFeedContentState? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = FeedPadding,
        state = listState,
    ) {
        if (liveSection != null) {
            item {
                DisplayLiveBubbles(liveSection, accountViewModel, nav)
                Spacer(StdVertSpacer)
            }
        }
        itemsIndexed(items.list, key = { _, item -> item.idHex }) { _, item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .animateItem(),
            ) {
                NoteCompose(
                    item,
                    modifier = Modifier.fillMaxWidth(),
                    routeForLastRead = routeForLastRead,
                    isBoostedNote = false,
                    isHiddenFeed = items.showHidden,
                    quotesLeft = 3,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            HorizontalDivider(
                thickness = DividerThickness,
            )
        }
    }
}

@Composable
fun DisplayLiveBubbles(
    liveSection: ChannelFeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedState by liveSection.feedContent.collectAsStateWithLifecycle()

    when (val state = feedState) {
        is ChannelFeedState.Empty -> null
        is ChannelFeedState.FeedError -> null
        is ChannelFeedState.Loaded -> DisplayLiveBubbles(state, accountViewModel, nav)
        is ChannelFeedState.Loading -> null
    }
}

@Composable
fun DisplayLiveBubbles(
    liveFeed: ChannelFeedState.Loaded,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feed by liveFeed.feed.collectAsStateWithLifecycle()

    LazyRow(HorzPadding, horizontalArrangement = spacedBy(Size5dp)) {
        itemsIndexed(feed.list, key = { _, item -> item.hashCode() }) { _, item ->
            when (item) {
                is EphemeralChatChannel -> RenderEphemeralBubble(item, accountViewModel, nav)
                is LiveActivitiesChannel -> RenderLiveActivityBubble(item, accountViewModel, nav)
            }
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
    val online by produceState(
        initialValue = OnlineChecker.isOnlineCached(url),
        key1 = url,
    ) {
        val isOnline = accountViewModel.checkVideoIsOnline(url)
        if (value != isOnline) {
            value = isOnline
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
    val online by produceState(
        initialValue = OnlineChecker.isOnlineCached(url),
        key1 = url,
    ) {
        val isOnline = accountViewModel.checkVideoIsOnline(url)
        if (value != isOnline) {
            value = isOnline
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
    val liveSection: ChannelFeedContentState? = null,
)
