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
package com.vitorpamplona.amethyst.ui.feeds

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedLoaded(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    routeForLastRead: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()
    val isPreFetching by accountViewModel.feedStates.homeNewThreads.isPreFetching
        .collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    // Create prefetch state
    val prefetchState = remember { LazyLayoutPrefetchState() }

    // Create prefetch manager
    val prefetchManager =
        remember(prefetchState, coroutineScope) {
            NotePrefetchManager(accountViewModel.feedStates.homeNewThreads, prefetchState, coroutineScope)
        }

    // Monitor scroll position for prefetching with debouncing
    LaunchedEffect(listState.firstVisibleItemIndex, items.list.size) {
        // Debounce scroll events to prevent excessive recomposition
        delay(100) // 100ms debounce
        val currentIndex = listState.firstVisibleItemIndex
        val totalItems = items.list.size
        prefetchManager.updatePrefetching(currentIndex, totalItems)
    }

    LazyColumn(
        contentPadding = FeedPadding,
        state = listState,
        // Performance optimizations for better scrolling
        userScrollEnabled = true,
        reverseLayout = false,
    ) {
        itemsIndexed(
            items.list,
            key = { index, item -> "${item.idHex}_$index" }, // More unique key with index
        ) { index, item ->
            // Use remember to stabilize the item and prevent unnecessary recomposition
            val stableItem = remember(item.idHex, index) { item }

            Row(Modifier.fillMaxWidth()) {
                NoteCompose(
                    stableItem,
                    modifier = Modifier.fillMaxWidth(),
                    routeForLastRead = routeForLastRead,
                    isBoostedNote = false,
                    isHiddenFeed = items.showHidden,
                    quotesLeft = 3,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            HorizontalDivider(
                thickness = DividerThickness,
            )
        }

        // Show prefetching indicator
        if (isPreFetching) {
            item {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(4.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "Loading more notes...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
