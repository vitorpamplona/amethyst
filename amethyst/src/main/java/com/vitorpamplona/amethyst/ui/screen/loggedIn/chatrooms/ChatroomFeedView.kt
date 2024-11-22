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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chatrooms

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
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.ui.feeds.FeedError
import com.vitorpamplona.amethyst.ui.feeds.FeedState
import com.vitorpamplona.amethyst.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.dateFormatter
import com.vitorpamplona.amethyst.ui.screen.FeedViewModel
import com.vitorpamplona.amethyst.ui.screen.SaveableFeedState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.HalfPadding
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.quartz.events.DraftEvent

@Composable
fun RefreshingChatroomFeedView(
    viewModel: FeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
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
    nav: INav,
    routeForLastRead: String,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    avoidDraft: String? = null,
) {
    val feedState by viewModel.feedState.feedContent.collectAsStateWithLifecycle()

    CrossfadeIfEnabled(targetState = feedState, animationSpec = tween(durationMillis = 100), accountViewModel = accountViewModel) { state ->
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
    loaded: FeedState.Loaded,
    accountViewModel: AccountViewModel,
    listState: LazyListState,
    nav: INav,
    routeForLastRead: String,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    avoidDraft: String? = null,
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
            if (avoidDraft == null || noteEvent !is DraftEvent || noteEvent.dTag() != avoidDraft) {
                ChatroomMessageCompose(
                    baseNote = item,
                    routeForLastRead = routeForLastRead,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onWantsToReply = onWantsToReply,
                    onWantsToEditDraft = onWantsToEditDraft,
                )

                NewDateSubject(items.list.getOrNull(index + 1), item)
            }
        }
    }
}

@Composable
fun NewDateSubject(
    previous: Note?,
    note: Note,
) {
    if (previous == null) return

    val never = stringRes(R.string.never)
    val today = stringRes(R.string.today)

    val prevDate = remember(previous) { dateFormatter(previous.event?.createdAt(), never, today) }
    val date = remember(note) { dateFormatter(note.event?.createdAt(), never, today) }

    val subject = remember(note) { note.event?.subject() }

    println("AABBCC $prevDate $date")

    if (prevDate != date) {
        if (subject != null) {
            ChatDivisor("$date - $subject")
        } else {
            ChatDivisor(date)
        }
    } else {
        if (subject != null) {
            ChatDivisor(subject)
        }
    }
}

@Composable
fun ChatDivisor(info: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = StdPadding) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = DividerThickness,
        )
        Text(
            text = info,
            fontWeight = FontWeight.Bold,
            fontSize = Font14SP,
            modifier = HalfPadding,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = DividerThickness,
        )
    }
}
