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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.WarningType
import com.vitorpamplona.amethyst.model.parseWarningType
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn

@Composable
fun SecurityFiltersScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(id = R.string.security_filters), nav) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SecurityPreferencesSection(accountViewModel)
            BlockedContentSection(accountViewModel, nav)
        }
    }
}

@Composable
private fun SecurityPreferencesSection(accountViewModel: AccountViewModel) {
    SettingsSection(R.string.security_section_filtering_preferences) {
        SensitiveContentTile(accountViewModel)
        SettingsDivider()
        FilterSpamTile(accountViewModel)
        SettingsDivider()
        HideCommunityViolationsTile(accountViewModel)
        SettingsDivider()
        DisableClientTagTile(accountViewModel)
        SettingsDivider()
        WarnReportsTile(accountViewModel)
        SettingsDivider()
        MaxHashtagsTile(accountViewModel)
    }
}

@Composable
private fun SensitiveContentTile(accountViewModel: AccountViewModel) {
    val security = accountViewModel.account.settings.syncedSettings.security
    val showSensitive by security.showSensitiveContent.collectAsStateWithLifecycle()
    val current = parseWarningType(showSensitive)
    val options = WarningType.entries

    SettingsBlockTile(
        icon = MaterialSymbols.Visibility,
        title = stringRes(R.string.show_sensitive_content_title),
        description = stringRes(R.string.show_sensitive_content_explainer),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = current == type,
                    onClick = { accountViewModel.updateShowSensitiveContent(type.prefCode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(stringRes(type.resourceId))
                }
            }
        }
    }
}

@Composable
private fun FilterSpamTile(accountViewModel: AccountViewModel) {
    val filterSpam by accountViewModel.account.settings.syncedSettings.security
        .filterSpamFromStrangers
        .collectAsStateWithLifecycle()

    SwitchTile(
        icon = MaterialSymbols.FilterAlt,
        title = R.string.filter_spam_from_strangers_title,
        description = R.string.filter_spam_from_strangers_explainer,
        checked = filterSpam,
        onCheckedChange = accountViewModel::updateFilterSpam,
    )
}

@Composable
private fun HideCommunityViolationsTile(accountViewModel: AccountViewModel) {
    val hideViolations by accountViewModel.account.settings.hideCommunityRulesViolations
        .collectAsStateWithLifecycle()

    SwitchTile(
        icon = MaterialSymbols.Shield,
        title = R.string.hide_community_rules_violations_title,
        description = R.string.hide_community_rules_violations_explainer,
        checked = hideViolations,
        onCheckedChange = { accountViewModel.account.settings.changeHideCommunityRulesViolations(it) },
    )
}

@Composable
private fun DisableClientTagTile(accountViewModel: AccountViewModel) {
    val disableClientTag by accountViewModel.account.settings.syncedSettings.security
        .disableClientTag
        .collectAsStateWithLifecycle()

    SwitchTile(
        icon = MaterialSymbols.Code,
        title = R.string.disable_client_tag_title,
        description = R.string.disable_client_tag_explainer,
        checked = disableClientTag,
        onCheckedChange = accountViewModel::updateDisableClientTag,
    )
}

@Composable
private fun WarnReportsTile(accountViewModel: AccountViewModel) {
    val security = accountViewModel.account.settings.syncedSettings.security
    val warnReports by security.warnAboutPostsWithReports.collectAsStateWithLifecycle()
    val threshold by security.reportWarningThreshold.collectAsStateWithLifecycle()

    SwitchTile(
        icon = MaterialSymbols.Report,
        title = R.string.warn_when_posts_have_reports_from_your_follows_title,
        description = R.string.warn_when_posts_have_reports_from_your_follows_explainer,
        checked = warnReports,
        onCheckedChange = accountViewModel::updateWarnReports,
    )
    SettingsSubControlRow(
        title = stringRes(R.string.report_warning_threshold_title),
        description = stringRes(R.string.report_warning_threshold_explainer),
        enabled = warnReports,
    ) {
        SettingsStepper(
            value = threshold.coerceAtLeast(1),
            min = 1,
            max = 999,
            enabled = warnReports,
            onValueChange = accountViewModel::updateReportWarningThreshold,
        )
    }
}

@Composable
private fun MaxHashtagsTile(accountViewModel: AccountViewModel) {
    val maxHashtags by accountViewModel.account.settings.syncedSettings.security
        .maxHashtagLimit
        .collectAsStateWithLifecycle()

    SettingsControlRow(
        icon = MaterialSymbols.Tag,
        title = stringRes(R.string.max_hashtag_limit_title),
        description = stringRes(R.string.max_hashtag_limit_explainer),
    ) {
        SettingsStepper(
            value = maxHashtags,
            min = 0,
            max = 99,
            unsetLabel = stringRes(R.string.security_unlimited),
            onValueChange = accountViewModel::updateMaxHashtagLimit,
        )
    }
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

@Composable
private fun BlockedContentSection(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val hidden by accountViewModel.account.hiddenUsers.flow
        .collectAsStateWithLifecycle()

    SettingsSection(R.string.security_section_blocked_content) {
        SettingsItem(
            title = R.string.blocked_users,
            icon = MaterialSymbols.PersonOff,
            trailing = { SettingsCountBadge(hidden.hiddenUsers.size) },
            onClick = { nav.nav(Route.BlockedUsers) },
        )
        SettingsDivider()
        SettingsItem(
            title = R.string.spamming_users,
            icon = MaterialSymbols.Block,
            trailing = { SettingsCountBadge(hidden.spammers.size) },
            onClick = { nav.nav(Route.SpammingUsers) },
        )
        SettingsDivider()
        SettingsItem(
            title = R.string.hidden_words,
            icon = MaterialSymbols.VisibilityOff,
            trailing = { SettingsCountBadge(hidden.hiddenWords.size) },
            onClick = { nav.nav(Route.HiddenWords) },
        )
        SettingsDivider()
        SettingsItem(
            title = R.string.settings_muted_threads_title,
            icon = MaterialSymbols.Forum,
            trailing = { SettingsCountBadge(hidden.mutedThreads.size) },
            onClick = { nav.nav(Route.MutedThreads) },
        )
    }
}

@Preview
@Composable
fun SecurityFiltersScreenPreview() {
    ThemeComparisonColumn {
        SecurityFiltersScreen(mockAccountViewModel(), EmptyNav())
    }
}
