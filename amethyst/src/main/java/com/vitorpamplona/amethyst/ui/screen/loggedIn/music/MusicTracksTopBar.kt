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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.FeedFilterSpinner
import com.vitorpamplona.amethyst.ui.navigation.topbars.UserDrawerSearchTopBar
import com.vitorpamplona.amethyst.ui.screen.FeedDefinition
import com.vitorpamplona.amethyst.ui.screen.TopNavFilterState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn

@Composable
fun MusicTracksTopBar(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    UserDrawerSearchTopBar(accountViewModel, nav) {
        val list by accountViewModel.account.settings.defaultMusicTracksFollowList
            .collectAsStateWithLifecycle()

        MusicTracksTopNavFilterBar(
            followListsModel = accountViewModel.feedStates.feedListOptions,
            listName = list,
            accountViewModel = accountViewModel,
            onChange = accountViewModel.account.settings::changeDefaultMusicTracksFollowList,
        )
    }
}

@Composable
private fun MusicTracksTopNavFilterBar(
    followListsModel: TopNavFilterState,
    listName: TopFilter,
    accountViewModel: AccountViewModel,
    onChange: (FeedDefinition) -> Unit,
) {
    // Music is content-style (like Articles / Long-form), so reuse the same route catalog
    // as those feeds — All Follows, Your Follows, kind3 Follows, Around Me, Global, custom
    // people lists, interest sets, mute list.
    val allLists by followListsModel.kind3GlobalPeopleRoutes.collectAsStateWithLifecycle()

    FeedFilterSpinner(
        placeholderCode = listName,
        explainer = stringRes(R.string.select_list_to_filter),
        options = allLists,
        onSelect = onChange,
        accountViewModel = accountViewModel,
    )
}

@Preview
@Composable
private fun MusicTracksTopBarPreview() {
    ThemeComparisonColumn {
        MusicTracksTopBar(accountViewModel = mockAccountViewModel(), nav = EmptyNav())
    }
}
