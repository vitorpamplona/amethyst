package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.pullrefresh.PullRefreshIndicator
import androidx.compose.material3.pullrefresh.pullRefresh
import androidx.compose.material3.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.ui.note.BadgeCompose
import com.vitorpamplona.amethyst.ui.note.MessageSetCompose
import com.vitorpamplona.amethyst.ui.note.MultiSetCompose
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.ZapUserSetCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.FeedPadding

@Composable
fun RefresheableCardView(
    viewModel: CardFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    routeForLastRead: String,
    scrollStateKey: String? = null,
    enablePullRefresh: Boolean = true
) {
    var refreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing,
        onRefresh =
        {
            refreshing = true
            viewModel.invalidateData()
            refreshing = false
        }
    )

    val modifier = if (enablePullRefresh) {
        Modifier.pullRefresh(pullRefreshState)
    } else {
        Modifier
    }

    Box(modifier) {
        Column {
            SaveableCardFeedState(viewModel, accountViewModel, nav, routeForLastRead, scrollStateKey)
        }

        if (enablePullRefresh) {
            PullRefreshIndicator(refreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun SaveableCardFeedState(
    viewModel: CardFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    routeForLastRead: String,
    scrollStateKey: String? = null
) {
    val listState = if (scrollStateKey != null) {
        rememberForeverLazyListState(scrollStateKey)
    } else {
        rememberLazyListState()
    }

    WatchScrollToTop(viewModel, listState)

    RenderCardFeed(viewModel, accountViewModel, listState, nav, routeForLastRead)
}

@Composable
private fun WatchScrollToTop(
    viewModel: CardFeedViewModel,
    listState: LazyListState
) {
    val scrollToTop by viewModel.scrollToTop.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop) {
        if (scrollToTop > 0 && viewModel.scrolltoTopPending) {
            listState.scrollToItem(index = 0)
            viewModel.sentToTop()
        }
    }
}

@Composable
fun RenderCardFeed(
    viewModel: CardFeedViewModel,
    accountViewModel: AccountViewModel,
    listState: LazyListState,
    nav: (String) -> Unit,
    routeForLastRead: String
) {
    val feedState by viewModel.feedContent.collectAsStateWithLifecycle()

    Crossfade(
        modifier = Modifier.fillMaxSize(),
        targetState = feedState,
        animationSpec = tween(durationMillis = 100)
    ) { state ->
        when (state) {
            is CardFeedState.Empty -> {
                FeedEmpty {
                    viewModel.invalidateData()
                }
            }
            is CardFeedState.FeedError -> {
                FeedError(state.errorMessage) {
                    viewModel.invalidateData()
                }
            }
            is CardFeedState.Loaded -> {
                FeedLoaded(
                    state = state,
                    listState = listState,
                    routeForLastRead = routeForLastRead,
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            }
            CardFeedState.Loading -> {
                LoadingFeed()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedLoaded(
    state: CardFeedState.Loaded,
    listState: LazyListState,
    routeForLastRead: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = FeedPadding,
        state = listState
    ) {
        itemsIndexed(state.feed.value, key = { _, item -> item.id() }) { _, item ->
            val defaultModifier = remember {
                Modifier
                    .fillMaxWidth().animateItemPlacement()
            }

            Row(defaultModifier) {
                RenderCardItem(item, routeForLastRead, showHidden = state.showHidden.value, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun RenderCardItem(
    item: Card,
    routeForLastRead: String,
    showHidden: Boolean,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    when (item) {
        is NoteCard -> NoteCardCompose(
            item,
            isBoostedNote = false,
            accountViewModel = accountViewModel,
            showHidden = showHidden,
            nav = nav,
            routeForLastRead = routeForLastRead
        )

        is ZapUserSetCard -> ZapUserSetCompose(
            item,
            isInnerNote = false,
            accountViewModel = accountViewModel,
            nav = nav,
            routeForLastRead = routeForLastRead
        )

        is MultiSetCard -> MultiSetCompose(
            item,
            accountViewModel = accountViewModel,
            showHidden = showHidden,
            nav = nav,
            routeForLastRead = routeForLastRead
        )

        is BadgeCard -> BadgeCompose(
            item,
            accountViewModel = accountViewModel,
            showHidden = showHidden,
            nav = nav,
            routeForLastRead = routeForLastRead
        )

        is MessageSetCard -> MessageSetCompose(
            messageSetCard = item,
            routeForLastRead = routeForLastRead,
            showHidden = showHidden,
            accountViewModel = accountViewModel,
            nav = nav
        )
    }
}

@Composable
fun NoteCardCompose(
    baseNote: NoteCard,
    routeForLastRead: String? = null,
    modifier: Modifier = remember { Modifier },
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: Boolean = true,
    makeItShort: Boolean = false,
    addMarginTop: Boolean = true,
    showHidden: Boolean = false,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val note = remember(baseNote) {
        baseNote.note
    }

    NoteCompose(
        baseNote = note,
        routeForLastRead = routeForLastRead,
        modifier = modifier,
        isBoostedNote = isBoostedNote,
        isQuotedNote = isQuotedNote,
        unPackReply = unPackReply,
        makeItShort = makeItShort,
        addMarginTop = addMarginTop,
        showHidden = showHidden,
        parentBackgroundColor = parentBackgroundColor,
        accountViewModel = accountViewModel,
        nav = nav
    )
}
