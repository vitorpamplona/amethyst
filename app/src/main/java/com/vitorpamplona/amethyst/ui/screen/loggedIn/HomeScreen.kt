package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.note.UpdateZapAmountDialog
import com.vitorpamplona.amethyst.ui.screen.FeedState
import com.vitorpamplona.amethyst.ui.screen.FeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeFeedLiveActivitiesViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeRepliesFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.PagerStateKeys
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.ui.screen.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.screen.rememberForeverPagerState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    homeFeedViewModel: NostrHomeFeedViewModel,
    repliesFeedViewModel: NostrHomeRepliesFeedViewModel,
    liveActivitiesViewModel: NostrHomeFeedLiveActivitiesViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    nip47: String? = null
) {
    var wantsToAddNip47 by remember { mutableStateOf(nip47) }

    val pagerState = rememberForeverPagerState(key = PagerStateKeys.HOME_SCREEN)

    WatchAccountForHomeScreen(homeFeedViewModel, repliesFeedViewModel, accountViewModel)

    if (wantsToAddNip47 != null) {
        UpdateZapAmountDialog({ wantsToAddNip47 = null }, wantsToAddNip47, accountViewModel)
    }

    val lifeCycleOwner = LocalLifecycleOwner.current
    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                NostrHomeDataSource.invalidateFilters()
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val tabs by remember(homeFeedViewModel, repliesFeedViewModel) {
        mutableStateOf(
            listOf(
                TabItem(R.string.new_threads, homeFeedViewModel, Route.Home.base + "Follows", ScrollStateKeys.HOME_FOLLOWS),
                TabItem(R.string.conversations, repliesFeedViewModel, Route.Home.base + "FollowsReplies", ScrollStateKeys.HOME_REPLIES)
            ).toImmutableList()
        )
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            HomePages(pagerState, tabs, liveActivitiesViewModel, accountViewModel, nav)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HomePages(
    pagerState: PagerState,
    tabs: ImmutableList<TabItem>,
    liveActivitiesViewModel: NostrHomeFeedLiveActivitiesViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    TabRow(
        backgroundColor = MaterialTheme.colors.background,
        selectedTabIndex = pagerState.currentPage
    ) {
        val coroutineScope = rememberCoroutineScope()

        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = pagerState.currentPage == index,
                text = {
                    Text(text = stringResource(tab.resource))
                },
                onClick = {
                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                }
            )
        }
    }

    LiveActivities(
        liveActivitiesViewModel = liveActivitiesViewModel,
        accountViewModel = accountViewModel,
        nav = nav
    )

    HorizontalPager(pageCount = 2, state = pagerState) { page ->
        RefresheableFeedView(
            viewModel = tabs[page].viewModel,
            routeForLastRead = tabs[page].routeForLastRead,
            scrollStateKey = tabs[page].scrollStateKey,
            accountViewModel = accountViewModel,
            nav = nav
        )
    }
}

@Composable
fun LiveActivities(
    liveActivitiesViewModel: NostrHomeFeedLiveActivitiesViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val feedState by liveActivitiesViewModel.feedContent.collectAsState()

    Crossfade(
        targetState = feedState,
        animationSpec = tween(durationMillis = 100)
    ) { state ->
        when (state) {
            is FeedState.Loaded -> {
                FeedLoaded(
                    state,
                    accountViewModel,
                    nav
                )
            }

            else -> {
            }
        }
    }
}

@Composable
private fun FeedLoaded(
    state: FeedState.Loaded,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val listState = rememberLazyListState()

    LazyColumn(
        contentPadding = PaddingValues(
            top = 10.dp
        ),
        state = listState
    ) {
        itemsIndexed(state.feed.value, key = { _, item -> item.idHex }) { _, item ->
            ChannelHeader(
                channelHex = item.idHex,
                showVideo = false,
                showBottomDiviser = true,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                accountViewModel = accountViewModel,
                nav = nav
            )
        }
    }
}

@Composable
fun WatchAccountForHomeScreen(
    homeFeedViewModel: NostrHomeFeedViewModel,
    repliesFeedViewModel: NostrHomeRepliesFeedViewModel,
    accountViewModel: AccountViewModel
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val followState by accountViewModel.account.userProfile().live().follows.observeAsState()

    LaunchedEffect(accountViewModel, accountState?.account?.defaultHomeFollowList) {
        launch(Dispatchers.IO) {
            NostrHomeDataSource.invalidateFilters()
            homeFeedViewModel.checkKeysInvalidateDataAndSendToTop()
            repliesFeedViewModel.checkKeysInvalidateDataAndSendToTop()
        }
    }

    LaunchedEffect(followState) {
        launch(Dispatchers.IO) {
            NostrHomeDataSource.invalidateFilters()
        }
    }
}

@Immutable
class TabItem(
    val resource: Int,
    val viewModel: FeedViewModel,
    val routeForLastRead: String,
    val scrollStateKey: String
)
