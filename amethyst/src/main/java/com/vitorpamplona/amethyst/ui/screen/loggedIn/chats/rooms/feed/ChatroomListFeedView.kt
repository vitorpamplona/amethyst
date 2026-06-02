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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.feed

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.ui.feeds.FeedError
import com.vitorpamplona.amethyst.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.DmLoadMoreIndicator
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.ChatroomHeaderCompose
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.Serializable

@Composable
fun ChatroomListFeedView(
    feedContentState: FeedContentState,
    scrollStateKey: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    DisposableEffect(Unit) {
        Log.d("DMPagination") { "rooms.list: OPEN" }
        onDispose { Log.d("DMPagination") { "rooms.list: CLOSE" } }
    }
    RefresheableBox(feedContentState, true) {
        SaveableFeedContentState(feedContentState, scrollStateKey) { listState ->
            CrossFadeState(feedContentState, listState, accountViewModel, nav)
        }
    }
}

@Composable
private fun CrossFadeState(
    feedContentState: FeedContentState,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedState by feedContentState.feedContent.collectAsStateWithLifecycle()

    // History is exhausted only once BOTH DM protocols have paged to their end (each stops when a
    // round of until+limit pages brings nothing back). Until then an empty feed means "still filling",
    // not "no conversations" — keep the spinner up rather than flash empty.
    val giftWrapsHistory = remember(accountViewModel) { accountViewModel.dataSources().account.giftWrapsHistory }
    val nip04History = remember(accountViewModel) { accountViewModel.dataSources().chatroomList.nip04History }
    val giftWrapsExhausted by giftWrapsHistory.exhausted.collectAsStateWithLifecycle()
    val nip04Exhausted by nip04History.exhausted.collectAsStateWithLifecycle()
    val historyExhausted = giftWrapsExhausted && nip04Exhausted

    // While the whole list is empty there is no LazyColumn to scroll, so keep widening the private
    // DM window here until rooms appear or it is exhausted. (Public / ephemeral / group rooms are
    // membership-based and load on their own — they are not part of the window.)
    WidenPrivateWindowWhen(accountViewModel, "empty") { feedState is FeedState.Empty }

    CrossfadeIfEnabled(
        targetState = feedState,
        animationSpec = tween(durationMillis = 100),
        accountViewModel = accountViewModel,
    ) { state ->
        when (state) {
            is FeedState.Empty -> {
                if (historyExhausted) {
                    FeedEmpty { feedContentState.invalidateData() }
                } else {
                    LoadingFeed()
                }
            }

            is FeedState.FeedError -> {
                FeedError(state.errorMessage) { feedContentState.invalidateData() }
            }

            is FeedState.Loaded -> {
                FeedLoaded(state, listState, accountViewModel, nav)
            }

            FeedState.Loading -> {
                LoadingFeed()
            }
        }
    }
}

