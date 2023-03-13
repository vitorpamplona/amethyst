package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun UserFeedView(viewModel: UserFeedViewModel, accountViewModel: AccountViewModel, navController: NavController) {
    val feedState by viewModel.feedContent.collectAsState()

    var refreshing by remember { mutableStateOf(false) }
    val refresh = { refreshing = true; viewModel.refresh(); refreshing = false }
    val pullRefreshState = rememberPullRefreshState(refreshing, onRefresh = refresh)

    Box(Modifier.pullRefresh(pullRefreshState)) {
        Column() {
            Crossfade(targetState = feedState, animationSpec = tween(durationMillis = 100)) { state ->
                when (state) {
                    is UserFeedState.Empty -> {
                        FeedEmpty {
                            refreshing = true
                        }
                    }
                    is UserFeedState.FeedError -> {
                        FeedError(state.errorMessage) {
                            refreshing = true
                        }
                    }
                    is UserFeedState.Loaded -> {
                        refreshing = false
                        FeedLoaded(state, accountViewModel, navController)
                    }
                    is UserFeedState.Loading -> {
                        LoadingFeed()
                    }
                }
            }
        }

        PullRefreshIndicator(refreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun FeedLoaded(
    state: UserFeedState.Loaded,
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
        itemsIndexed(state.feed.value, key = { _, item -> item.pubkeyHex }) { _, item ->
            UserCompose(item, accountViewModel = accountViewModel, navController = navController)
        }
    }
}
