package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.vitorpamplona.amethyst.service.NostrChatroomListDataSource
import com.vitorpamplona.amethyst.ui.dal.ChatroomListKnownFeedFilter
import com.vitorpamplona.amethyst.ui.dal.ChatroomListNewFeedFilter
import com.vitorpamplona.amethyst.ui.screen.ChatroomListFeedView
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomListKnownFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomListNewFeedViewModel
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalPagerApi::class)
@Composable
fun ChatroomListScreen(accountViewModel: AccountViewModel, navController: NavController) {
    val pagerState = rememberPagerState()
    val coroutineScope = rememberCoroutineScope()

    var moreActionsExpanded by remember { mutableStateOf(false) }

    var markKnownAsReadBefore by remember { mutableStateOf(0L) }
    var markNewAsReadBefore by remember { mutableStateOf(0L) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier.padding(vertical = 0.dp)
            ) {
                TabRow(
                    backgroundColor = MaterialTheme.colors.background,
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
                            Text(text = stringResource(R.string.known))
                        }
                    )

                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        text = {
                            Text(text = stringResource(R.string.new_requests))
                        }
                    )
                }
                HorizontalPager(count = 2, state = pagerState) {
                    when (pagerState.currentPage) {
                        0 -> TabKnown(accountViewModel, navController, markKnownAsReadBefore)
                        1 -> TabNew(accountViewModel, navController, markNewAsReadBefore)
                    }
                }
            }
        }
        IconButton(
            modifier = Modifier
                .padding(0.dp)
                .size(30.dp)
                .align(Alignment.TopEnd),
            onClick = { moreActionsExpanded = true }
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
            )

            ChatroomTabMenu(
                moreActionsExpanded,
                { moreActionsExpanded = false },
                { markKnownAsReadBefore = Date().time / 1000 },
                { markNewAsReadBefore = Date().time / 1000 },
            )
        }
    }
}

@Composable
fun TabKnown(accountViewModel: AccountViewModel, navController: NavController, markAsReadBefore: Long) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    ChatroomListKnownFeedFilter.account = account
    val feedViewModel: NostrChatroomListKnownFeedViewModel = viewModel()

    LaunchedEffect(accountViewModel) {
        NostrChatroomListDataSource.resetFilters()
        feedViewModel.invalidateData()
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            ChatroomListFeedView(feedViewModel, accountViewModel, navController, markAsReadBefore)
        }
    }
}

@Composable
fun TabNew(accountViewModel: AccountViewModel, navController: NavController, markAsReadBefore: Long) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    ChatroomListNewFeedFilter.account = account
    val feedViewModel: NostrChatroomListNewFeedViewModel = viewModel()

    LaunchedEffect(accountViewModel) {
        NostrChatroomListDataSource.resetFilters()
        feedViewModel.invalidateData() // refresh view
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            ChatroomListFeedView(feedViewModel, accountViewModel, navController, markAsReadBefore)
        }
    }
}

@Composable
fun ChatroomTabMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onMarkKnownAsRead: () -> Unit,
    onMarkNewAsRead: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(onClick = {
            onMarkKnownAsRead()
            onDismiss()
        }) {
            Text("Mark all Known as read")
        }
        DropdownMenuItem(onClick = {
            onMarkNewAsRead()
            onDismiss()
        }) {
            Text("Mark all New as read")
        }
        DropdownMenuItem(onClick = {
            onMarkKnownAsRead()
            onMarkNewAsRead()
            onDismiss()
        }) {
            Text("Mark all as read")
        }
    }
}
