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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.text.font.FontWeight
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

    SettingsTile(
        icon = MaterialSymbols.VisibilityOff,
        title = stringRes(R.string.show_sensitive_content_title),
        description = stringRes(R.string.show_sensitive_content_explainer),
    ) {
        val current = parseWarningType(showSensitive)
        val options = listOf(WarningType.WARN, WarningType.SHOW, WarningType.HIDE)
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
    val filterSpam by accountViewModel.account.settings.syncedSettings.security.filterSpamFromStrangers
        .collectAsStateWithLifecycle()

    SwitchTile(
        icon = MaterialSymbols.FilterAlt,
        title = stringRes(R.string.filter_spam_from_strangers_title),
        description = stringRes(R.string.filter_spam_from_strangers_explainer),
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
        title = stringRes(R.string.hide_community_rules_violations_title),
        description = stringRes(R.string.hide_community_rules_violations_explainer),
        checked = hideViolations,
        onCheckedChange = { accountViewModel.account.settings.changeHideCommunityRulesViolations(it) },
    )
}

@Composable
private fun DisableClientTagTile(accountViewModel: AccountViewModel) {
    val disableClientTag by accountViewModel.account.settings.syncedSettings.security.disableClientTag
        .collectAsStateWithLifecycle()

    SwitchTile(
        icon = MaterialSymbols.Tag,
        title = stringRes(R.string.disable_client_tag_title),
        description = stringRes(R.string.disable_client_tag_explainer),
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

    Column {
        SwitchTile(
            icon = MaterialSymbols.Report,
            title = stringRes(R.string.warn_when_posts_have_reports_from_your_follows_title),
            description = stringRes(R.string.warn_when_posts_have_reports_from_your_follows_explainer),
            checked = warnReports,
            onCheckedChange = { accountViewModel.updateWarnReports(it) },
        )

        SettingsTile(
            icon = null,
            title = stringRes(R.string.report_warning_threshold_title),
            description = stringRes(R.string.report_warning_threshold_explainer),
            indented = true,
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
}

@Composable
private fun MaxHashtagsTile(accountViewModel: AccountViewModel) {
    val maxHashtags by accountViewModel.account.settings.syncedSettings.security.maxHashtagLimit
        .collectAsStateWithLifecycle()

    SettingsTile(
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
private fun BlockedContentSection(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val hidden by accountViewModel.account.hiddenUsers.flow
        .collectAsStateWithLifecycle()

    SettingsSection(R.string.security_section_blocked_content) {
        BlockedContentRow(
            icon = MaterialSymbols.PersonOff,
            title = stringRes(R.string.blocked_users),
            count = hidden.hiddenUsers.size,
            onClick = { nav.nav(Route.BlockedUsers) },
        )
        SettingsDivider()
        BlockedContentRow(
            icon = MaterialSymbols.Block,
            title = stringRes(R.string.spamming_users),
            count = hidden.spammers.size,
            onClick = { nav.nav(Route.SpammingUsers) },
        )
        SettingsDivider()
        BlockedContentRow(
            icon = MaterialSymbols.Tag,
            title = stringRes(R.string.hidden_words),
            count = hidden.hiddenWords.size,
            onClick = { nav.nav(Route.HiddenWords) },
        )
        SettingsDivider()
        BlockedContentRow(
            icon = MaterialSymbols.Forum,
            title = stringRes(R.string.settings_muted_threads_title),
            count = hidden.mutedThreads.size,
            onClick = { nav.nav(Route.MutedThreads) },
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Building blocks
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringRes(title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun SettingsTile(
    icon: MaterialSymbol?,
    title: String,
    description: String,
    indented: Boolean = false,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = if (indented) 56.dp else 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            SettingsLeadingIcon(icon)
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = if (icon != null) 16.dp else 0.dp, end = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        Box(contentAlignment = Alignment.Center) { trailing() }
    }
}

@Composable
private fun SwitchTile(
    icon: MaterialSymbol,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsLeadingIcon(icon)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsLeadingIcon(icon: MaterialSymbol) {
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun BlockedContentRow(
    icon: MaterialSymbol,
    title: String,
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsLeadingIcon(icon)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
        )
        CountBadge(count)
        Icon(
            symbol = MaterialSymbols.ChevronRight,
            contentDescription = null,
            modifier = Modifier.padding(start = 8.dp).size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CountBadge(count: Int) {
    if (count <= 0) return
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

@Composable
private fun Stepper(
    value: Int,
    min: Int,
    max: Int,
    enabled: Boolean = true,
    unsetLabel: String? = null,
    onValueChange: (Int) -> Unit,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier =
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha)),
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
                tint = LocalContentColor.current.copy(alpha = alpha),
            )
        }
        val display =
            if (unsetLabel != null && value <= 0) {
                unsetLabel
            } else {
                value.toString()
            }
        Text(
            text = display,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        IconButton(
            enabled = enabled && value < max,
            onClick = { onValueChange((value + 1).coerceAtMost(max)) },
        ) {
            Icon(
                symbol = MaterialSymbols.Add,
                contentDescription = "+",
                modifier = Modifier.size(18.dp),
                tint = LocalContentColor.current.copy(alpha = alpha),
            )
        }
    }
}
