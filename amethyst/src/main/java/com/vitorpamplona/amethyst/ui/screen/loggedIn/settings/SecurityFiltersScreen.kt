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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.WarningType
import com.vitorpamplona.amethyst.model.parseWarningType
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

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
    val showSensitive by accountViewModel.account.settings.syncedSettings.security.showSensitiveContent
        .collectAsStateWithLifecycle()

    SettingsBlockTile(
        icon = MaterialSymbols.Visibility,
        title = stringRes(R.string.show_sensitive_content_title),
        description = stringRes(R.string.show_sensitive_content_explainer),
    ) {
        val current = parseWarningType(showSensitive)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            WarningType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = current == type,
                    onClick = { accountViewModel.updateShowSensitiveContent(type.prefCode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = WarningType.entries.size),
                ) {
                    Text(stringRes(type.resourceId))
                }
            }
        }
    }
}

@Composable
private fun FilterSpamTile(accountViewModel: AccountViewModel) {
    val filterSpam by accountViewModel.account.settings.syncedSettings.security.filterSpamFromStrangers
        .collectAsStateWithLifecycle()

    SwitchTile(
        icon = MaterialSymbols.FilterAlt,
        titleRes = R.string.filter_spam_from_strangers_title,
        descriptionRes = R.string.filter_spam_from_strangers_explainer,
        checked = filterSpam,
        onCheckedChange = { accountViewModel.updateFilterSpam(it) },
    )
}

@Composable
private fun HideCommunityViolationsTile(accountViewModel: AccountViewModel) {
    val hideViolations by accountViewModel.account.settings.hideCommunityRulesViolations
        .collectAsStateWithLifecycle()

    SwitchTile(
        icon = MaterialSymbols.Shield,
        titleRes = R.string.hide_community_rules_violations_title,
        descriptionRes = R.string.hide_community_rules_violations_explainer,
        checked = hideViolations,
        onCheckedChange = { accountViewModel.account.settings.changeHideCommunityRulesViolations(it) },
    )
}

@Composable
private fun DisableClientTagTile(accountViewModel: AccountViewModel) {
    val disableClientTag by accountViewModel.account.settings.syncedSettings.security.disableClientTag
        .collectAsStateWithLifecycle()

    SwitchTile(
        icon = MaterialSymbols.Code,
        titleRes = R.string.disable_client_tag_title,
        descriptionRes = R.string.disable_client_tag_explainer,
        checked = disableClientTag,
        onCheckedChange = { accountViewModel.updateDisableClientTag(it) },
    )
}

@Composable
private fun WarnReportsTile(accountViewModel: AccountViewModel) {
    val warnReports by accountViewModel.account.settings.syncedSettings.security.warnAboutPostsWithReports
        .collectAsStateWithLifecycle()
    val threshold by accountViewModel.account.settings.syncedSettings.security.reportWarningThreshold
        .collectAsStateWithLifecycle()

    SwitchTile(
        icon = MaterialSymbols.Report,
        titleRes = R.string.warn_when_posts_have_reports_from_your_follows_title,
        descriptionRes = R.string.warn_when_posts_have_reports_from_your_follows_explainer,
        checked = warnReports,
        onCheckedChange = { accountViewModel.updateWarnReports(it) },
    )
    SettingsSubControlRow(
        title = stringRes(R.string.report_warning_threshold_title),
        description = stringRes(R.string.report_warning_threshold_explainer),
        enabled = warnReports,
    ) {
        Stepper(
            value = threshold.coerceAtLeast(1),
            min = 1,
            max = 999,
            enabled = warnReports,
            onValueChange = { accountViewModel.updateReportWarningThreshold(it) },
        )
    }
}

@Composable
private fun MaxHashtagsTile(accountViewModel: AccountViewModel) {
    val maxHashtags by accountViewModel.account.settings.syncedSettings.security.maxHashtagLimit
        .collectAsStateWithLifecycle()

    SettingsControlRow(
        icon = MaterialSymbols.Tag,
        title = stringRes(R.string.max_hashtag_limit_title),
        description = stringRes(R.string.max_hashtag_limit_explainer),
    ) {
        Stepper(
            value = maxHashtags,
            min = 0,
            max = 99,
            unsetLabel = stringRes(R.string.security_unlimited),
            onValueChange = { accountViewModel.updateMaxHashtagLimit(it) },
        )
    }
}

@Composable
private fun SwitchTile(
    icon: MaterialSymbol,
    titleRes: Int,
    descriptionRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsControlRow(
        icon = icon,
        title = stringRes(titleRes),
        description = stringRes(descriptionRes),
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
            trailing = { CountBadge(hidden.hiddenUsers.size) },
            onClick = { nav.nav(Route.BlockedUsers) },
        )
        SettingsDivider()
        SettingsItem(
            title = R.string.spamming_users,
            icon = MaterialSymbols.Block,
            trailing = { CountBadge(hidden.spammers.size) },
            onClick = { nav.nav(Route.SpammingUsers) },
        )
        SettingsDivider()
        SettingsItem(
            title = R.string.hidden_words,
            icon = MaterialSymbols.VisibilityOff,
            trailing = { CountBadge(hidden.hiddenWords.size) },
            onClick = { nav.nav(Route.HiddenWords) },
        )
        SettingsDivider()
        SettingsItem(
            title = R.string.settings_muted_threads_title,
            icon = MaterialSymbols.Forum,
            trailing = { CountBadge(hidden.mutedThreads.size) },
            onClick = { nav.nav(Route.MutedThreads) },
        )
    }
}

@Composable
private fun CountBadge(count: Int) {
    // Reserve space so transitions to/from 0 don't shift the row.
    Box(
        modifier = Modifier.widthIn(min = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (count > 0) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun Stepper(
    value: Int,
    min: Int,
    max: Int,
    enabled: Boolean = true,
    unsetLabel: String? = null,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            enabled = enabled && value > min,
            onClick = { onValueChange((value - 1).coerceAtLeast(min)) },
        ) {
            Icon(
                symbol = MaterialSymbols.Remove,
                contentDescription = "−",
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = if (unsetLabel != null && value <= 0) unsetLabel else value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 28.dp),
        )
        IconButton(
            enabled = enabled && value < max,
            onClick = { onValueChange((value + 1).coerceAtMost(max)) },
        ) {
            Icon(
                symbol = MaterialSymbols.Add,
                contentDescription = "+",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
