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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.feeds.FeedError
import com.vitorpamplona.amethyst.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.buttons.CloseButton
import com.vitorpamplona.amethyst.ui.screen.UserFeedState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.HalfHorzPadding

@Composable
internal fun InvalidateOnBlockListChange(
    accountViewModel: AccountViewModel,
    invalidate: () -> Unit,
) {
    val transientSpammers by accountViewModel.account.hiddenUsers.transientHiddenUsers
        .collectAsStateWithLifecycle()
    val blockListState by accountViewModel.account.hiddenUsers.flow
        .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, transientSpammers, blockListState) {
        invalidate()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlockListTopBar(
    title: Int,
    selected: Set<*>,
    onCancel: () -> Unit,
    onUnblock: () -> Unit,
    nav: INav,
) {
    if (selected.isEmpty()) {
        TopBarWithBackButton(stringRes(id = title), nav)
    } else {
        ShorterTopAppBar(
            title = {
                Text(
                    text = stringRes(R.string.num_selected, selected.size),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            },
            navigationIcon = {
                CloseButton(modifier = HalfHorzPadding, onPress = onCancel)
            },
            actions = {
                Button(modifier = HalfHorzPadding, onClick = onUnblock) {
                    Text(text = stringRes(R.string.unblock))
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
        )
    }
}

@Composable
internal fun EmptyOrLoading(
    modifier: Modifier = Modifier,
    state: UserFeedState,
    emptyMessage: Int,
    onRefresh: () -> Unit,
) {
    when (state) {
        is UserFeedState.Empty -> EmptyState(modifier, emptyMessage)
        is UserFeedState.Loading -> LoadingFeed()
        is UserFeedState.FeedError -> FeedError(state.errorMessage, onRefresh)
        else -> Unit
    }
}

@Composable
internal fun EmptyState(
    modifier: Modifier = Modifier,
    message: Int,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            symbol = MaterialSymbols.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(48.dp).padding(bottom = 16.dp),
        )
        Text(
            text = stringRes(message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
