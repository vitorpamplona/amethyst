package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.pagerTabIndicatorOffset
import com.google.accompanist.pager.rememberPagerState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.dal.HiddenAccountsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.NostrHiddenAccountsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.UserFeedView
import kotlinx.coroutines.launch

@OptIn(ExperimentalPagerApi::class)
@Composable
fun FiltersScreen(accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account

    if (account != null) {
        HiddenAccountsFeedFilter.account = account

        val feedViewModel: NostrHiddenAccountsFeedViewModel = viewModel()

        Column(Modifier.fillMaxHeight()) {
            Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
                val pagerState = rememberPagerState()
                val coroutineScope = rememberCoroutineScope()

                TabRow(
                    backgroundColor = MaterialTheme.colors.background,
                    selectedTabIndex = pagerState.currentPage,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.pagerTabIndicatorOffset(pagerState, tabPositions),
                            color = MaterialTheme.colors.primary
                        )
                    }
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        text = {
                            Text(text = stringResource(R.string.blocked_users))
                        }
                    )
                }
                HorizontalPager(count = 1, state = pagerState) {
                    when (pagerState.currentPage) {
                        0 -> UserFeedView(feedViewModel, accountViewModel, navController)
                    }
                }
            }
        }
    }
}
