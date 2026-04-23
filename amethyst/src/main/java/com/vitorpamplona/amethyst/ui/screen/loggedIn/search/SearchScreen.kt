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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.search.SearchScope
import com.vitorpamplona.amethyst.commons.search.SearchSortOrder
import com.vitorpamplona.amethyst.commons.search.SearchSource
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.service.relayClient.searchCommand.TextSearchDataSourceSubscription
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.ClearTextIcon
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.SearchIcon
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.ChannelName
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.BasicRelaySetupInfoClickableRow
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.StdTopPadding
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

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
                    accountViewModel.nip05ClientBuilder(),
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

    LaunchedEffect(searchBarViewModel.focusRequester) {
        if (searchBarViewModel.listState.firstVisibleItemIndex == 0) {
            searchBarViewModel.focusRequester.requestFocus()
        }
    }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            SearchBar(searchBarViewModel, accountViewModel, nav)
        },
        bottomBar = {
            AppBottomBar(Route.Search, accountViewModel) { route ->
                nav.navBottomBar(route)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        DisplaySearchResults(
            searchBarViewModel = searchBarViewModel,
            headerContent = { ObserveRelayListForSearchAndDisplayIfNotFound(accountViewModel, nav) },
            nav = nav,
            accountViewModel = accountViewModel,
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun SearchBar(
    searchBarViewModel: SearchBarViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    TextSearchDataSourceSubscription(searchBarViewModel, accountViewModel)

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect {
                if (searchBarViewModel.isSearchingFun()) {
                    searchBarViewModel.invalidateData()
                }
            }
        }

        launch(Dispatchers.IO) {
            LocalCache.live.deletedEventBundles.collect {
                if (searchBarViewModel.isSearchingFun()) {
                    searchBarViewModel.invalidateData()
                }
            }
        }

        // bech32 auto-resolve: navigate on hit without displaying results
        launch {
            searchBarViewModel.directEventResolver.filterNotNull().collect { route ->
                nav.nav(route)
                searchBarViewModel.clear()
            }
        }
    }

    Column(modifier = Modifier.statusBarsPadding()) {
        SearchTextField(searchBarViewModel, Modifier)
        SearchFilterRow(searchBarViewModel)
    }
}

// Must match SearchBarViewModel.source's initial value.
private val DEFAULT_SOURCE = SearchSource.RELAYS

private fun hasNonDefaultFilters(
    scope: SearchScope,
    source: SearchSource,
    followsOnly: Boolean,
    sort: SearchSortOrder,
): Boolean {
    if (source != DEFAULT_SOURCE) return true
    if (followsOnly) return true
    if (scope != SearchScope.PEOPLE && sort != SearchSortOrder.EVENT_DEFAULT) return true
    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("AssignedValueIsNeverRead")
private fun SearchFilterRow(searchBarViewModel: SearchBarViewModel) {
    val currentScope by searchBarViewModel.scope.collectAsStateWithLifecycle()
    val currentSource by searchBarViewModel.source.collectAsStateWithLifecycle()
    val currentFollowsOnly by searchBarViewModel.followsOnly.collectAsStateWithLifecycle()
    val currentSort by searchBarViewModel.sortOrder.collectAsStateWithLifecycle()

    var sheetOpen by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            val scopes = listOf(SearchScope.ALL, SearchScope.PEOPLE, SearchScope.NOTES)
            scopes.forEachIndexed { index, s ->
                SegmentedButton(
                    selected = currentScope == s,
                    onClick = { searchBarViewModel.updateScope(s) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = scopes.size),
                ) {
                    Text(
                        text =
                            when (s) {
                                SearchScope.ALL -> stringRes(R.string.search_scope_all)
                                SearchScope.PEOPLE -> stringRes(R.string.search_scope_people)
                                SearchScope.NOTES -> stringRes(R.string.search_scope_notes)
                            },
                    )
                }
            }
        }

        FilterIconButton(
            hasBadge =
                hasNonDefaultFilters(
                    scope = currentScope,
                    source = currentSource,
                    followsOnly = currentFollowsOnly,
                    sort = currentSort,
                ),
            onClick = { sheetOpen = true },
        )
    }

    if (sheetOpen) {
        SearchFiltersSheet(
            scope = currentScope,
            source = currentSource,
            followsOnly = currentFollowsOnly,
            sort = currentSort,
            onSourceChange = searchBarViewModel::updateSource,
            onFollowsOnlyChange = searchBarViewModel::updateFollowsOnly,
            onSortChange = searchBarViewModel::updateSortOrder,
            onReset = {
                searchBarViewModel.updateSource(DEFAULT_SOURCE)
                searchBarViewModel.updateFollowsOnly(false)
                searchBarViewModel.updateSortOrder(SearchSortOrder.EVENT_DEFAULT)
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
private fun FilterIconButton(
    hasBadge: Boolean,
    onClick: () -> Unit,
) {
    Box {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = stringRes(R.string.search_filters_open),
            )
        }
        if (hasBadge) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-10).dp, y = 10.dp)
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFiltersSheet(
    scope: SearchScope,
    source: SearchSource,
    followsOnly: Boolean,
    sort: SearchSortOrder,
    onSourceChange: (SearchSource) -> Unit,
    onFollowsOnlyChange: (Boolean) -> Unit,
    onSortChange: (SearchSortOrder) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringRes(R.string.search_filters_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringRes(R.string.search_filters_section_source),
                    style = MaterialTheme.typography.labelLarge,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val sources = listOf(SearchSource.LOCAL, SearchSource.RELAYS)
                    sources.forEachIndexed { index, s ->
                        SegmentedButton(
                            selected = source == s,
                            onClick = { onSourceChange(s) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = sources.size),
                        ) {
                            Text(
                                text =
                                    when (s) {
                                        SearchSource.LOCAL -> stringRes(R.string.search_source_local)
                                        SearchSource.RELAYS -> stringRes(R.string.search_source_relays)
                                    },
                            )
                        }
                    }
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onFollowsOnlyChange(!followsOnly) }
                        .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(R.string.search_follows_only),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = followsOnly,
                    onCheckedChange = onFollowsOnlyChange,
                )
            }

            if (scope != SearchScope.PEOPLE) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringRes(R.string.search_filters_section_sort),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    SearchSortOrder.EVENT_OPTIONS.forEach { opt ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = sort == opt,
                                        onClick = { onSortChange(opt) },
                                    ).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = sort == opt,
                                onClick = { onSortChange(opt) },
                            )
                            Text(
                                text = sortLabel(opt),
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            TextButton(onClick = onReset) {
                Text(stringRes(R.string.search_filters_reset))
            }
        }
    }
}

