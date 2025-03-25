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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.header

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.model.PublicChatChannel
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.LikeReaction
import com.vitorpamplona.amethyst.ui.note.ZapReaction
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.header.actions.EditButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.header.actions.JoinChatButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.header.actions.LeaveChatButton
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.quartz.nip01Core.tags.events.isTaggedEvent

@Composable
fun ShortChannelActionOptions(
    channel: PublicChatChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadNote(baseNoteHex = channel.idHex, accountViewModel) {
        it?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = RowColSpacing) {
                LikeReaction(
                    baseNote = it,
                    grayTint = MaterialTheme.colorScheme.onSurface,
                    accountViewModel = accountViewModel,
                    nav,
                )
                ZapReaction(
                    baseNote = it,
                    grayTint = MaterialTheme.colorScheme.onSurface,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
                Spacer(modifier = StdHorzSpacer)
            }
        }
    }

    WatchChannelFollows(channel, accountViewModel) { isFollowing ->
        if (!isFollowing) {
            JoinChatButton(channel, accountViewModel, nav)
        }
    }
}

@Composable
fun LongChannelActionOptions(
    channel: PublicChatChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val isMe by
        remember(accountViewModel) {
            derivedStateOf { channel.creator == accountViewModel.account.userProfile() }
        }

    if (isMe) {
        EditButton(channel, accountViewModel, nav)
    }

    WatchChannelFollows(channel, accountViewModel) { isFollowing ->
        if (isFollowing) {
            LeaveChatButton(channel, accountViewModel, nav)
        }
    }
}

@Composable
private fun WatchChannelFollows(
    channel: PublicChatChannel,
    accountViewModel: AccountViewModel,
    content: @Composable (Boolean) -> Unit,
) {
    val isFollowing by
        accountViewModel
            .userProfile()
            .live()
            .follows
            .map { it.user.latestContactList?.isTaggedEvent(channel.idHex) ?: false }
            .distinctUntilChanged()
            .observeAsState(
                accountViewModel.userProfile().latestContactList?.isTaggedEvent(channel.idHex) ?: false,
            )

    content(isFollowing)
}
