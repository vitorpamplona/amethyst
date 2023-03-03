package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.note.ChatroomCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun ChatroomListFeedView(viewModel: FeedViewModel, accountViewModel: AccountViewModel, navController: NavController) {
    val feedState by viewModel.feedContent.collectAsStateWithLifecycle()

    var isRefreshing by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.refresh()
            isRefreshing = false
        }
    }

    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = {
            isRefreshing = true
        },
    ) {
        Column() {
            Crossfade(targetState = feedState, animationSpec = tween(durationMillis = 100)) { state ->
                when (state) {
                    is FeedState.Empty -> {
                        FeedEmpty {
                            isRefreshing = true
                        }
                    }
                    is FeedState.FeedError -> {
                        FeedError(state.errorMessage) {
                            isRefreshing = true
                        }
                    }
                    is FeedState.Loaded -> {
                        FeedLoaded(state, accountViewModel, navController)
                    }
                    FeedState.Loading -> {
                        LoadingFeed()
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedLoaded(
    state: FeedState.Loaded,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val listState = rememberLazyListState()

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account

    val context = LocalContext.current.applicationContext

    fun markAllAsRead() {
        state.feed.value.forEach { note ->
            val routeForLastRead = if (note.event == null) {
                return@forEach
            } else if (note.channel != null) {
                note.channel?.let {
                    "Channel/${it.idHex}"
                }

            } else {
                account?.let {
                    val replyAuthorBase = note.mentions?.first()

                    var userToComposeOn = note.author!!

                    if (replyAuthorBase != null) {
                        if (note.author == account.userProfile()) {
                            userToComposeOn = replyAuthorBase
                        }
                    }

                    "Room/${userToComposeOn.pubkeyHex}"
                }
            }

            routeForLastRead?.let {
                val createdAt = note.event?.createdAt
                if (createdAt != null) {
                    NotificationCache.markAsRead(it, createdAt, context)
                }
            }
        }
    }


    Column {
        TextButton(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            onClick = { markAllAsRead() }
        ) {
            Text(stringResource(R.string.mark_all_as_read))
        }

        LazyColumn(
            contentPadding = PaddingValues(
                top = 10.dp,
                bottom = 10.dp
            ),
            state = listState
        ) {
            itemsIndexed(state.feed.value, key = { index, item -> if (index == 0) index else item.idHex }) { index, item ->
                ChatroomCompose(
                    item,
                    accountViewModel = accountViewModel,
                    navController = navController
                )
            }
        }
    }

}
