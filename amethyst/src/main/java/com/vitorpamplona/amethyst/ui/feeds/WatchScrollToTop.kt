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
package com.vitorpamplona.amethyst.ui.feeds

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.CardFeedContentState

@Composable
fun WatchScrollToTop(
    feedContentState: FeedContentState,
    listState: LazyListState,
) {
    val scrollToTop by feedContentState.scrollToTop.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop) {
        if (scrollToTop > 0 && feedContentState.scrollToTopPending) {
            listState.scrollToItem(index = 0)
            feedContentState.sentToTop()
        }
    }
}

@Composable
fun WatchScrollToTop(
    feedContentState: FeedContentState,
    listState: LazyGridState,
) {
    val scrollToTop by feedContentState.scrollToTop.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop) {
        if (scrollToTop > 0 && feedContentState.scrollToTopPending) {
            listState.scrollToItem(index = 0)
            feedContentState.sentToTop()
        }
    }
}

@Composable
fun WatchScrollToTop(
    feedContent: CardFeedContentState,
    listState: LazyListState,
) {
    val scrollToTop by feedContent.scrollToTop.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop) {
        if (scrollToTop > 0 && feedContent.scrolltoTopPending) {
            listState.scrollToItem(index = 0)
            feedContent.sentToTop()
        }
    }
}

@Composable
fun WatchScrollToTop(
    videoFeedContentState: FeedContentState,
    pagerState: PagerState,
) {
    val scrollToTop by videoFeedContentState.scrollToTop.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop) {
        if (scrollToTop > 0 && videoFeedContentState.scrollToTopPending) {
            pagerState.scrollToPage(page = 0)
            videoFeedContentState.sentToTop()
        }
    }
}

/**
 * Keeps the user pinned to index 0 when new items prepend to a feed, but
 * only if they were already at the very top right before the update.
 *
 * Why this is non-trivial: every feed uses stable `key = item.idHex` in
 * its lazy list, which makes Compose preserve the visual anchor across
 * data changes. When N items prepend, the user's previously-visible
 * top item is still on screen but its index is now N — so
 * `firstVisibleItemIndex` shifts from 0 to N without any user gesture.
 * A naive `if (firstVisibleItemIndex <= 1) scrollToItem(0)` check inside
 * `LaunchedEffect(items.firstOrNull())` therefore fails as soon as more
 * than one item arrives in the same batch.
 *
 * The trick: track "was at top" continuously via [snapshotFlow], but
 * only flip it from true → false when [LazyListState.isScrollInProgress]
 * is true (i.e. the user is actively scrolling). Data-driven index
 * shifts happen with `isScrollInProgress == false`, so they never poison
 * the cached value. When [firstItemKey] changes (head of the list moved),
 * if the cached value is still true, snap back to 0 with an instant
 * (non-animated) scroll so the prepend appears as in-place growth rather
 * than a visible jump-then-scroll.
 */
@Composable
fun StickToTopOnPrepend(
    listState: LazyListState,
    firstItemKey: Any?,
) {
    val wasAtTop = remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }.collect { atTop ->
            if (atTop) {
                wasAtTop.value = true
            } else if (listState.isScrollInProgress) {
                wasAtTop.value = false
            }
        }
    }

    LaunchedEffect(firstItemKey) {
        if (firstItemKey != null && wasAtTop.value && listState.firstVisibleItemIndex > 0) {
            listState.scrollToItem(0)
        }
    }
}

@Composable
fun StickToTopOnPrepend(
    gridState: LazyGridState,
    firstItemKey: Any?,
) {
    val wasAtTop = remember { mutableStateOf(true) }

    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        }.collect { atTop ->
            if (atTop) {
                wasAtTop.value = true
            } else if (gridState.isScrollInProgress) {
                wasAtTop.value = false
            }
        }
    }

    LaunchedEffect(firstItemKey) {
        if (firstItemKey != null && wasAtTop.value && gridState.firstVisibleItemIndex > 0) {
            gridState.scrollToItem(0)
        }
    }
}
