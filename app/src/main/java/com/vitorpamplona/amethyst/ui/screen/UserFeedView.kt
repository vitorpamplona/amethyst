package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun RefreshingFeedUserFeedView(
    viewModel: UserFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    enablePullRefresh: Boolean = true
) {
    RefresheableView(viewModel, enablePullRefresh) {
        UserFeedView(viewModel, accountViewModel, nav)
    }
}

@Composable
fun UserFeedView(
    viewModel: UserFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val feedState by viewModel.feedContent.collectAsState()

    Crossfade(targetState = feedState, animationSpec = tween(durationMillis = 100)) { state ->
        when (state) {
            is UserFeedState.Empty -> {
                FeedEmpty {
                    viewModel.invalidateData()
                }
            }

            is UserFeedState.FeedError -> {
                FeedError(state.errorMessage) {
                    viewModel.invalidateData()
                }
            }

            is UserFeedState.Loaded -> {
                FeedLoaded(state, accountViewModel, nav)
            }

            is UserFeedState.Loading -> {
                LoadingFeed()
            }
        }
    }
}

@Composable
private fun FeedLoaded(
    state: UserFeedState.Loaded,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
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
            UserCompose(item, accountViewModel = accountViewModel, nav = nav)
        }
    }
}
