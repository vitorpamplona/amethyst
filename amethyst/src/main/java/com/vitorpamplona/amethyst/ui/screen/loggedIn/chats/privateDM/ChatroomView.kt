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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.dal.ChatroomFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.datasource.ChatroomFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.ChatNewMessageViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.PrivateMessageEditFieldRow
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
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

// NIP-17 senders may backdate the gift wrap's OUTER created_at up to two days (randomWithTwoDays),
// and relays filter on that outer time. So once we've fetched outer >= F, we're only guaranteed to
// hold every message whose real (inner) time is >= F + 2d. The revealed floor carries this margin.
private const val GIFT_WRAP_OUTER_JITTER_SECONDS = 2L * 24 * 60 * 60

/**
 * Scroll-driven history loader for a conversation, advancing BOTH DM protocols together. The thread
 * is reverse-laid-out (newest at the bottom, index 0), so older messages live at higher indices;
 * this widens one step whenever nothing is loaded yet or the oldest visible row crosses the midpoint
 * of what's loaded — prefetching before the user reaches the top — and stops once exhausted.
 *
 * The account-wide gift-wrap window is the single source of truth for the floor: NIP-17 advances via
 * [AccountGiftWrapsEoseManager.loadMore], and the NIP-04 loader [ChatroomFilterSubAssembler.reload]
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
                total == 0 || lastVisible >= total / 2
            },
            giftWraps.loadingMore,
            nip04.loadingMore,
            giftWraps.exhausted,
        ) { wantMore, loadingGiftWraps, loadingNip04, exhausted ->
            wantMore && !loadingGiftWraps && !loadingNip04 && !exhausted
        }.distinctUntilChanged()
            .filter { it }
            .collect {
                giftWraps.loadMore(accountViewModel.userProfile())
                nip04.reload()
            }
    }
}

/**
 * The epoch-second floor at or above which the thread is safe to reveal: the deepest gift-wrap floor
 * at which BOTH protocols have finished loading, plus the [GIFT_WRAP_OUTER_JITTER_SECONDS] margin.
 *
 * It only descends (monotonic), so revealed history never retracts. Until the first completion it is
 * [Long.MAX_VALUE] (reveal nothing yet); once the window is exhausted it is [Long.MIN_VALUE] (reveal
 * everything). Holding back a depth until both NIP-04 and NIP-17 have covered it is what prevents a
 * fast NIP-04 stream from painting a thread that's missing the NIP-17 messages in between.
 */
@Composable
private fun rememberConversationDisplayFloor(accountViewModel: AccountViewModel): Long {
    val giftWraps = remember(accountViewModel) { accountViewModel.dataSources().account.giftWraps }
    val nip04 = remember(accountViewModel) { accountViewModel.dataSources().chatroom.nip04 }
    val loadingGiftWraps by giftWraps.loadingMore.collectAsStateWithLifecycle()
    val loadingNip04 by nip04.loadingMore.collectAsStateWithLifecycle()
    val exhausted by giftWraps.exhausted.collectAsStateWithLifecycle()

    var coveredSince by remember(accountViewModel) { mutableStateOf(Long.MAX_VALUE) }
    LaunchedEffect(loadingGiftWraps, loadingNip04, accountViewModel) {
        if (!loadingGiftWraps && !loadingNip04) {
            val since = giftWraps.windowSince(accountViewModel.userProfile())
            if (since < coveredSince) coveredSince = since
        }
    }

    return when {
        exhausted -> Long.MIN_VALUE
        coveredSince == Long.MAX_VALUE -> Long.MAX_VALUE
        else -> coveredSince + GIFT_WRAP_OUTER_JITTER_SECONDS
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

    // Only reveal a depth once BOTH DM protocols have fully loaded it (gap-free timeline); show a
    // "loading older" boundary while more history may still arrive.
    val displayFloor = rememberConversationDisplayFloor(accountViewModel)
    val loadingOlder = displayFloor != Long.MIN_VALUE

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
                oldestVisibleTime = displayFloor,
                loadingOlder = loadingOlder,
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
