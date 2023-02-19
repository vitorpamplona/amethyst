package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.pagerTabIndicatorOffset
import com.google.accompanist.pager.rememberPagerState
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrChatroomListDataSource
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.ui.dal.ChatroomListKnownFeedFilter
import com.vitorpamplona.amethyst.ui.dal.ChatroomListNewFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalPagerApi::class)
@Composable
fun ChatroomListScreen(accountViewModel: AccountViewModel, navController: NavController) {
    val pagerState = rememberPagerState()
    val coroutineScope = rememberCoroutineScope()

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.pagerTabIndicatorOffset(pagerState, tabPositions),
                        color = MaterialTheme.colors.primary
                    )
                },
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = {
                        Text(text = "Known")
                    }
                )

                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = {
                        Text(text = "New Requests")
                    }
                )
            }
            HorizontalPager(count = 2, state = pagerState) {
                when (pagerState.currentPage) {
                    0 -> TabKnown(accountViewModel, navController)
                    1 -> TabNew(accountViewModel, navController)
                }
            }
        }
    }
}

@Composable
fun TabKnown(accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    ChatroomListKnownFeedFilter.account = account
    val feedViewModel: NostrChatroomListKnownFeedViewModel = viewModel()

    LaunchedEffect(Unit) {
        NostrChatroomListDataSource.resetFilters()
        feedViewModel.invalidateData()
    }

    val lifeCycleOwner = LocalLifecycleOwner.current
    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { source, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("Chatroom List Start")
                NostrChatroomListDataSource.start()
                feedViewModel.invalidateData()
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                println("Chatroom List Stop")
                NostrChatroomListDataSource.stop()
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
            ChatroomListFeedView(feedViewModel, accountViewModel, navController)
        }
    }
}

@Composable
fun TabNew(accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    ChatroomListNewFeedFilter.account = account
    val feedViewModel: NostrChatroomListNewFeedViewModel = viewModel()

    LaunchedEffect(Unit) {
        NostrChatroomListDataSource.resetFilters()
        feedViewModel.refresh() // refresh view
    }

    val lifeCycleOwner = LocalLifecycleOwner.current
    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { source, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("Chatroom List Start")
                NostrChatroomListDataSource.start()
                feedViewModel.invalidateData()
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                println("Chatroom List Stop")
                NostrChatroomListDataSource.stop()
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
            ChatroomListFeedView(feedViewModel, accountViewModel, navController)
        }
    }
}