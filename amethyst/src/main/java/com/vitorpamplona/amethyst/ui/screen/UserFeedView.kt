/*
 * Copyright (c) 2025 Vitor Pamplona
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

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.ui.feeds.FeedError
import com.vitorpamplona.amethyst.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.layouts.rememberMergedPadding
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding

@Composable
fun RefreshingFeedUserFeedView(
    viewModel: UserFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    enablePullRefresh: Boolean = true,
    scaffoldPadding: PaddingValues = PaddingValues(0.dp),
) {
    RefresheableBox(viewModel, enablePullRefresh) { UserFeedView(viewModel, scaffoldPadding, accountViewModel, nav) }
}

@Composable
fun UserFeedView(
    viewModel: UserFeedViewModel,
    scaffoldPadding: PaddingValues = PaddingValues(0.dp),
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedState by viewModel.feedContent.collectAsStateWithLifecycle()

    CrossfadeIfEnabled(targetState = feedState, animationSpec = tween(durationMillis = 100), accountViewModel = accountViewModel) { state ->
        when (state) {
            is UserFeedState.Empty -> {
                FeedEmpty { viewModel.invalidateData() }
            }

            is UserFeedState.FeedError -> {
                FeedError(state.errorMessage) { viewModel.invalidateData() }
            }

            is UserFeedState.Loaded -> {
                FeedLoaded(state, scaffoldPadding, accountViewModel, nav)
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
    scaffoldPadding: PaddingValues,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by state.feed.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = rememberMergedPadding(scaffoldPadding, FeedPadding),
        state = listState,
    ) {
        itemsIndexed(items, key = { _, item -> item.pubkeyHex }) { _, item ->
            UserCompose(item, accountViewModel = accountViewModel, nav = nav)
            HorizontalDivider(
                thickness = DividerThickness,
            )
        }
    }
}
