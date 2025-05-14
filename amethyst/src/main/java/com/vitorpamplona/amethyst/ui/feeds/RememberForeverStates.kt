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

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.math.roundToInt

private val savedScrollStates = mutableMapOf<String, ScrollState>()

private data class ScrollState(
    val index: Int,
    val scrollOffsetFraction: Float,
)

object ScrollStateKeys {
    const val NOTIFICATION_SCREEN = "NotificationsFeed"
    const val VIDEO_SCREEN = "VideoFeed"
    const val HOME_FOLLOWS = "HomeFollowsFeed"
    const val HOME_REPLIES = "HomeFollowsRepliesFeed"
    const val PROFILE_GALLERY = "ProfileGalleryFeed"

    const val DRAFTS = "DraftsFeed"

    const val DISCOVER_FOLLOWS = "DiscoverFollowSetsFeed"
    const val DISCOVER_READS = "DiscoverReadsFeed"
    const val DISCOVER_CONTENT = "DiscoverDiscoverContentFeed"
    const val DISCOVER_MARKETPLACE = "DiscoverMarketplaceFeed"
    const val DISCOVER_LIVE = "DiscoverLiveFeed"
    const val DISCOVER_COMMUNITY = "DiscoverCommunitiesFeed"
    const val DISCOVER_CHATS = "DiscoverChatsFeed"
}

object PagerStateKeys {
    const val HOME_SCREEN = "PagerHome"
    const val DISCOVER_SCREEN = "PagerDiscover"
}

@Composable
fun rememberForeverLazyGridState(
    key: String,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
): LazyGridState {
    val scrollState =
        rememberSaveable(saver = LazyGridState.Saver) {
            val savedValue = savedScrollStates[key]
            val savedIndex = savedValue?.index ?: initialFirstVisibleItemIndex
            val savedOffset =
                savedValue?.scrollOffsetFraction ?: initialFirstVisibleItemScrollOffset.toFloat()
            LazyGridState(
                savedIndex,
                savedOffset.roundToInt(),
            )
        }
    DisposableEffect(scrollState) {
        onDispose {
            val lastIndex = scrollState.firstVisibleItemIndex
            val lastOffset = scrollState.firstVisibleItemScrollOffset
            savedScrollStates[key] = ScrollState(lastIndex, lastOffset.toFloat())
        }
    }
    return scrollState
}

@Composable
fun rememberForeverLazyListState(
    key: String,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
): LazyListState {
    val scrollState =
        rememberSaveable(saver = LazyListState.Saver) {
            val savedValue = savedScrollStates[key]
            val savedIndex = savedValue?.index ?: initialFirstVisibleItemIndex
            val savedOffset =
                savedValue?.scrollOffsetFraction ?: initialFirstVisibleItemScrollOffset.toFloat()
            LazyListState(
                savedIndex,
                savedOffset.roundToInt(),
            )
        }
    DisposableEffect(scrollState) {
        onDispose {
            val lastIndex = scrollState.firstVisibleItemIndex
            val lastOffset = scrollState.firstVisibleItemScrollOffset
            savedScrollStates[key] = ScrollState(lastIndex, lastOffset.toFloat())
        }
    }
    return scrollState
}

@Composable
fun rememberForeverPagerState(
    key: String,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Float = 0.0f,
    pageCount: () -> Int,
): PagerState =
    rememberForeverPagerState(key, initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset, pageCount) { initialPage, initialPageOffsetFraction, pageCount ->
        rememberPagerState(initialPage, initialPageOffsetFraction, pageCount)
    }

@Composable
fun rememberForeverPagerState(
    key: String,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Float = 0.0f,
    pageCount: () -> Int,
    rememberPagerStateFunction: @Composable (
        initialPage: Int,
        initialPageOffsetFraction: Float,
        pageCount: () -> Int,
    ) -> PagerState,
): PagerState {
    val savedValue = savedScrollStates[key]
    val savedIndex = savedValue?.index ?: initialFirstVisibleItemIndex
    val savedOffset = savedValue?.scrollOffsetFraction ?: initialFirstVisibleItemScrollOffset

    val scrollState =
        rememberPagerStateFunction(
            savedIndex,
            savedOffset,
            pageCount,
        )

    DisposableEffect(scrollState) {
        onDispose {
            val lastIndex = scrollState.currentPage
            val lastOffset = scrollState.currentPageOffsetFraction
            savedScrollStates[key] = ScrollState(lastIndex, lastOffset)
        }
    }

    return scrollState
}
