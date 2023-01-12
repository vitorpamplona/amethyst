package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.ExperimentalLifecycleComposeApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun FeedView(viewModel: FeedViewModel, accountViewModel: AccountViewModel, navController: NavController) {
    val feedState by viewModel.feedContent.collectAsStateWithLifecycle()

    var isRefreshing by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    val listState = rememberLazyListState()

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
            Crossfade(targetState = feedState) { state ->
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
                        LazyColumn(
                            contentPadding = PaddingValues(
                                top = 10.dp,
                                bottom = 10.dp
                            ),
                            state = listState
                        ) {
                            itemsIndexed(state.feed, key = { _, item -> item.idHex }) { index, item ->
                                NoteCompose(item, isInnerNote = false, accountViewModel = accountViewModel, navController = navController)
                            }
                        }
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
fun LoadingFeed() {
    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Loading feed")
    }
}

@Composable
fun FeedError(errorMessage: String, onRefresh: () -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Error loading replies: $errorMessage")
        Button(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            onClick = onRefresh
        ) {
            Text(text = "Try again")
        }
    }
}

@Composable
fun FeedEmpty(onRefresh: () -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Feed is empty.")
        OutlinedButton(onClick = onRefresh) {
            Text(text = "Refresh")
        }
    }
}


// Bosted code to be deleted:

/*

                                Boosted By: removed because it was ugly

                                if (item.event is RepostEvent) {
                                    Row(
                                        modifier = Modifier.padding(
                                            start = 12.dp,
                                            end = 12.dp,
                                            bottom = 8.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_retweet),
                                            null,
                                            modifier = Modifier.size(20.dp),
                                            tint = Color.Gray
                                        )
                                        Text(
                                            text = "Boosted by ${item.author.toBestDisplayName()}",
                                            modifier = Modifier.padding(start = 10.dp),
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray,
                                        )
                                    }
                                    val refNote = item.replyTo.firstOrNull()
                                    if (refNote != null) {
                                        NoteCompose(index, refNote)
                                    } else {
                                        Row(
                                            modifier = Modifier.padding(
                                                start = 40.dp,
                                                end = 40.dp,
                                                bottom = 25.dp,
                                                top = 15.dp
                                            ),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Could not find referenced event",
                                                modifier = Modifier.padding(30.dp),
                                                color = Color.Gray,
                                            )
                                        }
                                    }
                                } else {
                                    NoteCompose(index, item)
                                }*/