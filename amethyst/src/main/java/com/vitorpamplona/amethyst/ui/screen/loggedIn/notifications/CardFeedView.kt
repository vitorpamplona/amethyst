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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.relayClient.paging.PagingStatus
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachCursor
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachDetailDialog
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachMarkers
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachState
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.notifications.Card
import com.vitorpamplona.amethyst.commons.ui.notifications.CardFeedState
import com.vitorpamplona.amethyst.logTime
import com.vitorpamplona.amethyst.ui.feeds.FeedError
import com.vitorpamplona.amethyst.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.ui.feeds.StickToTopOnPrepend
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.BadgeCompose
import com.vitorpamplona.amethyst.ui.note.CloseIcon
import com.vitorpamplona.amethyst.ui.note.MessageSetCompose
import com.vitorpamplona.amethyst.ui.note.MultiSetCompose
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.NutzapUserSetCompose
import com.vitorpamplona.amethyst.ui.note.ZapUserSetCompose
import com.vitorpamplona.amethyst.ui.note.types.ReplyRenderType
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.formatHistoryReachDate
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.donations.ShowDonationCard
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayPagingProgress
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun RenderCardFeed(
    feedContent: CardFeedContentState,
    pollContent: OpenPollsState,
    accountViewModel: AccountViewModel,
    listState: LazyListState,
    nav: INav,
    routeForLastRead: String,
    scrollToEventId: String? = null,
    headerContent: (@Composable () -> Unit)? = null,
) {
    val feedState by feedContent.feedContent.collectAsStateWithLifecycle()

    // A genuinely empty feed has no rows for the look-ahead buffer driver to measure, so step every relay
    // one page at a time to hunt for the first notifications. Once cards appear the buffer driver takes
    // over. Gated on Empty only (never the transient Loading navigation flashes through).
    val history = remember(accountViewModel) { accountViewModel.dataSources().account.notificationsHistory }
    BootstrapNotificationHistoryWhenEmpty(feedState is CardFeedState.Empty, history.loadingMore, history.status) { history.advanceAll() }

    // Direct switch instead of CrossfadeIfEnabled: the crossfade's `currentlyVisible`
    // accumulator can leave a previous `Loaded` instance composed alongside the new
    // one when refreshes (e.g. double-tap on the Notifications tab) bounce the
    // state through Empty/Loading and back inside the animation window. Two
    // LazyColumns then share the same listState and only the top one scrolls.
    when (val state = feedState) {
        is CardFeedState.Empty -> {
            NotificationFeedEmpty(feedContent::invalidateData)
        }

        is CardFeedState.FeedError -> {
            FeedError(state.errorMessage, feedContent::invalidateData)
        }

        is CardFeedState.Loaded -> {
            FeedLoaded(
                loaded = state,
                polls = pollContent,
                listState = listState,
                routeForLastRead = routeForLastRead,
                accountViewModel = accountViewModel,
                nav = nav,
                scrollToEventId = scrollToEventId,
                headerContent = headerContent,
            )
        }

        CardFeedState.Loading -> {
            LoadingFeed()
        }
    }
}

