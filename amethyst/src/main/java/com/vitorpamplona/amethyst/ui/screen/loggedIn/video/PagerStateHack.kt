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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video

import androidx.annotation.FloatRange
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.vitorpamplona.amethyst.ui.feeds.rememberForeverPagerState
import kotlin.math.abs

/**
 * This file only exists to fix an interference between the Disappearing Top
 * and Bottom Scaffold bars and the offsetFraction of the pager. The current
 * implementation ends the scroll at a state where some fraction is still present
 * which places videos away from their natural position in the page.
 *
 * The fix simply animates it back to the fraction = 0.
 */

@Composable
fun myRememberPagerState(
    initialPage: Int = 0,
    @FloatRange(from = -0.5, to = 0.5) initialPageOffsetFraction: Float = 0f,
    pageCount: () -> Int,
): PagerState =
    rememberSaveable(saver = DefaultPagerState.Saver) {
        DefaultPagerState(
            initialPage,
            initialPageOffsetFraction,
            pageCount,
        )
    }.apply {
        pageCountState.value = pageCount
    }

@Composable
fun myRememberForeverPagerState(
    key: String,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Float = 0.0f,
    pageCount: () -> Int,
): PagerState =
    rememberForeverPagerState(key, initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset, pageCount) { initialPage, initialPageOffsetFraction, pageCount ->
        myRememberPagerState(initialPage, initialPageOffsetFraction, pageCount)
    }

private class DefaultPagerState(
    currentPage: Int,
    currentPageOffsetFraction: Float,
    updatedPageCount: () -> Int,
) : PagerState(currentPage, currentPageOffsetFraction) {
    var pageCountState = mutableStateOf(updatedPageCount)
    override val pageCount: Int get() = pageCountState.value.invoke()

    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit,
    ) {
        super.scroll(scrollPriority, block)
        if (abs(currentPageOffsetFraction) > 0) {
            animateScrollToPage(currentPage, 0f)
        }
    }

    companion object {
        /**
         * To keep current page and current page offset saved
         */
        val Saver: Saver<DefaultPagerState, *> =
            listSaver(
                save = {
                    listOf(
                        it.currentPage,
                        (it.currentPageOffsetFraction).coerceIn(-0.5f, 0.5f),
                        it.pageCount,
                    )
                },
                restore = {
                    DefaultPagerState(
                        currentPage = it[0] as Int,
                        currentPageOffsetFraction = it[1] as Float,
                        updatedPageCount = { it[2] as Int },
                    )
                },
            )
    }
}
