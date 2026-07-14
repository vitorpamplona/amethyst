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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.layouts.NotificationPanelWidth
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Size12dp
import com.vitorpamplona.amethyst.ui.theme.Size16dp
import com.vitorpamplona.amethyst.ui.theme.Size22Modifier
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer

/**
 * The docked notification feed shown on very wide windows, to the right of the center pane.
 * It renders the same card feed body as the Notifications screen (same last-read marking —
 * cards mark themselves read as they become visible here, exactly as they would on the
 * screen, so the new-item dot only lights for items the panel hasn't displayed). When the
 * user has split notifications enabled, the panel shows the Following feed to match the
 * screen's default tab. Tapping the header opens the full screen, which adds the summary
 * chart and the Following/Everyone tabs.
 */
@Composable
fun NotificationSidePanel(
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val split by accountViewModel.account.settings.splitNotificationsEnabled
        .collectAsStateWithLifecycle()

    val notifFeedContentState =
        if (split) {
            accountViewModel.feedStates.notificationsFollowing
        } else {
            accountViewModel.feedStates.notifications
        }
    val scrollStateKey =
        if (split) {
            ScrollStateKeys.NOTIFICATION_SIDE_PANEL_FOLLOWING
        } else {
            ScrollStateKeys.NOTIFICATION_SIDE_PANEL
        }

    WatchAccountForNotifications(notifFeedContentState, accountViewModel)

    Column(
        modifier
            .width(NotificationPanelWidth)
            .fillMaxHeight()
            .windowInsetsPadding(
                WindowInsets.systemBars.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End,
                ),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { nav.nav(Route.Notification()) }
                    .padding(horizontal = Size16dp, vertical = Size12dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol = MaterialSymbols.Notifications,
                contentDescription = null,
                modifier = Size22Modifier,
                tint = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = StdHorzSpacer)
            Text(
                text = stringRes(R.string.route_notifications),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        HorizontalDivider(thickness = DividerThickness)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            SingleNotificationsBody(
                notifFeedContentState = notifFeedContentState,
                notifPolls = accountViewModel.feedStates.notificationsOpenPolls,
                scrollToEventId = null,
                accountViewModel = accountViewModel,
                nav = nav,
                scrollStateKey = scrollStateKey,
            )
        }
    }
}