@Composable
fun NotificationFeedEmpty(onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringRes(R.string.notification_feed_is_empty))
        Spacer(modifier = StdVertSpacer)
        OutlinedButton(onClick = onRefresh) { Text(text = stringRes(R.string.refresh)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedLoaded(
    loaded: CardFeedState.Loaded,
    polls: OpenPollsState,
    listState: LazyListState,
    routeForLastRead: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    scrollToEventId: String? = null,
    headerContent: (@Composable () -> Unit)? = null,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()
    val openPolls by polls.flow.collectAsStateWithLifecycle()

    // Infinite-scroll backward pagination: the notifications feed keeps a fat look-ahead buffer of older
    // notifications loaded below the viewport. When fewer than [NOTIFICATION_LOOKAHEAD_BUFFER] rows remain
    // ahead of the last visible one, every not-done relay is stepped one older page (until+limit, gap-proof)
    // — refilling eagerly and repeating until the buffer is full again or all relays run dry. The per-relay
    // [BackwardRelayPager] engine is the same one the DM history uses; only the trigger differs (buffer depth
    // here vs. marker visibility there).
    val history = remember(accountViewModel) { accountViewModel.dataSources().account.notificationsHistory }
    val historyStatus by history.status.collectAsStateWithLifecycle()

    // Keep a big runway of already-loaded rows below the fold so the user effectively never reaches the end.
    val exhausted = historyStatus.exhausted
    val loadingMore by history.loadingMore.collectAsStateWithLifecycle()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems > 0 && lastVisibleIndex >= totalItems - NOTIFICATION_LOOKAHEAD_BUFFER
        }
    }
    // Re-evaluated when the buffer runs low, a page settles (loadingMore falls), or paging exhausts — so a
    // single page that doesn't refill the whole buffer keeps pulling the next until it does or relays run dry.
    LaunchedEffect(shouldLoadMore, loadingMore, exhausted) {
        if (shouldLoadMore && !loadingMore && !exhausted) history.advanceAll()
    }

    // One cursor per relay: its reached depth, state (reaching / stalled / done) and the advance() that pulls
    // its next page (also usable to retry a stalled relay by tapping). A done relay's marker sinks to the
    // oldest end reading "fully loaded".
    val limits =
        remember(historyStatus) {
            historyStatus.relayProgress.map { (relay, p) ->
                RelayReachCursor(relay.url, relayShortName(relay), p.reachedUntil, reachState(p)) { history.advance(relay) }
            }
        }

    // The relays behind a tapped in-stream marker; non-null shows the per-relay breakdown popup.
    var syncDetail by remember { mutableStateOf<List<RelayReachCursor>?>(null) }
    syncDetail?.let { detail ->
        RelayReachDetailDialog(detail, ::formatHistoryReachDate) { syncDetail = null }
    }

    StickToTopOnPrepend(listState, items.list.firstOrNull()?.id())

    // Track which card is highlighted (will auto-clear after animation)
    var highlightedCardId by remember { mutableStateOf<String?>(null) }

    // Scroll to the card containing the target event ID
    if (scrollToEventId != null) {
        LaunchedEffect(scrollToEventId, items) {
            val position = items.list.indexOfFirst { it.containsEventId(scrollToEventId) }
            if (position >= 0) {
                // +1 offset for the donation card header item
                val scrollIndex = position + 1 + openPolls.size
                listState.animateScrollToItem(scrollIndex)
                highlightedCardId = items.list[position].id()
                delay(2000)
                highlightedCardId = null
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        if (headerContent != null) {
            item(key = "scaffold-header") { headerContent() }
        }
        item {
            ShowDonationCard(accountViewModel, nav)
        }

        if (openPolls.isNotEmpty()) {
            itemsIndexed(
                items = openPolls,
                key = { _, item -> "open-poll-${item.idHex}" },
                contentType = { _, _ -> "OpenPoll" },
            ) { _, note ->
                Row(modifier = Modifier.padding(start = Size10dp, end = Size10dp, bottom = Size10dp)) {
                    Card(
                        modifier = MaterialTheme.colorScheme.imageModifier,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OpenPollsSectionHeader()
                            IconButton(
                                modifier = Modifier.padding(end = Size10dp),
                                onClick = { accountViewModel.dismissPollNotification(note.idHex) },
                            ) {
                                CloseIcon()
                            }
                        }
                        Row(Modifier.fillMaxWidth().animateItem()) {
                            NoteCompose(
                                baseNote = note,
                                modifier = Modifier.fillMaxWidth(),
                                routeForLastRead = routeForLastRead,
                                isBoostedNote = false,
                                isQuotedNote = false,
                                quotesLeft = 3,
                                accountViewModel = accountViewModel,
                                nav = nav,
                            )
                        }
                        HorizontalDivider(
                            thickness = DividerThickness,
                        )
                    }
                }
            }
        }

        itemsIndexed(
            items = items.list,
            key = { _, item -> item.id() },
            contentType = { _, item -> item.javaClass.simpleName },
        ) { index, item ->
            val isHighlighted = highlightedCardId == item.id()
            val highlightColor by animateColorAsState(
                targetValue = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                animationSpec = tween(durationMillis = if (isHighlighted) 300 else 1000),
                label = "highlightAnimation",
            )

            Row(Modifier.fillMaxWidth().background(highlightColor).animateItem()) {
                logTime(
                    debugMessage = { "CardFeedView $item" },
                ) {
                    RenderCardItem(
                        item,
                        routeForLastRead,
                        showHidden = items.showHidden,
                        accountViewModel,
                        nav,
                    )
                }
            }
            HorizontalDivider(
                thickness = DividerThickness,
            )

            // Per-relay progress markers in the gap toward the next-older card, at the depth each relay has
            // paged to (loading is driven by the look-ahead buffer above, not by these). olderCreatedAt is
            // null past the oldest loaded card, so relays that reached the bottom sit there as "fully loaded".
            if (limits.isNotEmpty()) {
                RelayReachMarkers(
                    limits,
                    item.createdAt(),
                    items.list.getOrNull(index + 1)?.createdAt(),
                ) { syncDetail = it }
            }
        }
    }
}

/**
 * Bootstraps notification history while the feed is genuinely empty: steps every relay one page at a
 * time, gated on its own loader, until notifications appear or every relay exhausts. Once cards load this
 * stops and the look-ahead buffer driver takes over, keeping older pages loaded ahead of the viewport.
 *
 * Leads with a debounce so the brief Empty/Loading flash navigation passes through does NOT trigger a
 * hunt; if [active] drops before it elapses (cards loaded) the effect cancels and nothing pages.
 */
@Composable
private fun BootstrapNotificationHistoryWhenEmpty(
    active: Boolean,
    loadingMore: StateFlow<Boolean>,
    status: StateFlow<PagingStatus>,
    advanceAll: () -> Unit,
) {
    LaunchedEffect(active, loadingMore, status) {
        if (!active) return@LaunchedEffect
        delay(BOOTSTRAP_DEBOUNCE_MS)
        combine(loadingMore, status) { loading, s -> !loading && !s.exhausted }
            .distinctUntilChanged()
            .filter { it }
            .collect { advanceAll() }
    }
}

// Ignore the transient empty feed that navigation flashes through before notifications re-appear.
private const val BOOTSTRAP_DEBOUNCE_MS = 1200L

// How many already-loaded rows to keep below the last visible one before pulling the next older page.
// Large on purpose: the feed reads as infinite scroll, the user practically never reaches the bottom.
private const val NOTIFICATION_LOOKAHEAD_BUFFER = 100

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

@Composable
private fun RenderCardItem(
    item: Card,
    routeForLastRead: String,
    showHidden: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (item) {
        is NoteCard -> {
            NoteCardCompose(
                item,
                routeForLastRead = routeForLastRead,
                isBoostedNote = false,
                showHidden = showHidden,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        is ZapUserSetCard -> {
            ZapUserSetCompose(
                item,
                isInnerNote = false,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = routeForLastRead,
            )
        }

        is NutzapUserSetCard -> {
            NutzapUserSetCompose(
                item,
                isInnerNote = false,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = routeForLastRead,
            )
        }

        is MultiSetCard -> {
            MultiSetCompose(
                item,
                accountViewModel = accountViewModel,
                showHidden = showHidden,
                nav = nav,
                routeForLastRead = routeForLastRead,
            )
        }

        is BadgeCard -> {
            BadgeCompose(
                item,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = routeForLastRead,
            )
        }

        is MessageSetCard -> {
            MessageSetCompose(
                messageSetCard = item,
                routeForLastRead = routeForLastRead,
                showHidden = showHidden,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun NoteCardCompose(
    baseNote: NoteCard,
    modifier: Modifier = Modifier,
    routeForLastRead: String? = null,
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: ReplyRenderType = ReplyRenderType.FULL,
    makeItShort: Boolean = false,
    showHidden: Boolean = false,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NoteCompose(
        baseNote = baseNote.note,
        modifier = modifier.fillMaxWidth(),
        routeForLastRead = routeForLastRead,
        isBoostedNote = isBoostedNote,
        isQuotedNote = isQuotedNote,
        unPackReply = unPackReply,
        makeItShort = makeItShort,
        isHiddenFeed = showHidden,
        quotesLeft = 3,
        parentBackgroundColor = parentBackgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}
