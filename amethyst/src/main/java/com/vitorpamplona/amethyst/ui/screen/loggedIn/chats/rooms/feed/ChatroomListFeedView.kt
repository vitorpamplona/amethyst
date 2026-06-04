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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.RelayPagingProgress
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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.RelayReachState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.RelayWindowLimit
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.RelayWindowLimitMarkers
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.ChatroomHeaderCompose
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.StateFlow
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

    // History is exhausted only once BOTH DM protocols have paged to their end (each stops when a
    // round of until+limit pages brings nothing back). Until then an empty feed means "still filling",
    // not "no conversations" — keep the spinner up rather than flash empty.
    val giftWrapsHistory = remember(accountViewModel) { accountViewModel.dataSources().account.giftWrapsHistory }
    val nip04History = remember(accountViewModel) { accountViewModel.dataSources().chatroomList.nip04History }
    val giftWrapsExhausted by giftWrapsHistory.exhausted.collectAsStateWithLifecycle()
    val nip04Exhausted by nip04History.exhausted.collectAsStateWithLifecycle()
    val historyExhausted = giftWrapsExhausted && nip04Exhausted

    // While the whole list is empty there is no LazyColumn to host the per-relay window-limit markers
    // that normally drive paging, so we step every relay one page at a time (each protocol independently)
    // to hunt for the first rooms until they appear or the protocol is exhausted. Once rooms load the
    // markers take over and paging becomes demand-driven by their visibility.
    val user = accountViewModel.userProfile()
    val bootstrap = feedState is FeedState.Empty || feedState is FeedState.Loading
    BootstrapHistoryWhenEmpty(bootstrap, giftWrapsHistory.loadingMore, giftWrapsHistory.exhausted) { giftWrapsHistory.advanceAll(user) }
    BootstrapHistoryWhenEmpty(bootstrap, nip04History.loadingMore, nip04History.exhausted) { nip04History.advanceAll(user) }

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

    // One status card PER protocol, at that protocol's oldest loaded room: it shows what the app is
    // reaching for (relays + how far back it has paged) while it loads, then crossfades to "All caught
    // up" and collapses when it runs dry.
    val giftWrapsRelays by giftWrapsHistory.relayCount.collectAsStateWithLifecycle()
    val giftWrapsReached by giftWrapsHistory.reachedBack.collectAsStateWithLifecycle()
    val nip04Relays by nip04History.relayCount.collectAsStateWithLifecycle()
    val nip04Reached by nip04History.reachedBack.collectAsStateWithLifecycle()
    val giftWrapsProgress by giftWrapsHistory.relayProgress.collectAsStateWithLifecycle()
    val nip04Progress by nip04History.relayProgress.collectAsStateWithLifecycle()
    val nip17Name = stringResource(R.string.chats_history_proto_nip17)
    val nip04Name = stringResource(R.string.chats_history_proto_nip04)
    val oldestNip17Index = items.list.indexOfLast { it.event is ChatroomKeyable && it.event !is PrivateDmEvent }
    val oldestNip04Index = items.list.indexOfLast { it.event is PrivateDmEvent }

    // Each relay's window limit, carrying the advance() that pulls its OWN next page. Placed in the list
    // at its reached depth as a sentinel (see RelayWindowLimitMarkers): a relay pages only while its
    // marker is on screen and keeps paging while it stays there, so a spam-dense relay never floods —
    // you have to scroll through its messages to pull more. A protocol drops out once exhausted.
    val limits =
        remember(giftWrapsProgress, nip04Progress, giftWrapsExhausted, nip04Exhausted, user) {
            buildList {
                if (!giftWrapsExhausted) {
                    giftWrapsProgress.forEach { (relay, p) ->
                        add(RelayWindowLimit("17:${relay.url}", relayShortName(relay), p.reachedUntil, reachState(p)) { giftWrapsHistory.advance(user, relay) })
                    }
                }
                if (!nip04Exhausted) {
                    nip04Progress.forEach { (relay, p) ->
                        add(RelayWindowLimit("04:${relay.url}", relayShortName(relay), p.reachedUntil, reachState(p)) { nip04History.advance(user, relay) })
                    }
                }
            }
        }

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

            // Per-relay window-limit markers/sentinels belonging in the gap toward the next-older room:
            // each pulls its relay's next page while it's on screen. olderCreatedAt is null past the
            // oldest loaded room, so relays that have reached the bottom of the list sit there.
            RelayWindowLimitMarkers(
                limits,
                item.createdAt(),
                items.list.getOrNull(index + 1)?.createdAt(),
            )
        }
    }
}

/**
 * Bootstraps history while the rooms list has nothing to scroll (empty / still loading): steps every
 * relay one page at a time, gated only on its own loader, until rooms appear or the protocol exhausts.
 * Once rooms load this stops and the per-relay window-limit markers drive paging on demand.
 */
@Composable
private fun BootstrapHistoryWhenEmpty(
    active: Boolean,
    loadingMore: StateFlow<Boolean>,
    exhausted: StateFlow<Boolean>,
    advanceAll: () -> Unit,
) {
    LaunchedEffect(active, loadingMore, exhausted) {
        if (!active) return@LaunchedEffect
        combine(loadingMore, exhausted) { loading, exhaustedNow -> !loading && !exhaustedNow }
            .distinctUntilChanged()
            .filter { it }
            .collect { advanceAll() }
    }
}

private fun reachState(p: RelayPagingProgress) =
    when {
        p.done -> RelayReachState.DONE
        p.stalled -> RelayReachState.STALLED
        else -> RelayReachState.REACHING
    }

private fun relayShortName(relay: NormalizedRelayUrl): String =
    relay.url
        .substringAfter("://")
        .trimEnd('/')
        .substringBefore('/')

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
