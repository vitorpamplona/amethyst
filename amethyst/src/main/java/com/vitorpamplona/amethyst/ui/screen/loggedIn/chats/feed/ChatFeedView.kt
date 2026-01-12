/**
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent

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
) {
    SaveableFeedState(feedContentState, scrollStateKey) { listState ->
        RenderChatFeedView(
            feedContentState,
            accountViewModel,
            listState,
            nav,
            routeForLastRead,
            onWantsToReply,
            onWantsToEditDraft,
            avoidDraft,
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
) {
    val feedState by feed.feedContent.collectAsStateWithLifecycle()

    CrossfadeIfEnabled(targetState = feedState, animationSpec = tween(durationMillis = 100), accountViewModel = accountViewModel) { state ->
        when (state) {
            is FeedState.Loading -> LoadingFeed()
            is FeedState.Empty -> FeedEmpty { feed.invalidateData() }
            is FeedState.FeedError -> FeedError(state.errorMessage) { feed.invalidateData() }
            is FeedState.Loaded ->
                ChatFeedLoaded(
                    state,
                    accountViewModel,
                    listState,
                    nav,
                    routeForLastRead,
                    onWantsToReply,
                    onWantsToEditDraft,
                    avoidDraft,
                )
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
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    LaunchedEffect(items.list.firstOrNull()) {
        if (listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        contentPadding = FeedPadding,
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        state = listState,
    ) {
        itemsIndexed(items.list, key = { _, item -> item.idHex }) { index, item ->
            val noteEvent = item.event
            if (avoidDraft == null || noteEvent !is DraftWrapEvent || noteEvent.dTag() !in avoidDraft.usedDraftTags) {
                ChatroomMessageCompose(
                    baseNote = item,
                    routeForLastRead = routeForLastRead,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onWantsToReply = onWantsToReply,
                    onWantsToEditDraft = onWantsToEditDraft,
                )

                NewDateOrSubjectDivisor(items.list.getOrNull(index + 1), item)
            }
        }
    }
}
