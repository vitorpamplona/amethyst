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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.UiSettingsFlow
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
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
        HomeTabsSettingsContent(accountViewModel.settings.uiSettingsFlow, Modifier.padding(padding))
    }
}

@Composable
fun HomeTabsSettingsContent(
    ui: UiSettingsFlow,
    modifier: Modifier = Modifier,
) {
    val showNewThreads by ui.showHomeNewThreadsTab.collectAsStateWithLifecycle()
    val showConversations by ui.showHomeConversationsTab.collectAsStateWithLifecycle()
    val showEverything by ui.showHomeEverythingTab.collectAsStateWithLifecycle()

    val activeCount = listOf(showNewThreads, showConversations, showEverything).count { it }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsSection(R.string.settings_section_home_tabs) {
            SettingsSwitchTile(
                icon = MaterialSymbols.Forum,
                title = R.string.new_threads,
                checked = showNewThreads,
                // Don't allow disabling the last remaining tab.
                enabled = !(showNewThreads && activeCount == 1),
                onCheckedChange = { ui.showHomeNewThreadsTab.tryEmit(it) },
            )
            SettingsDivider()
            SettingsSwitchTile(
                icon = MaterialSymbols.Chat,
                title = R.string.conversations,
                checked = showConversations,
                enabled = !(showConversations && activeCount == 1),
                onCheckedChange = { ui.showHomeConversationsTab.tryEmit(it) },
            )
            SettingsDivider()
            SettingsSwitchTile(
                icon = MaterialSymbols.Public,
                title = R.string.home_tab_everything,
                checked = showEverything,
                enabled = !(showEverything && activeCount == 1),
                onCheckedChange = { ui.showHomeEverythingTab.tryEmit(it) },
            )
        }
    }
}
