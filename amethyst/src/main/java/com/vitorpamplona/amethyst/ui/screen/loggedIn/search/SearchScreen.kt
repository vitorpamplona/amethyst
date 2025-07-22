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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.searchCommand.TextSearchDataSourceSubscription
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.ClearTextIcon
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.SearchIcon
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.ephemChat.header.loadRelayInfo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.ChannelName
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.StdTopPadding
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.coroutines.FlowPreview

@Composable
fun SearchScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val searchBarViewModel: SearchBarViewModel =
        viewModel(
            key = "SearchBarViewModel",
            factory =
                SearchBarViewModel.Factory(
                    accountViewModel.account,
                ),
        )

    SearchScreen(searchBarViewModel, accountViewModel, nav)
}

@Composable
fun SearchScreen(
    searchBarViewModel: SearchBarViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(searchBarViewModel)

    val listState = rememberLazyListState()

    LaunchedEffect(searchBarViewModel.focusRequester) {
        searchBarViewModel.focusRequester.requestFocus()
    }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            SearchBar(searchBarViewModel, listState, accountViewModel, nav)
        },
        bottomBar = {
            AppBottomBar(Route.Search, accountViewModel) { route ->
                nav.newStack(route)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        Column(
            modifier = Modifier.padding(it).consumeWindowInsets(it),
        ) {
            ObserveRelayListForSearchAndDisplayIfNotFound(accountViewModel, nav)
            DisplaySearchResults(searchBarViewModel, listState, nav, accountViewModel)
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun SearchBar(
    searchBarViewModel: SearchBarViewModel,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    TextSearchDataSourceSubscription(searchBarViewModel, accountViewModel)

    LaunchedEffect(Unit) {
        LocalCache.live.newEventBundles.collect {
            if (searchBarViewModel.isSearchingFun()) {
                searchBarViewModel.invalidateData()
            }
        }
    }

    AnimateOnNewSearch(searchBarViewModel, listState)

    SearchTextField(searchBarViewModel, Modifier.statusBarsPadding())
}

@Composable
fun AnimateOnNewSearch(
    searchBarViewModel: SearchBarViewModel,
    listState: LazyListState,
) {
    val searchTerm by searchBarViewModel.searchTerm.collectAsStateWithLifecycle()

    LaunchedEffect(searchTerm) {
        listState.animateScrollToItem(0)
    }
}

@Composable
private fun SearchTextField(
    searchBarViewModel: SearchBarViewModel,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.padding(10.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = searchBarViewModel.searchValue,
            onValueChange = {
                searchBarViewModel.updateSearchValue(it)
            },
            shape = RoundedCornerShape(25.dp),
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            leadingIcon = { SearchIcon(modifier = Size20Modifier, MaterialTheme.colorScheme.placeholderText) },
            modifier =
                Modifier
                    .weight(1f, true)
                    .defaultMinSize(minHeight = 20.dp)
                    .focusRequester(searchBarViewModel.focusRequester),
            placeholder = {
                Text(
                    text = stringRes(R.string.npub_hex_username),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            trailingIcon = {
                if (searchBarViewModel.isRefreshing.value) {
                    IconButton(
                        onClick = {
                            searchBarViewModel.clear()
                        },
                    ) {
                        ClearTextIcon()
                    }
                }
            },
            singleLine = true,
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
        )
    }
}

@Composable
private fun DisplaySearchResults(
    searchBarViewModel: SearchBarViewModel,
    listState: LazyListState,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    if (!searchBarViewModel.isRefreshing.value) {
        return
    }

    val hashTags by searchBarViewModel.hashtagResults.collectAsStateWithLifecycle()
    val users by searchBarViewModel.searchResultsUsers.collectAsStateWithLifecycle()
    val publicChatChannels by searchBarViewModel.searchResultsPublicChatChannels.collectAsStateWithLifecycle()
    val ephemeralChannels by searchBarViewModel.searchResultsEphemeralChannels.collectAsStateWithLifecycle()
    val liveActivityChannels by searchBarViewModel.searchResultsLiveActivityChannels.collectAsStateWithLifecycle()
    val notes by searchBarViewModel.searchResultsNotes.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxHeight(),
        contentPadding = FeedPadding,
        state = listState,
    ) {
        itemsIndexed(
            hashTags,
            key = { _, item -> "#$item" },
        ) { _, item ->
            HashtagLine(item) { nav.nav(Route.Hashtag(item)) }

            HorizontalDivider(
                modifier = Modifier.padding(top = 10.dp),
                thickness = DividerThickness,
            )
        }

        itemsIndexed(
            users,
            key = { _, item -> "u" + item.pubkeyHex },
        ) { _, item ->
            UserCompose(item, accountViewModel = accountViewModel, nav = nav)

            HorizontalDivider(
                thickness = DividerThickness,
            )
        }

        itemsIndexed(
            publicChatChannels,
            key = { _, item -> "public" + item.idHex },
        ) { _, item ->
            ChannelName(
                channelIdHex = item.idHex,
                channelPicture = item.profilePicture(),
                channelTitle = {
                    Text(
                        item.toBestDisplayName(),
                        fontWeight = FontWeight.Bold,
                    )
                },
                channelLastTime = null,
                channelLastContent = item.summary(),
                hasNewMessages = false,
                loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
                loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
                onClick = { nav.nav(routeFor(item)) },
            )

            HorizontalDivider(
                modifier = StdTopPadding,
                thickness = DividerThickness,
            )
        }

        itemsIndexed(
            ephemeralChannels,
            key = { _, item -> "ephem" + item.roomId.toKey() },
        ) { _, item ->
            val relayInfo by loadRelayInfo(item.roomId.relayUrl, accountViewModel)

            ChannelName(
                channelIdHex = item.roomId.toKey(),
                channelPicture = relayInfo.icon,
                channelTitle = {
                    Text(
                        item.toBestDisplayName(),
                        fontWeight = FontWeight.Bold,
                    )
                },
                channelLastTime = null,
                channelLastContent = stringRes(R.string.ephemeral_relay_chat),
                hasNewMessages = false,
                loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
                loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
                onClick = { nav.nav(routeFor(item)) },
            )

            HorizontalDivider(
                modifier = StdTopPadding,
                thickness = DividerThickness,
            )
        }

        itemsIndexed(
            liveActivityChannels,
            key = { _, item -> "live" + item.address.toValue() },
        ) { _, item ->
            ChannelName(
                channelIdHex = item.address.toValue(),
                channelPicture = item.profilePicture(),
                channelTitle = {
                    Text(
                        item.toBestDisplayName(),
                        fontWeight = FontWeight.Bold,
                    )
                },
                channelLastTime = null,
                channelLastContent = item.summary(),
                hasNewMessages = false,
                loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
                loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
                onClick = { nav.nav(routeFor(item)) },
            )

            HorizontalDivider(
                modifier = StdTopPadding,
                thickness = DividerThickness,
            )
        }

        itemsIndexed(
            notes,
            key = { _, item -> "n" + item.idHex },
        ) { _, item ->
            NoteCompose(
                item,
                quotesLeft = 1,
                accountViewModel = accountViewModel,
                nav = nav,
            )

            HorizontalDivider(
                thickness = DividerThickness,
            )
        }
    }
}

@Composable
fun HashtagLine(
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(
                start = 12.dp,
                end = 12.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringRes(R.string.search_by_hashtag, tag),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
