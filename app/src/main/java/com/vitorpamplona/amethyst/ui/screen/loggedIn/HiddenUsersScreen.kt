package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.NostrHiddenAccountsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefreshingFeedUserFeedView
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import kotlinx.coroutines.launch

@Composable
fun HiddenUsersScreen(accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val feedViewModel: NostrHiddenAccountsFeedViewModel = viewModel(
        factory = NostrHiddenAccountsFeedViewModel.Factory(accountViewModel.account)
    )

    HiddenUsersScreen(feedViewModel, accountViewModel, nav)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HiddenUsersScreen(
    feedViewModel: NostrHiddenAccountsFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val lifeCycleOwner = LocalLifecycleOwner.current

    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("Hidden Users Start")
                feedViewModel.invalidateData()
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                println("Hidden Users Stop")
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(Modifier.fillMaxHeight()) {
        Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
            val pagerState = rememberPagerState()
            val coroutineScope = rememberCoroutineScope()
            var warnAboutReports by remember { mutableStateOf(accountViewModel.account.warnAboutPostsWithReports) }
            var filterSpam by remember { mutableStateOf(accountViewModel.account.filterSpamFromStrangers) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = warnAboutReports,
                    onCheckedChange = {
                        warnAboutReports = it
                        accountViewModel.account.updateOptOutOptions(warnAboutReports, filterSpam)
                        LocalPreferences.saveToEncryptedStorage(accountViewModel.account)
                    }
                )

                Text(stringResource(R.string.warn_when_posts_have_reports_from_your_follows))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = filterSpam,
                    onCheckedChange = {
                        filterSpam = it
                        accountViewModel.account.updateOptOutOptions(warnAboutReports, filterSpam)
                        LocalPreferences.saveToEncryptedStorage(accountViewModel.account)
                    }
                )

                Text(stringResource(R.string.filter_spam_from_strangers))
            }

            TabRow(
                backgroundColor = MaterialTheme.colors.background,
                selectedTabIndex = pagerState.currentPage,
                modifier = TabRowHeight
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = {
                        Text(text = stringResource(R.string.blocked_users))
                    }
                )
            }
            HorizontalPager(pageCount = 1, state = pagerState) { page ->
                when (page) {
                    0 -> RefreshingUserFeedView(feedViewModel, accountViewModel, nav)
                }
            }
        }
    }
}

@Composable
fun RefreshingUserFeedView(
    feedViewModel: NostrHiddenAccountsFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    WatchAccount(feedViewModel, accountViewModel)
    RefreshingFeedUserFeedView(feedViewModel, accountViewModel, nav)
}

@Composable
fun WatchAccount(
    feedViewModel: NostrHiddenAccountsFeedViewModel,
    accountViewModel: AccountViewModel
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()

    LaunchedEffect(accountViewModel, accountState) {
        feedViewModel.invalidateData()
    }
}
