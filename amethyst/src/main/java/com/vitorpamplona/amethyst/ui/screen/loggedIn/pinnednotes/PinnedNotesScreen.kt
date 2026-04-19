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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.pinnednotes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.amethyst.ui.components.DeletedItemsBanner
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.pinnednotes.dal.PinnedNotesFeedViewModel
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun PinnedNotesScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pinnedNotesFeedViewModel: PinnedNotesFeedViewModel =
        viewModel(
            key = "NostrPinnedNotesFeedViewModel",
            factory = PinnedNotesFeedViewModel.Factory(accountViewModel.account),
        )

    val pinState by accountViewModel.account.pinState.pinnedNotesList
        .collectAsStateWithLifecycle(null)

    LaunchedEffect(pinState) {
        pinnedNotesFeedViewModel.invalidateData()
    }

    // Preload all pinned events so they don't load one-by-one when scrolling
    PreloadPinnedEvents(pinState, accountViewModel)

    RenderPinnedNotesScreen(pinnedNotesFeedViewModel, pinState, accountViewModel, nav)
}

@Composable
private fun RenderPinnedNotesScreen(
    pinnedNotesFeedViewModel: PinnedNotesFeedViewModel,
    pinState: List<Note>?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var bannerDismissed by remember { mutableStateOf(false) }
    val deletedPins =
        remember(pinState) {
            pinState
                ?.filter { note ->
                    note.event?.let(accountViewModel.account.cache::hasBeenDeleted) == true
                }.orEmpty()
        }

    LaunchedEffect(deletedPins) {
        if (deletedPins.isEmpty()) bannerDismissed = false
    }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.pinned_notes), nav::popBack)
        },
        accountViewModel = accountViewModel,
    ) {
        Column(Modifier.padding(it).fillMaxHeight()) {
            if (!bannerDismissed) {
                DeletedItemsBanner(
                    count = deletedPins.size,
                    onRemove = {
                        accountViewModel.removeDeletedPins(deletedPins.toSet())
                        bannerDismissed = true
                    },
                    onDismiss = { bannerDismissed = true },
                )
            }
            RefresheableFeedView(
                pinnedNotesFeedViewModel,
                null,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun PreloadPinnedEvents(
    pinState: List<Note>?,
    accountViewModel: AccountViewModel,
) {
    val eventFinder = accountViewModel.dataSources().eventFinder
    val account = accountViewModel.account

    val queries =
        remember(pinState) {
            pinState
                .orEmpty()
                .filter { it.event == null }
                .map { EventFinderQueryState(it, account) }
        }

    DisposableEffect(queries) {
        eventFinder.subscribe(queries)
        onDispose {
            eventFinder.unsubscribe(queries)
        }
    }
}
