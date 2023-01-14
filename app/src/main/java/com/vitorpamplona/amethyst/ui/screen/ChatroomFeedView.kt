package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.ExperimentalLifecycleComposeApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.RichTextViewer
import com.vitorpamplona.amethyst.ui.note.ChatroomMessageCompose
import com.vitorpamplona.amethyst.ui.note.timeAgoLong
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun ChatroomFeedView(userId: String, viewModel: FeedViewModel, accountViewModel: AccountViewModel, navController: NavController) {
    val feedState by viewModel.feedContent.collectAsStateWithLifecycle()

    var isRefreshing by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    val listState = rememberLazyListState()

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.refresh()
            isRefreshing = false
        }
    }

    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = {
            isRefreshing = true
        },
    ) {
        Column() {
            Crossfade(targetState = feedState) { state ->
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
                            state = listState
                        ) {
                            var previousDate: String = ""
                            itemsIndexed(state.feed, key = { _, item -> item.idHex }) { index, item ->
                                ChatroomMessageCompose(item, accountViewModel = accountViewModel, navController = navController)
                            }
                        }

                        LaunchedEffect(Unit) {
                            listState.animateScrollToItem(state.feed.size-1, 0)
                        }
                    }
                    FeedState.Loading -> {
                        LoadingFeed()
                    }
                }
            }
        }
    }
}