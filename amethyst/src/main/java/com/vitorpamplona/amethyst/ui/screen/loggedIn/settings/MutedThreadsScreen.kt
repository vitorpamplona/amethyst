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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.feeds.FeedError
import com.vitorpamplona.amethyst.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.dal.MutedThreadsFeedViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size15dp

@Composable
fun MutedThreadsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: MutedThreadsFeedViewModel =
        viewModel(factory = MutedThreadsFeedViewModel.Factory(accountViewModel.account))

    InvalidateOnBlockListChange(accountViewModel) { viewModel.invalidateData() }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(R.string.settings_muted_threads_title), nav) },
    ) { padding ->
        MutedThreadsList(
            modifier = Modifier.padding(padding),
            viewModel = viewModel,
            accountViewModel = accountViewModel,
        )
    }
}

@Composable
private fun MutedThreadsList(
    modifier: Modifier = Modifier,
    viewModel: MutedThreadsFeedViewModel,
    accountViewModel: AccountViewModel,
) {
    val feedState by viewModel.feedState.feedContent.collectAsStateWithLifecycle()

    when (val state = feedState) {
        is FeedState.Loaded -> {
            val items by state.feed.collectAsStateWithLifecycle()
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = rememberFeedContentPadding(FeedPadding),
                state = listState,
            ) {
                items(items.list, key = { it.idHex }) { note ->
                    MutedThreadRow(note = note, accountViewModel = accountViewModel)
                    HorizontalDivider(thickness = DividerThickness)
                }
            }
        }

        is FeedState.Empty -> {
            EmptyState(modifier, R.string.settings_muted_threads_empty)
        }

        is FeedState.Loading -> {
            LoadingFeed()
        }

        is FeedState.FeedError -> {
            FeedError(state.errorMessage) { viewModel.invalidateData() }
        }
    }
}

@Composable
private fun MutedThreadRow(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val event = note.event

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Size15dp, vertical = Size10dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (event == null) {
                Text(
                    text = stringRes(R.string.settings_muted_threads_unknown, note.idHex.take(12) + "…"),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                val authorName = note.author?.metadataOrNull()?.bestName()
                if (authorName != null) {
                    Text(
                        text = authorName,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text =
                        event.content
                            .lines()
                            .firstOrNull()
                            ?.trim() ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Button(
            modifier = Modifier.padding(start = 3.dp),
            onClick = { accountViewModel.unmuteThread(note) },
            shape = ButtonBorder,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            contentPadding = ButtonPadding,
        ) {
            Text(
                text = stringRes(R.string.action_unmute),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
