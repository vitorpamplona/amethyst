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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.FeedFilterSpinner
import com.vitorpamplona.amethyst.ui.navigation.topbars.UserDrawerSearchTopBar
import com.vitorpamplona.amethyst.ui.screen.FeedDefinition
import com.vitorpamplona.amethyst.ui.screen.FollowListState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun StoriesTopBar(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    UserDrawerSearchTopBar(accountViewModel, nav) {
        val list by accountViewModel.account.settings.defaultStoriesFollowList
            .collectAsStateWithLifecycle()

        FollowList(
            followListsModel = accountViewModel.feedStates.feedListOptions,
            listName = list,
            accountViewModel = accountViewModel,
        ) { listName ->
            accountViewModel.account.settings.changeDefaultStoriesFollowList(listName.code)
        }
    }
}

@Composable
private fun FollowList(
    followListsModel: FollowListState,
    listName: String,
    accountViewModel: AccountViewModel,
    onChange: (FeedDefinition) -> Unit,
) {
    val allLists by followListsModel.kind3GlobalPeopleRoutes.collectAsStateWithLifecycle()

    FeedFilterSpinner(
        placeholderCode = listName,
        explainer = stringRes(R.string.select_list_to_filter),
        options = allLists,
        onSelect = { onChange(allLists.getOrNull(it) ?: followListsModel.kind3Follow) },
        accountViewModel = accountViewModel,
    )
}
