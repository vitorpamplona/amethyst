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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.ui.feeds.FeedError
import com.vitorpamplona.amethyst.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.creators.draftTags.DraftTagState
import com.vitorpamplona.amethyst.ui.screen.SaveableFeedState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.watchChatGroupPosition
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import kotlinx.coroutines.launch

@Composable
fun RefreshingChatroomFeedView(
    feedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
    routeForLastRead: String,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    avoidDraft: DraftTagState? = null,
    scrollStateKey: String? = null,
    // Opt-in hook handed the feed's scroll state, so a specific screen (e.g. private DMs) can
    // attach scroll-driven loading. No-op for the public-chat / channel callers that don't paginate.
    listStateObserver: @Composable (LazyListState) -> Unit = {},
    // Optional footer rendered at the oldest end of the thread (a "load more" / spinner affordance).
    // Null for callers that load their whole history at once (public chats / channels).
    olderBoundary: (@Composable () -> Unit)? = null,
    // Optional per-gap hook: invoked between each message and its next-older neighbour with their
    // createdAt bounds, so a caller (private DMs) can draw per-relay paging markers at the depth each
    // relay has reached. No-op for callers without per-relay progress.
    markersInGap: (@Composable (newerCreatedAt: Long?, olderCreatedAt: Long?) -> Unit)? = null,
    // Optional hoisted load driver: handed the loaded message list and its scroll state once (above the
    // LazyColumn), so a caller (private DMs) can drive demand-driven paging off viewport visibility
    // rather than per-row composition. No-op for callers that don't paginate.
    sentinels: (@Composable (items: List<Note>, listState: LazyListState) -> Unit)? = null,
    // Optional external jump request (e.g. a "pinned message" bar): when it holds a note id, the feed
    // scrolls to that message and highlights it, then calls [onJumpHandled] to clear it. Null for
    // callers with no external jump affordance.
    jumpToNoteId: State<String?>? = null,
    onJumpHandled: () -> Unit = {},
    onWantsToEditBuzz: ((Note) -> Unit)? = null,
) {
    SaveableFeedState(feedContentState, scrollStateKey) { listState ->
        listStateObserver(listState)
        RenderChatFeedView(
            feedContentState,
            accountViewModel,
            listState,
            nav,
            routeForLastRead,
            onWantsToReply,
            onWantsToEditDraft,
            avoidDraft,
            olderBoundary,
            markersInGap,
            sentinels,
            jumpToNoteId,
            onJumpHandled,
            onWantsToEditBuzz,
        )
    }
}

@Composable
fun RenderChatFeedView(
    feed: FeedContentState,
    accountViewModel: AccountViewModel,
    listState: LazyListState,
    nav: INav,
    routeForLastRead: String,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    avoidDraft: DraftTagState? = null,
    olderBoundary: (@Composable () -> Unit)? = null,
    markersInGap: (@Composable (newerCreatedAt: Long?, olderCreatedAt: Long?) -> Unit)? = null,
    sentinels: (@Composable (items: List<Note>, listState: LazyListState) -> Unit)? = null,
    jumpToNoteId: State<String?>? = null,
    onJumpHandled: () -> Unit = {},
    onWantsToEditBuzz: ((Note) -> Unit)? = null,
) {
    val feedState by feed.feedContent.collectAsStateWithLifecycle()

    CrossfadeIfEnabled(targetState = feedState, animationSpec = tween(durationMillis = 100), accountViewModel = accountViewModel) { state ->
        when (state) {
            is FeedState.Loading -> {
                LoadingFeed()
            }

            is FeedState.Empty -> {
                FeedEmpty { feed.invalidateData() }
            }

            is FeedState.FeedError -> {
                FeedError(state.errorMessage) { feed.invalidateData() }
            }

            is FeedState.Loaded -> {
                ChatFeedLoaded(
                    state,
                    accountViewModel,
                    listState,
                    nav,
                    routeForLastRead,
                    onWantsToReply,
                    onWantsToEditDraft,
                    avoidDraft,
                    olderBoundary,
                    markersInGap,
                    sentinels,
                    jumpToNoteId,
                    onJumpHandled,
                    onWantsToEditBuzz,
                )
            }
        }
    }
}

