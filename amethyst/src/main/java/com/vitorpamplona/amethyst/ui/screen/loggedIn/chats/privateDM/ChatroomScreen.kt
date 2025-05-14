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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.header.RenderRoomTopBar
import com.vitorpamplona.amethyst.ui.theme.BottomTopHeight
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ChatroomScreen(
    roomId: String?,
    draftMessage: String? = null,
    replyToNote: HexKey? = null,
    editFromDraft: HexKey? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (roomId == null) return

    DisappearingScaffold(
        isInvertedLayout = true,
        topBar = {
            RoomTopBar(roomId, accountViewModel, nav)
        },
        accountViewModel = accountViewModel,
    ) {
        Column(Modifier.padding(it)) {
            Chatroom(roomId, draftMessage, replyToNote, editFromDraft, accountViewModel, nav)
        }
    }
}

@Composable
private fun RoomTopBar(
    id: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadRoom(roomId = id, accountViewModel) { room ->
        if (room != null) {
            RenderRoomTopBar(room, accountViewModel, nav)
        } else {
            Spacer(BottomTopHeight)
        }
    }
}

@Composable
fun Chatroom(
    roomId: String?,
    draftMessage: String? = null,
    replyToNote: HexKey? = null,
    editFromDraft: HexKey? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (roomId == null) return

    LoadRoom(roomId, accountViewModel) {
        it?.let {
            ChatroomView(
                room = it,
                draftMessage = draftMessage,
                replyToNote = replyToNote,
                editFromDraft = editFromDraft,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun LoadRoom(
    roomId: String,
    accountViewModel: AccountViewModel,
    content: @Composable (ChatroomKey?) -> Unit,
) {
    var room by remember(roomId) { mutableStateOf<ChatroomKey?>(null) }

    if (room == null) {
        LaunchedEffect(key1 = roomId) {
            launch(Dispatchers.IO) {
                val newRoom =
                    accountViewModel.userProfile().privateChatrooms.keys.firstOrNull {
                        it.hashCode().toString() == roomId
                    }
                if (room != newRoom) {
                    room = newRoom
                }
            }
        }
    }

    content(room)
}
