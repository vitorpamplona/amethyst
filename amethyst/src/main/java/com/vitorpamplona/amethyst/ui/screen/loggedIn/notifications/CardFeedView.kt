/**
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.logTime
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.feeds.FeedError
import com.vitorpamplona.amethyst.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.feeds.WatchScrollToTop
import com.vitorpamplona.amethyst.ui.feeds.rememberForeverLazyListState
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.BadgeCompose
import com.vitorpamplona.amethyst.ui.note.MessageSetCompose
import com.vitorpamplona.amethyst.ui.note.MultiSetCompose
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.ZapUserSetCompose
import com.vitorpamplona.amethyst.ui.note.elements.ZapTheDevsCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer

@Composable
fun RefreshableCardView(
    feedContent: CardFeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
    routeForLastRead: String,
    scrollStateKey: String? = null,
    enablePullRefresh: Boolean = true,
) {
    RefresheableBox(feedContent, enablePullRefresh) {
        SaveableCardFeedState(feedContent, accountViewModel, nav, routeForLastRead, scrollStateKey)
    }
}

@Composable
private fun SaveableCardFeedState(
    feedContent: CardFeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
    routeForLastRead: String,
    scrollStateKey: String? = null,
) {
    val listState =
        if (scrollStateKey != null) {
            rememberForeverLazyListState(scrollStateKey)
        } else {
            rememberLazyListState()
        }

    WatchScrollToTop(feedContent, listState)

    RenderCardFeed(feedContent, accountViewModel, listState, nav, routeForLastRead)
}

@Composable
fun RenderCardFeed(
    feedContent: CardFeedContentState,
    accountViewModel: AccountViewModel,
    listState: LazyListState,
    nav: INav,
    routeForLastRead: String,
) {
    val feedState by feedContent.feedContent.collectAsStateWithLifecycle()

    CrossfadeIfEnabled(
        modifier = Modifier.fillMaxSize(),
        targetState = feedState,
        animationSpec = tween(durationMillis = 100),
        accountViewModel = accountViewModel,
    ) { state ->
        when (state) {
            is CardFeedState.Empty -> {
                NotificationFeedEmpty(feedContent::invalidateData)
            }
            is CardFeedState.FeedError -> {
                FeedError(state.errorMessage, feedContent::invalidateData)
            }
            is CardFeedState.Loaded -> {
                FeedLoaded(
                    loaded = state,
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

@Composable
fun NotificationFeedEmpty(onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringRes(R.string.notification_feed_is_empty))
        Spacer(modifier = StdVertSpacer)
        OutlinedButton(onClick = onRefresh) { Text(text = stringRes(R.string.refresh)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedLoaded(
    loaded: CardFeedState.Loaded,
    listState: LazyListState,
    routeForLastRead: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = FeedPadding,
        state = listState,
    ) {
        item {
            ShowDonationCard(accountViewModel, nav)
        }

        itemsIndexed(
            items = items.list,
            key = { _, item -> item.id() },
            contentType = { _, item -> item.javaClass.simpleName },
        ) { _, item ->
            Row(Modifier.fillMaxWidth().animateItem()) {
                logTime(
                    debugMessage = { "CardFeedView $item" },
                ) {
                    RenderCardItem(
                        item,
                        routeForLastRead,
                        showHidden = items.showHidden,
                        accountViewModel,
                        nav,
                    )
                }
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
    nav: INav,
) {
    if (!accountViewModel.account.hasDonatedInThisVersion()) {
        val donated by accountViewModel.account.observeDonatedInThisVersion().collectAsStateWithLifecycle()
        if (!donated) {
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
}

@Composable
private fun RenderCardItem(
    item: Card,
    routeForLastRead: String,
    showHidden: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (item) {
        is NoteCard ->
            NoteCardCompose(
                item,
                routeForLastRead = routeForLastRead,
                isBoostedNote = false,
                showHidden = showHidden,
                accountViewModel = accountViewModel,
                nav = nav,
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
    modifier: Modifier = Modifier,
    routeForLastRead: String? = null,
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: Boolean = true,
    makeItShort: Boolean = false,
    showHidden: Boolean = false,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NoteCompose(
        baseNote = baseNote.note,
        modifier = modifier.fillMaxWidth(),
        routeForLastRead = routeForLastRead,
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
