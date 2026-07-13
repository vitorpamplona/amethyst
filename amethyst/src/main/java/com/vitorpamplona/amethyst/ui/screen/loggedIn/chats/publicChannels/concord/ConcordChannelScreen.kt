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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.concord.ConcordCommunitySession
import com.vitorpamplona.amethyst.commons.ui.feeds.DmHistoryLoadingCard
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachCursor
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachMarkers
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachSentinels
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachState
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserInfo
import com.vitorpamplona.amethyst.ui.actions.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.ui.actions.UrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.formatHistoryReachDate
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelHistorySubAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelHistorySubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.send.ConcordNewMessageViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.dal.ChannelFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.DisplayReplyingToNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ReplyModeToggle
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ThinSendButton
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.ui.theme.EditFieldModifier
import com.vitorpamplona.amethyst.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayPagingProgress
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The chat screen of one Concord Channel. Messages are real Notes in [LocalCache]
 * attached to the channel, so the feed reuses the shared [RefreshingChatroomFeedView]
 * (avatars, reactions, replies, zaps, OTS) and the composer reuses the same
 * @-mention / inline-mention / reply machinery as the other chats. Only the send
 * path is Concord-specific: it wraps the message on the channel plane.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordChannelScreen(
    communityId: String,
    channelId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ConcordChannelSubscription(accountViewModel.dataSources().concordChannels, accountViewModel)
    ConcordChannelHistorySubscription(communityId, channelId, accountViewModel.dataSources().concordChannelHistory, accountViewModel)

    val account = accountViewModel.account
    val channel = remember(account, communityId, channelId) { LocalCache.getOrCreateConcordChannel(ConcordChannelId(communityId, channelId)) }

    val feedViewModel: ChannelFeedViewModel =
        viewModel(
            key = channel.channelId.toKey() + "ConcordFeedViewModel",
            factory = ChannelFeedViewModel.Factory(channel, account),
        )
    WatchLifecycleAndUpdateModel(feedViewModel)

    // Backward history pager for this open channel (older wraps by until+limit, per relay), mirroring
    // the NIP-04 per-conversation history: markers drive paging while on screen, a status card sits at
    // the oldest end, and an empty channel bootstraps one page so there is something to scroll from.
    val history = remember(accountViewModel) { accountViewModel.dataSources().concordChannelHistory.history }
    val loadingHistory by history.loadingMore.collectAsStateWithLifecycle()
    val historyStatus by history.status.collectAsStateWithLifecycle()
    val limits =
        remember(historyStatus) {
            buildList {
                if (!historyStatus.exhausted) {
                    historyStatus.relayProgress.forEach { (relay, p) ->
                        add(RelayReachCursor("cord:${relay.url}", relayShortName(relay), p.reachedUntil, reachState(p), "Concord") { history.advance(relay) })
                    }
                }
            }
        }
    ConcordBackfillHistoryToWindow(feedViewModel.feedState, history)

    val newMessageModel: ConcordNewMessageViewModel = viewModel(key = channel.channelId.toKey() + "ConcordNewMessageViewModel")
    newMessageModel.init(accountViewModel)
    newMessageModel.load(communityId, channelId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(channel.toBestDisplayName(), fontWeight = FontWeight.Bold, maxLines = 1)
                        channel.communityName?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        SymbolIcon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(com.vitorpamplona.amethyst.R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxHeight().padding(padding)) {
            Column(Modifier.fillMaxHeight().weight(1f, true)) {
                RefreshingChatroomFeedView(
                    feedContentState = feedViewModel.feedState,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    routeForLastRead = concordChannelLastReadRoute(communityId, channelId),
                    onWantsToReply = { newMessageModel.reply(it) },
                    onWantsToEditDraft = {},
                    // A status card at the oldest end: shows what it's reaching for while it pages and
                    // crossfades to "All caught up" when every relay runs dry.
                    olderBoundary = {
                        DmHistoryLoadingCard(
                            "Concord",
                            "Concord",
                            loadingHistory,
                            historyStatus.exhausted,
                            historyStatus.relayCount,
                            historyStatus.stalledCount,
                            historyStatus.reachedBack,
                            historyStatus.relayProgress,
                            ::formatHistoryReachDate,
                        )
                    },
                    // Each relay's window-limit marker at its reached cursor (pure UI). Hidden when exhausted.
                    markersInGap =
                        if (limits.isEmpty()) {
                            null
                        } else {
                            { newer, older -> RelayReachMarkers(limits, newer, older) {} }
                        },
                    // Pulls each relay's next page while its marker is on screen, off viewport visibility.
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

            ConcordTypingIndicator(communityId, channelId, accountViewModel)

            if (channel.canPost()) {
                Spacer(modifier = DoubleVertSpacer)
                ConcordMessageComposer(
                    newMessageModel = newMessageModel,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onMessageSent = { feedViewModel.feedState.sendToTop() },
                )
            }
        }
    }
}

/** The number of messages a freshly-opened channel eagerly backfills to before paging goes demand-driven. */
private const val CONCORD_HISTORY_TARGET = 50

/**
 * On open, eagerly backfill this channel's older history until the feed holds at least
 * [CONCORD_HISTORY_TARGET] messages (or the relays are exhausted) — mirroring Armada's multi-page
 * `backfillStore`. The live subscription only carries each plane's relay-capped recent tail, shared
 * across one merged REQ per relay for every channel; so without this, a channel that has plenty of
 * history opens showing just its last few messages until the user scrolls. Once the target is reached,
 * paging is purely demand-driven by the markers' visibility. A short startup delay skips the transient
 * empty feed that navigation flashes through. Supersedes the old empty-only bootstrap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun ConcordBackfillHistoryToWindow(
    feedContentState: FeedContentState,
    history: ConcordChannelHistorySubAssembler,
) {
    LaunchedEffect(feedContentState, history) {
        delay(1200L)
        // Reactive count of currently-loaded messages (0 while empty/loading).
        val loadedCount =
            feedContentState.feedContent.flatMapLatest { state ->
                when (state) {
                    is FeedState.Loaded -> state.feed.map { it.list.size }
                    else -> flowOf(0)
                }
            }
        // Pull another page whenever we're below target, no page is in flight, and relays aren't done.
        // Each landed page grows the count (or flips exhausted), so this re-fires page-by-page and then
        // latches off. The !loading gate prevents overlapping REQs / a tight loop.
        combine(loadedCount, history.loadingMore, history.status) { count, loading, status ->
            count < CONCORD_HISTORY_TARGET && !loading && !status.exhausted
        }.distinctUntilChanged()
            .filter { it }
            .collect { history.advanceAll() }
    }
}

/** Republish a typing heartbeat at most this often (seconds) while composing. */
private const val TYPING_HEARTBEAT_SECS = 4L

/**
 * A slim "X is typing…" line above the composer, driven by the session's ephemeral
 * typing heartbeats (kind 23311). A ~2s ticker re-applies the freshness window so a
 * typist who stops silently fades out even without a new ingest.
 */
@Composable
private fun ConcordTypingIndicator(
    communityId: String,
    channelId: String,
    accountViewModel: AccountViewModel,
) {
    val session = remember(communityId) { accountViewModel.account.concordSessions.sessionFor(communityId) } ?: return
    val typingMap by session.typing.collectAsStateWithLifecycle()

    var nowSecs by remember { mutableLongStateOf(TimeUtils.now()) }
    LaunchedEffect(session) {
        while (true) {
            delay(2000L)
            nowSecs = TimeUtils.now()
        }
    }

    val typers =
        remember(typingMap, channelId, nowSecs) {
            (typingMap[channelId] ?: emptyMap())
                .filterValues { nowSecs - it <= ConcordCommunitySession.TYPING_STALE_SECS }
                .keys
                .sorted()
        }

    if (typers.isEmpty()) return

    val label =
        when (typers.size) {
            1 -> stringRes(R.string.concord_typing_one, rememberTypistName(typers[0], accountViewModel))
            2 ->
                stringRes(
                    R.string.concord_typing_two,
                    rememberTypistName(typers[0], accountViewModel),
                    rememberTypistName(typers[1], accountViewModel),
                )
            else -> stringRes(R.string.concord_typing_many)
        }

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.placeholderText,
            maxLines = 1,
        )
    }
}

