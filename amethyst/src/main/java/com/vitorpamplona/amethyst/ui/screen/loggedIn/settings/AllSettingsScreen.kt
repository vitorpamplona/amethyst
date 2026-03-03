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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
        Column(Modifier.padding(padding)) {
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
                title = R.string.media_servers,
                icon = Icons.Outlined.CloudUpload,
                tint = tint,
                onClick = { nav.nav(Route.EditMediaServers) },
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
                title = R.string.translations,
                icon = Icons.Outlined.Translate,
                tint = tint,
                onClick = { nav.nav(Route.UserSettings) },
            )
            accountViewModel.account.settings.keyPair.privKey?.let {
                HorizontalDivider()
                SettingsNavigationRow(
                    title = R.string.backup_keys,
                    icon = Icons.Outlined.Key,
                    tint = tint,
                    onClick = { nav.nav(Route.AccountBackup) },
                )
            }
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
                title = R.string.ui_preferences,
                icon = Icons.Outlined.Settings,
                tint = tint,
                onClick = { nav.nav(Route.Settings) },
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
