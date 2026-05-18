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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.service.notifications.BatteryOptimizationHelper
import com.vitorpamplona.amethyst.ui.components.PushNotificationProviderTile
import com.vitorpamplona.amethyst.ui.components.hasPushNotificationProvider
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn

@Composable
fun NotificationSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(id = R.string.notification_settings), nav) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (hasPushNotificationProvider()) {
                SettingsSection(R.string.notification_settings_section_push) {
                    PushNotificationProviderTile(accountViewModel.settings.uiSettingsFlow)
                }
            }

            val alwaysOn by accountViewModel.account.settings.alwaysOnNotificationService
                .collectAsStateWithLifecycle()
            val splitByFollows by accountViewModel.account.settings.splitNotificationsEnabled
                .collectAsStateWithLifecycle()

            SettingsSection(R.string.notification_settings_section_in_app) {
                SettingsSwitchTile(
                    icon = MaterialSymbols.Notifications,
                    title = R.string.always_on_notif_setting_title,
                    description = R.string.always_on_notif_setting_description,
                    checked = alwaysOn,
                    onCheckedChange = { accountViewModel.account.settings.toggleAlwaysOnNotificationService() },
                )
                SettingsDivider()
                SettingsSwitchTile(
                    icon = MaterialSymbols.Forum,
                    title = R.string.split_notifications_setting_title,
                    description = R.string.split_notifications_setting_description,
                    checked = splitByFollows,
                    onCheckedChange = { accountViewModel.account.settings.toggleSplitNotificationsEnabled() },
                )
            }

            if (alwaysOn) {
                BatteryOptimizationBanner()
            }
        }
    }
}

@Composable
private fun BatteryOptimizationBanner() {
    val context = LocalContext.current
    var isExempt by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }
    LifecycleResumeEffect(Unit) {
        isExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        onPauseOrDispose {}
    }

    if (isExempt) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringRes(R.string.battery_optimization_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringRes(R.string.battery_optimization_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(
                onClick = { BatteryOptimizationHelper.requestBatteryOptimizationExemption(context) },
            ) {
                Text(stringRes(R.string.battery_optimization_fix_now))
            }
        }
    }
}

@Preview
@Composable
fun NotificationSettingsScreenPreview() {
    ThemeComparisonColumn {
        NotificationSettingsScreen(mockAccountViewModel(), EmptyNav())
    }
}
