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
import com.vitorpamplona.amethyst.model.ProfileGalleryType
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
        ProfileUiSettingsContent(accountViewModel.settings.uiSettingsFlow, Modifier.padding(padding))
    }
}

@Composable
fun ProfileUiSettingsContent(
    ui: UiSettingsFlow,
    modifier: Modifier = Modifier,
) {
    val showBadges by ui.showProfileBadges.collectAsStateWithLifecycle()
    val showAppRecommendations by ui.showProfileAppRecommendations.collectAsStateWithLifecycle()
    val showZapReceived by ui.showProfileZapReceivedFeed.collectAsStateWithLifecycle()
    val showFollowers by ui.showProfileFollowersFeed.collectAsStateWithLifecycle()
    val showOnchainWallet by ui.showOnchainWallet.collectAsStateWithLifecycle()
    val gallery by ui.gallerySet.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsSection(R.string.settings_section_profile_sections) {
            SettingsSwitchTile(
                icon = MaterialSymbols.MilitaryTech,
                title = R.string.profile_ui_setting_badges,
                checked = showBadges,
                onCheckedChange = { ui.showProfileBadges.tryEmit(it) },
            )
            SettingsDivider()
            SettingsSwitchTile(
                icon = MaterialSymbols.Recommend,
                title = R.string.profile_ui_setting_app_recommendations,
                checked = showAppRecommendations,
                onCheckedChange = { ui.showProfileAppRecommendations.tryEmit(it) },
            )
            SettingsDivider()
            SettingsSwitchTile(
                icon = MaterialSymbols.Bolt,
                title = R.string.profile_ui_setting_zap_received_feed,
                checked = showZapReceived,
                onCheckedChange = { ui.showProfileZapReceivedFeed.tryEmit(it) },
            )
            SettingsDivider()
            SettingsSwitchTile(
                icon = MaterialSymbols.Group,
                title = R.string.profile_ui_setting_followers_feed,
                checked = showFollowers,
                onCheckedChange = { ui.showProfileFollowersFeed.tryEmit(it) },
            )
            SettingsDivider()
            SettingsSwitchTile(
                icon = MaterialSymbols.AccountBalanceWallet,
                title = R.string.profile_ui_setting_onchain_wallet,
                checked = showOnchainWallet,
                onCheckedChange = { ui.showOnchainWallet.tryEmit(it) },
            )
        }

        SettingsSection(R.string.settings_section_appearance) {
            SegmentedChoiceTile(
                icon = MaterialSymbols.Collections,
                title = R.string.gallery_style,
                description = R.string.gallery_style_description,
                options = ProfileGalleryType.entries,
                labelRes = { it.resourceId },
                selected = gallery,
                onSelect = { ui.gallerySet.tryEmit(it) },
            )
        }
    }
}
