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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachCursor
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachDetailDialog
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachMarkers
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
import kotlinx.coroutines.delay

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
    // Whether THIS feed drives the shared account history pager. False for an off-screen split tab / second
    // pane, so only the feed the user is actually looking at pages the pager (see #2 in the audit).
    drivesPaging: Boolean = true,
) {
    val feedState by feedContent.feedContent.collectAsStateWithLifecycle()

    // A genuinely empty feed has no rows for the look-ahead buffer driver to measure, so step every relay
    // one page at a time to hunt for the first notifications. Once cards appear the buffer driver takes
    // over. Gated on Empty only (never the transient Loading navigation flashes through), and only for the
    // active feed so an off-screen tab doesn't hunt on the shared pager.
    val history = remember(accountViewModel) { accountViewModel.dataSources().account.notificationsHistory }
    if (drivesPaging) {
        BootstrapNotificationHistoryWhenEmpty(feedState is CardFeedState.Empty, history.loadingMore, history.status) { history.advanceAll() }
    }

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
                drivesPaging = drivesPaging,
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
    drivesPaging: Boolean = true,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()
    val openPolls by polls.flow.collectAsStateWithLifecycle()

    // Infinite-scroll backward pagination of the notifications history. All the driving (look-ahead buffer,
    // auto-retry, per-relay sentinels) lives in [rememberNotificationHistoryPaging]; here we just get back
    // the per-relay cursors to draw as frontier markers, and keep [history] for the detail dialog's retry.
    val history = remember(accountViewModel) { accountViewModel.dataSources().account.notificationsHistory }
    // Count of items above the notification cards in the LazyColumn (scaffold header + donation card + open
    // polls), so the hoisted sentinel can map a visible LazyColumn index back to a card.
    val leadingItemCount = (if (headerContent != null) 1 else 0) + 1 + openPolls.size
    val limits =
        rememberNotificationHistoryPaging(history, listState, drivesPaging) { index ->
            items.list.getOrNull(index - leadingItemCount)?.createdAt()
        }

    // The relays behind a tapped in-stream marker; non-null shows the per-relay breakdown popup.
    var syncDetail by remember { mutableStateOf<List<RelayReachCursor>?>(null) }
    syncDetail?.let { detail ->
        // Tap-through offers a Try Again on stalled relays, so a user who sees a bad relay can act on it.
        RelayReachDetailDialog(detail, ::formatHistoryReachDate, onRetry = { history.advanceAll() }) { syncDetail = null }
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

            // Per-relay markers in the gap toward the next-older card, at the depth each relay has paged to.
            // The bulk load is buffer-driven (above); these mark each relay's frontier and drive the
            // stalled-relay retry when scrolled into view (see the sentinel above). olderCreatedAt is null
            // past the oldest loaded card, so relays that reached the bottom sit there as "fully loaded".
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
