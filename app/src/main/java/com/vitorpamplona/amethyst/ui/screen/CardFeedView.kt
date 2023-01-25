package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.vitorpamplona.amethyst.ui.note.BoostSetCompose
import com.vitorpamplona.amethyst.ui.note.LikeSetCompose
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun CardFeedView(viewModel: CardFeedViewModel, accountViewModel: AccountViewModel, navController: NavController) {
    val feedState by viewModel.feedContent.collectAsState()

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
                    is CardFeedState.Empty -> {
                        FeedEmpty {
                            isRefreshing = true
                        }
                    }
                    is CardFeedState.FeedError -> {
                        FeedError(state.errorMessage) {
                            isRefreshing = true
                        }
                    }
                    is CardFeedState.Loaded -> {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                top = 10.dp,
                                bottom = 10.dp
                            ),
                            state = listState
                        ) {
                            itemsIndexed(state.feed, key = { _, item -> item.id() }) { index, item ->
                                when (item) {
                                    is NoteCard -> NoteCompose(item.note, isInnerNote = false, accountViewModel = accountViewModel, navController = navController)
                                    is LikeSetCard -> LikeSetCompose(item, isInnerNote = false, accountViewModel = accountViewModel, navController = navController)
                                    is BoostSetCard -> BoostSetCompose(item, isInnerNote = false, accountViewModel = accountViewModel, navController = navController)
                                }
                            }
                        }
                    }
                    CardFeedState.Loading -> {
                        LoadingFeed()
                    }
                }
            }
        }
    }
}
