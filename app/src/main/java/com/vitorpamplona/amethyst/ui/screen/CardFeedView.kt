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
package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.note.BadgeCompose
import com.vitorpamplona.amethyst.ui.note.MessageSetCompose
import com.vitorpamplona.amethyst.ui.note.MultiSetCompose
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.ZapTheDevsCard
import com.vitorpamplona.amethyst.ui.note.ZapUserSetCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding

@Composable
fun RefreshableCardView(
    viewModel: CardFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    routeForLastRead: String,
    scrollStateKey: String? = null,
    enablePullRefresh: Boolean = true,
) {
    RefresheableBox(viewModel, enablePullRefresh) {
        SaveableCardFeedState(viewModel, accountViewModel, nav, routeForLastRead, scrollStateKey)
    }
}

@Composable
private fun SaveableCardFeedState(
    viewModel: CardFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    routeForLastRead: String,
    scrollStateKey: String? = null,
) {
    val listState =
        if (scrollStateKey != null) {
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
    listState: LazyListState,
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
    routeForLastRead: String,
) {
    val feedState by viewModel.feedContent.collectAsStateWithLifecycle()

    Crossfade(
        modifier = Modifier.fillMaxSize(),
        targetState = feedState,
        animationSpec = tween(durationMillis = 100),
    ) { state ->
        when (state) {
            is CardFeedState.Empty -> {
                FeedEmpty { viewModel.invalidateData() }
            }
            is CardFeedState.FeedError -> {
                FeedError(state.errorMessage) { viewModel.invalidateData() }
            }
            is CardFeedState.Loaded -> {
                FeedLoaded(
                    state = state,
                    listState = listState,
                    routeForLastRead = routeForLastRead,
                    accountViewModel = accountViewModel,
                    nav = nav,
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
    nav: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = FeedPadding,
        state = listState,
    ) {
        item {
            ShowDonationCard(accountViewModel, nav)
        }

        itemsIndexed(
            items = state.feed.value,
            key = { _, item -> item.id() },
            contentType = { _, item -> item.javaClass.simpleName },
        ) { _, item ->
            val defaultModifier = remember { Modifier.fillMaxWidth().animateItemPlacement() }

            Row(defaultModifier) {
                RenderCardItem(
                    item,
                    routeForLastRead,
                    showHidden = state.showHidden.value,
                    accountViewModel,
                    nav,
                )
            }
            HorizontalDivider(
                thickness = DividerThickness,
            )
        }
    }
}

@Composable
private fun ShowDonationCard(
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val account by accountViewModel.account.live.observeAsState()
    if (account?.account?.hasDonatedInThisVersion() == false) {
        LoadNote(
            BuildConfig.RELEASE_NOTES_ID,
            accountViewModel,
        ) { loadedNoteId ->
            if (loadedNoteId != null) {
                ZapTheDevsCard(
                    loadedNoteId,
                    accountViewModel,
                    nav,
                )
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
    nav: (String) -> Unit,
) {
    when (item) {
        is NoteCard ->
            NoteCardCompose(
                item,
                isBoostedNote = false,
                accountViewModel = accountViewModel,
                showHidden = showHidden,
                nav = nav,
                routeForLastRead = routeForLastRead,
            )
        is ZapUserSetCard ->
            ZapUserSetCompose(
                item,
                isInnerNote = false,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = routeForLastRead,
            )
        is MultiSetCard ->
            MultiSetCompose(
                item,
                accountViewModel = accountViewModel,
                showHidden = showHidden,
                nav = nav,
                routeForLastRead = routeForLastRead,
            )
        is BadgeCard ->
            BadgeCompose(
                item,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = routeForLastRead,
            )
        is MessageSetCard ->
            MessageSetCompose(
                messageSetCard = item,
                routeForLastRead = routeForLastRead,
                showHidden = showHidden,
                accountViewModel = accountViewModel,
                nav = nav,
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
    showHidden: Boolean = false,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val note = remember(baseNote) { baseNote.note }

    NoteCompose(
        baseNote = note,
        routeForLastRead = routeForLastRead,
        modifier = modifier,
        isBoostedNote = isBoostedNote,
        isQuotedNote = isQuotedNote,
        unPackReply = unPackReply,
        makeItShort = makeItShort,
        isHiddenFeed = showHidden,
        quotesLeft = 3,
        parentBackgroundColor = parentBackgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}
