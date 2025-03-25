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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.NostrThreadDataSource
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.screen.NostrThreadFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun ThreadScreen(
    noteId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (noteId == null) return

    val lifeCycleOwner = LocalLifecycleOwner.current

    val feedViewModel: NostrThreadFeedViewModel =
        viewModel(
            key = noteId + "NostrThreadFeedViewModel",
            factory = NostrThreadFeedViewModel.Factory(accountViewModel.account, noteId),
        )

    NostrThreadDataSource.loadThread(noteId)

    DisposableEffect(noteId) {
        feedViewModel.invalidateData(true)
        onDispose {
            NostrThreadDataSource.loadThread(null)
            NostrThreadDataSource.stop()
        }
    }

    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    println("Thread Start")
                    NostrThreadDataSource.loadThread(noteId)
                    NostrThreadDataSource.start()
                    feedViewModel.invalidateData(true)
                }
                if (event == Lifecycle.Event.ON_PAUSE) {
                    println("Thread Stop")
                    NostrThreadDataSource.loadThread(null)
                    NostrThreadDataSource.stop()
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    LoadNote(noteId, accountViewModel) {
        if (it != null) {
            // this will force loading every post from this thread.
            val metadata = it.live().metadata.observeAsState()
        }
    }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarExtensibleWithBackButton(
                title = { Text(stringRes(id = R.string.thread_title)) },
                popBack = nav::popBack,
            )
        },
        accountViewModel = accountViewModel,
    ) {
        Column(Modifier.padding(it)) {
            ThreadFeedView(noteId, feedViewModel, accountViewModel, nav)
        }
    }
}
