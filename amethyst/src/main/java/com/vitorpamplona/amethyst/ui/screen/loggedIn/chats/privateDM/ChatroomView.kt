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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.actions.uploads.resolveSharedMedia
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.elements.ObserveRelayListForDMsAndDisplayIfNotFound
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.DmHistoryLoadingCard
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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Composable
fun ChatroomView(
    room: ChatroomKey,
    draftMessage: String?,
    attachmentUri: String? = null,
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
    val context = LocalContext.current
    if (attachmentUri != null) {
        LaunchedEffect(key1 = attachmentUri) {
            resolveSharedMedia(context, attachmentUri)?.let {
                newPostModel.pickedMedia(persistentListOf(it))
            }
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
 * bottom, index 0), so older messages (and the load-more boundary) live at the highest indices. It
 * loads the next, older page whenever the oldest end is in view — including a thread too short to
 * scroll, so sitting at the start of a one-message chat keeps walking history back to its real
 * beginning (or until both protocols are exhausted). Each step is a bounded `until`+`limit` page that
 * never re-downloads, so walking a short thread is cheap per step — gift wraps can't be filtered per
 * room, so this advances the shared account-wide history window and the conversation's messages
 * surface as its pages are decrypted.
 *
 * Both protocols advance via their history managers' `loadMore`; the step is gated on BOTH loaders
 * being idle, so it never outruns the slower one, and stops once both report exhausted.
 */
@Composable
private fun LoadOlderMessagesWhenScrolling(
    listState: LazyListState,
    accountViewModel: AccountViewModel,
) {
    val giftWrapsHistory = remember(accountViewModel) { accountViewModel.dataSources().account.giftWrapsHistory }
    val nip04History = remember(accountViewModel) { accountViewModel.dataSources().chatroom.nip04History }

    LaunchedEffect(listState, giftWrapsHistory, nip04History) {
        combine(
            snapshotFlow {
                val info = listState.layoutInfo
                val total = info.totalItemsCount
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                // The oldest end is in view (no overflow requirement, so a one-message thread that
                // can't scroll still qualifies and walks history to its start).
                total > 0 && lastVisible >= total - PREFETCH_OLDER_MESSAGES
            },
            giftWrapsHistory.loadingMore,
            nip04History.loadingMore,
            giftWrapsHistory.exhausted,
            nip04History.exhausted,
        ) { wantMore, loadingGiftWraps, loadingNip04, giftWrapsExhausted, nip04Exhausted ->
            // Keep paging while either protocol still has older history to reach.
            wantMore && !loadingGiftWraps && !loadingNip04 && !(giftWrapsExhausted && nip04Exhausted)
        }.distinctUntilChanged()
            .filter { it }
            .collect {
                Log.d("DMPagination") { "convo: widen (oldest in view) → loadMore" }
                giftWrapsHistory.loadMore(accountViewModel.userProfile())
                nip04History.loadMore()
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

    val giftWrapsHistory = remember(accountViewModel) { accountViewModel.dataSources().account.giftWrapsHistory }
    val nip04History = remember(accountViewModel) { accountViewModel.dataSources().chatroom.nip04History }
    val loadingGiftWraps by giftWrapsHistory.loadingMore.collectAsStateWithLifecycle()
    val loadingNip04 by nip04History.loadingMore.collectAsStateWithLifecycle()
    val giftWrapsExhausted by giftWrapsHistory.exhausted.collectAsStateWithLifecycle()
    val nip04Exhausted by nip04History.exhausted.collectAsStateWithLifecycle()
    val giftWrapsRelays by giftWrapsHistory.relayCount.collectAsStateWithLifecycle()
    val giftWrapsReached by giftWrapsHistory.reachedBack.collectAsStateWithLifecycle()
    val nip04Relays by nip04History.relayCount.collectAsStateWithLifecycle()
    val nip04Reached by nip04History.reachedBack.collectAsStateWithLifecycle()
    val nip17Name = stringResource(R.string.chats_history_proto_nip17)
    val nip04Name = stringResource(R.string.chats_history_proto_nip04)

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
                // One status card per protocol at the oldest end: each shows what it's reaching for
                // while it pages and crossfades to "All caught up" when that protocol runs dry.
                olderBoundary = {
                    Column {
                        DmHistoryLoadingCard(nip17Name, "NIP-17", loadingGiftWraps, giftWrapsExhausted, giftWrapsRelays, giftWrapsReached)
                        DmHistoryLoadingCard(nip04Name, "NIP-04", loadingNip04, nip04Exhausted, nip04Relays, nip04Reached)
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
