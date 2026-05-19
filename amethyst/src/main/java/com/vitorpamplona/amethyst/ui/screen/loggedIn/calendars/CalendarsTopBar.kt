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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.FeedFilterSpinner
import com.vitorpamplona.amethyst.ui.navigation.topbars.UserDrawerSearchTopBar
import com.vitorpamplona.amethyst.ui.screen.FeedDefinition
import com.vitorpamplona.amethyst.ui.screen.TopNavFilterState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun CalendarsTopBar(
    viewMode: CalendarsViewMode,
    onViewModeChange: (CalendarsViewMode) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column {
        UserDrawerSearchTopBar(accountViewModel, nav) {
            val list by accountViewModel.account.settings.defaultCalendarsFollowList
                .collectAsStateWithLifecycle()

            CalendarsTopNavFilterBar(
                followListsModel = accountViewModel.feedStates.feedListOptions,
                listName = list,
                accountViewModel = accountViewModel,
                onChange = accountViewModel.account.settings::changeDefaultCalendarsFollowList,
            )
        }

        CalendarsViewModeTabs(
            current = viewMode,
            onChange = onViewModeChange,
        )
    }
}

@Composable
private fun CalendarsTopNavFilterBar(
    followListsModel: TopNavFilterState,
    listName: TopFilter,
    accountViewModel: AccountViewModel,
    onChange: (FeedDefinition) -> Unit,
) {
    val allLists by followListsModel.kind3GlobalPeopleRoutes.collectAsStateWithLifecycle()

    FeedFilterSpinner(
        placeholderCode = listName,
        explainer = stringRes(R.string.select_list_to_filter),
        options = allLists,
        onSelect = onChange,
        accountViewModel = accountViewModel,
    )
}

@Composable
private fun CalendarsViewModeTabs(
    current: CalendarsViewMode,
    onChange: (CalendarsViewMode) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        CalendarsViewMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == current,
                onClick = { onChange(mode) },
                label = {
                    Text(
                        text = stringRes(mode.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                modifier = Modifier.padding(end = 6.dp),
                colors = FilterChipDefaults.filterChipColors(),
                shape = MaterialTheme.shapes.small,
            )
        }
    }
}

@Suppress("unused")
private val ChipPad = PaddingValues(horizontal = 4.dp)
