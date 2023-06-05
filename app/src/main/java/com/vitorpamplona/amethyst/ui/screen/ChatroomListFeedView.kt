package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.ui.note.ChatroomCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ChatroomListFeedView(
    viewModel: FeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    markAsRead: MutableState<Boolean>
) {
    val feedState by viewModel.feedContent.collectAsStateWithLifecycle()

    var refreshing by remember { mutableStateOf(false) }
    val refresh = { refreshing = true; viewModel.invalidateData(); refreshing = false }
    val pullRefreshState = rememberPullRefreshState(refreshing, onRefresh = refresh)

    Box(Modifier.pullRefresh(pullRefreshState)) {
        Column() {
            Crossfade(
                targetState = feedState,
                animationSpec = tween(durationMillis = 100)
            ) { state ->
                when (state) {
                    is FeedState.Empty -> {
                        FeedEmpty {
                            refreshing = true
                        }
                    }

                    is FeedState.FeedError -> {
                        FeedError(state.errorMessage) {
                            refreshing = true
                        }
                    }

                    is FeedState.Loaded -> {
                        if (refreshing) {
                            refreshing = false
                        }
                        FeedLoaded(state, accountViewModel, nav, markAsRead)
                    }

                    FeedState.Loading -> {
                        LoadingFeed()
                    }
                }
            }
        }

        PullRefreshIndicator(refreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun FeedLoaded(
    state: FeedState.Loaded,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    markAsRead: MutableState<Boolean>
) {
    val listState = rememberLazyListState()

    LaunchedEffect(key1 = markAsRead.value) {
        if (markAsRead.value) {
            for (note in state.feed.value) {
                note.event?.let {
                    val channelHex = note.channelHex()
                    val route = if (channelHex != null) {
                        "Channel/$channelHex"
                    } else {
                        val roomUser = (note.event as? PrivateDmEvent)?.talkingWith(accountViewModel.account.userProfile().pubkeyHex)
                        "Room/$roomUser"
                    }

                    NotificationCache.markAsRead(route, it.createdAt())
                }
            }
            markAsRead.value = false
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            top = 10.dp,
            bottom = 10.dp
        ),
        state = listState
    ) {
        itemsIndexed(
            state.feed.value,
            key = { index, item -> if (index == 0) index else item.idHex }
        ) { _, item ->
            Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 75.dp)) {
                ChatroomCompose(
                    item,
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            }
        }
    }
}
