package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.note.ChatroomMessageCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.HalfPadding

@Composable
fun RefreshingChatroomFeedView(
    viewModel: FeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    routeForLastRead: String,
    onWantsToReply: (Note) -> Unit,
    scrollStateKey: String? = null,
    enablePullRefresh: Boolean = true
) {
    RefresheableView(viewModel, enablePullRefresh) {
        SaveableFeedState(viewModel, scrollStateKey) { listState ->
            RenderChatroomFeedView(viewModel, accountViewModel, listState, nav, routeForLastRead, onWantsToReply)
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
    onWantsToReply: (Note) -> Unit
) {
    val feedState by viewModel.feedContent.collectAsStateWithLifecycle()

    Crossfade(targetState = feedState, animationSpec = tween(durationMillis = 100)) { state ->
        when (state) {
            is FeedState.Empty -> {
                FeedEmpty {
                    viewModel.invalidateData()
                }
            }
            is FeedState.FeedError -> {
                FeedError(state.errorMessage) {
                    viewModel.invalidateData()
                }
            }
            is FeedState.Loaded -> {
                ChatroomFeedLoaded(state, accountViewModel, listState, nav, routeForLastRead, onWantsToReply)
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
    onWantsToReply: (Note) -> Unit
) {
    LaunchedEffect(state.feed.value.firstOrNull()) {
        if (listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            top = 10.dp,
            bottom = 10.dp
        ),
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        state = listState
    ) {
        itemsIndexed(state.feed.value, key = { _, item -> item.idHex }) { _, item ->
            ChatroomMessageCompose(
                baseNote = item,
                routeForLastRead = routeForLastRead,
                accountViewModel = accountViewModel,
                nav = nav,
                onWantsToReply = onWantsToReply
            )
            NewSubject(item)
        }
    }
}

@Composable
fun NewSubject(note: Note) {
    val subject = remember(note) {
        note.event?.subject()
    }

    if (subject != null) {
        NewSubject(newSubject = subject)
    }
}

@Composable
fun NewSubject(newSubject: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Divider(
            modifier = Modifier.weight(1f)
        )
        Text(
            text = newSubject,
            fontWeight = FontWeight.Bold,
            fontSize = Font14SP,
            modifier = HalfPadding
        )
        Divider(
            modifier = Modifier.weight(1f)
        )
    }
}