@Composable
private fun sortLabel(opt: SearchSortOrder): String =
    when (opt) {
        SearchSortOrder.NEWEST -> stringRes(R.string.search_sort_newest)
        SearchSortOrder.RELEVANCE -> stringRes(R.string.search_sort_relevance)
        SearchSortOrder.POPULAR -> stringRes(R.string.search_sort_popular)
        SearchSortOrder.OLDEST -> stringRes(R.string.search_sort_oldest)
        SearchSortOrder.NAME_AZ, SearchSortOrder.NAME_ZA -> opt.label
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
    headerContent: @Composable () -> Unit,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    val isRefreshing by searchBarViewModel.isRefreshing
    val hashTags by searchBarViewModel.hashtagResults.collectAsStateWithLifecycle()
    val relays by searchBarViewModel.relayResults.collectAsStateWithLifecycle()
    val users by searchBarViewModel.searchResultsUsers.collectAsStateWithLifecycle()
    val publicChatChannels by searchBarViewModel.searchResultsPublicChatChannels.collectAsStateWithLifecycle()
    val ephemeralChannels by searchBarViewModel.searchResultsEphemeralChannels.collectAsStateWithLifecycle()
    val liveActivityChannels by searchBarViewModel.searchResultsLiveActivityChannels.collectAsStateWithLifecycle()
    val notes by searchBarViewModel.searchResultsNotes.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxHeight(),
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = searchBarViewModel.listState,
    ) {
        item(key = "scaffold-header") { headerContent() }

        if (!isRefreshing) return@LazyColumn

        itemsIndexed(
            hashTags,
            key = { _, item -> "#$item" },
        ) { _, item ->
            HashtagLine(item.lowercase()) { nav.nav(Route.Hashtag(item.lowercase())) }

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
            relays,
            key = { _, item -> "relay${item.relay.url}" },
        ) { _, relayInfo ->
            BasicRelaySetupInfoClickableRow(
                item = relayInfo,
                loadProfilePicture = accountViewModel.settings.showProfilePictures(),
                loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
                onClick = { nav.nav(Route.RelayInfo(relayInfo.relay.url)) },
                onDelete = null,
                nip11CachedRetriever = Amethyst.instance.nip11Cache,
                modifier = Modifier.padding(vertical = 5.dp, horizontal = 10.dp),
                accountViewModel = accountViewModel,
                nav = nav,
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
                loadProfilePicture = accountViewModel.settings.showProfilePictures(),
                loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
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
            val relayInfo by loadRelayInfo(item.roomId.relayUrl)

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
                loadProfilePicture = accountViewModel.settings.showProfilePictures(),
                loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
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
                loadProfilePicture = accountViewModel.settings.showProfilePictures(),
                loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
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
