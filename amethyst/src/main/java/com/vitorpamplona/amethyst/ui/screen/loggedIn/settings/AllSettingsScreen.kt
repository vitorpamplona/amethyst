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

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Preview
@Composable
fun AllSettingsScreenPreview() {
    ThemeComparisonColumn {
        AllSettingsScreen(
            mockAccountViewModel(),
            EmptyNav(),
        )
    }
}

@Composable
fun AllSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showResetMarmotDialog by remember { mutableStateOf(false) }
    var isResettingMarmot by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val hasPrivateKey = accountViewModel.account.settings.keyPair.privKey != null

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.settings), nav)
        },
        bottomBar = {
            AppBottomBar(Route.AllSettings, nav, accountViewModel) { route ->
                if (route == Route.AllSettings) {
                    scope.launch { scrollState.animateScrollTo(0) }
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsSection(R.string.account_settings) {
                SettingsItem(
                    title = R.string.relay_setup,
                    iconPainter = R.drawable.relays,
                    iconPainterRef = 4,
                    onClick = { nav.nav(Route.EditRelays) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.event_sync_title,
                    icon = MaterialSymbols.Sync,
                    onClick = { nav.nav(Route.EventSync) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.route_import_follows,
                    icon = MaterialSymbols.GroupAdd,
                    onClick = { nav.nav(Route.ImportFollowsSelectUser) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.media_servers,
                    icon = MaterialSymbols.CloudUpload,
                    onClick = { nav.nav(Route.EditMediaServers) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.nests_servers_title,
                    icon = MaterialSymbols.CloudUpload,
                    onClick = { nav.nav(Route.EditNestsServers) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.profile_badges_title,
                    icon = MaterialSymbols.MilitaryTech,
                    onClick = { nav.nav(Route.ProfileBadges) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.favorite_dvms_title,
                    icon = MaterialSymbols.AutoAwesome,
                    onClick = { nav.nav(Route.EditFavoriteAlgoFeeds) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.reactions,
                    icon = MaterialSymbols.FavoriteBorder,
                    onClick = { nav.nav(Route.UpdateReactionType) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.video_player_settings,
                    icon = MaterialSymbols.VideoSettings,
                    onClick = { nav.nav(Route.VideoPlayerSettings) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.zaps,
                    icon = MaterialSymbols.Bolt,
                    onClick = { nav.nav(Route.UpdateZapAmount()) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.payment_targets,
                    icon = MaterialSymbols.Payment,
                    onClick = { nav.nav(Route.EditPaymentTargets) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.security_filters,
                    icon = MaterialSymbols.Security,
                    onClick = { nav.nav(Route.SecurityFilters) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.call_settings,
                    icon = MaterialSymbols.Phone,
                    onClick = { nav.nav(Route.CallSettings) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.translations,
                    icon = MaterialSymbols.Translate,
                    onClick = { nav.nav(Route.UserSettings) },
                )
            }

            SettingsSection(R.string.app_settings) {
                SettingsItem(
                    title = R.string.privacy_options,
                    iconPainter = R.drawable.ic_tor,
                    iconPainterRef = 1,
                    onClick = { nav.nav(Route.PrivacyOptions) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.ots_explorer_settings,
                    icon = MaterialSymbols.Search,
                    onClick = { nav.nav(Route.OtsSettings) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.namecoin_settings,
                    icon = MaterialSymbols.Security,
                    onClick = { nav.nav(Route.NamecoinSettings) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.ui_preferences,
                    icon = MaterialSymbols.Settings,
                    onClick = { nav.nav(Route.Settings) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.reactions_settings,
                    icon = MaterialSymbols.ThumbUp,
                    onClick = { nav.nav(Route.ReactionsSettings) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.bottom_bar_settings,
                    icon = MaterialSymbols.Dashboard,
                    onClick = { nav.nav(Route.BottomBarSettings) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.home_tabs_settings,
                    icon = MaterialSymbols.Home,
                    onClick = { nav.nav(Route.HomeTabsSettings) },
                )
            }

            SettingsSection(R.string.danger_zone, isDanger = true) {
                if (hasPrivateKey) {
                    SettingsItem(
                        title = R.string.backup_keys,
                        icon = MaterialSymbols.Key,
                        isDanger = true,
                        onClick = { nav.nav(Route.AccountBackup) },
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = R.string.request_to_vanish,
                        icon = MaterialSymbols.DeleteForever,
                        isDanger = true,
                        onClick = { nav.nav(Route.RequestToVanish) },
                    )
                    SettingsDivider()
                }
                SettingsItem(
                    title = R.string.vanish_history,
                    icon = MaterialSymbols.History,
                    isDanger = true,
                    onClick = { nav.nav(Route.VanishEvents) },
                )
                SettingsDivider()
                SettingsItem(
                    title = R.string.reset_marmot_state,
                    icon = MaterialSymbols.DeleteSweep,
                    isDanger = true,
                    onClick = { if (!isResettingMarmot) showResetMarmotDialog = true },
                )
            }
        }
    }

    if (showResetMarmotDialog) {
        ResetMarmotStateDialog(
            onConfirm = {
                showResetMarmotDialog = false
                isResettingMarmot = true
                scope.launch(Dispatchers.IO) {
                    val successMessage = stringRes(context, R.string.reset_marmot_success)
                    try {
                        accountViewModel.resetMarmotState()
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        val failureMessage =
                            stringRes(context, R.string.reset_marmot_failure, e.message ?: "")
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, failureMessage, Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        isResettingMarmot = false
                    }
                }
            },
            onDismiss = { showResetMarmotDialog = false },
        )
    }
}

@Composable
private fun ResetMarmotStateDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                symbol = MaterialSymbols.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = stringRes(R.string.reset_marmot_confirm_title),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(text = stringRes(R.string.reset_marmot_confirm_body))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(stringRes(R.string.reset_marmot_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SettingsSection(
    title: Int,
    isDanger: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringRes(title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color =
                if (isDanger) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
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
private fun SettingsItem(
    title: Int,
    icon: MaterialSymbol,
    isDanger: Boolean = false,
    onClick: () -> Unit,
) {
    SettingsItemRow(
        title = title,
        isDanger = isDanger,
        onClick = onClick,
        leadingIcon = { tint ->
            Icon(
                symbol = icon,
                contentDescription = stringRes(title),
                modifier = Modifier.size(20.dp),
                tint = tint,
            )
        },
    )
}

@Composable
private fun SettingsItem(
    title: Int,
    iconPainter: Int,
    iconPainterRef: Int,
    isDanger: Boolean = false,
    onClick: () -> Unit,
) {
    val painter: Painter = painterRes(iconPainter, iconPainterRef)
    SettingsItemRow(
        title = title,
        isDanger = isDanger,
        onClick = onClick,
        leadingIcon = { tint ->
            Icon(
                painter = painter,
                contentDescription = stringRes(title),
                modifier = Modifier.size(20.dp),
                tint = tint,
            )
        },
    )
}

@Composable
private fun SettingsItemRow(
    title: Int,
    isDanger: Boolean,
    onClick: () -> Unit,
    leadingIcon: @Composable (tint: Color) -> Unit,
) {
    val containerColor =
        if (isDanger) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
    val iconTint =
        if (isDanger) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
    val textColor =
        if (isDanger) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            leadingIcon(iconTint)
        }
        Text(
            text = stringRes(title),
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
        )
        Icon(
            symbol = MaterialSymbols.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
