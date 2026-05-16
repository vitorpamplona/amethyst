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

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.BooleanType
import com.vitorpamplona.amethyst.model.UiSettingsFlow
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun ComposeSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.compose_settings), nav)
        },
    ) { padding ->
        ComposeSettingsContent(
            sharedPrefs = accountViewModel.settings.uiSettingsFlow,
            accountViewModel = accountViewModel,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun ComposeSettingsContent(
    sharedPrefs: UiSettingsFlow,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsSection(R.string.compose_settings) {
            BooleanSwitchTile(
                flow = sharedPrefs.automaticallyCreateDrafts,
                icon = MaterialSymbols.Drafts,
                title = R.string.auto_create_drafts_setting_title,
                description = R.string.auto_create_drafts_setting_description,
            )
            SettingsDivider()
            BooleanSwitchTile(
                flow = sharedPrefs.automaticallyProposeAiImprovements,
                icon = MaterialSymbols.AutoAwesome,
                title = R.string.ai_writing_setting_title,
                description = R.string.ai_writing_setting_description,
            )
            SettingsDivider()
            BooleanSwitchTile(
                flow = sharedPrefs.useTrackedBroadcasts,
                icon = MaterialSymbols.CellTower,
                title = R.string.tracked_broadcasts_setting_title,
                description = R.string.tracked_broadcasts_setting_description,
            )
            SettingsDivider()
            AddClientTagTile(accountViewModel)
        }
    }
}

@Composable
private fun AddClientTagTile(accountViewModel: AccountViewModel) {
    val addClientTag by accountViewModel.account.settings.syncedSettings.security
        .addClientTag
        .collectAsStateWithLifecycle()

    SwitchTile(
        icon = MaterialSymbols.Code,
        title = R.string.add_client_tag_title,
        description = R.string.add_client_tag_explainer,
        checked = addClientTag,
        onCheckedChange = accountViewModel::updateAddClientTag,
    )
}

@Composable
private fun BooleanSwitchTile(
    flow: MutableStateFlow<BooleanType>,
    icon: MaterialSymbol,
    @StringRes title: Int,
    @StringRes description: Int,
) {
    val value by flow.collectAsState()

    SwitchTile(
        icon = icon,
        title = title,
        description = description,
        checked = value == BooleanType.ALWAYS,
        onCheckedChange = { isOn ->
            flow.tryEmit(if (isOn) BooleanType.ALWAYS else BooleanType.NEVER)
        },
    )
}

@Composable
private fun SwitchTile(
    icon: MaterialSymbol,
    @StringRes title: Int,
    @StringRes description: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsControlRow(
        icon = icon,
        title = stringRes(title),
        description = stringRes(description),
        onClick = { onCheckedChange(!checked) },
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(device = "spec:width=2160px,height=2340px,dpi=440")
@Composable
fun ComposeSettingsScreenPreview() {
    ThemeComparisonColumn {
        ComposeSettingsContent(UiSettingsFlow(), mockAccountViewModel())
    }
}
