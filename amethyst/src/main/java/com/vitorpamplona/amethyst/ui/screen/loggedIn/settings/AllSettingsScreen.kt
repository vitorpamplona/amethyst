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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn

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

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.settings), nav::popBack)
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
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
                icon = Icons.Outlined.Sync,
                tint = tint,
                onClick = { nav.nav(Route.EventSync) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.route_import_follows,
                icon = Icons.Outlined.GroupAdd,
                tint = tint,
                onClick = { nav.nav(Route.ImportFollowsSelectUser) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.media_servers,
                icon = Icons.Outlined.CloudUpload,
                tint = tint,
                onClick = { nav.nav(Route.EditMediaServers) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.profile_badges_title,
                icon = Icons.Outlined.MilitaryTech,
                tint = tint,
                onClick = { nav.nav(Route.ProfileBadges) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.favorite_dvms_title,
                icon = Icons.Outlined.AutoAwesome,
                tint = tint,
                onClick = { nav.nav(Route.EditFavoriteAlgoFeeds) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.reactions,
                icon = Icons.Outlined.FavoriteBorder,
                tint = tint,
                onClick = { nav.nav(Route.UpdateReactionType) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.zaps,
                icon = Icons.Outlined.Bolt,
                tint = tint,
                onClick = { nav.nav(Route.UpdateZapAmount()) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.security_filters,
                icon = Icons.Outlined.Security,
                tint = tint,
                onClick = { nav.nav(Route.SecurityFilters) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.call_settings,
                icon = Icons.Outlined.Phone,
                tint = tint,
                onClick = { nav.nav(Route.CallSettings) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.translations,
                icon = Icons.Outlined.Translate,
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
                icon = Icons.Outlined.Search,
                tint = tint,
                onClick = { nav.nav(Route.OtsSettings) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.namecoin_settings,
                icon = Icons.Outlined.Security,
                tint = tint,
                onClick = { nav.nav(Route.NamecoinSettings) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.ui_preferences,
                icon = Icons.Outlined.Settings,
                tint = tint,
                onClick = { nav.nav(Route.Settings) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.reactions_settings,
                icon = Icons.Outlined.ThumbUp,
                tint = tint,
                onClick = { nav.nav(Route.ReactionsSettings) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.bottom_bar_settings,
                icon = Icons.Outlined.Dashboard,
                tint = tint,
                onClick = { nav.nav(Route.BottomBarSettings) },
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = R.string.video_player_settings,
                icon = Icons.Outlined.VideoSettings,
                tint = tint,
                onClick = { nav.nav(Route.VideoPlayerSettings) },
            )
            HorizontalDivider(thickness = 4.dp)
            SettingsSectionHeader(R.string.danger_zone)
            accountViewModel.account.settings.keyPair.privKey?.let {
                SettingsNavigationRow(
                    title = R.string.backup_keys,
                    icon = Icons.Outlined.Key,
                    tint = tint,
                    onClick = { nav.nav(Route.AccountBackup) },
                )
                HorizontalDivider()
                SettingsNavigationRow(
                    title = R.string.request_to_vanish,
                    icon = Icons.Outlined.DeleteForever,
                    tint = tint,
                    onClick = { nav.nav(Route.RequestToVanish) },
                )
                HorizontalDivider()
            }
            SettingsNavigationRow(
                title = R.string.vanish_history,
                icon = Icons.Outlined.History,
                tint = tint,
                onClick = { nav.nav(Route.VanishEvents) },
            )
        }
    }
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
    icon: ImageVector,
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
            imageVector = icon,
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
