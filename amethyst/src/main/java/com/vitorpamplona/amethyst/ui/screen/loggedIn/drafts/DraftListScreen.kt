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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.drafts

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.components.SwipeToDeleteContainer
import com.vitorpamplona.amethyst.ui.feeds.FeedState
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.feeds.ScrollStateKeys.DRAFTS
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.RenderFeedState
import com.vitorpamplona.amethyst.ui.screen.SaveableFeedState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.drafts.dal.DraftEventsFeedViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.maxWidthWithBackground

@Composable
fun DraftListScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val draftFeedViewModel: DraftEventsFeedViewModel =
        viewModel(
            key = "NostrDraftEventsFeedViewModel",
            factory = DraftEventsFeedViewModel.Factory(accountViewModel.account),
        )

    RenderDraftListScreen(draftFeedViewModel, accountViewModel, nav)
}

@Composable
private fun RenderDraftListScreen(
    feedViewModel: DraftEventsFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
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

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.drafts), nav::popBack)
        },
        accountViewModel = accountViewModel,
    ) {
        Column(Modifier.padding(it).fillMaxHeight()) {
            RefresheableBox(feedViewModel) {
                SaveableFeedState(feedViewModel, DRAFTS) { listState ->
                    RenderFeedState(
                        viewModel = feedViewModel,
                        accountViewModel = accountViewModel,
                        listState = listState,
                        nav = nav,
                        routeForLastRead = null,
                        onLoaded = { DraftFeedLoaded(it, listState, null, accountViewModel, nav) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraftFeedLoaded(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    routeForLastRead: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
            },
            title = {
                Text(text = stringResource(R.string.drafts))
            },
            text = {
                Text(text = stringResource(R.string.delete_all_drafts_confirmation))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        accountViewModel.delete(items.list)
                        showDeleteDialog = false
                    },
                ) {
                    Text(text = stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                    },
                ) {
                    Text(text = stringResource(R.string.no))
                }
            },
        )
    }

    LazyColumn(
        contentPadding = FeedPadding,
        state = listState,
    ) {
        stickyHeader {
            Row(
                Modifier
                    .fillMaxWidth(),
                Arrangement.Center,
            ) {
                ElevatedButton(
                    onClick = { showDeleteDialog = true },
                ) {
                    Text(stringResource(R.string.delete_all))
                }
            }
        }
        itemsIndexed(items.list, key = { _, item -> item.idHex }) { _, item ->
            Row(Modifier.fillMaxWidth().animateItem()) {
                SwipeToDeleteContainer(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    onStartToEnd = { accountViewModel.delete(item) },
                ) {
                    NoteCompose(
                        item,
                        modifier = MaterialTheme.colorScheme.maxWidthWithBackground,
                        routeForLastRead = routeForLastRead,
                        isBoostedNote = false,
                        isHiddenFeed = items.showHidden,
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
