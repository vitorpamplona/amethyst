package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.vitorpamplona.amethyst.ui.note.ChatroomMessageCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun ChatroomFeedView(viewModel: FeedViewModel, accountViewModel: AccountViewModel, navController: NavController, routeForLastRead: String?) {
    val feedState by viewModel.feedContent.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.refresh()
            isRefreshing = false
        }
    }

    Column() {
        Crossfade(targetState = feedState, animationSpec = tween(durationMillis = 100)) { state ->
            when (state) {
                is FeedState.Empty -> {
                    FeedEmpty {
                        isRefreshing = true
                    }
                }
                is FeedState.FeedError -> {
                    FeedError(state.errorMessage) {
                        isRefreshing = true
                    }
                }
                is FeedState.Loaded -> {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = 10.dp,
                            bottom = 10.dp
                        ),
                        reverseLayout = true,
                        state = listState
                    ) {
                        var previousDate: String = ""
                        itemsIndexed(state.feed.value, key = { index, item -> if (index == 0) index else item.idHex }) { index, item ->
                            ChatroomMessageCompose(item, routeForLastRead, accountViewModel = accountViewModel, navController = navController)
                        }
                    }
                }
                FeedState.Loading -> {
                    LoadingFeed()
                }
            }
        }
    }
}