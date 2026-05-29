/*
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.ui.feeds.FeedError
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.screen.SaveableFeedState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.dal.OnePodcastFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource.OnePodcastFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent

@Composable
fun PodcastScreen(
    pubkey: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val podcast = remember(pubkey) { LocalCache.checkGetOrCreateUser(pubkey) } ?: return
    val metadataNote =
        remember(pubkey) {
            LocalCache.getOrCreateAddressableNote(PodcastMetadataEvent.createAddress(pubkey))
        }

    val feedViewModel: OnePodcastFeedViewModel =
        viewModel(
            key = pubkey + "OnePodcastFeedViewModel",
            factory = OnePodcastFeedViewModel.Factory(pubkey, accountViewModel.account),
        )

    PodcastScreen(podcast, metadataNote, feedViewModel, accountViewModel, nav)
}

@Composable
fun PodcastScreen(
    podcast: User,
    metadataNote: Note,
    feedViewModel: OnePodcastFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedViewModel)
    // Fetches both the show metadata (kind 10154) and every episode (kind 54) authored by
    // this podcast's key from its outbox relays.
    OnePodcastFilterAssemblerSubscription(podcast, accountViewModel)

    val metadataEvent by observeNoteEvent<PodcastMetadataEvent>(metadataNote, accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {
                    Text(
                        text = metadataEvent?.title() ?: stringRes(R.string.route_podcasts),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                },
                popBack = nav::popBack,
            )
        },
        accountViewModel = accountViewModel,
    ) {
        RefresheableBox(feedViewModel, true) {
            SaveableFeedState(feedViewModel.feedState, scrollStateKey = null) { listState ->
                PodcastScreenBody(
                    metadataNote = metadataNote,
                    metadataEvent = metadataEvent,
                    feedViewModel = feedViewModel,
                    listState = listState,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
private fun PodcastScreenBody(
    metadataNote: Note,
    metadataEvent: PodcastMetadataEvent?,
    feedViewModel: OnePodcastFeedViewModel,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedState by feedViewModel.feedState.feedContent.collectAsStateWithLifecycle()

    when (val state = feedState) {
        is FeedState.Loaded ->
            PodcastEpisodesList(metadataNote, metadataEvent, state, listState, accountViewModel, nav)

        is FeedState.Empty ->
            PodcastHeaderWithStatus(metadataNote, metadataEvent, listState, accountViewModel) {
                StatusText(stringRes(R.string.podcast_no_episodes))
            }

        is FeedState.FeedError ->
            PodcastHeaderWithStatus(metadataNote, metadataEvent, listState, accountViewModel) {
                FeedError(state.errorMessage) { feedViewModel.invalidateData() }
            }

        is FeedState.Loading ->
            PodcastHeaderWithStatus(metadataNote, metadataEvent, listState, accountViewModel) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
    }
}

@Composable
private fun PodcastEpisodesList(
    metadataNote: Note,
    metadataEvent: PodcastMetadataEvent?,
    loaded: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    LazyColumn(
        state = listState,
        contentPadding = rememberFeedContentPadding(FeedPadding),
    ) {
        item("header") {
            PodcastHeader(metadataNote, metadataEvent, items.list.size, accountViewModel)
        }

        itemsIndexed(
            items.list,
            key = { _, item -> item.idHex },
            contentType = { _, _ -> "episode" },
        ) { index, episode ->
            PodcastEpisodeListItem(episode, accountViewModel, nav)

            if (index < items.list.lastIndex) {
                HorizontalDivider(thickness = DividerThickness)
            }
        }
    }
}

@Composable
private fun PodcastHeaderWithStatus(
    metadataNote: Note,
    metadataEvent: PodcastMetadataEvent?,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    status: @Composable () -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = rememberFeedContentPadding(FeedPadding),
    ) {
        item("header") {
            PodcastHeader(metadataNote, metadataEvent, null, accountViewModel)
        }
        item("status") { status() }
    }
}

@Composable
private fun StatusText(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.grayText,
            textAlign = TextAlign.Center,
        )
    }
}
