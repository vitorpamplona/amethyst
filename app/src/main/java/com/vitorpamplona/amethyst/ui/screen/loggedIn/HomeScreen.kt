package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
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
    var wantsToAddNip47 by remember(nip47) { mutableStateOf(nip47) }

    val pagerState = rememberForeverPagerState(key = PagerStateKeys.HOME_SCREEN) { 2 }

    WatchAccountForHomeScreen(homeFeedViewModel, repliesFeedViewModel, accountViewModel)

    if (wantsToAddNip47 != null) {
        UpdateZapAmountDialog({ wantsToAddNip47 = null }, wantsToAddNip47, accountViewModel)
    }

    val lifeCycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifeCycleOwner) {
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
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = TabRowHeight,
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

    HorizontalPager(state = pagerState, userScrollEnabled = false) { page ->
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
fun CheckIfUrlIsOnline(url: String, accountViewModel: AccountViewModel, whenOnline: @Composable () -> Unit) {
    var online by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = url) {
        accountViewModel.checkIsOnline(url) { isOnline ->
            if (online != isOnline) {
                online = isOnline
            }
        }
    }

    Crossfade(targetState = online) {
        if (it) {
            whenOnline()
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

    LaunchedEffect(accountViewModel, accountState?.account?.defaultHomeFollowList, followState) {
        NostrHomeDataSource.invalidateFilters()
        homeFeedViewModel.checkKeysInvalidateDataAndSendToTop()
        repliesFeedViewModel.checkKeysInvalidateDataAndSendToTop()
    }
}

@Immutable
class TabItem(
    val resource: Int,
    val viewModel: FeedViewModel,
    val routeForLastRead: String,
    val scrollStateKey: String,
    val forceEventKind: Int? = null
)
