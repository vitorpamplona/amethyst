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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.AccountInfo
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.notifications.BatteryOptimizationHelper
import com.vitorpamplona.amethyst.service.notifications.NotificationChannels
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserInfo
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.PushNotificationProviderTile
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.components.hasPushNotificationProvider
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.toShortDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
            DeliverySection(accountViewModel)
            DisplaySection(accountViewModel)
            CategoriesSection()
        }
    }
}

@Composable
private fun DeliverySection(accountViewModel: AccountViewModel) {
    // Global master switch (persisted, all accounts). produceState + runCatching keeps
    // the @Preview safe when Amethyst.instance / LocalPreferences aren't available.
    val master by produceState(initialValue = true) {
        runCatching {
            LocalPreferences.notificationServiceEnabledFlow().collect { value = it }
        }
    }

    SettingsSection(R.string.notification_settings_section_delivery) {
        if (hasPushNotificationProvider()) {
            PushNotificationProviderTile(accountViewModel.settings.uiSettingsFlow)
            SettingsDivider()
        }
        SettingsSwitchTile(
            icon = MaterialSymbols.Notifications,
            title = R.string.notification_service_master_title,
            description = R.string.notification_service_master_description,
            checked = master,
            onCheckedChange = { LocalPreferences.setNotificationServiceEnabled(it) },
        )
    }

    if (master) {
        BackgroundAccountsSection(accountViewModel)
        BatteryOptimizationBanner()
    }
}

/**
 * Per-account participation list, shown under the master switch: one "keep active in the
 * background" toggle per write-enabled account. Each row toggles that account's own
 * [AccountSettings.alwaysOnNotificationService]; because LocalPreferences caches one
 * AccountSettings per npub, the toggle reaches the same instance the always-on manager
 * observes, so participation changes take effect live.
 */
@Composable
private fun BackgroundAccountsSection(accountViewModel: AccountViewModel) {
    val accounts by produceState<List<Pair<AccountInfo, AccountSettings>>>(emptyList()) {
        value =
            runCatching {
                LocalPreferences
                    .allSavedAccounts()
                    .filter { it.hasPrivKey || it.loggedInWithExternalSigner }
                    .mapNotNull { info ->
                        LocalPreferences.loadAccountConfigFromEncryptedStorage(info.npub)?.let { info to it }
                    }
            }.getOrDefault(emptyList())
    }

    if (accounts.isEmpty()) return

    SettingsSection(R.string.notification_service_accounts_title) {
        accounts.forEachIndexed { index, (info, settings) ->
            if (index > 0) SettingsDivider()
            AccountParticipationRow(info, settings, accountViewModel)
        }
    }
}

@Composable
private fun AccountParticipationRow(
    info: AccountInfo,
    settings: AccountSettings,
    accountViewModel: AccountViewModel,
) {
    val participates by settings.alwaysOnNotificationService.collectAsStateWithLifecycle()

    // Resolve the User behind this account so its live metadata (picture + display name)
    // can be observed. These are other/background accounts, not the logged-in one, so the
    // User may not exist in LocalCache yet — create it lazily off the main thread.
    val pubkeyHex = remember(info) { decodePublicKeyAsHexOrNull(info.npub) }
    var user by remember(info) { mutableStateOf(pubkeyHex?.let { LocalCache.getUserIfExists(it) }) }
    if (user == null && pubkeyHex != null) {
        LaunchedEffect(pubkeyHex) {
            launch(Dispatchers.IO) { user = LocalCache.getOrCreateUser(pubkeyHex) }
        }
    }

    val userInfo = user?.let { observeUserInfo(it, accountViewModel).value }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { settings.toggleAlwaysOnNotificationService() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RobohashFallbackAsyncImage(
            robot = pubkeyHex ?: info.npub,
            model = userInfo?.info?.profilePicture(),
            contentDescription = stringRes(R.string.profile_image),
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape),
            loadProfilePicture = accountViewModel.settings.showProfilePictures(),
            loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
            autoPlayGif =
                accountViewModel.settings.autoPlayVideosFlow
                    .collectAsStateWithLifecycle()
                    .value,
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 12.dp),
        ) {
            val bestName = userInfo?.info?.bestName()
            if (bestName != null) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                    CreateTextWithEmoji(
                        text = bestName,
                        tags = userInfo.tags,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = info.npub.toShortDisplay(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = info.npub.toShortDisplay(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringRes(R.string.notification_service_participation_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = participates,
            onCheckedChange = { settings.toggleAlwaysOnNotificationService() },
        )
    }
}

@Composable
private fun DisplaySection(accountViewModel: AccountViewModel) {
    val splitByFollows by accountViewModel.account.settings.splitNotificationsEnabled
        .collectAsStateWithLifecycle()
    val showMessages by accountViewModel.account.settings.showMessagesInNotifications
        .collectAsStateWithLifecycle()

    SettingsSection(R.string.notification_settings_section_display) {
        SettingsSwitchTile(
            icon = MaterialSymbols.Forum,
            title = R.string.split_notifications_setting_title,
            description = R.string.split_notifications_setting_description,
            checked = splitByFollows,
            onCheckedChange = { accountViewModel.account.settings.toggleSplitNotificationsEnabled() },
        )
        SettingsDivider()
        SettingsSwitchTile(
            icon = MaterialSymbols.Mail,
            title = R.string.show_messages_in_notifications_setting_title,
            description = R.string.show_messages_in_notifications_setting_description,
            checked = showMessages,
            onCheckedChange = { accountViewModel.account.settings.toggleShowMessagesInNotifications() },
        )
    }
}

@Composable
private fun CategoriesSection() {
    val context = LocalContext.current
    val entries = NotificationChannels.contentChannels

    // Read each channel's importance after every resume so toggling
    // sound/importance in the system page reflects back here. The map IS
    // the state — no key-bump trick needed.
    var statuses by remember {
        mutableStateOf<Map<String, NotificationChannels.ChannelStatus>>(emptyMap())
    }
    LifecycleResumeEffect(Unit) {
        statuses =
            entries.associate {
                val id = it.channelId(context)
                id to NotificationChannels.statusOf(context, id)
            }
        onPauseOrDispose {}
    }

    SettingsSection(R.string.notification_settings_section_categories) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) SettingsDivider()
            val channelId = remember(entry) { entry.channelId(context) }
            // Default to ON for channels not yet created — matches Android's
            // own default importance, so the badge isn't misleading before the
            // user has interacted with the channel.
            val status = statuses[channelId] ?: NotificationChannels.ChannelStatus.ON
            SettingsItem(
                title = entry.nameRes,
                icon = entry.icon,
                trailing = { ChannelStatusBadge(status) },
                onClick = {
                    // Lazy-create the channel right before opening so the system
                    // per-channel page has something to display; idempotent.
                    entry.ensure(context)
                    NotificationChannels.openChannelSettings(context, channelId)
                },
            )
        }
    }

    Text(
        text = stringRes(R.string.notification_settings_categories_explainer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun ChannelStatusBadge(status: NotificationChannels.ChannelStatus) {
    when (status) {
        NotificationChannels.ChannelStatus.ON ->
            StatusChip(
                label = stringRes(R.string.notification_channel_status_on),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        NotificationChannels.ChannelStatus.SILENT ->
            StatusChip(
                label = stringRes(R.string.notification_channel_status_silent),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        NotificationChannels.ChannelStatus.OFF ->
            StatusChip(
                label = stringRes(R.string.notification_channel_status_off),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
    }
}

@Composable
private fun StatusChip(
    label: String,
    containerColor: Color,
    contentColor: Color,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(containerColor)
                .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
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
