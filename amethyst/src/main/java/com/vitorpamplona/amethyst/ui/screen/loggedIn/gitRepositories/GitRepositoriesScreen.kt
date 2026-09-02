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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepositories

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_repositories_search_no_results
import com.vitorpamplona.amethyst.commons.resources.git_repositories_search_placeholder
import com.vitorpamplona.amethyst.commons.search.GitRepositorySearchMatcher
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.ClearTextIcon
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.SearchIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepositories.datasource.GitRepositoriesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

@Composable
fun GitRepositoriesScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    GitRepositoriesScreen(
        gitRepositoriesFeedContentState = accountViewModel.feedStates.gitRepositoriesFeed,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun GitRepositoriesScreen(
    gitRepositoriesFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(gitRepositoriesFeedContentState)
    WatchAccountForGitRepositoriesScreen(gitRepositoriesFeedContentState = gitRepositoriesFeedContentState, accountViewModel = accountViewModel)
    GitRepositoriesFilterAssemblerSubscription(accountViewModel)

    // Search UI state is remembered across configuration changes so the
    // user doesn't lose their query when rotating; scoped to this screen,
    // not persisted to disk (unlike the follow-list filter above).
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            GitRepositoriesTopBar(
                isSearchOpen = isSearchOpen,
                onToggleSearch = {
                    // Closing collapses the field AND clears the query so
                    // the feed is fully restored — the icon acts as a
                    // one-tap "reset" once the user has narrowed the view.
                    if (isSearchOpen) {
                        searchQuery = ""
                        isSearchOpen = false
                    } else {
                        isSearchOpen = true
                    }
                },
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
        bottomBar = {
            AppBottomBar(Route.GitRepositories, nav, accountViewModel) { route ->
                if (route == Route.GitRepositories) {
                    gitRepositoriesFeedContentState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        Column(Modifier.fillMaxSize()) {
            if (isSearchOpen) {
                GitRepositorySearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClearQuery = { searchQuery = "" },
                )
                HorizontalDivider(thickness = DividerThickness)
            }
            RefresheableBox(gitRepositoriesFeedContentState, true) {
                SaveableFeedContentState(gitRepositoriesFeedContentState, scrollStateKey = ScrollStateKeys.GIT_REPOSITORIES_SCREEN) { listState ->
                    val query = searchQuery
                    if (query.isBlank()) {
                        RenderFeedContentState(
                            feedContentState = gitRepositoriesFeedContentState,
                            accountViewModel = accountViewModel,
                            listState = listState,
                            nav = nav,
                            routeForLastRead = "GitRepositoriesFeed",
                        )
                    } else {
                        // When the filter is active we can't reuse the shared
                        // scroll state because the filtered list has a different
                        // set of item keys — using the same LazyListState would
                        // make Compose try to restore an index that no longer
                        // exists and jump the user to an unrelated repo. We
                        // scope a fresh, per-query LazyListState so scrolling
                        // stays inside the filtered view.
                        RenderFilteredFeed(
                            feedContentState = gitRepositoriesFeedContentState,
                            query = query,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Inline text field that drives the client-side ngit-repository search. Sits
 * directly under the top bar and above the feed so the user can see the
 * result of every keystroke narrow the list beneath it.
 */
@Composable
private fun GitRepositorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Focus on first appearance so the keyboard opens without a second
        // tap. Subsequent recompositions inside the same session don't re-
        // request focus, which would fight with the user pressing "back to
        // the feed" via the field's clear-text icon.
        focusRequester.requestFocus()
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = {
                Text(
                    text = stringRes(Res.string.git_repositories_search_placeholder),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            leadingIcon = { SearchIcon(modifier = Size20Modifier, MaterialTheme.colorScheme.placeholderText) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClearQuery) {
                        ClearTextIcon()
                    }
                }
            },
            singleLine = true,
        )
    }
}

/**
 * Renders the ngit repositories the user is already subscribed to, filtered
 * by [query]. Loading and error states are delegated to the shared
 * [RenderFeedContentState] via the appropriate branches; the loaded branch
 * is intercepted so we can filter the notes without touching the shared
 * feed model (which other screens also observe).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RenderFilteredFeed(
    feedContentState: FeedContentState,
    query: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val filteredListState = rememberLazyListState()

    RenderFeedContentState(
        feedContentState = feedContentState,
        accountViewModel = accountViewModel,
        listState = filteredListState,
        nav = nav,
        routeForLastRead = "GitRepositoriesFeed",
        onLoaded = { loaded ->
            val loadedItems by loaded.feed.collectAsStateWithLifecycle()

            val filtered =
                remember(loadedItems, query) {
                    loadedItems.list.filter { note ->
                        val event = note.event as? GitRepositoryEvent ?: return@filter false
                        GitRepositorySearchMatcher.matches(event, query)
                    }
                }

            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                ) {
                    Text(
                        text = stringRes(Res.string.git_repositories_search_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = rememberFeedContentPadding(FeedPadding),
                    state = filteredListState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        filtered,
                        key = { _, item -> item.idHex },
                        contentType = { _, item -> item.event?.kind ?: -1 },
                    ) { _, item ->
                        Row(Modifier.fillMaxWidth().animateItem()) {
                            NoteCompose(
                                item,
                                modifier = Modifier.fillMaxWidth(),
                                routeForLastRead = "GitRepositoriesFeed",
                                isBoostedNote = false,
                                isHiddenFeed = loadedItems.showHidden,
                                quotesLeft = 3,
                                accountViewModel = accountViewModel,
                                nav = nav,
                            )
                        }
                        HorizontalDivider(thickness = DividerThickness)
                    }
                }
            }
        },
    )
}

@Composable
fun WatchAccountForGitRepositoriesScreen(
    gitRepositoriesFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveGitRepositoriesFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers =
        accountViewModel.account.hiddenUsers.flow
            .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        gitRepositoriesFeedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}
