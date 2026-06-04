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
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.RelayPagingProgress
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.actions.uploads.resolveSharedMedia
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.elements.ObserveRelayListForDMsAndDisplayIfNotFound
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.DmHistoryLoadingCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.RelayReachState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.RelayWindowLimit
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.RelayWindowLimitMarkers
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.dal.ChatroomFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.datasource.ChatroomFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.ChatNewMessageViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.PrivateMessageEditFieldRow
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
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

/**
 * Bootstraps history when the conversation has no messages yet (the live tail came back empty for a
 * thread whose newest message is older than a week). There's nothing on screen to host the per-relay
 * window-limit markers that normally drive paging, so while the feed is empty we step every relay one
 * page at a time (each protocol independently, gated on its own loader) until messages appear — at which
 * point the on-screen markers take over — or the protocol is exhausted. Once the feed is Loaded this
 * does nothing; paging is then purely demand-driven by the markers' visibility.
 */
@Composable
private fun BootstrapHistoryWhenEmpty(
    feedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val giftWrapsHistory = remember(accountViewModel) { accountViewModel.dataSources().account.giftWrapsHistory }
    val nip04History = remember(accountViewModel) { accountViewModel.dataSources().chatroom.nip04History }
    val feedState by feedContentState.feedContent.collectAsStateWithLifecycle()
    val needsBootstrap = feedState is FeedState.Empty || feedState is FeedState.Loading

    LaunchedEffect(needsBootstrap, giftWrapsHistory) {
        if (!needsBootstrap) return@LaunchedEffect
        combine(giftWrapsHistory.loadingMore, giftWrapsHistory.exhausted) { loading, exhausted -> !loading && !exhausted }
            .distinctUntilChanged()
            .filter { it }
            .collect { giftWrapsHistory.advanceAll(accountViewModel.userProfile()) }
    }
    LaunchedEffect(needsBootstrap, nip04History) {
        if (!needsBootstrap) return@LaunchedEffect
        combine(nip04History.loadingMore, nip04History.exhausted) { loading, exhausted -> !loading && !exhausted }
            .distinctUntilChanged()
            .filter { it }
            .collect { nip04History.advanceAll() }
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
    val nip04Progress by nip04History.relayProgress.collectAsStateWithLifecycle()
    val giftWrapsProgress by giftWrapsHistory.relayProgress.collectAsStateWithLifecycle()
    val user = accountViewModel.userProfile()

    // Both protocols' per-relay window limits, each carrying the advance() that pulls its own next page.
    // Placed in the stream as sentinels (see RelayWindowLimitMarkers): a relay pages only while its
    // marker is on screen, and keeps paging while it stays there. A protocol drops out once exhausted.
    val limits =
        remember(nip04Progress, giftWrapsProgress, nip04Exhausted, giftWrapsExhausted, user) {
            buildList {
                if (!giftWrapsExhausted) {
                    giftWrapsProgress.forEach { (relay, p) ->
                        add(RelayWindowLimit("17:${relay.url}", relayShortName(relay), p.reachedUntil, reachState(p)) { giftWrapsHistory.advance(user, relay) })
                    }
                }
                if (!nip04Exhausted) {
                    nip04Progress.forEach { (relay, p) ->
                        add(RelayWindowLimit("04:${relay.url}", relayShortName(relay), p.reachedUntil, reachState(p)) { nip04History.advance(relay) })
                    }
                }
            }
        }
    val nip17Name = stringResource(R.string.chats_history_proto_nip17)
    val nip04Name = stringResource(R.string.chats_history_proto_nip04)

    BootstrapHistoryWhenEmpty(feedViewModel.feedState, accountViewModel)

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
                // Each relay's window-limit marker, placed at its reached cursor, doubles as the load
                // sentinel that pulls that relay's next page while it's on screen (see
                // RelayWindowLimitMarkers). Hidden once both protocols are exhausted.
                markersInGap =
                    if (limits.isEmpty()) {
                        null
                    } else {
                        { newer, older -> RelayWindowLimitMarkers(limits, newer, older) }
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

private fun reachState(p: RelayPagingProgress): RelayReachState =
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
