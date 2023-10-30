package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.theme.FeedPadding

@Composable
fun RefreshingFeedStringFeedView(
    viewModel: StringFeedViewModel,
    enablePullRefresh: Boolean = true,
    inner: @Composable (String) -> Unit
) {
    RefresheableView(viewModel, enablePullRefresh) {
        StringFeedView(viewModel, inner = inner)
    }
}

@Composable
fun StringFeedView(
    viewModel: StringFeedViewModel,
    pre: (@Composable () -> Unit)? = null,
    post: (@Composable () -> Unit)? = null,
    inner: @Composable (String) -> Unit
) {
    val feedState by viewModel.feedContent.collectAsStateWithLifecycle()

    Crossfade(targetState = feedState, animationSpec = tween(durationMillis = 100)) { state ->
        when (state) {
            is StringFeedState.Empty -> {
                StringFeedEmpty(pre, post) {
                    viewModel.invalidateData()
                }
            }

            is StringFeedState.FeedError -> {
                FeedError(state.errorMessage) {
                    viewModel.invalidateData()
                }
            }

            is StringFeedState.Loaded -> {
                FeedLoaded(state, pre, post, inner)
            }

            is StringFeedState.Loading -> {
                LoadingFeed()
            }
        }
    }
}

@Composable
fun StringFeedEmpty(
    pre: (@Composable () -> Unit)? = null,
    post: (@Composable () -> Unit)? = null,
    onRefresh: () -> Unit
) {
    Column() {
        pre?.let { it() }

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.feed_is_empty))
            OutlinedButton(onClick = onRefresh) {
                Text(text = stringResource(R.string.refresh))
            }
        }

        post?.let { it() }
    }
}

@Composable
private fun FeedLoaded(
    state: StringFeedState.Loaded,
    pre: (@Composable () -> Unit)? = null,
    post: (@Composable () -> Unit)? = null,
    inner: @Composable (String) -> Unit
) {
    val listState = rememberLazyListState()

    LazyColumn(
        contentPadding = FeedPadding,
        state = listState
    ) {
        item {
            pre?.let { it() }
        }

        itemsIndexed(state.feed.value, key = { _, item -> item }) { _, item ->
            inner(item)
        }

        item {
            post?.let { it() }
        }
    }
}
