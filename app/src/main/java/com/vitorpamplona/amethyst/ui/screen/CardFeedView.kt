package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.ui.note.BadgeCompose
import com.vitorpamplona.amethyst.ui.note.BoostSetCompose
import com.vitorpamplona.amethyst.ui.note.LikeSetCompose
import com.vitorpamplona.amethyst.ui.note.MessageSetCompose
import com.vitorpamplona.amethyst.ui.note.MultiSetCompose
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.ZapSetCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CardFeedView(viewModel: CardFeedViewModel, accountViewModel: AccountViewModel, navController: NavController, routeForLastRead: String) {
    val feedState by viewModel.feedContent.collectAsState()

    var refreshing by remember { mutableStateOf(false) }
    val refresh = { refreshing = true; viewModel.invalidateData(); refreshing = false }
    val pullRefreshState = rememberPullRefreshState(refreshing, onRefresh = refresh)

    Box(Modifier.pullRefresh(pullRefreshState)) {
        Column() {
            Crossfade(targetState = feedState, animationSpec = tween(durationMillis = 100)) { state ->
                when (state) {
                    is CardFeedState.Empty -> {
                        FeedEmpty {
                            refreshing = true
                        }
                    }
                    is CardFeedState.FeedError -> {
                        FeedError(state.errorMessage) {
                            refreshing = true
                        }
                    }
                    is CardFeedState.Loaded -> {
                        refreshing = false
                        FeedLoaded(
                            state,
                            accountViewModel,
                            navController,
                            routeForLastRead
                        )
                    }
                    CardFeedState.Loading -> {
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
    state: CardFeedState.Loaded,
    accountViewModel: AccountViewModel,
    navController: NavController,
    routeForLastRead: String
) {
    val listState = rememberLazyListState()

    LazyColumn(
        contentPadding = PaddingValues(
            top = 10.dp,
            bottom = 10.dp
        ),
        state = listState
    ) {
        itemsIndexed(state.feed.value, key = { _, item -> item.id() }) { _, item ->
            when (item) {
                is NoteCard -> NoteCompose(
                    item.note,
                    isBoostedNote = false,
                    accountViewModel = accountViewModel,
                    navController = navController,
                    routeForLastRead = routeForLastRead
                )
                is ZapSetCard -> ZapSetCompose(
                    item,
                    isInnerNote = false,
                    accountViewModel = accountViewModel,
                    navController = navController,
                    routeForLastRead = routeForLastRead
                )
                is LikeSetCard -> LikeSetCompose(
                    item,
                    isInnerNote = false,
                    accountViewModel = accountViewModel,
                    navController = navController,
                    routeForLastRead = routeForLastRead
                )
                is BoostSetCard -> BoostSetCompose(
                    item,
                    isInnerNote = false,
                    accountViewModel = accountViewModel,
                    navController = navController,
                    routeForLastRead = routeForLastRead
                )
                is MultiSetCard -> MultiSetCompose(
                    item,
                    accountViewModel = accountViewModel,
                    navController = navController,
                    routeForLastRead = routeForLastRead
                )
                is BadgeCard -> BadgeCompose(
                    item,
                    accountViewModel = accountViewModel,
                    navController = navController,
                    routeForLastRead = routeForLastRead
                )
                is MessageSetCard -> MessageSetCompose(
                    messageSetCard = item,
                    routeForLastRead = routeForLastRead,
                    accountViewModel = accountViewModel,
                    navController = navController
                )
            }
        }
    }
}
