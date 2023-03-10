package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.note.ChatroomMessageCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun ChatroomFeedView(viewModel: FeedViewModel, accountViewModel: AccountViewModel, navController: NavController, routeForLastRead: String, onWantsToReply: (Note) -> Unit) {
    val feedState by viewModel.feedContent.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }

    val listState = rememberForeverLazyListState(routeForLastRead)

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
                    LaunchedEffect(state.feed.value.firstOrNull()) {
                        if (listState.firstVisibleItemIndex <= 1) {
                            listState.animateScrollToItem(0)
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = 10.dp,
                            bottom = 10.dp
                        ),
                        reverseLayout = true,
                        state = listState
                    ) {
                        itemsIndexed(state.feed.value, key = { _, item -> item.idHex }) { _, item ->
                            ChatroomMessageCompose(item, routeForLastRead, accountViewModel = accountViewModel, navController = navController, onWantsToReply = onWantsToReply)
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
