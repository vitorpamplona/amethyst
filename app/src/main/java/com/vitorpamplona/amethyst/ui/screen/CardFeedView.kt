package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.OutlinedButton
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
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.vitorpamplona.amethyst.ui.note.BoostSetCompose
import com.vitorpamplona.amethyst.ui.note.LikeSetCompose
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun CardFeedView(viewModel: CardFeedViewModel, accountViewModel: AccountViewModel) {
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
                            itemsIndexed(state.feed) { index, item ->
                                when (item) {
                                    is NoteCard -> NoteCompose(item.note, isInnerNote = false, accountViewModel = accountViewModel)
                                    is LikeSetCard -> LikeSetCompose(item, isInnerNote = false, accountViewModel = accountViewModel)
                                    is BoostSetCard -> BoostSetCompose(item, isInnerNote = false, accountViewModel = accountViewModel)
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
