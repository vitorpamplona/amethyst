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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow

@Preview
@Composable
fun HomeTabsSettingsScreenPreview() {
    ThemeComparisonRow {
        HomeTabsSettingsScreen(
            mockAccountViewModel(),
            EmptyNav(),
        )
    }
}

@Composable
fun HomeTabsSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.home_tabs_settings), nav)
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            HomeTabsSettingsContent(accountViewModel)
        }
    }
}

@Composable
fun HomeTabsSettingsContent(accountViewModel: AccountViewModel) {
    val ui = accountViewModel.settings.uiSettingsFlow

    val showNewThreads by ui.showHomeNewThreadsTab.collectAsStateWithLifecycle()
    val showConversations by ui.showHomeConversationsTab.collectAsStateWithLifecycle()
    val showEverything by ui.showHomeEverythingTab.collectAsStateWithLifecycle()

    val activeCount = listOf(showNewThreads, showConversations, showEverything).count { it }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text = stringRes(R.string.home_tabs_settings_description),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp, start = Size20dp, end = Size20dp),
        )

        HomeTabSwitchRow(
            title = stringRes(R.string.new_threads),
            checked = showNewThreads,
            // Don't allow disabling the last remaining tab.
            enabled = !(showNewThreads && activeCount == 1),
            onCheckedChange = {
                ui.showHomeNewThreadsTab.tryEmit(it)
            },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = Size20dp))

        HomeTabSwitchRow(
            title = stringRes(R.string.conversations),
            checked = showConversations,
            enabled = !(showConversations && activeCount == 1),
            onCheckedChange = {
                ui.showHomeConversationsTab.tryEmit(it)
            },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = Size20dp))

        HomeTabSwitchRow(
            title = stringRes(R.string.home_tab_everything),
            checked = showEverything,
            enabled = !(showEverything && activeCount == 1),
            onCheckedChange = {
                ui.showHomeEverythingTab.tryEmit(it)
            },
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HomeTabSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = Size20dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}
