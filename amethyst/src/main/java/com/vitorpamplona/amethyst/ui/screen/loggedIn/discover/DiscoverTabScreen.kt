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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.ui.feeds.FeedError
import com.vitorpamplona.amethyst.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.SaveableGridFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.datasource.DiscoverTabFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.datasource.DiscoverTabFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding

@Composable
fun DiscoverTabScreen(
    feedViewModel: DiscoverTabFeedViewModel,
    dataSource: DiscoverTabFilterAssembler,
    routeForLastRead: String,
    scrollStateKey: String,
    forceEventKind: Int?,
    useGridLayout: Boolean = false,
    selectedRoute: Route,
    accountViewModel: AccountViewModel,
    nav: INav,
    floatingButton: @Composable () -> Unit = {},
) {
    WatchLifecycleAndUpdateModel(feedViewModel)
    WatchAccountForDiscoverTab(feedViewModel.feedState, accountViewModel)
    DiscoverTabFilterAssemblerSubscription(dataSource, feedViewModel.feedState, accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            DiscoveryTopBar(accountViewModel, nav)
        },
        bottomBar = {
            AppBottomBar(selectedRoute, accountViewModel) { route ->
                if (route == selectedRoute) {
                    feedViewModel.sendToTop()
                } else {
                    nav.newStack(route)
                }
            }
        },
        floatingButton = floatingButton,
        accountViewModel = accountViewModel,
    ) {
        RefresheableBox(feedViewModel, true) {
            if (useGridLayout) {
                SaveableGridFeedContentState(feedViewModel.feedState, scrollStateKey = scrollStateKey) { listState ->
                    RenderDiscoverTabFeed(
                        feedContentState = feedViewModel.feedState,
                        routeForLastRead = routeForLastRead,
                        forceEventKind = forceEventKind,
                        listState = listState,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            } else {
                SaveableFeedContentState(feedViewModel.feedState, scrollStateKey = scrollStateKey) { listState ->
                    RenderDiscoverTabFeed(
                        feedContentState = feedViewModel.feedState,
                        routeForLastRead = routeForLastRead,
                        forceEventKind = forceEventKind,
                        listState = listState,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchAccountForDiscoverTab(
    feedState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveDiscoveryFollowLists.collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState) {
        feedState.checkKeysInvalidateDataAndSendToTop()
    }
}

@Composable
private fun RenderDiscoverTabFeed(
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
        label = "RenderDiscoverTabFeed",
        accountViewModel = accountViewModel,
    ) { state ->
        when (state) {
            is FeedState.Empty -> FeedEmpty(feedContentState::invalidateData)
            is FeedState.FeedError -> FeedError(state.errorMessage, feedContentState::invalidateData)
            is FeedState.Loaded -> DiscoverTabFeedColumnsLoaded(state, routeForLastRead, listState, forceEventKind, accountViewModel, nav)
            is FeedState.Loading -> LoadingFeed()
        }
    }
}

@Composable
private fun RenderDiscoverTabFeed(
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
        label = "RenderDiscoverTabFeed",
        accountViewModel = accountViewModel,
    ) { state ->
        when (state) {
            is FeedState.Empty -> FeedEmpty(feedContentState::invalidateData)
            is FeedState.FeedError -> FeedError(state.errorMessage, feedContentState::invalidateData)
            is FeedState.Loaded -> DiscoverTabFeedLoaded(state, routeForLastRead, listState, forceEventKind, accountViewModel, nav)
            is FeedState.Loading -> LoadingFeed()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverTabFeedLoaded(
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
            HorizontalDivider(thickness = DividerThickness)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverTabFeedColumnsLoaded(
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
            HorizontalDivider(thickness = DividerThickness)
        }
    }
}
