package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.service.OnlineChecker
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
    ResolveNIP47(nip47, accountViewModel)

    WatchAccountForHomeScreen(homeFeedViewModel, repliesFeedViewModel, accountViewModel)

    WatchLifeCycleChanges(accountViewModel)

    AssembleHomeTabs(homeFeedViewModel, repliesFeedViewModel) { pagerState, tabItems ->
        AssembleHomePage(pagerState, tabItems, accountViewModel, nav)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssembleHomeTabs(
    homeFeedViewModel: NostrHomeFeedViewModel,
    repliesFeedViewModel: NostrHomeRepliesFeedViewModel,
    inner: @Composable (PagerState, ImmutableList<TabItem>) -> Unit
) {
    val pagerState = rememberForeverPagerState(key = PagerStateKeys.HOME_SCREEN) { 2 }

    val tabs by remember(homeFeedViewModel, repliesFeedViewModel) {
        mutableStateOf(
            listOf(
                TabItem(R.string.new_threads, homeFeedViewModel, Route.Home.base + "Follows", ScrollStateKeys.HOME_FOLLOWS),
                TabItem(R.string.conversations, repliesFeedViewModel, Route.Home.base + "FollowsReplies", ScrollStateKeys.HOME_REPLIES)
            ).toImmutableList()
        )
    }

    inner(pagerState, tabs)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssembleHomePage(
    pagerState: PagerState,
    tabs: ImmutableList<TabItem>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    Column(Modifier.fillMaxHeight()) {
        HomePages(pagerState, tabs, accountViewModel, nav)
    }
}

@Composable
fun ResolveNIP47(
    nip47: String?,
    accountViewModel: AccountViewModel
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
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                NostrHomeDataSource.account = accountViewModel.account
                NostrHomeDataSource.invalidateFilters()
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
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
fun CheckIfUrlIsOnline(url: String, accountViewModel: AccountViewModel, whenOnline: @Composable (Boolean) -> Unit) {
    var online by remember {
        mutableStateOf(
            OnlineChecker.isOnlineCached(url)
        )
    }

    LaunchedEffect(key1 = url) {
        accountViewModel.checkIsOnline(url) { isOnline ->
            if (online != isOnline) {
                online = isOnline
            }
        }
    }

    whenOnline(online)
}

@Composable
fun CrossfadeCheckIfUrlIsOnline(url: String, accountViewModel: AccountViewModel, whenOnline: @Composable () -> Unit) {
    var online by remember {
        mutableStateOf(
            OnlineChecker.isOnlineCached(url)
        )
    }

    LaunchedEffect(key1 = url) {
        accountViewModel.checkIsOnline(url) { isOnline ->
            if (online != isOnline) {
                online = isOnline
            }
        }
    }

    Crossfade(
        targetState = online,
        label = "CheckIfUrlIsOnline"
    ) {
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
    val homeFollowList by accountViewModel.account.liveHomeFollowLists.collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, homeFollowList) {
        NostrHomeDataSource.account = accountViewModel.account
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
