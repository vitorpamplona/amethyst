/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.note.ChatroomMessageCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.HalfPadding
import com.vitorpamplona.quartz.events.DraftEvent

@Composable
fun RefreshingChatroomFeedView(
    viewModel: FeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    routeForLastRead: String,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    avoidDraft: String? = null,
    scrollStateKey: String? = null,
    enablePullRefresh: Boolean = true,
) {
    RefresheableBox(viewModel, enablePullRefresh) {
        SaveableFeedState(viewModel, scrollStateKey) { listState ->
            RenderChatroomFeedView(
                viewModel,
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
fun RenderChatroomFeedView(
    viewModel: FeedViewModel,
    accountViewModel: AccountViewModel,
    listState: LazyListState,
    nav: (String) -> Unit,
    routeForLastRead: String,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    avoidDraft: String? = null,
) {
    val feedState by viewModel.feedContent.collectAsStateWithLifecycle()

    Crossfade(targetState = feedState, animationSpec = tween(durationMillis = 100)) { state ->
        when (state) {
            is FeedState.Empty -> {
                FeedEmpty { viewModel.invalidateData() }
            }
            is FeedState.FeedError -> {
                FeedError(state.errorMessage) { viewModel.invalidateData() }
            }
            is FeedState.Loaded -> {
                ChatroomFeedLoaded(
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
            is FeedState.Loading -> {
                LoadingFeed()
            }
        }
    }
}

@Composable
fun ChatroomFeedLoaded(
    state: FeedState.Loaded,
    accountViewModel: AccountViewModel,
    listState: LazyListState,
    nav: (String) -> Unit,
    routeForLastRead: String,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    avoidDraft: String? = null,
) {
    LaunchedEffect(state.feed.value.firstOrNull()) {
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
        itemsIndexed(state.feed.value, key = { _, item -> item.idHex }) { _, item ->
            val noteEvent = item.event
            if (avoidDraft == null || noteEvent !is DraftEvent || noteEvent.dTag() != avoidDraft) {
                ChatroomMessageCompose(
                    baseNote = item,
                    routeForLastRead = routeForLastRead,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onWantsToReply = onWantsToReply,
                    onWantsToEditDraft = onWantsToEditDraft,
                )
            }
            NewSubject(item)
        }
    }
}

@Composable
fun NewSubject(note: Note) {
    val subject = remember(note) { note.event?.subject() }

    if (subject != null) {
        NewSubject(newSubject = subject)
    }
}

@Composable
fun NewSubject(newSubject: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
        )
        Text(
            text = newSubject,
            fontWeight = FontWeight.Bold,
            fontSize = Font14SP,
            modifier = HalfPadding,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
        )
    }
}
