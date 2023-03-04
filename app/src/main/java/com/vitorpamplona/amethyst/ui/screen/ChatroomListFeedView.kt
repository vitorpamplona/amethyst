package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.ui.note.ChatroomCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun ChatroomListFeedView(
    viewModel: FeedViewModel,
    accountViewModel: AccountViewModel,
    navController: NavController,
    markAsRead: MutableState<Boolean>
) {
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
            Crossfade(
                targetState = feedState,
                animationSpec = tween(durationMillis = 100)
            ) { state ->
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
                        FeedLoaded(state, accountViewModel, navController, markAsRead)
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
    navController: NavController,
    markAsRead: MutableState<Boolean>,
) {
    val listState = rememberLazyListState()

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return
    val notificationCacheState = NotificationCache.live.observeAsState()
    val notificationCache = notificationCacheState.value ?: return
    val context = LocalContext.current.applicationContext

    if (markAsRead.value) {
        LaunchedEffect(key1 = notificationCache) {
            for (note in state.feed.value) {
                note.event?.let {
                    var route = ""
                    val channel = note.channel()

                    if (channel != null) {
                        route = "Channel/${channel.idHex}"
                    } else {
                        val replyAuthorBase = note.mentions?.first()
                        var userToComposeOn = note.author!!
                        if (replyAuthorBase != null) {
                            if (note.author == account.userProfile()) {
                                userToComposeOn = replyAuthorBase
                            }
                        }
                        route = "Room/${userToComposeOn.pubkeyHex}"
                    }

                    notificationCache.cache.markAsRead(route, it.createdAt, context)
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
            key = { index, item -> if (index == 0) index else item.idHex }) { index, item ->
            ChatroomCompose(
                item,
                accountViewModel = accountViewModel,
                navController = navController
            )
        }
    }
}
