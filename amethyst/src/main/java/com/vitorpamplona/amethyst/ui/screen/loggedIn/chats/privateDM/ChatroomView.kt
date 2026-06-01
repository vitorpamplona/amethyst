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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.elements.ObserveRelayListForDMsAndDisplayIfNotFound
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.DmLoadMoreIndicator
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.dal.ChatroomFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.datasource.ChatroomFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.ChatNewMessageViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.PrivateMessageEditFieldRow
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Composable
fun ChatroomView(
    room: ChatroomKey,
    draftMessage: String?,
    replyToNote: HexKey? = null,
    editFromDraft: HexKey? = null,
    expiresDays: Int? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedViewModel: ChatroomFeedViewModel =
        viewModel(
            key = room.hashCode().toString() + "ChatroomViewModels",
            factory =
                ChatroomFeedViewModel.Factory(
                    room,
                    accountViewModel.account,
                ),
        )

    val newPostModel: ChatNewMessageViewModel = viewModel()
    newPostModel.init(accountViewModel)
    newPostModel.load(room)

    if (replyToNote != null) {
        LaunchedEffect(key1 = replyToNote, newPostModel, accountViewModel) {
            val replyNote = accountViewModel.checkGetOrCreateNote(replyToNote)
            if (replyNote != null) {
                newPostModel.reply(replyNote)
            }
        }
    }
    if (editFromDraft != null) {
        LaunchedEffect(editFromDraft, newPostModel, accountViewModel) {
            val draftNote = accountViewModel.checkGetOrCreateNote(editFromDraft)
            if (draftNote != null) {
                newPostModel.editFromDraft(draftNote)
            }
        }
    }
    if (expiresDays != null) {
        LaunchedEffect(expiresDays, newPostModel, accountViewModel) {
            newPostModel.loadExpiration(expiresDays)
        }
    }

    // Reactively check if recipients have DM relays for NIP-17 delivery
    for (userHex in room.users) {
        LoadAddressableNote(
            ChatMessageRelayListEvent.createAddress(userHex),
            accountViewModel,
        ) { note ->
            if (note != null) {
                EventFinderFilterAssemblerSubscription(note, accountViewModel)
            }
        }
    }

    if (draftMessage != null) {
        LaunchedEffect(key1 = draftMessage) {
            newPostModel.message.setTextAndPlaceCursorAtEnd(draftMessage)
            newPostModel.onMessageChanged()
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

// Rows from the oldest loaded message at which to prefetch the next, older window.
private const val PREFETCH_OLDER_MESSAGES = 3

/**
 * Scroll-driven history loader for a conversation. The thread is reverse-laid-out (newest at the
 * bottom, index 0), so older messages live at higher indices. It loads the next, older window only
 * when the thread already overflows the screen AND the user has scrolled near the oldest loaded
 * message — so a short thread is never auto-walked to the start of history (that would load the whole
 * account's gift-wrap history). For more than what scrolling reaches, the oldest-end boundary offers
 * an explicit "Load entire history".
 *
 * The account-wide gift-wrap window is the single source of truth for the floor: NIP-17 advances via
 * [AccountGiftWrapsEoseManager.loadMore], and the NIP-04 loader [ChatroomNip04SubAssembler.reload]
 * re-requests kind:4 from that same floor. The step is gated on BOTH loaders being idle, so it never
 * outruns the slower protocol.
 */
@Composable
private fun LoadOlderMessagesWhenScrolling(
    listState: LazyListState,
    accountViewModel: AccountViewModel,
) {
    val giftWraps = remember(accountViewModel) { accountViewModel.dataSources().account.giftWraps }
    val nip04 = remember(accountViewModel) { accountViewModel.dataSources().chatroom.nip04 }

    LaunchedEffect(listState, giftWraps, nip04) {
        combine(
            snapshotFlow {
                val info = listState.layoutInfo
                val total = info.totalItemsCount
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                val overflowsScreen = info.visibleItemsInfo.size < total
                overflowsScreen && lastVisible >= total - PREFETCH_OLDER_MESSAGES
            },
            giftWraps.loadingMore,
            nip04.loadingMore,
            giftWraps.exhausted,
        ) { wantMore, loadingGiftWraps, loadingNip04, exhausted ->
            wantMore && !loadingGiftWraps && !loadingNip04 && !exhausted
        }.distinctUntilChanged()
            .filter { it }
            .collect {
                Log.d("DMPagination") { "convo: widen (scrolled near oldest) → loadMore + reload" }
                giftWraps.loadMore(accountViewModel.userProfile())
                nip04.reload()
            }
    }
}

@Composable
fun ChatroomViewUI(
    room: ChatroomKey,
    feedViewModel: ChatroomFeedViewModel,
    newPostModel: ChatNewMessageViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedViewModel)
    ChatroomFilterAssemblerSubscription(room, accountViewModel.dataSources().chatroom, accountViewModel)

    DisposableEffect(room) {
        Log.d("DMPagination") { "convo: OPEN room=${room.hashCode()}" }
        onDispose { Log.d("DMPagination") { "convo: CLOSE room=${room.hashCode()}" } }
    }

    val giftWraps = remember(accountViewModel) { accountViewModel.dataSources().account.giftWraps }
    val nip04 = remember(accountViewModel) { accountViewModel.dataSources().chatroom.nip04 }
    val loadingGiftWraps by giftWraps.loadingMore.collectAsStateWithLifecycle()
    val loadingNip04 by nip04.loadingMore.collectAsStateWithLifecycle()
    val historyExhausted by giftWraps.exhausted.collectAsStateWithLifecycle()

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
                feedContentState = feedViewModel.feedState,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = "Room/${room.hashCode()}",
                avoidDraft = newPostModel.draftTag,
                onWantsToReply = newPostModel::reply,
                onWantsToEditDraft = newPostModel::editFromDraft,
                // While there is older history to reach, show the same spinner / "load all" boundary
                // as the rooms list at the oldest end (spinner only while actually loading).
                olderBoundary =
                    if (historyExhausted) {
                        null
                    } else {
                        {
                            DmLoadMoreIndicator(
                                loadingMore = loadingGiftWraps || loadingNip04,
                                showLoadAll = true,
                            ) {
                                Log.d("DMPagination") { "convo: Load entire history tapped" }
                                val user = accountViewModel.userProfile()
                                giftWraps.loadEverything(user)
                                nip04.reload()
                            }
                        }
                    },
                listStateObserver = { listState ->
                    LoadOlderMessagesWhenScrolling(listState, accountViewModel)
                },
            )
        }

        Spacer(modifier = DoubleVertSpacer)

        val scope = rememberCoroutineScope()

        // LAST ROW
        PrivateMessageEditFieldRow(
            newPostModel,
            accountViewModel,
            onSendNewMessage = {
                scope.launch {
                    feedViewModel.feedState.sendToTop()
                }
            },
            nav,
        )
    }
}
