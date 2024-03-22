/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding

@Composable
fun StringFeedView(
    viewModel: StringFeedViewModel,
    pre: (@Composable () -> Unit)? = null,
    post: (@Composable () -> Unit)? = null,
    inner: @Composable (String) -> Unit,
) {
    val feedState by viewModel.feedContent.collectAsStateWithLifecycle()

    Crossfade(targetState = feedState, animationSpec = tween(durationMillis = 100)) { state ->
        when (state) {
            is StringFeedState.Empty -> {
                StringFeedEmpty(pre, post) { viewModel.invalidateData() }
            }
            is StringFeedState.FeedError -> {
                FeedError(state.errorMessage) { viewModel.invalidateData() }
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
    onRefresh: () -> Unit,
) {
    Column {
        pre?.let { it() }

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.feed_is_empty))
            OutlinedButton(onClick = onRefresh) { Text(text = stringResource(R.string.refresh)) }
        }

        post?.let { it() }
    }
}

@Composable
private fun FeedLoaded(
    state: StringFeedState.Loaded,
    pre: (@Composable () -> Unit)? = null,
    post: (@Composable () -> Unit)? = null,
    inner: @Composable (String) -> Unit,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        contentPadding = FeedPadding,
        state = listState,
    ) {
        item { pre?.let { it() } }

        itemsIndexed(state.feed.value, key = { _, item -> item }) { _, item ->
            inner(item)

            HorizontalDivider(
                thickness = DividerThickness,
            )
        }

        item { post?.let { it() } }
    }
}
