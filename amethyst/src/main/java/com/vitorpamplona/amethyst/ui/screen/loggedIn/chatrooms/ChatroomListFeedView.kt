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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chatrooms

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.ui.feeds.FeedError
import com.vitorpamplona.amethyst.ui.feeds.FeedState
import com.vitorpamplona.amethyst.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding

@Composable
fun ChatroomListFeedView(
    feedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    markAsRead: MutableState<Boolean>,
) {
    RefresheableBox(feedContentState, true) { CrossFadeState(feedContentState, accountViewModel, nav, markAsRead) }
}

@Composable
private fun CrossFadeState(
    feedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    markAsRead: MutableState<Boolean>,
) {
    val feedState by feedContentState.feedContent.collectAsStateWithLifecycle()

    CrossfadeIfEnabled(
        targetState = feedState,
        animationSpec = tween(durationMillis = 100),
        accountViewModel = accountViewModel,
    ) { state ->
        when (state) {
            is FeedState.Empty -> {
                FeedEmpty { feedContentState.invalidateData() }
            }
            is FeedState.FeedError -> {
                FeedError(state.errorMessage) { feedContentState.invalidateData() }
            }
            is FeedState.Loaded -> {
                FeedLoaded(state, accountViewModel, nav, markAsRead)
            }
            FeedState.Loading -> {
                LoadingFeed()
            }
        }
    }
}

@Composable
private fun FeedLoaded(
    loaded: FeedState.Loaded,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    markAsRead: MutableState<Boolean>,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    LaunchedEffect(key1 = markAsRead.value) {
        if (markAsRead.value) {
            accountViewModel.markAllAsRead(items.list) { markAsRead.value = false }
        }
    }

    LazyColumn(
        contentPadding = FeedPadding,
        state = listState,
    ) {
        itemsIndexed(
            items.list,
            key = { index, item -> if (index == 0) index else item.idHex },
        ) { _, item ->
            Row(Modifier.fillMaxWidth()) {
                ChatroomHeaderCompose(
                    item,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            HorizontalDivider(
                thickness = DividerThickness,
            )
        }
    }
}
