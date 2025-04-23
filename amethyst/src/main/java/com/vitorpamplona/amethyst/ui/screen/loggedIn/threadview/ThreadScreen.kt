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
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview.dal.ThreadFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview.datasources.ThreadFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun ThreadScreen(
    noteId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (noteId == null) return

    val feedViewModel: ThreadFeedViewModel =
        viewModel(
            key = noteId + "NostrThreadFeedViewModel",
            factory = ThreadFeedViewModel.Factory(accountViewModel.account, noteId),
        )

    WatchLifecycleAndUpdateModel(feedViewModel)
    ThreadFilterAssemblerSubscription(noteId, accountViewModel.dataSources().thread)

    LoadNote(noteId, accountViewModel) {
        if (it != null) {
            // this will force loading every post from this thread.
            EventFinderFilterAssemblerSubscription(it)
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
