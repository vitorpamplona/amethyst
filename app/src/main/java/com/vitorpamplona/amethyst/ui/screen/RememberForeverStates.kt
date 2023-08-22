package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.vitorpamplona.amethyst.ui.navigation.Route
import kotlin.math.roundToInt

private val savedScrollStates = mutableMapOf<String, ScrollState>()

private data class ScrollState(val index: Int, val scrollOffsetFraction: Float)

object ScrollStateKeys {
    const val GLOBAL_SCREEN = "Global"
    const val NOTIFICATION_SCREEN = "Notifications"
    const val VIDEO_SCREEN = "Video"
    const val DISCOVER_SCREEN = "Discover"
    val HOME_FOLLOWS = Route.Home.base + "Follows"
    val HOME_REPLIES = Route.Home.base + "FollowsReplies"

    val DISCOVER_LIVE = Route.Home.base + "Live"
    val DISCOVER_COMMUNITY = Route.Home.base + "Communities"
    val DISCOVER_CHATS = Route.Home.base + "Chats"
}

object PagerStateKeys {
    const val HOME_SCREEN = "PagerHome"
    const val DISCOVER_SCREEN = "PagerDiscover"
}

@Composable
fun rememberForeverLazyListState(
    key: String,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0
): LazyListState {
    val scrollState = rememberSaveable(saver = LazyListState.Saver) {
        val savedValue = savedScrollStates[key]
        val savedIndex = savedValue?.index ?: initialFirstVisibleItemIndex
        val savedOffset = savedValue?.scrollOffsetFraction ?: initialFirstVisibleItemScrollOffset.toFloat()
        LazyListState(
            savedIndex,
            savedOffset.roundToInt()
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            val lastIndex = scrollState.firstVisibleItemIndex
            val lastOffset = scrollState.firstVisibleItemScrollOffset
            savedScrollStates[key] = ScrollState(lastIndex, lastOffset.toFloat())
        }
    }
    return scrollState
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberForeverPagerState(
    key: String,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Float = 0.0f,
    pageCount: () -> Int
): PagerState {
    val savedValue = savedScrollStates[key]
    val savedIndex = savedValue?.index ?: initialFirstVisibleItemIndex
    val savedOffset = savedValue?.scrollOffsetFraction ?: initialFirstVisibleItemScrollOffset

    val scrollState = rememberPagerState(
        savedIndex,
        savedOffset,
        pageCount
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
