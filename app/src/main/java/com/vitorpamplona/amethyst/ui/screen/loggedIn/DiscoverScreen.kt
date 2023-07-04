package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.service.NostrDiscoveryDataSource
import com.vitorpamplona.amethyst.ui.note.ChannelCardCompose
import com.vitorpamplona.amethyst.ui.screen.FeedEmpty
import com.vitorpamplona.amethyst.ui.screen.FeedError
import com.vitorpamplona.amethyst.ui.screen.FeedState
import com.vitorpamplona.amethyst.ui.screen.FeedViewModel
import com.vitorpamplona.amethyst.ui.screen.LoadingFeed
import com.vitorpamplona.amethyst.ui.screen.NostrDiscoverFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableView
import com.vitorpamplona.amethyst.ui.screen.SaveableFeedState
import com.vitorpamplona.amethyst.ui.screen.ScrollStateKeys

@Composable
fun DiscoverScreen(
    discoveryFeedViewModel: NostrDiscoverFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val lifeCycleOwner = LocalLifecycleOwner.current

    WatchAccountForDiscoveryScreen(discoveryViewModel = discoveryFeedViewModel, accountViewModel = accountViewModel)

    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("Discovery Start")
                NostrDiscoveryDataSource.start()
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                println("Discovery Stop")
                NostrDiscoveryDataSource.stop()
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            RefresheableView(discoveryFeedViewModel, true) {
                SaveableFeedState(discoveryFeedViewModel, scrollStateKey = ScrollStateKeys.DISCOVER_SCREEN) { listState ->
                    RenderDiscoverFeed(discoveryFeedViewModel, accountViewModel, listState, nav)
                }
            }
        }
    }
}

@Composable
private fun RenderDiscoverFeed(
    viewModel: FeedViewModel,
    accountViewModel: AccountViewModel,
    listState: LazyListState,
    nav: (String) -> Unit
) {
    val feedState by viewModel.feedContent.collectAsState()

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
                DiscoverFeedLoaded(
                    state,
                    listState,
                    accountViewModel,
                    nav
                )
            }

            is FeedState.Loading -> {
                LoadingFeed()
            }
        }
    }
}

@Composable
fun WatchAccountForDiscoveryScreen(discoveryViewModel: NostrDiscoverFeedViewModel, accountViewModel: AccountViewModel) {
    val accountState by accountViewModel.accountLiveData.observeAsState()

    LaunchedEffect(accountViewModel, accountState?.account?.defaultDiscoveryFollowList) {
        NostrDiscoveryDataSource.resetFilters()
        discoveryViewModel.checkKeysInvalidateDataAndSendToTop()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverFeedLoaded(
    state: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top = 10.dp,
            bottom = 10.dp
        ),
        state = listState
    ) {
        itemsIndexed(state.feed.value, key = { _, item -> item.idHex }) { _, item ->
            val defaultModifier = remember {
                Modifier.padding(10.dp).animateItemPlacement()
            }

            ChannelCardCompose(
                item,
                modifier = defaultModifier,
                accountViewModel = accountViewModel,
                nav = nav
            )
        }
    }
}
