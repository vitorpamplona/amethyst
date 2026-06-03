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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.DmHistoryLoadingCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.ChatroomHeaderCompose
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.StateFlow
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

    // While the whole list is empty there is no LazyColumn to scroll, so hunt BOTH protocols for the
    // first rooms (no stall-gate while searching) until they appear or each is exhausted. The two run
    // independently — NIP-04 and NIP-17 have very different histories and depths.
    val user = accountViewModel.userProfile()
    WidenHistoryWhen(
        "empty.nip17",
        giftWrapsHistory.loadingMore,
        giftWrapsHistory.exhausted,
        roomCount = null,
        loadMore = { giftWrapsHistory.loadMore(user) },
    ) { feedState is FeedState.Empty }
    WidenHistoryWhen(
        "empty.nip04",
        nip04History.loadingMore,
        nip04History.exhausted,
        roomCount = null,
        loadMore = { nip04History.loadMore(user) },
    ) { feedState is FeedState.Empty }

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
    val giftWrapsExhausted by giftWrapsHistory.exhausted.collectAsStateWithLifecycle()
    val nip04Exhausted by nip04History.exhausted.collectAsStateWithLifecycle()
    val user = accountViewModel.userProfile()

    // NIP-17 and NIP-04 have very different histories and depths (e.g. NIP-04 reaching back to 2023
    // while NIP-17 is shallow), so each protocol gets its OWN trigger keyed to its OWN oldest loaded
    // room — otherwise the deeper protocol's tail pins the boundary to the bottom and the shallower
    // one never loads until the user scrolls all the way past it. Each is gated only on its own loader
    // and "is my oldest room near the bottom of the viewport", so while the boundary is in view it keeps
    // paging to exhaustion (no stall-gate — a visible card means the user is waiting for more). Public /
    // group / ephemeral rooms are membership-based and excluded.
    WidenHistoryWhen(
        "scroll.nip17",
        giftWrapsHistory.loadingMore,
        giftWrapsHistory.exhausted,
        roomCount = { items.list.count { it.event is ChatroomKeyable && it.event !is PrivateDmEvent } },
        loadMore = { giftWrapsHistory.loadMore(user) },
    ) {
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        val oldest = items.list.indexOfLast { it.event is ChatroomKeyable && it.event !is PrivateDmEvent }
        info.totalItemsCount > 0 && (oldest < 0 || lastVisible >= oldest - PREFETCH_PRIVATE_CHATS)
    }
    WidenHistoryWhen(
        "scroll.nip04",
        nip04History.loadingMore,
        nip04History.exhausted,
        roomCount = { items.list.count { it.event is PrivateDmEvent } },
        loadMore = { nip04History.loadMore(user) },
    ) {
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        val oldest = items.list.indexOfLast { it.event is PrivateDmEvent }
        info.totalItemsCount > 0 && (oldest < 0 || lastVisible >= oldest - PREFETCH_PRIVATE_CHATS)
    }

    // One status card PER protocol, at that protocol's oldest loaded room: it shows what the app is
    // reaching for (relays + how far back it has paged) while it loads, then crossfades to "All caught
    // up" and collapses when it runs dry. The two sit at different depths (NIP-04's typically deeper),
    // so the user sees each protocol load where its own history actually ends.
    val giftWrapsRelays by giftWrapsHistory.relayCount.collectAsStateWithLifecycle()
    val giftWrapsReached by giftWrapsHistory.reachedBack.collectAsStateWithLifecycle()
    val nip04Relays by nip04History.relayCount.collectAsStateWithLifecycle()
    val nip04Reached by nip04History.reachedBack.collectAsStateWithLifecycle()
    val nip17Name = stringResource(R.string.chats_history_proto_nip17)
    val nip04Name = stringResource(R.string.chats_history_proto_nip04)
    val oldestNip17Index = items.list.indexOfLast { it.event is ChatroomKeyable && it.event !is PrivateDmEvent }
    val oldestNip04Index = items.list.indexOfLast { it.event is PrivateDmEvent }

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

            // Rendered unconditionally at the protocol's oldest room so the card can run its own
            // "All caught up" crossfade-and-collapse when that protocol exhausts.
            if (index == oldestNip17Index) {
                DmHistoryLoadingCard(nip17Name, "NIP-17", loadingGiftWraps, giftWrapsExhausted, giftWrapsRelays, giftWrapsReached)
            }
            if (index == oldestNip04Index) {
                DmHistoryLoadingCard(nip04Name, "NIP-04", loadingNip04, nip04Exhausted, nip04Relays, nip04Reached)
            }
        }

        // Protocols with no room loaded yet (e.g. only public rooms so far): show their card at the end.
        if (oldestNip17Index < 0 && (loadingGiftWraps || !giftWrapsExhausted)) {
            item(key = "nip17Footer") {
                DmHistoryLoadingCard(nip17Name, "NIP-17", loadingGiftWraps, giftWrapsExhausted, giftWrapsRelays, giftWrapsReached)
            }
        }
        if (oldestNip04Index < 0 && (loadingNip04 || !nip04Exhausted)) {
            item(key = "nip04Footer") {
                DmHistoryLoadingCard(nip04Name, "NIP-04", loadingNip04, nip04Exhausted, nip04Relays, nip04Reached)
            }
        }
    }
}

// How many rows ahead of the oldest loaded private chat to start widening, so older private
// history lands before the user scrolls into the (membership-based) public/group rooms below it.
private const val PREFETCH_PRIVATE_CHATS = 5

/**
 * Drives ONE protocol's history paging from a scroll/empty trigger. While [wantMore] is true and that
 * protocol isn't already loading or [exhausted], it keeps calling [loadMore] round after round until
 * the history is genuinely exhausted (an empty `until`+`limit` page) — there is no stall-gate: if the
 * boundary card is in view the user is waiting for more, so we don't stop just because a band of older
 * messages surfaced no new conversation row. Paging naturally stops when [wantMore] goes false (the
 * boundary scrolls out of view) or the protocol exhausts.
 *
 * [wantMore] and [roomCount] are read inside a snapshotFlow, so they may observe live Compose state
 * (scroll position, the feed list). [roomCount] is only carried for the log line; pass `null` when the
 * caller has no room measure (the empty feed, hunting for the first room).
 *
 * Each protocol gets its own instance, gated only on its own loader, so NIP-04 and NIP-17 — which have
 * very different histories — page independently as the user scrolls.
 */
@Composable
private fun WidenHistoryWhen(
    trigger: String,
    loadingMore: StateFlow<Boolean>,
    exhausted: StateFlow<Boolean>,
    roomCount: (() -> Int)?,
    loadMore: () -> Unit,
    wantMore: () -> Boolean,
) {
    LaunchedEffect(loadingMore, exhausted) {
        combine(
            // Carries this protocol's room count (>= 0) while a widen is wanted, or NOT_WANTED otherwise.
            snapshotFlow { if (wantMore()) (roomCount?.invoke() ?: STILL_SEARCHING) else NOT_WANTED },
            loadingMore,
            exhausted,
        ) { count, loading, exhaustedNow ->
            if (count != NOT_WANTED && !loading && !exhaustedNow) count else NOT_WANTED
        }.distinctUntilChanged()
            .collect { count ->
                if (count == NOT_WANTED) return@collect
                Log.d("DMPagination") { "rooms.list: widen ($trigger) → loadMore (rooms=$count)" }
                loadMore()
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
