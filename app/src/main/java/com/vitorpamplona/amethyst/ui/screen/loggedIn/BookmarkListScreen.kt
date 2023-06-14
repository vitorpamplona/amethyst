package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.dal.BookmarkPrivateFeedFilter
import com.vitorpamplona.amethyst.ui.dal.BookmarkPublicFeedFilter
import com.vitorpamplona.amethyst.ui.screen.NostrBookmarkPrivateFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrBookmarkPublicFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkListScreen(accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account

    if (account != null) {
        BookmarkPublicFeedFilter.account = account
        BookmarkPrivateFeedFilter.account = account

        val publicFeedViewModel: NostrBookmarkPublicFeedViewModel = viewModel()
        val privateFeedViewModel: NostrBookmarkPrivateFeedViewModel = viewModel()

        val userState by account.userProfile().live().bookmarks.observeAsState()

        LaunchedEffect(userState) {
            publicFeedViewModel.invalidateData()
            privateFeedViewModel.invalidateData()
        }

        Column(Modifier.fillMaxHeight()) {
            Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
                val pagerState = rememberPagerState()
                val coroutineScope = rememberCoroutineScope()

                TabRow(
                    backgroundColor = MaterialTheme.colors.background,
                    selectedTabIndex = pagerState.currentPage
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        text = {
                            Text(text = stringResource(R.string.private_bookmarks))
                        }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        text = {
                            Text(text = stringResource(R.string.public_bookmarks))
                        }
                    )
                }
                HorizontalPager(pageCount = 2, state = pagerState) { page ->
                    when (page) {
                        0 -> RefresheableFeedView(privateFeedViewModel, null, accountViewModel, nav)
                        1 -> RefresheableFeedView(publicFeedViewModel, null, accountViewModel, nav)
                    }
                }
            }
        }
    }
}
