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
import com.vitorpamplona.amethyst.ui.dal.HomeConversationsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HomeNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.note.UpdateZapAmountDialog
import com.vitorpamplona.amethyst.ui.screen.FeedView
import com.vitorpamplona.amethyst.ui.screen.FeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeRepliesFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.ScrollStateKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    homeFeedViewModel: NostrHomeFeedViewModel,
    repliesFeedViewModel: NostrHomeRepliesFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    pagerState: PagerState,
    scrollToTop: Boolean = false,
    nip47: String? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var wantsToAddNip47 by remember { mutableStateOf(nip47) }

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    LaunchedEffect(accountViewModel, account.defaultHomeFollowList) {
        HomeNewThreadFeedFilter.account = account
        HomeConversationsFeedFilter.account = account
        NostrHomeDataSource.invalidateFilters()
    }

    if (wantsToAddNip47 != null) {
        UpdateZapAmountDialog({ wantsToAddNip47 = null }, account = account, wantsToAddNip47)
    }

    val lifeCycleOwner = LocalLifecycleOwner.current
    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                HomeNewThreadFeedFilter.account = account
                HomeConversationsFeedFilter.account = account
                NostrHomeDataSource.invalidateFilters()
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (scrollToTop) {
        val scope = rememberCoroutineScope()
        LaunchedEffect(key1 = Unit) {
            scope.launch(Dispatchers.IO) {
                NostrHomeDataSource.invalidateFilters()
                homeFeedViewModel.invalidateData(true)
                repliesFeedViewModel.invalidateData(true)
            }
        }
    }

    val tabs by remember(homeFeedViewModel, repliesFeedViewModel) {
        mutableStateOf(
            listOf(
                TabItem(R.string.new_threads, homeFeedViewModel, Route.Home.base + "Follows", ScrollStateKeys.HOME_FOLLOWS),
                TabItem(R.string.conversations, repliesFeedViewModel, Route.Home.base + "FollowsReplies", ScrollStateKeys.HOME_REPLIES)
            )
        )
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            TabRow(
                backgroundColor = MaterialTheme.colors.background,
                selectedTabIndex = pagerState.currentPage
            ) {
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
                FeedView(
                    viewModel = tabs[page].viewModel,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    routeForLastRead = tabs[page].routeForLastRead,
                    scrollStateKey = tabs[page].scrollStateKey,
                    scrollToTop = scrollToTop
                )
            }
        }
    }
}

class TabItem(val resource: Int, val viewModel: FeedViewModel, val routeForLastRead: String, val scrollStateKey: String)
