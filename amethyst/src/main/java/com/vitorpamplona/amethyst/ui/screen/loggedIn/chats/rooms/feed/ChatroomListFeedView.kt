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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.ChatroomHeaderCompose
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size25dp
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

    // Both DM windows reach the maximum lookback together (lockstep), so the rooms list has
    // pulled everything there is once both report exhausted. Until then an empty feed means
    // "still filling", not "no conversations" — keep the spinner up rather than flash empty.
    val giftWraps = remember(accountViewModel) { accountViewModel.dataSources().account.giftWraps }
    val nip04Dms = remember(accountViewModel) { accountViewModel.dataSources().chatroomList.nip04Dms }
    val giftWrapsExhausted by giftWraps.exhausted.collectAsStateWithLifecycle()
    val nip04Exhausted by nip04Dms.exhausted.collectAsStateWithLifecycle()
    val historyExhausted = giftWrapsExhausted && nip04Exhausted

    // Drive auto-fill / prefetch here (not inside FeedLoaded) so it keeps widening even while
    // the feed is still Empty and there is no LazyColumn to scroll yet.
    AutoFillAndPrefetch(listState, { feedState is FeedState.Empty }, accountViewModel)

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
    val nip04Dms = remember(accountViewModel) { accountViewModel.dataSources().chatroomList.nip04Dms }
    val loadingGiftWraps by giftWraps.loadingMore.collectAsStateWithLifecycle()
    val loadingNip04 by nip04Dms.loadingMore.collectAsStateWithLifecycle()
    val loadingMore = loadingGiftWraps || loadingNip04
    val exhaustedGiftWraps by giftWraps.exhausted.collectAsStateWithLifecycle()
    val exhaustedNip04 by nip04Dms.exhausted.collectAsStateWithLifecycle()
    val historyExhausted = exhaustedGiftWraps && exhaustedNip04

    LazyColumn(
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        items(
            items.list,
            key = { item -> chatroomLazyKey(item, myPubKey) },
        ) { item ->
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
        }

        // Footer: shows the auto-fill / full-load spinner, and — while there is still older history
        // to reach — a button to skip the windowed paging and pull the entire history at once.
        if (loadingMore || !historyExhausted) {
            item(key = "loadingMoreFooter") {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = Size10dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (loadingMore) {
                        CircularProgressIndicator(Modifier.size(Size25dp))
                    }
                    if (!historyExhausted) {
                        TextButton(
                            onClick = {
                                val user = accountViewModel.userProfile()
                                giftWraps.loadEverything(user)
                                nip04Dms.loadEverything(user)
                            },
                        ) {
                            Text(stringResource(R.string.chats_load_entire_history))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Keeps the messages list filled and prefetched by widening the DM time windows.
 *
 * One condition drives three behaviors at once: widen when nothing is loaded yet (empty feed),
 * or when the last visible row has crossed the midpoint of what's loaded. Because everything
 * fits on screen while the list is short, the midpoint is trivially crossed, so it keeps
 * widening until the list overflows the viewport with a buffer below the fold — and once it
 * does, it only fires again as the user scrolls past the new midpoint, so fresh (geometrically
 * larger) windows land well before the user reaches the end. It stops only when the window is
 * exhausted (reached max lookback — nothing older exists).
 *
 * Both DM protocols advance in lockstep: NIP-17 gift wraps (always-on account loader) and NIP-04
 * (this screen's loader). They must move together — if only one were windowed, the merged
 * time-sorted list would mix a deep tail of one protocol with a shallow window of the other, and
 * a widen would pull rooms that land in the middle of the feed instead of extending the end.
 *
 * The per-window [loadingMore] guard gates each step on ALL of that window's relays answering
 * (or a timeout), not the first EOSE — otherwise a fast, near-empty relay would clear the guard
 * and let this loop outrun the slow relay that actually holds the conversations.
 */
@Composable
private fun AutoFillAndPrefetch(
    listState: LazyListState,
    isFeedEmpty: () -> Boolean,
    accountViewModel: AccountViewModel,
) {
    val giftWraps = remember(accountViewModel) { accountViewModel.dataSources().account.giftWraps }
    val nip04Dms = remember(accountViewModel) { accountViewModel.dataSources().chatroomList.nip04Dms }

    LaunchedEffect(listState, giftWraps, nip04Dms) {
        combine(
            snapshotFlow {
                val info = listState.layoutInfo
                val total = info.totalItemsCount
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                // Want more when nothing is loaded yet, or when the last visible row has crossed
                // the midpoint of what's loaded (prefetch well before reaching the end).
                isFeedEmpty() || (total > 0 && lastVisible >= total / 2)
            },
            giftWraps.loadingMore,
            nip04Dms.loadingMore,
            giftWraps.exhausted,
            nip04Dms.exhausted,
        ) { wantMore, loadingGiftWraps, loadingNip04, giftWrapsExhausted, nip04Exhausted ->
            wantMore && !loadingGiftWraps && !loadingNip04 && !(giftWrapsExhausted && nip04Exhausted)
        }.distinctUntilChanged()
            .filter { it }
            .collect {
                Log.d("DMPagination") { "rooms list needs more (auto-fill/prefetch), widening NIP-17 + NIP-04 windows one step" }
                val user = accountViewModel.userProfile()
                giftWraps.loadMore(user)
                nip04Dms.loadMore(user)
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
