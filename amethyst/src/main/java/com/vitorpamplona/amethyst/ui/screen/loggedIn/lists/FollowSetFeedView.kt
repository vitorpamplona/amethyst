/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.lists

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.feeds.FeedError
import com.vitorpamplona.amethyst.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer

@Composable
fun FollowSetFeedView(
    modifier: Modifier = Modifier,
    followSetState: FollowSetState,
    onRefresh: () -> Unit = {},
    onOpenItem: (String) -> Unit = {},
    onRenameItem: (targetSet: FollowSet, newName: String) -> Unit,
    onDeleteItem: (followSet: FollowSet) -> Unit,
) {
    when (followSetState) {
        FollowSetState.Loading -> LoadingFeed()

        is FollowSetState.Loaded -> {
            val followSetFeed = followSetState.feed
            FollowListLoaded(
                loadedFeedState = followSetFeed,
                onRefresh = onRefresh,
                onItemClick = onOpenItem,
                onItemRename = onRenameItem,
                onItemDelete = onDeleteItem,
            )
        }

        is FollowSetState.Empty -> {
            FollowListFeedEmpty(
                message =
                    "It seems you do not have any follow lists yet.\n " +
                        "Tap below to refresh, or tap the add buttons to create a new one.",
            ) {
                onRefresh()
            }
        }

        is FollowSetState.FeedError ->
            FeedError(
                followSetState.errorMessage,
            ) {
                onRefresh()
            }
    }
}

@Composable
fun FollowListLoaded(
    modifier: Modifier = Modifier,
    loadedFeedState: List<FollowSet>,
    onRefresh: () -> Unit = {},
    onItemClick: (itemIdentifier: String) -> Unit = {},
    onItemRename: (followSet: FollowSet, newName: String) -> Unit,
    onItemDelete: (followSet: FollowSet) -> Unit,
) {
    Log.d("FollowSetComposable", "FollowListLoaded: Follow Set size: ${loadedFeedState.size}")

    val listState = rememberLazyListState()
    RefresheableBox(
        onRefresh = onRefresh,
    ) {
        LazyColumn(
            state = listState,
            contentPadding = FeedPadding,
        ) {
            itemsIndexed(loadedFeedState, key = { _, item -> item.identifierTag }) { _, set ->
                CustomListItem(
                    modifier = Modifier.animateItem(),
                    followSet = set,
                    onFollowSetClick = {
                        onItemClick(set.identifierTag)
                    },
                    onFollowSetRename = {
                        onItemRename(set, it)
                    },
                    onFollowSetDelete = {
                        onItemDelete(set)
                    },
                )
                Spacer(modifier = StdVertSpacer)
            }
        }
    }
}

@Composable
fun FollowListFeedEmpty(
    message: String = stringRes(R.string.feed_is_empty),
    onRefresh: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message)
        Spacer(modifier = StdVertSpacer)
        OutlinedButton(onClick = onRefresh) { Text(text = stringRes(R.string.refresh)) }
    }
}
