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
package com.vitorpamplona.amethyst.ui.feeds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.pullrefresh.PullRefreshIndicator
import androidx.compose.material3.pullrefresh.pullRefresh
import androidx.compose.material3.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun RefresheableBox(
    invalidateableContent: InvalidatableContent,
    enablePullRefresh: Boolean = true,
    content: @Composable () -> Unit,
) {
    RefresheableBox(
        enablePullRefresh = enablePullRefresh,
        onRefresh = { invalidateableContent.invalidateData() },
        content = content,
    )
}

@Composable
fun RefresheableBox(
    enablePullRefresh: Boolean = true,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    var refreshing by remember { mutableStateOf(false) }
    val refresh = {
        refreshing = true
        onRefresh()
        refreshing = false
    }
    val pullRefreshState = rememberPullRefreshState(refreshing, onRefresh = refresh)

    val modifier =
        if (enablePullRefresh) {
            Modifier.fillMaxSize().pullRefresh(pullRefreshState)
        } else {
            Modifier.fillMaxSize()
        }

    Box(modifier) {
        content()

        if (enablePullRefresh) {
            PullRefreshIndicator(
                refreshing = refreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
