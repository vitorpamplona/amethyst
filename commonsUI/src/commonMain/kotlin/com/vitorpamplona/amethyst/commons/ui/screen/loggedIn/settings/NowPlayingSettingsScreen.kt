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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingAccess
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingSettingsState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.now_playing_access_granted
import com.vitorpamplona.amethyst.commons.resources.now_playing_access_needed
import com.vitorpamplona.amethyst.commons.resources.now_playing_apps
import com.vitorpamplona.amethyst.commons.resources.now_playing_apps_empty
import com.vitorpamplona.amethyst.commons.resources.now_playing_description
import com.vitorpamplona.amethyst.commons.resources.now_playing_grant_access
import com.vitorpamplona.amethyst.commons.resources.now_playing_manage_access
import com.vitorpamplona.amethyst.commons.resources.now_playing_settings
import com.vitorpamplona.amethyst.commons.resources.now_playing_share_in_app
import com.vitorpamplona.amethyst.commons.resources.now_playing_share_in_app_description
import com.vitorpamplona.amethyst.commons.resources.now_playing_share_other_apps
import com.vitorpamplona.amethyst.commons.resources.now_playing_share_other_apps_description
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * Settings for sharing what the user listens to as their NIP-38 music status.
 *
 * [access] is the platform permission other apps' playback needs (Android: notification access);
 * null where none is needed.
 */
@Composable
fun NowPlayingSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
    access: NowPlayingAccess? = null,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(Res.string.now_playing_settings), nav)
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            NowPlayingSettingsContent(accountViewModel.account.nowPlayingSettings, access)
        }
    }
}

/**
 * The body of the settings screen, also embedded by front ends with their own settings layout.
 * [showInApp] hides the in-app toggle where the app has no music or podcast player of its own.
 */
@Composable
fun NowPlayingSettingsContent(
    state: NowPlayingSettingsState,
    access: NowPlayingAccess?,
    showInApp: Boolean = true,
) {
    val settings by state.flow.collectAsState()

    Text(
        text = stringRes(Res.string.now_playing_description),
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
    )

    if (showInApp) {
        SwitchRow(
            title = stringRes(Res.string.now_playing_share_in_app),
            description = stringRes(Res.string.now_playing_share_in_app_description),
            checked = settings.shareInApp,
            onCheckedChange = state::setShareInApp,
        )
    }

    SwitchRow(
        title = stringRes(Res.string.now_playing_share_other_apps),
        description = stringRes(Res.string.now_playing_share_other_apps_description),
        checked = settings.shareOtherApps,
        onCheckedChange = state::setShareOtherApps,
    )

    if (!settings.shareOtherApps) {
        Spacer(modifier = Modifier.height(16.dp))
        return
    }

    if (access != null) {
        AccessRow(access)
    }

    HorizontalDivider(thickness = 4.dp, modifier = Modifier.padding(vertical = 8.dp))

    Text(
        text = stringRes(Res.string.now_playing_apps),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )

    if (settings.knownApps.isEmpty()) {
        Text(
            text = stringRes(Res.string.now_playing_apps_empty),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
    } else {
        settings.knownApps.entries
            .sortedBy { it.value.lowercase() }
            .forEach { (id, label) ->
                SwitchRow(
                    title = label,
                    description = id.takeIf { it != label },
                    checked = id !in settings.blockedApps,
                    onCheckedChange = { allowed -> state.setAppBlocked(id, !allowed) },
                )
            }
    }

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun AccessRow(access: NowPlayingAccess) {
    // The user grants access in system settings and comes back, so check again on every resume.
    var granted by remember(access) { mutableStateOf(access.hasAccess()) }
    LifecycleResumeEffect(access) {
        granted = access.hasAccess()
        onPauseOrDispose {}
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(if (granted) Res.string.now_playing_access_granted else Res.string.now_playing_access_needed),
            fontSize = 13.sp,
            color = if (granted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(16.dp))
        OutlinedButton(onClick = access::requestAccess) {
            Text(stringRes(if (granted) Res.string.now_playing_manage_access else Res.string.now_playing_grant_access))
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            if (description != null) {
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
