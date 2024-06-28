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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.ui.components.SwipeToDeleteContainer
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.FeedState
import com.vitorpamplona.amethyst.ui.screen.NostrDraftEventsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableBox
import com.vitorpamplona.amethyst.ui.screen.RenderFeedState
import com.vitorpamplona.amethyst.ui.screen.SaveableFeedState
import com.vitorpamplona.amethyst.ui.screen.ScrollStateKeys.DRAFTS
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding

@Composable
fun DraftListScreen(
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val draftFeedViewModel: NostrDraftEventsFeedViewModel =
        viewModel(
            key = "NostrDraftEventsFeedViewModel",
            factory = NostrDraftEventsFeedViewModel.Factory(accountViewModel.account),
        )

    RenderDraftListScreen(draftFeedViewModel, accountViewModel, nav)
}

@Composable
private fun RenderDraftListScreen(
    feedViewModel: NostrDraftEventsFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val lifeCycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(feedViewModel) {
        feedViewModel.invalidateData()
    }

    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    println("DraftList Start")
                    feedViewModel.invalidateData()
                }
                if (event == Lifecycle.Event.ON_PAUSE) {
                    println("DraftList Stop")
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    RefresheableBox(feedViewModel) {
        SaveableFeedState(feedViewModel, DRAFTS) { listState ->
            RenderFeedState(
                feedViewModel,
                accountViewModel,
                listState,
                nav,
                null,
                onLoaded = { DraftFeedLoaded(it, listState, null, accountViewModel, nav) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraftFeedLoaded(
    state: FeedState.Loaded,
    listState: LazyListState,
    routeForLastRead: String?,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = FeedPadding,
        state = listState,
    ) {
        itemsIndexed(state.feed.value, key = { _, item -> item.idHex }) { _, item ->
            SwipeToDeleteContainer(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                onStartToEnd = { accountViewModel.delete(item) },
                onEndToStart = { accountViewModel.delete(item) },
            ) {
                Row(Modifier.fillMaxWidth().animateItemPlacement().background(MaterialTheme.colorScheme.background)) {
                    NoteCompose(
                        item,
                        routeForLastRead = routeForLastRead,
                        modifier = Modifier.fillMaxWidth(),
                        isBoostedNote = false,
                        isHiddenFeed = state.showHidden.value,
                        quotesLeft = 3,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }

            HorizontalDivider(
                thickness = DividerThickness,
            )
        }
    }
}