@Composable
fun ChatFeedLoaded(
    loaded: FeedState.Loaded,
    accountViewModel: AccountViewModel,
    listState: LazyListState,
    nav: INav,
    routeForLastRead: String,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    avoidDraft: DraftTagState? = null,
    olderBoundary: (@Composable () -> Unit)? = null,
    markersInGap: (@Composable (newerCreatedAt: Long?, olderCreatedAt: Long?) -> Unit)? = null,
    sentinels: (@Composable (items: List<Note>, listState: LazyListState) -> Unit)? = null,
    jumpToNoteId: State<String?>? = null,
    onJumpHandled: () -> Unit = {},
    onWantsToEditBuzz: ((Note) -> Unit)? = null,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    // Hoisted load driver (above the LazyColumn): pages each relay off viewport visibility, so feed
    // reorders no longer re-fire paging. The per-gap markers below are pure UI.
    sentinels?.invoke(items.list, listState)

    LaunchedEffect(items.list.firstOrNull()) {
        if (listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    val scope = rememberCoroutineScope()
    val highlightedNoteId = remember { mutableStateOf<String?>(null) }
    val onScrollToNote: (Note) -> Unit = { note ->
        val index = items.list.indexOfFirst { it.idHex == note.idHex }
        if (index >= 0) {
            scope.launch {
                listState.animateScrollToItem(index)
                highlightedNoteId.value = note.idHex
            }
        }
    }

    // External jump request (pinned-message bar). Keyed on the id alone, so a message arriving mid-jump
    // can't cancel the scroll animation or restart the effect. Always clears the request after one
    // attempt — even when the target isn't loaded — so it never sticks and a repeat tap fires again.
    val jumpId = jumpToNoteId?.value
    LaunchedEffect(jumpId) {
        if (jumpId != null) {
            val index = items.list.indexOfFirst { it.idHex == jumpId }
            if (index >= 0) {
                listState.animateScrollToItem(index)
                highlightedNoteId.value = jumpId
            }
            onJumpHandled()
        }
    }

    LazyColumn(
        contentPadding = FeedPadding,
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        state = listState,
    ) {
        itemsIndexed(items.list, key = { _, item -> item.idHex }, contentType = { _, item -> item.event?.kind ?: -1 }) { index, item ->
            val noteEvent = item.event
            if (avoidDraft == null || noteEvent !is DraftWrapEvent || noteEvent.dTag() !in avoidDraft.usedDraftTags) {
                // Reverse layout: index - 1 is the newer message (visually below),
                // index + 1 the older one (visually above).
                val newer = items.list.getOrNull(index - 1)
                val older = items.list.getOrNull(index + 1)

                // Send/arrival motion: new items fade in and existing ones slide to
                // make room, so a sent message enters instead of appearing.
                val itemModifier =
                    if (accountViewModel.settings.isPerformanceMode()) {
                        Modifier
                    } else {
                        Modifier.animateItem()
                    }

                Column(modifier = itemModifier) {
                    // A day/subject header belongs ABOVE the message it introduces. `reverseLayout`
                    // flips the order of the lazy list's items, but NOT the content inside one item:
                    // this Column still lays out top-to-bottom, so the divisor must be composed
                    // before the bubble. Composing it after put the header below its own message —
                    // i.e. visually heading the NEXT (newer) message while showing this one's date,
                    // which is why a "Jul 1, 2025" header sat on top of a Sep 23 bubble.
                    NewDateOrSubjectDivisor(older, item)

                    // Per-relay paging markers for the gap toward the next-older message. Older items sit
                    // ABOVE newer ones under `reverseLayout`, so that gap is the space above this bubble —
                    // which means these belong before it, for the same reason the divisor does. Composed
                    // after the bubble they rendered in the gap toward the NEWER message, contradicting the
                    // bounds they are handed.
                    markersInGap?.invoke(
                        item.event?.createdAt,
                        older?.event?.createdAt,
                    )

                    ChatroomMessageCompose(
                        baseNote = item,
                        routeForLastRead = routeForLastRead,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        onWantsToReply = onWantsToReply,
                        onWantsToEditDraft = onWantsToEditDraft,
                        onScrollToNote = onScrollToNote,
                        shouldHighlight = highlightedNoteId.value == item.idHex,
                        onHighlightFinished = { highlightedNoteId.value = null },
                        groupPosition = watchChatGroupPosition(newer, item, older),
                        previousNoteId = older?.idHex,
                        onWantsToEditBuzz = onWantsToEditBuzz,
                    )
                }
            }
        }

        // Reverse layout: a trailing item sits at the highest index, i.e. the visual TOP (oldest end).
        // That's where the caller's "load more" affordance / spinner lives.
        if (olderBoundary != null) {
            item(key = "olderBoundary") { olderBoundary() }
        }
    }
}
