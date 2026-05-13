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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.BooleanType
import com.vitorpamplona.amethyst.model.UiSettingsFlow
import com.vitorpamplona.amethyst.model.parseBooleanType
import com.vitorpamplona.amethyst.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow
import kotlinx.collections.immutable.persistentListOf

@Composable
fun ComposeSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.compose_settings), nav)
        },
    ) {
        Column(Modifier.padding(it)) {
            ComposeSettingsContent(accountViewModel.settings.uiSettingsFlow)
        }
    }
}

@Preview(device = "spec:width=2160px,height=2340px,dpi=440")
@Composable
fun ComposeSettingsScreenPreview() {
    ThemeComparisonRow {
        ComposeSettingsContent(UiSettingsFlow())
    }
}

@Composable
fun ComposeSettingsContent(sharedPrefs: UiSettingsFlow) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = Size10dp, start = Size20dp, end = Size20dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = RowColSpacing,
    ) {
        AutoCreateDraftsChoice(sharedPrefs)
        AiWritingHelpChoice(sharedPrefs)
        TrackedBroadcastsChoice(sharedPrefs)
    }
}

@Composable
fun AutoCreateDraftsChoice(sharedPrefs: UiSettingsFlow) {
    val createDraftsIndex by sharedPrefs.automaticallyCreateDrafts.collectAsState()

    val booleanItems =
        persistentListOf(
            TitleExplainer(stringRes(BooleanType.ALWAYS.reourceId)),
            TitleExplainer(stringRes(BooleanType.NEVER.reourceId)),
        )

    SettingsRow(
        R.string.auto_create_drafts_setting_title,
        R.string.auto_create_drafts_setting_description,
        booleanItems,
        createDraftsIndex.screenCode,
    ) {
        sharedPrefs.automaticallyCreateDrafts.tryEmit(parseBooleanType(it))
    }
}
