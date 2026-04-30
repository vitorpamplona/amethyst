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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val tint = MaterialTheme.colorScheme.onBackground
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showResetMarmotDialog by remember { mutableStateOf(false) }
    var isResettingMarmot by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

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
        Column(Modifier.padding(padding).verticalScroll(scrollState)) {
            SettingsSectionHeader(R.string.account_settings)
            SettingsNavigationRow(
                title = R.string.relay_setup,
                iconPainter = R.drawable.relays,
                iconPainterRef = 4,
                tint = tint,
                onClick = { nav.nav(Route.EditRelays) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.event_sync_title,
                icon = MaterialSymbols.Sync,
                tint = tint,
                onClick = { nav.nav(Route.EventSync) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.route_import_follows,
                icon = MaterialSymbols.GroupAdd,
                tint = tint,
                onClick = { nav.nav(Route.ImportFollowsSelectUser) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.media_servers,
                icon = MaterialSymbols.CloudUpload,
                tint = tint,
                onClick = { nav.nav(Route.EditMediaServers) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.nests_servers_title,
                icon = MaterialSymbols.CloudUpload,
                tint = tint,
                onClick = { nav.nav(Route.EditNestsServers) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.profile_badges_title,
                icon = MaterialSymbols.MilitaryTech,
                tint = tint,
                onClick = { nav.nav(Route.ProfileBadges) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.favorite_dvms_title,
                icon = MaterialSymbols.AutoAwesome,
                tint = tint,
                onClick = { nav.nav(Route.EditFavoriteAlgoFeeds) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.reactions,
                icon = MaterialSymbols.FavoriteBorder,
                tint = tint,
                onClick = { nav.nav(Route.UpdateReactionType) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.video_player_settings,
                icon = MaterialSymbols.VideoSettings,
                tint = tint,
                onClick = { nav.nav(Route.VideoPlayerSettings) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.zaps,
                icon = MaterialSymbols.Bolt,
                tint = tint,
                onClick = { nav.nav(Route.UpdateZapAmount()) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.payment_targets,
                icon = MaterialSymbols.Payment,
                tint = tint,
                onClick = { nav.nav(Route.EditPaymentTargets) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.security_filters,
                icon = MaterialSymbols.Security,
                tint = tint,
                onClick = { nav.nav(Route.SecurityFilters) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.call_settings,
                icon = MaterialSymbols.Phone,
                tint = tint,
                onClick = { nav.nav(Route.CallSettings) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.translations,
                icon = MaterialSymbols.Translate,
                tint = tint,
                onClick = { nav.nav(Route.UserSettings) },
            )
            HorizontalDivider(thickness = 4.dp)
            SettingsSectionHeader(R.string.app_settings)
            SettingsNavigationRow(
                title = R.string.privacy_options,
                iconPainter = R.drawable.ic_tor,
                iconPainterRef = 1,
                tint = tint,
                onClick = { nav.nav(Route.PrivacyOptions) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.ots_explorer_settings,
                icon = MaterialSymbols.Search,
                tint = tint,
                onClick = { nav.nav(Route.OtsSettings) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.namecoin_settings,
                icon = MaterialSymbols.Security,
                tint = tint,
                onClick = { nav.nav(Route.NamecoinSettings) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.ui_preferences,
                icon = MaterialSymbols.Settings,
                tint = tint,
                onClick = { nav.nav(Route.Settings) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.reactions_settings,
                icon = MaterialSymbols.ThumbUp,
                tint = tint,
                onClick = { nav.nav(Route.ReactionsSettings) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.bottom_bar_settings,
                icon = MaterialSymbols.Dashboard,
                tint = tint,
                onClick = { nav.nav(Route.BottomBarSettings) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.home_tabs_settings,
                icon = MaterialSymbols.Home,
                tint = tint,
                onClick = { nav.nav(Route.HomeTabsSettings) },
            )
            HorizontalDivider(thickness = 4.dp)
            SettingsSectionHeader(R.string.danger_zone)
            accountViewModel.account.settings.keyPair.privKey?.let {
                SettingsNavigationRow(
                    title = R.string.backup_keys,
                    icon = MaterialSymbols.Key,
                    tint = tint,
                    onClick = { nav.nav(Route.AccountBackup) },
                )
                HorizontalDivider()
                SettingsNavigationRow(
                    title = R.string.request_to_vanish,
                    icon = MaterialSymbols.DeleteForever,
                    tint = tint,
                    onClick = { nav.nav(Route.RequestToVanish) },
                )
                HorizontalDivider()
            }
            SettingsNavigationRow(
                title = R.string.vanish_history,
                icon = MaterialSymbols.History,
                tint = tint,
                onClick = { nav.nav(Route.VanishEvents) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.reset_marmot_state,
                icon = MaterialSymbols.DeleteSweep,
                tint = tint,
                onClick = { if (!isResettingMarmot) showResetMarmotDialog = true },
            )
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
private fun SettingsSectionHeader(title: Int) {
    Text(
        text = stringRes(title),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsNavigationRow(
    title: Int,
    icon: MaterialSymbol,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 16.dp, horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = icon,
            contentDescription = stringRes(title),
            modifier = Modifier.size(24.dp),
            tint = tint,
        )
        Text(
            text = stringRes(title),
            fontSize = 18.sp,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    title: Int,
    iconPainter: Int,
    iconPainterRef: Int,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 16.dp, horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterRes(iconPainter, iconPainterRef),
            contentDescription = stringRes(title),
            modifier = Modifier.size(24.dp),
            tint = tint,
        )
        Text(
            text = stringRes(title),
            fontSize = 18.sp,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
