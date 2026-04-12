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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.default

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.default.dal.BookmarkPrivateFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.default.dal.BookmarkPublicFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.default.dal.PinnedNotesFeedViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import kotlinx.coroutines.launch

@Composable
fun BookmarkListScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val publicFeedViewModel: BookmarkPublicFeedViewModel =
        viewModel(
            key = "NostrBookmarkPublicFeedViewModel",
            factory = BookmarkPublicFeedViewModel.Factory(accountViewModel.account),
        )

    val privateFeedViewModel: BookmarkPrivateFeedViewModel =
        viewModel(
            key = "NostrBookmarkPrivateFeedViewModel",
            factory = BookmarkPrivateFeedViewModel.Factory(accountViewModel.account),
        )

    val pinnedNotesFeedViewModel: PinnedNotesFeedViewModel =
        viewModel(
            key = "NostrPinnedNotesFeedViewModel",
            factory = PinnedNotesFeedViewModel.Factory(accountViewModel.account),
        )

    val bookmarkState by accountViewModel.account.bookmarkState.bookmarks
        .collectAsStateWithLifecycle(null)

    val pinState by accountViewModel.account.pinState.pinnedNotesList
        .collectAsStateWithLifecycle(null)

    LaunchedEffect(bookmarkState) {
        publicFeedViewModel.invalidateData()
        privateFeedViewModel.invalidateData()
    }

    LaunchedEffect(pinState) {
        pinnedNotesFeedViewModel.invalidateData()
    }

    // Preload all bookmarked and pinned events so they don't load one-by-one when scrolling
    PreloadBookmarkEvents(bookmarkState, pinState, accountViewModel)

    RenderBookmarkScreen(publicFeedViewModel, privateFeedViewModel, pinnedNotesFeedViewModel, accountViewModel, nav)
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RenderBookmarkScreen(
    publicFeedViewModel: BookmarkPublicFeedViewModel,
    privateFeedViewModel: BookmarkPrivateFeedViewModel,
    pinnedNotesFeedViewModel: PinnedNotesFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pagerState = rememberPagerState { 3 }
    val coroutineScope = rememberCoroutineScope()

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                TopBarWithBackButton(stringRes(id = R.string.bookmarks_title), nav::popBack)
                SecondaryScrollableTabRow(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 8.dp,
                    modifier = TabRowHeight,
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text(text = stringRes(R.string.private_bookmarks)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text(text = stringRes(R.string.public_bookmarks)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 2,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                        text = { Text(text = stringRes(R.string.pinned_notes)) },
                    )
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        Column(Modifier.padding(it).fillMaxHeight()) {
            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> {
                        RefresheableFeedView(
                            privateFeedViewModel,
                            null,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }

                    1 -> {
                        RefresheableFeedView(
                            publicFeedViewModel,
                            null,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }

                    2 -> {
                        RefresheableFeedView(
                            pinnedNotesFeedViewModel,
                            null,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreloadBookmarkEvents(
    bookmarkState: com.vitorpamplona.amethyst.commons.model.nip51Lists.BookmarkListState.BookmarkList?,
    pinState: List<com.vitorpamplona.amethyst.model.Note>?,
    accountViewModel: AccountViewModel,
) {
    val eventFinder = accountViewModel.dataSources().eventFinder
    val account = accountViewModel.account

    val queries =
        remember(bookmarkState, pinState) {
            val allNotes = (bookmarkState?.public.orEmpty() + bookmarkState?.private.orEmpty() + pinState.orEmpty())
            allNotes
                .filter { it.event == null }
                .map { EventFinderQueryState(it, account) }
        }

    DisposableEffect(queries) {
        eventFinder.subscribe(queries)
        onDispose {
            eventFinder.unsubscribe(queries)
        }
    }
}
