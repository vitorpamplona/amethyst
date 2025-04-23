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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.header

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserShortName
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import kotlin.math.min

@Composable
fun RoomNameOnlyDisplay(
    room: ChatroomKey,
    modifier: Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
    accountViewModel: AccountViewModel,
) {
    val roomSubject by
        accountViewModel
            .userProfile()
            .live()
            .messages
            .map { it.user.privateChatrooms[room]?.subject }
            .distinctUntilChanged()
            .observeAsState(accountViewModel.userProfile().privateChatrooms[room]?.subject)

    CrossfadeIfEnabled(targetState = roomSubject, modifier, accountViewModel = accountViewModel) {
        if (!it.isNullOrBlank()) {
            DisplayRoomSubject(it, fontWeight)
        } else {
            DisplayUserSetAsSubject(room, accountViewModel, FontWeight.Normal)
        }
    }
}

@Composable
fun DisplayUserSetAsSubject(
    room: ChatroomKey,
    accountViewModel: AccountViewModel,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    val userList = remember(room) { room.users.toList() }

    if (userList.size == 1) {
        // Regular Design
        Row {
            LoadUser(baseUserHex = userList[0], accountViewModel) {
                it?.let { UsernameDisplay(it, Modifier.weight(1f), fontWeight = fontWeight, accountViewModel = accountViewModel) }
            }
        }
    } else {
        Row {
            userList.take(4).forEachIndexed { index, value ->
                LoadUser(baseUserHex = value, accountViewModel) {
                    it?.let { ShortUsernameDisplay(baseUser = it, fontWeight = fontWeight, accountViewModel = accountViewModel) }
                }

                if (min(userList.size, 4) - 1 != index) {
                    Text(
                        text = ", ",
                        fontWeight = fontWeight,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun RoomNameDisplay(
    room: ChatroomKey,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    val roomSubject by
        accountViewModel
            .userProfile()
            .live()
            .messages
            .map { it.user.privateChatrooms[room]?.subject }
            .distinctUntilChanged()
            .observeAsState(accountViewModel.userProfile().privateChatrooms[room]?.subject)

    CrossfadeIfEnabled(targetState = roomSubject, modifier, label = "RoomNameDisplay", accountViewModel = accountViewModel) {
        if (!it.isNullOrBlank()) {
            if (room.users.size > 1) {
                DisplayRoomSubject(it)
            } else {
                DisplayUserAndSubject(room.users.first(), it, accountViewModel)
            }
        } else {
            DisplayUserSetAsSubject(room, accountViewModel)
        }
    }
}

@Composable
fun DisplayRoomSubject(
    roomSubject: String,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    Row {
        Text(
            text = roomSubject,
            fontWeight = fontWeight,
            maxLines = 1,
        )
    }
}

@Composable
private fun DisplayUserAndSubject(
    user: HexKey,
    subject: String,
    accountViewModel: AccountViewModel,
) {
    Row {
        Text(
            text = subject,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = " - ",
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        LoadUser(baseUserHex = user, accountViewModel = accountViewModel) {
            it?.let { UsernameDisplay(it, Modifier.weight(1f), accountViewModel = accountViewModel) }
        }
    }
}

@Composable
fun ShortUsernameDisplay(
    baseUser: User,
    weight: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
    accountViewModel: AccountViewModel,
) {
    val userName by observeUserShortName(baseUser)

    CrossfadeIfEnabled(targetState = userName, modifier = weight, accountViewModel = accountViewModel) {
        CreateTextWithEmoji(
            text = it,
            tags = baseUser.info?.tags,
            fontWeight = fontWeight,
            maxLines = 1,
        )
    }
}
