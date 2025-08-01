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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.ui.feeds.FeedError
import com.vitorpamplona.amethyst.ui.feeds.FeedState
import com.vitorpamplona.amethyst.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.ui.feeds.PagerStateKeys
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.SaveableGridFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.feeds.rememberForeverPagerState
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.datasource.DiscoveryFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.TabItem
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.Size26Modifier
import com.vitorpamplona.amethyst.ui.theme.Size55Modifier
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Composable
fun DiscoverScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    DiscoverScreen(
        discoveryFollowSetsFeedContentState = accountViewModel.feedStates.discoverFollowSets,
        discoveryReadsFeedContentState = accountViewModel.feedStates.discoverReads,
        discoveryContentNIP89FeedContentState = accountViewModel.feedStates.discoverDVMs,
        discoveryMarketplaceFeedContentState = accountViewModel.feedStates.discoverMarketplace,
        discoveryLiveFeedContentState = accountViewModel.feedStates.discoverLive,
        discoveryCommunityFeedContentState = accountViewModel.feedStates.discoverCommunities,
        discoveryChatFeedContentState = accountViewModel.feedStates.discoverPublicChats,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscoverScreen(
    discoveryFollowSetsFeedContentState: FeedContentState,
    discoveryReadsFeedContentState: FeedContentState,
    discoveryContentNIP89FeedContentState: FeedContentState,
    discoveryMarketplaceFeedContentState: FeedContentState,
    discoveryLiveFeedContentState: FeedContentState,
    discoveryCommunityFeedContentState: FeedContentState,
    discoveryChatFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val tabs by
        remember(accountViewModel) {
            mutableStateOf(
                listOf(
                    TabItem(
                        R.string.discover_follows,
                        discoveryFollowSetsFeedContentState,
                        "DiscoverFollowSets",
                        ScrollStateKeys.DISCOVER_FOLLOWS,
                        FollowListEvent.KIND,
                    ),
                    TabItem(
                        R.string.discover_reads,
                        discoveryReadsFeedContentState,
                        "DiscoverReads",
                        ScrollStateKeys.DISCOVER_READS,
                        LongTextNoteEvent.KIND,
                    ),
                    TabItem(
                        R.string.discover_content_v2,
                        discoveryContentNIP89FeedContentState,
                        "DiscoverDiscoverContent",
                        ScrollStateKeys.DISCOVER_CONTENT,
                        AppDefinitionEvent.KIND,
                    ),
                    TabItem(
                        R.string.discover_live_v2,
                        discoveryLiveFeedContentState,
                        "DiscoverLive",
                        ScrollStateKeys.DISCOVER_LIVE,
                        LiveActivitiesEvent.KIND,
                    ),
                    TabItem(
                        R.string.discover_community_v2,
                        discoveryCommunityFeedContentState,
                        "DiscoverCommunity",
                        ScrollStateKeys.DISCOVER_COMMUNITY,
                        CommunityDefinitionEvent.KIND,
                    ),
                    TabItem(
                        R.string.discover_marketplace,
                        discoveryMarketplaceFeedContentState,
                        "DiscoverMarketplace",
                        ScrollStateKeys.DISCOVER_MARKETPLACE,
                        ClassifiedsEvent.KIND,
                        useGridLayout = true,
                    ),
                    TabItem(
                        R.string.discover_chat,
                        discoveryChatFeedContentState,
                        "DiscoverChats",
                        ScrollStateKeys.DISCOVER_CHATS,
                        ChannelCreateEvent.KIND,
                    ),
                ).toImmutableList(),
            )
        }

    val pagerState = rememberForeverPagerState(key = PagerStateKeys.DISCOVER_SCREEN) { tabs.size }

    WatchAccountForDiscoveryScreen(
        discoveryFollowSetsFeedContentState = discoveryFollowSetsFeedContentState,
        discoveryReadsFeedContentState = discoveryReadsFeedContentState,
        discoveryContentNIP89FeedContentState = discoveryContentNIP89FeedContentState,
        discoveryMarketplaceFeedContentState = discoveryMarketplaceFeedContentState,
        discoveryLiveFeedContentState = discoveryLiveFeedContentState,
        discoveryCommunityFeedContentState = discoveryCommunityFeedContentState,
        discoveryChatFeedContentState = discoveryChatFeedContentState,
        accountViewModel = accountViewModel,
    )

    WatchLifecycleAndUpdateModel(discoveryFollowSetsFeedContentState)
    WatchLifecycleAndUpdateModel(discoveryReadsFeedContentState)
    WatchLifecycleAndUpdateModel(discoveryContentNIP89FeedContentState)
    WatchLifecycleAndUpdateModel(discoveryMarketplaceFeedContentState)
    WatchLifecycleAndUpdateModel(discoveryLiveFeedContentState)
    WatchLifecycleAndUpdateModel(discoveryCommunityFeedContentState)
    WatchLifecycleAndUpdateModel(discoveryChatFeedContentState)

    DiscoveryFilterAssemblerSubscription(accountViewModel.dataSources().discovery, accountViewModel)

    DiscoverPages(pagerState, tabs, accountViewModel, nav)
}

@Composable
private fun DiscoverPages(
    pagerState: PagerState,
    tabs: ImmutableList<TabItem>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                DiscoveryTopBar(accountViewModel, nav)
                ScrollableTabRow(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    selectedTabIndex = pagerState.currentPage,
                    modifier = TabRowHeight,
                    edgePadding = 8.dp,
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
            AppBottomBar(Route.Discover, accountViewModel) { route ->
                if (route == Route.Discover) {
                    tabs[pagerState.currentPage].feedState.sendToTop()
                } else {
                    nav.newStack(route)
                }
            }
        },
        floatingButton = {
            if (tabs[pagerState.currentPage].resource == R.string.discover_marketplace) {
                NewProductButton(accountViewModel, nav)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        HorizontalPager(state = pagerState, contentPadding = it) { page ->
            RefresheableBox(tabs[page].feedState, true) {
                if (tabs[page].useGridLayout) {
                    SaveableGridFeedContentState(tabs[page].feedState, scrollStateKey = tabs[page].scrollStateKey) { listState ->
                        RenderDiscoverFeed(
                            feedContentState = tabs[page].feedState,
                            routeForLastRead = tabs[page].routeForLastRead,
                            forceEventKind = tabs[page].forceEventKind,
                            listState = listState,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                } else {
                    SaveableFeedContentState(tabs[page].feedState, scrollStateKey = tabs[page].scrollStateKey) { listState ->
                        RenderDiscoverFeed(
                            feedContentState = tabs[page].feedState,
                            routeForLastRead = tabs[page].routeForLastRead,
                            forceEventKind = tabs[page].forceEventKind,
                            listState = listState,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderDiscoverFeed(
    feedContentState: FeedContentState,
    routeForLastRead: String?,
    forceEventKind: Int?,
    listState: LazyGridState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedState by feedContentState.feedContent.collectAsStateWithLifecycle()

    CrossfadeIfEnabled(
        targetState = feedState,
        animationSpec = tween(durationMillis = 100),
        label = "RenderDiscoverFeed",
        accountViewModel = accountViewModel,
    ) { state ->
        when (state) {
            is FeedState.Empty -> {
                FeedEmpty(feedContentState::invalidateData)
            }
            is FeedState.FeedError -> {
                FeedError(state.errorMessage, feedContentState::invalidateData)
            }
            is FeedState.Loaded -> {
                DiscoverFeedColumnsLoaded(
                    state,
                    routeForLastRead,
                    listState,
                    forceEventKind,
                    accountViewModel,
                    nav,
                )
            }
            is FeedState.Loading -> {
                LoadingFeed()
            }
        }
    }
}

@Composable
fun NewProductButton(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    FloatingActionButton(
        onClick = {
            nav.nav(Route.NewProduct())
        },
        modifier = Size55Modifier,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = stringRes(id = R.string.new_product),
            modifier = Size26Modifier,
            tint = Color.White,
        )
    }
}

@Composable
private fun RenderDiscoverFeed(
    feedContentState: FeedContentState,
    routeForLastRead: String?,
    forceEventKind: Int?,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedState by feedContentState.feedContent.collectAsStateWithLifecycle()

    CrossfadeIfEnabled(
        targetState = feedState,
        animationSpec = tween(durationMillis = 100),
        label = "RenderDiscoverFeed",
        accountViewModel = accountViewModel,
    ) { state ->
        when (state) {
            is FeedState.Empty -> {
                FeedEmpty(feedContentState::invalidateData)
            }
            is FeedState.FeedError -> {
                FeedError(state.errorMessage, feedContentState::invalidateData)
            }
            is FeedState.Loaded -> {
                DiscoverFeedLoaded(
                    state,
                    routeForLastRead,
                    listState,
                    forceEventKind,
                    accountViewModel,
                    nav,
                )
            }
            is FeedState.Loading -> {
                LoadingFeed()
            }
        }
    }
}

@Composable
fun WatchAccountForDiscoveryScreen(
    discoveryFollowSetsFeedContentState: FeedContentState,
    discoveryReadsFeedContentState: FeedContentState,
    discoveryContentNIP89FeedContentState: FeedContentState,
    discoveryMarketplaceFeedContentState: FeedContentState,
    discoveryLiveFeedContentState: FeedContentState,
    discoveryCommunityFeedContentState: FeedContentState,
    discoveryChatFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveDiscoveryFollowLists.collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState) {
        discoveryFollowSetsFeedContentState.checkKeysInvalidateDataAndSendToTop()
        discoveryReadsFeedContentState.checkKeysInvalidateDataAndSendToTop()
        discoveryContentNIP89FeedContentState.checkKeysInvalidateDataAndSendToTop()
        discoveryMarketplaceFeedContentState.checkKeysInvalidateDataAndSendToTop()
        discoveryLiveFeedContentState.checkKeysInvalidateDataAndSendToTop()
        discoveryCommunityFeedContentState.checkKeysInvalidateDataAndSendToTop()
        discoveryChatFeedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverFeedLoaded(
    loaded: FeedState.Loaded,
    routeForLastRead: String?,
    listState: LazyListState,
    forceEventKind: Int?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = FeedPadding,
        state = listState,
    ) {
        itemsIndexed(items.list, key = { _, item -> item.idHex }) { _, item ->
            Row(Modifier.fillMaxWidth().animateItem()) {
                ChannelCardCompose(
                    baseNote = item,
                    routeForLastRead = routeForLastRead,
                    modifier = Modifier.fillMaxWidth(),
                    forceEventKind = forceEventKind,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverFeedColumnsLoaded(
    loaded: FeedState.Loaded,
    routeForLastRead: String?,
    listState: LazyGridState,
    forceEventKind: Int?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = FeedPadding,
        state = listState,
    ) {
        itemsIndexed(items.list, key = { _, item -> item.idHex }) { _, item ->
            Row(Modifier.fillMaxWidth().animateItem()) {
                ChannelCardCompose(
                    baseNote = item,
                    routeForLastRead = routeForLastRead,
                    modifier = Modifier,
                    forceEventKind = forceEventKind,
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
