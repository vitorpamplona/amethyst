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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.service.NostrChatroomDataSource
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.elements.ObserveRelayListForDMs
import com.vitorpamplona.amethyst.ui.note.elements.ObserveRelayListForDMsAndDisplayIfNotFound
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.ChatNewMessageViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.PrivateMessageEditFieldRow
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import kotlinx.coroutines.launch

@Composable
fun ChatroomView(
    room: ChatroomKey,
    draftMessage: String?,
    replyToNote: HexKey? = null,
    editFromDraft: HexKey? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedViewModel: NostrChatroomFeedViewModel =
        viewModel(
            key = room.hashCode().toString() + "ChatroomViewModels",
            factory =
                NostrChatroomFeedViewModel.Factory(
                    room,
                    accountViewModel.account,
                ),
        )

    val newPostModel: ChatNewMessageViewModel = viewModel()
    newPostModel.init(accountViewModel)
    newPostModel.load(room)

    if (replyToNote != null) {
        LaunchedEffect(key1 = replyToNote) {
            accountViewModel.checkGetOrCreateNote(replyToNote) {
                if (it != null) {
                    newPostModel.reply(it)
                }
            }
        }
    }
    if (editFromDraft != null) {
        LaunchedEffect(key1 = replyToNote) {
            accountViewModel.checkGetOrCreateNote(editFromDraft) {
                if (it != null) {
                    newPostModel.editFromDraft(it)
                }
            }
        }
    }

    if (room.users.size == 1) {
        // Activates NIP-17 if the user has DM relays
        ObserveRelayListForDMs(pubkey = room.users.first(), accountViewModel = accountViewModel) {
            if (it?.relays().isNullOrEmpty()) {
                newPostModel.nip17 = false
            } else {
                newPostModel.nip17 = true
            }
        }
    }

    if (draftMessage != null) {
        LaunchedEffect(key1 = draftMessage) {
            newPostModel.updateMessage(TextFieldValue(draftMessage))
        }
    }

    ChatroomViewUI(
        room = room,
        feedViewModel = feedViewModel,
        newPostModel = newPostModel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun ChatroomViewUI(
    room: ChatroomKey,
    feedViewModel: NostrChatroomFeedViewModel,
    newPostModel: ChatNewMessageViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NostrChatroomDataSource.loadMessagesBetween(accountViewModel.account, room)

    val lifeCycleOwner = LocalLifecycleOwner.current

    DisposableEffect(room, accountViewModel) {
        NostrChatroomDataSource.loadMessagesBetween(accountViewModel.account, room)
        NostrChatroomDataSource.start()
        feedViewModel.invalidateData()

        onDispose { NostrChatroomDataSource.stop() }
    }

    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    println("Private Message Start")
                    NostrChatroomDataSource.start()
                    feedViewModel.invalidateData()
                }
                if (event == Lifecycle.Event.ON_PAUSE) {
                    println("Private Message Stop")
                    NostrChatroomDataSource.stop()
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxHeight()) {
        ObserveRelayListForDMsAndDisplayIfNotFound(accountViewModel, nav)

        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(vertical = 0.dp)
                    .weight(1f, true),
        ) {
            RefreshingChatroomFeedView(
                viewModel = feedViewModel,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = "Room/${room.hashCode()}",
                avoidDraft = newPostModel.draftTag,
                onWantsToReply = newPostModel::reply,
                onWantsToEditDraft = newPostModel::editFromDraft,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        val scope = rememberCoroutineScope()

        // LAST ROW
        PrivateMessageEditFieldRow(
            newPostModel,
            accountViewModel,
            onSendNewMessage = {
                scope.launch {
                    feedViewModel.sendToTop()
                }
            },
            nav,
        )
    }
}