/** Resolves [hex] to its best display name, reactively, falling back to a short hex. */
@Composable
private fun rememberTypistName(
    hex: String,
    accountViewModel: AccountViewModel,
): String {
    val user = remember(hex) { accountViewModel.checkGetOrCreateUser(hex) } ?: return remember(hex) { hex.take(8) }
    val info by observeUserInfo(user, accountViewModel)
    return info?.info?.bestName() ?: remember(user) { user.pubkeyDisplayHex() }
}

private fun reachState(p: RelayPagingProgress): RelayReachState =
    when {
        p.done -> RelayReachState.DONE
        p.stalled -> RelayReachState.STALLED
        else -> RelayReachState.REACHING
    }

private fun relayShortName(relay: NormalizedRelayUrl): String =
    relay.url
        .removePrefix("wss://")
        .removePrefix("ws://")
        .removeSuffix("/")

@Composable
private fun ConcordMessageComposer(
    newMessageModel: ConcordNewMessageViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    onMessageSent: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val canPost by remember { derivedStateOf { newMessageModel.canPost() } }
    val context = LocalContext.current

    // Throttle typing heartbeats to at most one every few seconds while the field is non-empty.
    val lastTypingSecs = remember(newMessageModel.channelId) { longArrayOf(0L) }

    DisposableEffect(newMessageModel.channelId) {
        onDispose { newMessageModel.userSuggestions?.reset() }
    }

    newMessageModel.replyTo.value?.let {
        DisplayReplyingToNote(it, accountViewModel, nav) { newMessageModel.clearReply() }
        ReplyModeToggle(
            mode = newMessageModel.replyMode.value,
            onToggle = { newMessageModel.toggleReplyMode() },
        )
    }

    Column(modifier = EditFieldModifier) {
        newMessageModel.userSuggestions?.let {
            ShowUserSuggestionList(
                it,
                newMessageModel::autocompleteWithUser,
                accountViewModel,
                SuggestionListDefaultHeightChat,
            )
        }

        ThinPaddingTextField(
            state = newMessageModel.message,
            onTextChanged = {
                newMessageModel.onMessageChanged()
                val community = newMessageModel.communityId
                val channel = newMessageModel.channelId
                val now = TimeUtils.now()
                if (community != null && channel != null &&
                    newMessageModel.message.text.isNotEmpty() &&
                    now - lastTypingSecs[0] >= TYPING_HEARTBEAT_SECS
                ) {
                    lastTypingSecs[0] = now
                    accountViewModel.sendConcordTyping(community, channel)
                }
            },
            inputTransformation = MentionPreservingInputTransformation,
            outputTransformation = UrlUserTagOutputTransformation(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
            shape = EditFieldBorder,
            placeholder = {
                Text(
                    text = stringRes(com.vitorpamplona.amethyst.R.string.reply_here),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            trailingIcon = {
                ThinSendButton(
                    isActive = canPost,
                    modifier = EditFieldTrailingIconModifier,
                ) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            newMessageModel.sendPost()
                            onMessageSent()
                        } catch (e: Exception) {
                            launch(Dispatchers.Main) {
                                Toast.makeText(context, "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            },
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
        )
    }
}
