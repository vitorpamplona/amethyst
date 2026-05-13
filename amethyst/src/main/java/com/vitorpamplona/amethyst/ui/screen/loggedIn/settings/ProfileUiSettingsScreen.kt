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
fun ProfileUiSettingsScreenPreview() {
    ThemeComparisonRow {
        ProfileUiSettingsScreen(
            mockAccountViewModel(),
            EmptyNav(),
        )
    }
}

@Composable
fun ProfileUiSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.profile_ui_settings), nav)
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ProfileUiSettingsContent(accountViewModel)
        }
    }
}

@Composable
fun ProfileUiSettingsContent(accountViewModel: AccountViewModel) {
    val ui = accountViewModel.settings.uiSettingsFlow

    val showBadges by ui.showProfileBadges.collectAsStateWithLifecycle()
    val showAppRecommendations by ui.showProfileAppRecommendations.collectAsStateWithLifecycle()
    val showZapReceived by ui.showProfileZapReceivedFeed.collectAsStateWithLifecycle()
    val showFollowers by ui.showProfileFollowersFeed.collectAsStateWithLifecycle()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text = stringRes(R.string.profile_ui_settings_description),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp, start = Size20dp, end = Size20dp),
        )

        ProfileUiSwitchRow(
            title = stringRes(R.string.profile_ui_setting_badges),
            checked = showBadges,
            onCheckedChange = { ui.showProfileBadges.tryEmit(it) },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = Size20dp))

        ProfileUiSwitchRow(
            title = stringRes(R.string.profile_ui_setting_app_recommendations),
            checked = showAppRecommendations,
            onCheckedChange = { ui.showProfileAppRecommendations.tryEmit(it) },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = Size20dp))

        ProfileUiSwitchRow(
            title = stringRes(R.string.profile_ui_setting_zap_received_feed),
            checked = showZapReceived,
            onCheckedChange = { ui.showProfileZapReceivedFeed.tryEmit(it) },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = Size20dp))

        ProfileUiSwitchRow(
            title = stringRes(R.string.profile_ui_setting_followers_feed),
            checked = showFollowers,
            onCheckedChange = { ui.showProfileFollowersFeed.tryEmit(it) },
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ProfileUiSwitchRow(
    title: String,
    checked: Boolean,
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
            onCheckedChange = onCheckedChange,
        )
    }
}
