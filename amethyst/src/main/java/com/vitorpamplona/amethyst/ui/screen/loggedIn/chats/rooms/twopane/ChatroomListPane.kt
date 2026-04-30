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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.twopane

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.LocalDisappearingScaffoldPadding
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.datasource.ChatroomListFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.feed.MessagesPager
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.feed.MessagesTabHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.feed.MessagesTabItem
import kotlinx.collections.immutable.persistentListOf

@Composable
fun ChatroomList(
    knownFeedContentState: FeedContentState,
    newFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pagerState = rememberPagerState { 2 }

    WatchLifecycleAndUpdateModel(knownFeedContentState)
    WatchLifecycleAndUpdateModel(newFeedContentState)

    ChatroomListFilterAssemblerSubscription(accountViewModel)

    val tabs by
        remember(knownFeedContentState) {
            derivedStateOf {
                persistentListOf(
                    MessagesTabItem(R.string.known, ScrollStateKeys.MESSAGES_KNOWN, knownFeedContentState),
                    MessagesTabItem(R.string.new_requests, ScrollStateKeys.MESSAGES_NEW, newFeedContentState),
                )
            }
        }

    // The outer DisappearingScaffold publishes its top-app-bar height through
    // LocalDisappearingScaffoldPadding so single-pane feeds can clear the bar via
    // rememberFeedContentPadding. In the two-pane layout the tabs sit inside the
    // column (not the scaffold's top slot), so the inner LazyColumn must not add
    // that top inset again — otherwise it shows up as a gap below the TabRow.
    // Drop only the top component; keep start/end/bottom so feeds still clear
    // the bottom bar.
    val outerScaffoldPadding = LocalDisappearingScaffoldPadding.current
    val layoutDirection = LocalLayoutDirection.current
    val innerScaffoldPadding =
        remember(outerScaffoldPadding, layoutDirection) {
            PaddingValues(
                start = outerScaffoldPadding.calculateStartPadding(layoutDirection),
                top = 0.dp,
                end = outerScaffoldPadding.calculateEndPadding(layoutDirection),
                bottom = outerScaffoldPadding.calculateBottomPadding(),
            )
        }

    Column {
        MessagesTabHeader(
            pagerState,
            tabs,
            { accountViewModel.markAllChatNotesAsRead(knownFeedContentState.visibleNotes()) },
            { accountViewModel.markAllChatNotesAsRead(newFeedContentState.visibleNotes()) },
        )

        CompositionLocalProvider(LocalDisappearingScaffoldPadding provides innerScaffoldPadding) {
            MessagesPager(
                pagerState,
                tabs,
                accountViewModel,
                nav,
            )
        }
    }
}
