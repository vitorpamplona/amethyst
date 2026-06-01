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
import kotlinx.coroutines.flow.filter
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

    // The gift-wrap window is the single DM window (NIP-04 follows it), so once it reaches the
    // maximum lookback the rooms list has pulled everything there is. Until then an empty feed means
    // "still filling", not "no conversations" — keep the spinner up rather than flash empty.
    val giftWraps = remember(accountViewModel) { accountViewModel.dataSources().account.giftWraps }
    val historyExhausted by giftWraps.exhausted.collectAsStateWithLifecycle()

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

    val giftWraps = remember(accountViewModel) { accountViewModel.dataSources().account.giftWraps }
    val nip04 = remember(accountViewModel) { accountViewModel.dataSources().chatroomList.nip04 }
    val loadingGiftWraps by giftWraps.loadingMore.collectAsStateWithLifecycle()
    val loadingNip04 by nip04.loadingMore.collectAsStateWithLifecycle()
    val loadingMore = loadingGiftWraps || loadingNip04
    val historyExhausted by giftWraps.exhausted.collectAsStateWithLifecycle()

    // Widen the private DM window only as the user approaches the oldest LOADED private chat —
    // ignoring public / group / ephemeral rooms below it. Those are membership-based and can be
    // arbitrarily old; counting them would either stall private paging or drag the window back
    // years. The lambda reads the live items + scroll state inside the snapshotFlow.
    WidenPrivateWindowWhen(accountViewModel, "scroll") {
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
                    giftWraps.loadEverything(user)
                    nip04.reload()
                }
            }
        }

        // No private chat is loaded yet (e.g. only public rooms so far): show the boundary at the end.
        if (privateBoundaryIndex < 0 && (loadingMore || !historyExhausted)) {
            item(key = "loadingMoreFooter") {
                DmLoadMoreIndicator(loadingMore, showLoadAll = !historyExhausted) {
                    val user = accountViewModel.userProfile()
                    giftWraps.loadEverything(user)
                    nip04.reload()
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
    wantMore: () -> Boolean,
) {
    val giftWraps = remember(accountViewModel) { accountViewModel.dataSources().account.giftWraps }
    val nip04 = remember(accountViewModel) { accountViewModel.dataSources().chatroomList.nip04 }

    LaunchedEffect(giftWraps, nip04) {
        combine(
            snapshotFlow { wantMore() },
            giftWraps.loadingMore,
            nip04.loadingMore,
            giftWraps.exhausted,
        ) { want, loadingGiftWraps, loadingNip04, exhausted ->
            want && !loadingGiftWraps && !loadingNip04 && !exhausted
        }.distinctUntilChanged()
            .filter { it }
            .collect {
                Log.d("DMPagination") { "rooms.list: widen ($trigger) → loadMore + reload" }
                giftWraps.loadMore(accountViewModel.userProfile())
                nip04.reload()
            }
    }
}

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
