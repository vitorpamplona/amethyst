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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.PublicChatChannel
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.ui.note.elements.MoreOptionsButton
import com.vitorpamplona.amethyst.ui.note.elements.NormalTimeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.quartz.nip02FollowList.EmptyTagList

@Composable
fun LongPublicChatChannelHeader(
    baseChannel: PublicChatChannel,
    lineModifier: Modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val channelState by baseChannel.live.observeAsState()
    val channel = channelState?.channel as? PublicChatChannel ?: return

    Row(lineModifier) {
        val summary = remember(channelState) { channel.summary()?.ifBlank { null } }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val defaultBackground = MaterialTheme.colorScheme.background
                val background = remember { mutableStateOf(defaultBackground) }

                TranslatableRichTextViewer(
                    content = summary ?: stringRes(id = R.string.groups_no_descriptor),
                    canPreview = false,
                    quotesLeft = 1,
                    tags = EmptyTagList,
                    backgroundColor = background,
                    id = baseChannel.idHex,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }

        Spacer(DoubleHorzSpacer)
        LongChannelActionOptions(channel, accountViewModel, nav)
    }

    LoadNote(baseNoteHex = channel.idHex, accountViewModel) { loadingNote ->
        loadingNote?.let { note ->
            Row(
                lineModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(id = R.string.owner),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(75.dp),
                )
                Spacer(DoubleHorzSpacer)
                NoteAuthorPicture(note, nav, accountViewModel, Size25dp)
                Spacer(DoubleHorzSpacer)
                NoteUsernameDisplay(note, Modifier.weight(1f), accountViewModel = accountViewModel)
            }

            Row(
                lineModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(id = R.string.created_at),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(75.dp),
                )
                Spacer(DoubleHorzSpacer)
                NormalTimeAgo(note, remember { Modifier.weight(1f) })
                MoreOptionsButton(note, null, accountViewModel, nav)
            }
        }
    }
}
