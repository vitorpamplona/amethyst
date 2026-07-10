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
import androidx.compose.runtime.mutableStateOf
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
import com.vitorpamplona.amethyst.commons.ui.feeds.DmHistoryLoadingCard
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachCursor
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachDetailDialog
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachMarkers
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachSentinels
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachState
import com.vitorpamplona.amethyst.model.privateChatLastReadRoute
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.actions.uploads.resolveSharedMedia
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.elements.ObserveRelayListForDMsAndDisplayIfNotFound
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.formatHistoryReachDate
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.dal.ChatroomFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.datasource.ChatroomFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.ChatNewMessageViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.PrivateMessageEditFieldRow
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayPagingProgress
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
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
    // Empty only (never the transient Loading navigation flashes through), and debounced below, so
    // re-opening a conversation that has messages doesn't kick a hunt.
    val needsBootstrap = feedState is FeedState.Empty

    LaunchedEffect(needsBootstrap, giftWrapsHistory) {
        if (!needsBootstrap) return@LaunchedEffect
        delay(BOOTSTRAP_DEBOUNCE_MS)
        combine(giftWrapsHistory.loadingMore, giftWrapsHistory.status) { loading, s -> !loading && !s.exhausted }
            .distinctUntilChanged()
            .filter { it }
            .collect { giftWrapsHistory.advanceAll() }
    }
    LaunchedEffect(needsBootstrap, nip04History) {
        if (!needsBootstrap) return@LaunchedEffect
        delay(BOOTSTRAP_DEBOUNCE_MS)
        combine(nip04History.loadingMore, nip04History.status) { loading, s -> !loading && !s.exhausted }
            .distinctUntilChanged()
            .filter { it }
            .collect { nip04History.advanceAll() }
    }
}

// Ignore the transient empty feed that navigation flashes through before messages re-appear.
private const val BOOTSTRAP_DEBOUNCE_MS = 1200L

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
    // One atomic snapshot per protocol (exhausted + relays + reached + per-relay progress) instead of six
    // separate collectors — the status card and the per-relay markers read all of it together anyway.
    val giftWrapsStatus by giftWrapsHistory.status.collectAsStateWithLifecycle()
    val nip04Status by nip04History.status.collectAsStateWithLifecycle()
    val user = accountViewModel.userProfile()

    // Both protocols' per-relay window limits, each carrying the advance() that pulls its own next page.
    // Placed in the stream as sentinels (see RelayReachMarkers): a relay pages only while its
    // marker is on screen, and keeps paging while it stays there. A protocol drops out once exhausted.
    val limits =
        remember(nip04Status, giftWrapsStatus, user) {
            buildList {
                if (!giftWrapsStatus.exhausted) {
                    giftWrapsStatus.relayProgress.forEach { (relay, p) ->
                        add(RelayReachCursor("17:${relay.url}", relayShortName(relay), p.reachedUntil, reachState(p), "NIP-17") { giftWrapsHistory.advance(relay) })
                    }
                }
                if (!nip04Status.exhausted) {
                    nip04Status.relayProgress.forEach { (relay, p) ->
                        add(RelayReachCursor("04:${relay.url}", relayShortName(relay), p.reachedUntil, reachState(p), "NIP-04") { nip04History.advance(relay) })
                    }
                }
            }
        }
    val nip17Name = stringResource(R.string.chats_history_proto_nip17)
    val nip04Name = stringResource(R.string.chats_history_proto_nip04)

    // The relays behind a tapped in-stream "Relay sync" marker; non-null shows the detail popup.
    var syncDetail by remember { mutableStateOf<List<RelayReachCursor>?>(null) }
    syncDetail?.let { detail ->
        RelayReachDetailDialog(detail, ::formatHistoryReachDate) { syncDetail = null }
    }

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
                routeForLastRead = privateChatLastReadRoute(room),
                avoidDraft = newPostModel.draftTag,
                onWantsToReply = newPostModel::reply,
                onWantsToEditDraft = newPostModel::editFromDraft,
                // One status card per protocol at the oldest end: each shows what it's reaching for
                // while it pages and crossfades to "All caught up" when that protocol runs dry.
                olderBoundary = {
                    Column {
                        DmHistoryLoadingCard(nip17Name, "NIP-17", loadingGiftWraps, giftWrapsStatus.exhausted, giftWrapsStatus.relayCount, giftWrapsStatus.stalledCount, giftWrapsStatus.reachedBack, giftWrapsStatus.relayProgress, ::formatHistoryReachDate)
                        DmHistoryLoadingCard(nip04Name, "NIP-04", loadingNip04, nip04Status.exhausted, nip04Status.relayCount, nip04Status.stalledCount, nip04Status.reachedBack, nip04Status.relayProgress, ::formatHistoryReachDate)
                    }
                },
                // Each relay's window-limit marker, placed at its reached cursor (pure UI). Hidden once
                // both protocols are exhausted.
                markersInGap =
                    if (limits.isEmpty()) {
                        null
                    } else {
                        { newer, older -> RelayReachMarkers(limits, newer, older) { syncDetail = it } }
                    },
                // The hoisted load driver that pulls each relay's next page while its marker is on screen,
                // off viewport visibility (see RelayReachSentinels) so feed reorders don't re-page.
                sentinels =
                    if (limits.isEmpty()) {
                        null
                    } else {
                        { items, listState ->
                            RelayReachSentinels(limits, listState) { index -> items.getOrNull(index)?.event?.createdAt }
                        }
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
