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
import com.vitorpamplona.amethyst.ui.note.ZapNoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun LnZapFeedView(
    viewModel: LnZapFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val feedState by viewModel.feedContent.collectAsState()

    Crossfade(targetState = feedState, animationSpec = tween(durationMillis = 100)) { state ->
        when (state) {
            is LnZapFeedState.Empty -> {
                FeedEmpty {
                    viewModel.invalidateData()
                }
            }
            is LnZapFeedState.FeedError -> {
                FeedError(state.errorMessage) {
                    viewModel.invalidateData()
                }
            }
            is LnZapFeedState.Loaded -> {
                LnZapFeedLoaded(state, accountViewModel, nav)
            }
            is LnZapFeedState.Loading -> {
                LoadingFeed()
            }
        }
    }
}

@Composable
private fun LnZapFeedLoaded(
    state: LnZapFeedState.Loaded,
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
        itemsIndexed(state.feed.value, key = { _, item -> item.zapEvent.idHex }) { _, item ->
            ZapNoteCompose(item, accountViewModel = accountViewModel, nav = nav)
        }
    }
}
