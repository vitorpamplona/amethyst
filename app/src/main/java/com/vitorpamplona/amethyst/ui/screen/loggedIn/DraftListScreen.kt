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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.ui.screen.NostrDraftEventsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView

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

    RefresheableFeedView(
        feedViewModel,
        null,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}
