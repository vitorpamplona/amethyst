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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.ui.actions.relays.AllRelayListView
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.RelayCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding

@Composable
fun RelayFeedView(
    viewModel: RelayFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    enablePullRefresh: Boolean = true,
) {
    val feedState by viewModel.feedContent.collectAsStateWithLifecycle()

    var wantsToAddRelay by remember { mutableStateOf("") }

    if (wantsToAddRelay.isNotEmpty()) {
        AllRelayListView({ wantsToAddRelay = "" }, wantsToAddRelay, accountViewModel, nav = nav)
    }

    RefresheableBox(viewModel, enablePullRefresh) {
        val listState = rememberLazyListState()

        LazyColumn(
            contentPadding = FeedPadding,
            state = listState,
        ) {
            itemsIndexed(feedState, key = { _, item -> item.url }) { _, item ->
                RelayCompose(
                    item,
                    accountViewModel = accountViewModel,
                    onAddRelay = { wantsToAddRelay = item.url },
                    onRemoveRelay = { wantsToAddRelay = item.url },
                )
                HorizontalDivider(
                    thickness = DividerThickness,
                )
            }
        }
    }
}
