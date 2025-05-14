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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.NonClickableUserPictures
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Size34dp
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.ZeroPadding
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import kotlinx.collections.immutable.toPersistentList

@Composable
fun RenderRoomTopBar(
    room: ChatroomKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (room.users.size == 1) {
        TopBarExtensibleWithBackButton(
            title = {
                LoadUser(baseUserHex = room.users.first(), accountViewModel) { baseUser ->
                    if (baseUser != null) {
                        ClickableUserPicture(
                            baseUser = baseUser,
                            accountViewModel = accountViewModel,
                            size = Size34dp,
                        )

                        Spacer(modifier = DoubleHorzSpacer)

                        UsernameDisplay(baseUser, Modifier.weight(1f), fontWeight = FontWeight.Normal, accountViewModel = accountViewModel)
                    }
                }
            },
            extendableRow = {
                LoadUser(baseUserHex = room.users.first(), accountViewModel) {
                    if (it != null) {
                        UserCompose(
                            baseUser = it,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            },
            popBack = nav::popBack,
        )
    } else {
        TopBarExtensibleWithBackButton(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NonClickableUserPictures(
                        room = room,
                        accountViewModel = accountViewModel,
                        size = Size34dp,
                    )

                    RoomNameOnlyDisplay(room, Modifier.padding(start = 10.dp).weight(1f), FontWeight.Normal, accountViewModel)
                }
            },
            extendableRow = {
                GroupMembersHeader(room = room, accountViewModel = accountViewModel, nav = nav)
            },
            popBack = nav::popBack,
        )
    }
}

@Composable
fun GroupMembersHeader(
    room: ChatroomKey,
    lineModifier: Modifier = StdPadding,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val list = remember(room) { room.users.toPersistentList() }

    Row(
        modifier = Modifier.fillMaxWidth().padding(5.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(id = R.string.messages_group_descriptor),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )

        EditRoomSubjectButton(room, accountViewModel)
    }

    LazyColumn(
        modifier = Modifier,
        state = rememberLazyListState(),
    ) {
        itemsIndexed(list, key = { _, item -> item }) { _, item ->
            LoadUser(baseUserHex = item, accountViewModel) {
                if (it != null) {
                    UserCompose(
                        baseUser = it,
                        overallModifier = lineModifier,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                    HorizontalDivider(
                        thickness = DividerThickness,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditRoomSubjectButton(
    room: ChatroomKey,
    accountViewModel: AccountViewModel,
) {
    var wantsToPost by remember { mutableStateOf(false) }

    if (wantsToPost) {
        NewChatroomSubjectDialog({ wantsToPost = false }, accountViewModel, room)
    }

    FilledTonalButton(
        modifier =
            Modifier
                .padding(horizontal = 3.dp)
                .width(50.dp),
        onClick = { wantsToPost = true },
        contentPadding = ZeroPadding,
    ) {
        Icon(
            imageVector = Icons.Default.EditNote,
            contentDescription = stringRes(R.string.edits_the_channel_metadata),
        )
    }
}
