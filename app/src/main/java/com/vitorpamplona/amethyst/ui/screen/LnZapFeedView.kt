package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.note.ZapNoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun LnZapFeedView(viewModel: LnZapFeedViewModel, accountViewModel: AccountViewModel, navController: NavController) {
    val feedState by viewModel.feedContent.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

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
            Crossfade(targetState = feedState, animationSpec = tween(durationMillis = 100)) { state ->
                when (state) {
                    is LnZapFeedState.Empty -> {
                        FeedEmpty {
                            isRefreshing = true
                        }
                    }
                    is LnZapFeedState.FeedError -> {
                        FeedError(state.errorMessage) {
                            isRefreshing = true
                        }
                    }
                    is LnZapFeedState.Loaded -> {
                        LnZapFeedLoaded(state, accountViewModel, navController)
                    }
                    is LnZapFeedState.Loading -> {
                        LoadingFeed()
                    }
                }
            }
        }
    }
}

@Composable
private fun LnZapFeedLoaded(
    state: LnZapFeedState.Loaded,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val listState = rememberLazyListState()

    LazyColumn(
        contentPadding = PaddingValues(
            top = 10.dp,
            bottom = 10.dp
        ),
        state = listState
    ) {
        itemsIndexed(state.feed.value, key = { _, item -> item.second.idHex }) { index, item ->
            ZapNoteCompose(item, accountViewModel = accountViewModel, navController = navController)
        }
    }
}
