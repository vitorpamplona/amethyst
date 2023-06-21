package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.ui.note.ChatroomCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun ChatroomListFeedView(
    viewModel: FeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    markAsRead: MutableState<Boolean>
) {
    RefresheableView(viewModel, true) {
        CorssFadeState(viewModel, accountViewModel, nav, markAsRead)
    }
}

@Composable
private fun CorssFadeState(
    viewModel: FeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    markAsRead: MutableState<Boolean>
) {
    val feedState by viewModel.feedContent.collectAsStateWithLifecycle()
    Crossfade(
        targetState = feedState,
        animationSpec = tween(durationMillis = 100)
    ) { state ->
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
                FeedLoaded(state, accountViewModel, nav, markAsRead)
            }

            FeedState.Loading -> {
                LoadingFeed()
            }
        }
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

                    accountViewModel.account.markAsRead(route, it.createdAt())
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
            Row(Modifier.fillMaxWidth()) {
                ChatroomCompose(
                    item,
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            }
        }
    }
}