@Composable
private fun FeedLoaded(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    val myPubKey = accountViewModel.userProfile().pubkeyHex

    val giftWrapsHistory = remember(accountViewModel) { accountViewModel.dataSources().account.giftWrapsHistory }
    val nip04History = remember(accountViewModel) { accountViewModel.dataSources().chatroomList.nip04History }
    val loadingGiftWraps by giftWrapsHistory.loadingMore.collectAsStateWithLifecycle()
    val loadingNip04 by nip04History.loadingMore.collectAsStateWithLifecycle()
    val loadingMore = loadingGiftWraps || loadingNip04
    val giftWrapsExhausted by giftWrapsHistory.exhausted.collectAsStateWithLifecycle()
    val nip04Exhausted by nip04History.exhausted.collectAsStateWithLifecycle()
    val historyExhausted = giftWrapsExhausted && nip04Exhausted

    // Widen the private DM window only as the user approaches the oldest LOADED private chat —
    // ignoring public / group / ephemeral rooms below it. Those are membership-based and can be
    // arbitrarily old; counting them would either stall private paging or drag the window back
    // years. [privateRoomCount] feeds the stall-gate: widening keeps pulling older MESSAGES, which for
    // a few busy correspondents floods events without adding a row — so paging stops once a step
    // brings in no new private room. The lambda reads the live items + scroll state in the snapshotFlow.
    WidenPrivateWindowWhen(
        accountViewModel,
        "scroll",
        privateRoomCount = { items.list.count { it.event is ChatroomKeyable } },
    ) {
        val info = listState.layoutInfo
        val total = info.totalItemsCount
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        val oldestPrivate = items.list.indexOfLast { it.event is ChatroomKeyable }
        total > 0 && (oldestPrivate < 0 || lastVisible >= oldestPrivate - PREFETCH_PRIVATE_CHATS)
    }

    // The private-DM loading boundary sits right after the last loaded private chat: that's where
    // older private history streams in, while public / group rooms below are shown regardless.
    val privateBoundaryIndex = items.list.indexOfLast { it.event is ChatroomKeyable }

    LazyColumn(
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        itemsIndexed(
            items.list,
            key = { _, item -> chatroomLazyKey(item, myPubKey) },
        ) { index, item ->
            Row(Modifier.fillMaxWidth()) {
                ChatroomHeaderCompose(
                    item,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            HorizontalDivider(
                thickness = DividerThickness,
            )

            if (index == privateBoundaryIndex && (loadingMore || !historyExhausted)) {
                DmLoadMoreIndicator(loadingMore, showLoadAll = !historyExhausted) {
                    val user = accountViewModel.userProfile()
                    giftWrapsHistory.loadEverything(user)
                    nip04History.loadEverything(user)
                }
            }
        }

        // No private chat is loaded yet (e.g. only public rooms so far): show the boundary at the end.
        if (privateBoundaryIndex < 0 && (loadingMore || !historyExhausted)) {
            item(key = "loadingMoreFooter") {
                DmLoadMoreIndicator(loadingMore, showLoadAll = !historyExhausted) {
                    val user = accountViewModel.userProfile()
                    giftWrapsHistory.loadEverything(user)
                    nip04History.loadEverything(user)
                }
            }
        }
    }
}

// How many rows ahead of the oldest loaded private chat to start widening, so older private
// history lands before the user scrolls into the (membership-based) public/group rooms below it.
private const val PREFETCH_PRIVATE_CHATS = 5

/**
 * Widens the DM window whenever [wantMore] becomes true and the previous step isn't still loading,
 * stopping once the window is exhausted. Advances the single gift-wrap window ([loadMore]) and tells
 * the NIP-04 follower to re-request at the new floor ([reload]).
 *
 * [wantMore] is evaluated inside a snapshotFlow, so it may read live Compose state (scroll position,
 * the feed list). Callers decide the policy: the empty feed widens to discover the first rooms; the
 * loaded feed widens as the user approaches the oldest loaded PRIVATE chat — public, group and
 * ephemeral rooms are membership-based (shown regardless of age) and deliberately excluded, so an
 * old public chat at the bottom never drags the window back with it.
 *
 * The guard waits on BOTH loaders, gated on all of each one's relays answering (or a timeout) rather
 * than the first EOSE, so a fast near-empty relay can't let the loop outrun the slow relay that
 * holds the conversations.
 */
@Composable
private fun WidenPrivateWindowWhen(
    accountViewModel: AccountViewModel,
    trigger: String,
    // Number of distinct private rooms currently loaded, or null for callers that should keep widening
    // regardless (the empty feed, still hunting for the first room). When provided, the loop stops
    // advancing once a widen brings in no new private room — widening only adds older MESSAGES, so a
    // few correspondents' history can flood thousands of events without adding a row, and "fill the
    // screen" must stop at "no new people" instead of walking the window to the 10-year backstop. The
    // mark lives on the history manager so it survives leaving/reopening the screen.
    privateRoomCount: (() -> Int)? = null,
    wantMore: () -> Boolean,
) {
    val giftWrapsHistory = remember(accountViewModel) { accountViewModel.dataSources().account.giftWrapsHistory }
    val nip04History = remember(accountViewModel) { accountViewModel.dataSources().chatroomList.nip04History }

    LaunchedEffect(giftWrapsHistory, nip04History) {
        combine(
            // Carries the private-room count (>= 0) while a widen is wanted, or NOT_WANTED otherwise.
            snapshotFlow { if (wantMore()) (privateRoomCount?.invoke() ?: STILL_SEARCHING) else NOT_WANTED },
            giftWrapsHistory.loadingMore,
            nip04History.loadingMore,
            giftWrapsHistory.exhausted,
            nip04History.exhausted,
        ) { count, loadingGiftWraps, loadingNip04, giftWrapsExhausted, nip04Exhausted ->
            // Keep paging while either protocol still has older history to reach.
            if (count != NOT_WANTED && !loadingGiftWraps && !loadingNip04 && !(giftWrapsExhausted && nip04Exhausted)) count else NOT_WANTED
        }.distinctUntilChanged()
            .collect { count ->
                if (count == NOT_WANTED) return@collect
                // Stop once a widen adds no new private room (but keep hunting while none are loaded).
                if (privateRoomCount != null && count > 0 && count <= giftWrapsHistory.autoFillPrivateRoomMark) {
                    Log.d("DMPagination") { "rooms.list: widen ($trigger) stop — no new private rooms (count=$count)" }
                    return@collect
                }
                if (privateRoomCount != null) giftWrapsHistory.autoFillPrivateRoomMark = count
                Log.d("DMPagination") { "rooms.list: widen ($trigger) → loadMore (privateRooms=$count)" }
                giftWrapsHistory.loadMore(accountViewModel.userProfile())
                nip04History.loadMore(accountViewModel.userProfile())
            }
    }
}

// Sentinels for the widen-trigger flow: NOT_WANTED suppresses widening; STILL_SEARCHING is the count a
// caller without a private-room measure reports while it wants to keep widening (e.g. the empty feed).
private const val NOT_WANTED = -1
private const val STILL_SEARCHING = 0

// Stable per-chatroom key — derived from chatroom identity, not the latest
// message id, so reorders move the row instead of recreating it. Compose
// stores LazyColumn item keys in a SaveableStateHolder, which on Android
// requires Bundle-storable types, so each variant is Serializable and only
// holds primitives.
private sealed interface ChatroomLazyKey : Serializable

private data class MarmotChatroomLazyKey(
    val groupId: HexKey,
) : ChatroomLazyKey

private data class PublicChannelLazyKey(
    val channelId: HexKey,
) : ChatroomLazyKey

private data class EphemeralChannelLazyKey(
    val id: String,
    val relayUrl: String,
) : ChatroomLazyKey

private data class PrivateChatLazyKey(
    val users: HashSet<HexKey>,
) : ChatroomLazyKey

private data class FallbackChatroomLazyKey(
    val noteIdHex: HexKey,
) : ChatroomLazyKey

private fun chatroomLazyKey(
    item: Note,
    myPubKey: HexKey,
): ChatroomLazyKey {
    item.inGatherers
        ?.firstNotNullOfOrNull { it as? MarmotGroupChatroom }
        ?.let { return MarmotChatroomLazyKey(it.nostrGroupId) }

    return when (val event = item.event) {
        is ChannelMessageEvent -> {
            PublicChannelLazyKey(event.channelId() ?: item.idHex)
        }

        is ChannelMetadataEvent -> {
            PublicChannelLazyKey(event.channelId() ?: item.idHex)
        }

        is ChannelCreateEvent -> {
            PublicChannelLazyKey(event.id)
        }

        is EphemeralChatEvent -> {
            event.roomId()?.let { EphemeralChannelLazyKey(it.id, it.relayUrl.url) }
                ?: FallbackChatroomLazyKey(item.idHex)
        }

        is ChatroomKeyable -> {
            // ChatroomKey.users may be a kotlinx PersistentOrderedSet, which
            // is not Serializable. Copy into a HashSet so the key survives
            // Bundle round-trips; Set equality stays order-independent.
            PrivateChatLazyKey(HashSet(event.chatroomKey(myPubKey).users))
        }

        else -> {
            FallbackChatroomLazyKey(item.idHex)
        }
    }
}
