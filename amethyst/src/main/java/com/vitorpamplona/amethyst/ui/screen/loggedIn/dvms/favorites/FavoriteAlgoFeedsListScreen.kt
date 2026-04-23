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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms.favorites

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.elements.BannerImage
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms.observeAppDefinition
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.SimpleImage35Modifier
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.grayText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteAlgoFeedsListScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(
                caption = stringRes(R.string.favorite_dvms_title),
                popBack = nav::popBack,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 16.dp,
                        top = padding.calculateTopPadding(),
                        end = 16.dp,
                        bottom = padding.calculateBottomPadding(),
                    ).consumeWindowInsets(padding),
        ) {
            Text(
                text = stringRes(R.string.favorite_dvms_explainer),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
            )

            FavoriteAlgoFeedList(accountViewModel, nav)
        }
    }
}

@Composable
private fun FavoriteAlgoFeedList(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val favorites by accountViewModel.account.favoriteAlgoFeedsList.flowNotes
        .collectAsStateWithLifecycle()

    if (favorites.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringRes(R.string.favorite_dvms_empty),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = FeedPadding,
    ) {
        items(
            items = favorites,
            key = { it.address.toValue() },
        ) { feedNote ->
            FavoriteAlgoFeedRow(
                feedNote = feedNote,
                accountViewModel = accountViewModel,
                onOpen = { nav.nav(Route.ContentDiscovery(feedNote.idHex)) },
                onRemove = { accountViewModel.unfollowFavoriteAlgoFeed(feedNote.address) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteAlgoFeedRow(
    feedNote: AddressableNote,
    accountViewModel: AccountViewModel,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val card = observeAppDefinition(feedNote, accountViewModel)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        card.cover?.let { cover ->
            Box(contentAlignment = BottomStart) {
                MyAsyncImage(
                    imageUrl = cover,
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    mainImageModifier = Modifier,
                    loadedImageModifier = SimpleImage35Modifier,
                    accountViewModel = accountViewModel,
                    onLoadingBackground = {
                        feedNote.author?.let { author ->
                            BannerImage(author, SimpleImage35Modifier, accountViewModel)
                        }
                    },
                    onError = {
                        feedNote.author?.let { author ->
                            BannerImage(author, SimpleImage35Modifier, accountViewModel)
                        }
                    },
                )
            }
        } ?: run {
            feedNote.author?.let { author ->
                BannerImage(author, SimpleImage35Modifier, accountViewModel)
            }
        }

        Spacer(modifier = DoubleHorzSpacer)

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = card.name.ifBlank { feedNote.dTag() },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            card.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = StdVertSpacer)
                Text(
                    text = it,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.grayText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        IconButton(onClick = onRemove) {
            Icon(
                symbol = MaterialSymbols.Delete,
                contentDescription = stringRes(R.string.remove_dvm_from_favorites),
            )
        }
    }
}
