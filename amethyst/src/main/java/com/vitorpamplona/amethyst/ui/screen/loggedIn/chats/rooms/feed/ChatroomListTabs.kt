/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size40dp
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.coroutines.launch

@Immutable
class MessagesTabItem(
    val resource: Int,
    val scrollStateKey: String,
    val feedContentState: FeedContentState,
)

@Composable
fun MessagesTabHeader(
    pagerState: PagerState,
    tabs: List<MessagesTabItem>,
    onMarkKnownAsRead: () -> Unit,
    onMarkNewAsRead: () -> Unit,
) {
    var moreActionsExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Box(Modifier.fillMaxWidth()) {
        TabRow(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            selectedTabIndex = pagerState.currentPage,
            modifier = TabRowHeight,
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    text = { Text(text = stringRes(tab.resource)) },
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                )
            }
        }

        IconButton(
            modifier =
                Modifier
                    .size(Size40dp)
                    .align(Alignment.CenterEnd),
            onClick = { moreActionsExpanded = true },
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringRes(id = R.string.more_options),
                tint = MaterialTheme.colorScheme.placeholderText,
            )

            MessagesTabMenu(
                moreActionsExpanded,
                { moreActionsExpanded = false },
                onMarkKnownAsRead,
                onMarkNewAsRead,
            )
        }
    }
}

@Composable
fun MessagesPager(
    pagerState: PagerState,
    tabs: List<MessagesTabItem>,
    paddingValues: PaddingValues,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    HorizontalPager(
        contentPadding = paddingValues,
        state = pagerState,
        userScrollEnabled = false,
    ) { page ->
        ChatroomListFeedView(
            feedContentState = tabs[page].feedContentState,
            scrollStateKey = tabs[page].scrollStateKey,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun MessagesTabMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onMarkKnownAsRead: () -> Unit,
    onMarkNewAsRead: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringRes(R.string.mark_all_known_as_read)) },
            onClick = {
                onMarkKnownAsRead()
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text(stringRes(R.string.mark_all_new_as_read)) },
            onClick = {
                onMarkNewAsRead()
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text(stringRes(R.string.mark_all_as_read)) },
            onClick = {
                onMarkKnownAsRead()
                onMarkNewAsRead()
                onDismiss()
            },
        )
    }
}
