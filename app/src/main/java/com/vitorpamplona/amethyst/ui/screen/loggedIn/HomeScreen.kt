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
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.ui.dal.HomeConversationsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HomeNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.note.UpdateZapAmountDialog
import com.vitorpamplona.amethyst.ui.screen.FeedView
import com.vitorpamplona.amethyst.ui.screen.NostrHomeFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeRepliesFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.ScrollStateKeys
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    homeFeedViewModel: NostrHomeFeedViewModel,
    repliesFeedViewModel: NostrHomeRepliesFeedViewModel,
    accountViewModel: AccountViewModel,
    navController: NavController,
    pagerState: PagerState,
    scrollToTop: Boolean = false,
    nip47: String? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val account = accountViewModel.accountLiveData.value?.account ?: return
    var wantsToAddNip47 by remember { mutableStateOf(nip47) }

    val accountState = account.live.observeAsState()

    LaunchedEffect(accountViewModel, accountState.value?.account?.defaultHomeFollowList) {
        account.sendRecommendationRequest(account.defaultHomeFollowList) {
            NostrHomeDataSource.resetFilters()
            homeFeedViewModel.invalidateData()
            repliesFeedViewModel.invalidateData()
        }

        HomeNewThreadFeedFilter.account = account
        HomeConversationsFeedFilter.account = account
        NostrHomeDataSource.resetFilters()
        homeFeedViewModel.invalidateData()
        repliesFeedViewModel.invalidateData()
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
                NostrHomeDataSource.resetFilters()
                homeFeedViewModel.invalidateData()
                repliesFeedViewModel.invalidateData()
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            TabRow(
                backgroundColor = MaterialTheme.colors.background,
                selectedTabIndex = pagerState.currentPage
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = {
                        Text(text = stringResource(R.string.new_threads))
                    }
                )

                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = {
                        Text(text = stringResource(R.string.conversations))
                    }
                )
            }
            HorizontalPager(pageCount = 2, state = pagerState) {
                when (pagerState.currentPage) {
                    0 -> FeedView(homeFeedViewModel, accountViewModel, navController, Route.Home.base + "Follows", ScrollStateKeys.HOME_FOLLOWS, scrollToTop)
                    1 -> FeedView(repliesFeedViewModel, accountViewModel, navController, Route.Home.base + "FollowsReplies", ScrollStateKeys.HOME_REPLIES, scrollToTop)
                }
            }
        }
    }
}
