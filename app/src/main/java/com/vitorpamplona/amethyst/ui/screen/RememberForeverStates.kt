package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.vitorpamplona.amethyst.ui.navigation.Route
import kotlin.math.roundToInt

private val savedScrollStates = mutableMapOf<String, ScrollState>()

private data class ScrollState(val index: Int, val scrollOffsetFraction: Float)

object ScrollStateKeys {
    const val GLOBAL_SCREEN = "Global"
    const val NOTIFICATION_SCREEN = "Notifications"
    const val VIDEO_SCREEN = "Video"
    val HOME_FOLLOWS = Route.Home.base + "Follows"
    val HOME_REPLIES = Route.Home.base + "FollowsReplies"
}

object PagerStateKeys {
    const val HOME_SCREEN = "Home"
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
    initialFirstVisibleItemScrollOffset: Float = 0.0f
): PagerState {
    val scrollState = rememberSaveable(saver = PagerState.Saver) {
        val savedValue = savedScrollStates[key]
        val savedIndex = savedValue?.index ?: initialFirstVisibleItemIndex
        val savedOffset = savedValue?.scrollOffsetFraction ?: initialFirstVisibleItemScrollOffset
        PagerState(
            savedIndex,
            savedOffset
        )
    }
    DisposableEffect(scrollState) {
        onDispose {
            val lastIndex = scrollState.currentPage
            val lastOffset = scrollState.currentPageOffsetFraction
            savedScrollStates[key] = ScrollState(lastIndex, lastOffset)
        }
    }
    return scrollState
}
