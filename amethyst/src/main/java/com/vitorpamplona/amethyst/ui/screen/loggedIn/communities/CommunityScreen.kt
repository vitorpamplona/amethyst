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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.communities

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.service.NostrCommunityDataSource
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.types.LongCommunityHeader
import com.vitorpamplona.amethyst.ui.note.types.ShortCommunityHeader
import com.vitorpamplona.amethyst.ui.screen.NostrCommunityFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.theme.BottomTopHeight

@Composable
fun CommunityScreen(
    aTagHex: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (aTagHex == null) return

    LoadAddressableNote(aTagHex = aTagHex, accountViewModel) {
        it?.let {
            PrepareViewModelsCommunityScreen(
                note = it,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun PrepareViewModelsCommunityScreen(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val followsFeedViewModel: NostrCommunityFeedViewModel =
        viewModel(
            key = note.idHex + "CommunityFeedViewModel",
            factory =
                NostrCommunityFeedViewModel.Factory(
                    note,
                    accountViewModel.account,
                ),
        )

    CommunityScreen(note, followsFeedViewModel, accountViewModel, nav)
}

@Composable
fun CommunityScreen(
    note: AddressableNote,
    feedViewModel: NostrCommunityFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val lifeCycleOwner = LocalLifecycleOwner.current

    NostrCommunityDataSource.loadCommunity(note)

    LaunchedEffect(note) { feedViewModel.invalidateData() }

    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    println("Community Start")
                    NostrCommunityDataSource.start()
                    feedViewModel.invalidateData()
                }
                if (event == Lifecycle.Event.ON_PAUSE) {
                    println("Community Stop")
                    NostrCommunityDataSource.loadCommunity(null)
                    NostrCommunityDataSource.stop()
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            CommunityTopBar(note.idHex, accountViewModel, nav)
        },
        floatingButton = {
            NewCommunityNoteButton(note.idHex, accountViewModel, nav)
        },
        accountViewModel = accountViewModel,
    ) {
        Column(
            modifier = Modifier.padding(it).consumeWindowInsets(it),
        ) {
            RefresheableFeedView(
                feedViewModel,
                null,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun CommunityTopBar(
    id: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(aTagHex = id, accountViewModel) { baseNote ->
        if (baseNote != null) {
            TopBarExtensibleWithBackButton(
                title = { ShortCommunityHeader(baseNote, accountViewModel, nav) },
                extendableRow = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        LongCommunityHeader(baseNote = baseNote, accountViewModel = accountViewModel, nav = nav)
                    }
                },
                popBack = nav::popBack,
            )
        } else {
            Spacer(BottomTopHeight)
        }
    }
}
