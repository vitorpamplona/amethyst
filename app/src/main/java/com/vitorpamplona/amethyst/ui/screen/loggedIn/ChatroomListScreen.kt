package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.NostrChatroomListDataSource
import com.vitorpamplona.amethyst.ui.screen.ChatroomListFeedView
import com.vitorpamplona.amethyst.ui.screen.FeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomListKnownFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomListNewFeedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatroomListScreen(
    knownFeedViewModel: NostrChatroomListKnownFeedViewModel,
    newFeedViewModel: NostrChatroomListNewFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val pagerState = rememberPagerState()
    val coroutineScope = rememberCoroutineScope()

    var moreActionsExpanded by remember { mutableStateOf(false) }
    val markKnownAsRead = remember { mutableStateOf(false) }
    val markNewAsRead = remember { mutableStateOf(false) }

    WatchAccountForListScreen(knownFeedViewModel, newFeedViewModel, accountViewModel)

    val lifeCycleOwner = LocalLifecycleOwner.current
    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                NostrChatroomListDataSource.start()
                knownFeedViewModel.invalidateData(true)
                newFeedViewModel.invalidateData(true)
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val tabs by remember(knownFeedViewModel, markKnownAsRead) {
        derivedStateOf {
            listOf(
                ChatroomListTabItem(R.string.known, knownFeedViewModel, markKnownAsRead),
                ChatroomListTabItem(R.string.new_requests, newFeedViewModel, markNewAsRead)
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier.padding(vertical = 0.dp)
            ) {
                Box(Modifier.fillMaxWidth()) {
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

                    IconButton(
                        modifier = Modifier
                            .padding(end = 5.dp)
                            .size(40.dp)
                            .align(Alignment.CenterEnd),
                        onClick = { moreActionsExpanded = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )

                        ChatroomTabMenu(
                            moreActionsExpanded,
                            { moreActionsExpanded = false },
                            { markKnownAsRead.value = true },
                            { markNewAsRead.value = true }
                        )
                    }
                }

                HorizontalPager(pageCount = 2, state = pagerState) { page ->
                    ChatroomListFeedView(
                        viewModel = tabs[page].viewModel,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        markAsRead = tabs[page].markAsRead
                    )
                }
            }
        }
    }
}

@Composable
fun WatchAccountForListScreen(knownFeedViewModel: NostrChatroomListKnownFeedViewModel, newFeedViewModel: NostrChatroomListNewFeedViewModel, accountViewModel: AccountViewModel) {
    LaunchedEffect(accountViewModel) {
        launch(Dispatchers.IO) {
            NostrChatroomListDataSource.start()
            knownFeedViewModel.invalidateData(true)
            newFeedViewModel.invalidateData(true)
        }
    }
}

@Immutable
class ChatroomListTabItem(val resource: Int, val viewModel: FeedViewModel, val markAsRead: MutableState<Boolean>)

@Composable
fun ChatroomTabMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onMarkKnownAsRead: () -> Unit,
    onMarkNewAsRead: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(onClick = {
            onMarkKnownAsRead()
            onDismiss()
        }) {
            Text(stringResource(R.string.mark_all_known_as_read))
        }
        DropdownMenuItem(onClick = {
            onMarkNewAsRead()
            onDismiss()
        }) {
            Text(stringResource(R.string.mark_all_new_as_read))
        }
        DropdownMenuItem(onClick = {
            onMarkKnownAsRead()
            onMarkNewAsRead()
            onDismiss()
        }) {
            Text(stringResource(R.string.mark_all_as_read))
        }
    }
}
