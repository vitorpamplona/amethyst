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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.softwareapps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.OutboxLoaderState
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.types.RenderSoftwareApplication
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.softwareapps.dal.SoftwareAppsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.softwareapps.datasource.SoftwareAppsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SoftwareAppsScreen(
    route: Route.SoftwareApps,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val override = route.topFilter
    if (override == null) {
        SharedSoftwareAppsScreen(accountViewModel, nav)
    } else {
        FilteredSoftwareAppsScreen(override, accountViewModel, nav)
    }
}

@Composable
private fun SharedSoftwareAppsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account
    SoftwareAppsScreen(
        feedContentState = accountViewModel.feedStates.softwareAppsFeed,
        topFilterFlow = account.settings.defaultSoftwareAppsFollowList,
        liveFollowListsFlow = account.liveSoftwareAppsFollowLists,
        followsPerRelayFlow = account.liveSoftwareAppsFollowListsPerRelay,
        onChangeFilter = account.settings::changeDefaultSoftwareAppsFollowList,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
private fun FilteredSoftwareAppsScreen(
    initialFilter: TopFilter,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account
    val scope = accountViewModel.viewModelScope

    // Local pipeline scoped to this back-stack entry. Keying on the initial
    // filter means a fresh push for a different hashtag rebuilds the pipeline.
    val localState =
        remember(account, initialFilter) {
            val localTopFilter = MutableStateFlow(initialFilter)
            val localLiveFollows = account.topNavFilterFlow(localTopFilter)
            val localLiveFollowsPerRelay = OutboxLoaderState(localLiveFollows, LocalCache, scope).flow
            val localFeedFilter = SoftwareAppsFeedFilter(account, localTopFilter, localLiveFollows)
            val localFeedContentState = FeedContentState(localFeedFilter, scope, LocalCache)
            LocalSoftwareAppsState(
                topFilterFlow = localTopFilter,
                liveFollowListsFlow = localLiveFollows,
                followsPerRelayFlow = localLiveFollowsPerRelay,
                feedContentState = localFeedContentState,
            )
        }

    SoftwareAppsScreen(
        feedContentState = localState.feedContentState,
        topFilterFlow = localState.topFilterFlow,
        liveFollowListsFlow = localState.liveFollowListsFlow,
        followsPerRelayFlow = localState.followsPerRelayFlow,
        onChangeFilter = { localState.topFilterFlow.tryEmit(it) },
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

private class LocalSoftwareAppsState(
    val topFilterFlow: MutableStateFlow<TopFilter>,
    val liveFollowListsFlow: StateFlow<IFeedTopNavFilter>,
    val followsPerRelayFlow: StateFlow<IFeedTopNavPerRelayFilterSet>,
    val feedContentState: FeedContentState,
)

@Composable
fun SoftwareAppsScreen(
    feedContentState: FeedContentState,
    topFilterFlow: StateFlow<TopFilter>,
    liveFollowListsFlow: StateFlow<IFeedTopNavFilter>,
    followsPerRelayFlow: StateFlow<IFeedTopNavPerRelayFilterSet>,
    onChangeFilter: (TopFilter) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedContentState)
    WatchAccountForSoftwareAppsScreen(
        feedContentState = feedContentState,
        liveFollowListsFlow = liveFollowListsFlow,
        accountViewModel = accountViewModel,
    )
    SoftwareAppsFilterAssemblerSubscription(
        dataSource = accountViewModel.dataSources().softwareApps,
        feedContentState = feedContentState,
        topFilterFlow = topFilterFlow,
        followsPerRelayFlow = followsPerRelayFlow,
        accountViewModel = accountViewModel,
    )

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            SoftwareAppsTopBar(
                topFilterFlow = topFilterFlow,
                onChangeFilter = onChangeFilter,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
        bottomBar = {
            AppBottomBar(Route.SoftwareApps(), nav, accountViewModel) { route ->
                if (route is Route.SoftwareApps) {
                    feedContentState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        RefresheableBox(feedContentState, true) {
            SaveableFeedContentState(feedContentState) { listState ->
                RenderFeedContentState(
                    feedContentState = feedContentState,
                    accountViewModel = accountViewModel,
                    listState = listState,
                    nav = nav,
                    routeForLastRead = "SoftwareAppsFeed",
                    onLoaded = { loaded ->
                        SoftwareAppsFeedLoaded(
                            loaded = loaded,
                            listState = listState,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun WatchAccountForSoftwareAppsScreen(
    feedContentState: FeedContentState,
    liveFollowListsFlow: StateFlow<IFeedTopNavFilter>,
    accountViewModel: AccountViewModel,
) {
    val listState by liveFollowListsFlow.collectAsStateWithLifecycle()
    val hiddenUsers =
        accountViewModel.account.hiddenUsers.flow
            .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        feedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}

@Composable
fun SoftwareAppsFeedLoaded(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        itemsIndexed(
            items.list,
            key = { _, item -> item.idHex },
            contentType = { _, item -> item.event?.kind ?: -1 },
        ) { _, item ->
            if (item.event is SoftwareApplicationEvent) {
                RenderSoftwareApplication(
                    note = item,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )

                HorizontalDivider(thickness = DividerThickness)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
