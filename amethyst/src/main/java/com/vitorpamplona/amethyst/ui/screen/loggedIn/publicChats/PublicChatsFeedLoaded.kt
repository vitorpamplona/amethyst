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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.publicChats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.ChannelCardCompose
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent

@Composable
fun PublicChatsFeedLoaded(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()
    val followedSet by accountViewModel.account.publicChatList.flowSet
        .collectAsStateWithLifecycle()

    val pinned =
        remember(followedSet) {
            followedSet.mapNotNull { idHex ->
                LocalCache.getNoteIfExists(idHex)?.takeIf { it.event is ChannelCreateEvent }
            }
        }

    val unpinned =
        remember(items.list, followedSet) {
            items.list.filter { it.idHex !in followedSet }
        }

    LazyColumn(
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        items(
            pinned,
            key = { item -> "pinned-" + item.idHex },
            contentType = { item -> item.event?.kind ?: -1 },
        ) { item ->
            PublicChatRow(item, pinned = true, accountViewModel, nav)
        }

        itemsIndexed(
            unpinned,
            key = { _, item -> item.idHex },
            contentType = { _, item -> item.event?.kind ?: -1 },
        ) { _, item ->
            PublicChatRow(item, pinned = false, accountViewModel, nav)
        }
    }
}

@Composable
private fun LazyItemScope.PublicChatRow(
    item: Note,
    pinned: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Box(Modifier.fillMaxWidth().animateItem()) {
        ChannelCardCompose(
            baseNote = item,
            routeForLastRead = "PublicChatsFeed",
            modifier = Modifier.fillMaxWidth(),
            forceEventKind = ChannelCreateEvent.KIND,
            accountViewModel = accountViewModel,
            nav = nav,
        )
        if (pinned) {
            Icon(
                symbol = MaterialSymbols.PushPin,
                contentDescription = null,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(16.dp),
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }
    }

    HorizontalDivider(
        thickness = DividerThickness,
    )
}
