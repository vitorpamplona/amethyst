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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.select_list_to_filter
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.FeedFilterSpinner
import com.vitorpamplona.amethyst.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarNavigationIcon
import com.vitorpamplona.amethyst.ui.note.SearchIcon
import com.vitorpamplona.amethyst.ui.screen.FeedDefinition
import com.vitorpamplona.amethyst.ui.screen.TopNavFilterState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size22Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText

/**
 * Top bar for the ngit repositories discovery screen.
 *
 * Two search affordances live side-by-side in the actions row:
 *
 *  1. A **repository filter** (magnifier-with-a-plus icon) that toggles
 *     an inline text field over the loaded feed. This is the ngit-specific
 *     search — it matches the fields NIP-34 announcements carry: name,
 *     identifier, description, hashtags, clone/web/relay URLs, and
 *     maintainer pubkeys. It filters what the user is already looking
 *     at without touching relays.
 *
 *  2. The **generic Nostr search** (plain magnifier) that navigates to
 *     the global [Route.Search] screen, matching the affordance on
 *     every other top-level screen.
 *
 * Splitting them this way makes it obvious which magnifier does what: the
 * inline one narrows the current list, the outbound one opens the fleet-
 * wide search that also queries people, notes, hashtags, etc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitRepositoriesTopBar(
    isSearchOpen: Boolean,
    onToggleSearch: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ShorterTopAppBar(
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val list by accountViewModel.account.settings.defaultGitRepositoriesFollowList
                    .collectAsStateWithLifecycle()

                GitRepositoriesTopNavFilterBar(
                    followListsModel = accountViewModel.feedStates.feedListOptions,
                    listName = list,
                    accountViewModel = accountViewModel,
                    onChange = accountViewModel.account.settings::changeDefaultGitRepositoriesFollowList,
                )
            }
        },
        navigationIcon = { TopBarNavigationIcon(accountViewModel, nav) },
        actions = {
            IconButton(onClick = onToggleSearch) {
                Icon(
                    symbol =
                        if (isSearchOpen) {
                            MaterialSymbols.Close
                        } else {
                            MaterialSymbols.FilterAlt
                        },
                    contentDescription =
                        stringRes(
                            if (isSearchOpen) {
                                R.string.git_repositories_search_close
                            } else {
                                R.string.git_repositories_search_open
                            },
                        ),
                )
            }
            IconButton(onClick = { nav.nav(Route.Search) }) {
                SearchIcon(modifier = Size22Modifier, MaterialTheme.colorScheme.placeholderText)
            }
        },
    )
}

@Composable
private fun GitRepositoriesTopNavFilterBar(
    followListsModel: TopNavFilterState,
    listName: TopFilter,
    accountViewModel: AccountViewModel,
    onChange: (FeedDefinition) -> Unit,
) {
    val allLists by followListsModel.gitRepositoryRoutes.collectAsStateWithLifecycle()

    FeedFilterSpinner(
        placeholderCode = listName,
        explainer = stringRes(Res.string.select_list_to_filter),
        options = allLists,
        onSelect = onChange,
        accountViewModel = accountViewModel,
    )
}
