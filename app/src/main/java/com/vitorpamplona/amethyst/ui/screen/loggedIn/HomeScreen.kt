package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
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
import com.vitorpamplona.amethyst.ui.screen.FeedViewModel
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
            HomePages(pagerState, tabs, accountViewModel, nav)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HomePages(
    pagerState: PagerState,
    tabs: ImmutableList<TabItem>,
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

    HorizontalPager(pageCount = 2, state = pagerState) { page ->
        RefresheableFeedView(
            viewModel = tabs[page].viewModel,
            accountViewModel = accountViewModel,
            nav = nav,
            routeForLastRead = tabs[page].routeForLastRead,
            scrollStateKey = tabs[page].scrollStateKey
        )
    }
}

@Composable
fun WatchAccountForHomeScreen(
    homeFeedViewModel: NostrHomeFeedViewModel,
    repliesFeedViewModel: NostrHomeRepliesFeedViewModel,
    accountViewModel: AccountViewModel
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()

    var firstTime by remember(accountViewModel) { mutableStateOf(true) }

    LaunchedEffect(accountViewModel, accountState?.account?.defaultHomeFollowList) {
        // Only invalidate when things change. Not in the first run
        if (firstTime) {
            firstTime = false
        } else {
            launch(Dispatchers.IO) {
                NostrHomeDataSource.invalidateFilters()
                homeFeedViewModel.invalidateDataAndSendToTop(true)
                repliesFeedViewModel.invalidateDataAndSendToTop(true)
            }
        }
    }
}

@Immutable
class TabItem(val resource: Int, val viewModel: FeedViewModel, val routeForLastRead: String, val scrollStateKey: String)
