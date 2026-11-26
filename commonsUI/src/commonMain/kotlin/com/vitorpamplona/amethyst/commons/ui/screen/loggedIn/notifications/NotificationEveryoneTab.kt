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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.notification_tab_everyone
import com.vitorpamplona.amethyst.commons.resources.notification_tab_mode_everyone_description
import com.vitorpamplona.amethyst.commons.resources.notification_tab_mode_menu
import com.vitorpamplona.amethyst.commons.resources.notification_tab_mode_trust_description
import com.vitorpamplona.amethyst.commons.resources.notification_tab_mode_trust_inactive
import com.vitorpamplona.amethyst.commons.resources.notification_tab_web_of_trust
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The split screen's second tab. Its label says who the tab shows: "Web of Trust" while the
 * filter is on and a network is active, else "Everyone". Tapping it while it is the selected tab
 * opens a menu to switch between the two. The menu's choice is the stored one, as in Settings, so
 * a choice made before a network is ready still applies once it is; while none is active, the Web
 * of Trust choice says so and points to its settings.
 */
@Composable
fun NotificationEveryoneTab(
    selected: Boolean,
    onSelect: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val useTrust by accountViewModel.account.settings.everyoneNotificationsUseTrust
        .collectAsStateWithLifecycle()
    val verdicts by accountViewModel.account.trustVerdicts.collectAsStateWithLifecycle()
    val trustActive = verdicts.isActive
    val filtering = useTrust && trustActive

    // Not saveable: a menu reopening by itself after a rotation is a surprise. Closed when the
    // user swipes away, so it never hangs off a tab that is not selected.
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(selected) { if (!selected) menuOpen = false }

    Tab(
        selected = selected,
        onClick = { if (selected) menuOpen = true else onSelect() },
        text = {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringRes(
                            if (filtering) Res.string.notification_tab_web_of_trust else Res.string.notification_tab_everyone,
                        ),
                    )
                    Icon(
                        symbol = MaterialSymbols.ExpandMore,
                        contentDescription = stringRes(Res.string.notification_tab_mode_menu),
                        modifier = Size20Modifier,
                        tint = MaterialTheme.colorScheme.placeholderText,
                    )
                }

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.widthIn(max = 320.dp),
                ) {
                    TabModeItem(
                        title = stringRes(Res.string.notification_tab_web_of_trust),
                        description =
                            stringRes(
                                if (trustActive) Res.string.notification_tab_mode_trust_description else Res.string.notification_tab_mode_trust_inactive,
                            ),
                        selected = useTrust,
                        onClick = {
                            menuOpen = false
                            accountViewModel.account.settings.changeEveryoneNotificationsUseTrust(true)
                            if (!trustActive) nav.nav(Route.WebOfTrust)
                        },
                    )
                    TabModeItem(
                        title = stringRes(Res.string.notification_tab_everyone),
                        description = stringRes(Res.string.notification_tab_mode_everyone_description),
                        selected = !useTrust,
                        onClick = {
                            menuOpen = false
                            accountViewModel.account.settings.changeEveryoneNotificationsUseTrust(false)
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun TabModeItem(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        leadingIcon = { RadioButton(selected = selected, onClick = null) },
        text = {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            }
        },
        onClick = onClick,
    )
}
