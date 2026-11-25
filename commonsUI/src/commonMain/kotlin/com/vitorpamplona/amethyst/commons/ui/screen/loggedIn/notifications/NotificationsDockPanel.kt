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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.dock.PanelId
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.route_notifications
import com.vitorpamplona.amethyst.commons.ui.layouts.NotificationPanelWidth
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.DockPanelSpec
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.PanelBadge

/**
 * The notification feed as a dock panel, on every platform: the same feed the Notifications screen
 * shows, its refresh in the header, and a strip badge counting the cards that landed while folded.
 * It steps out of the dock on the Notifications screen, which it would duplicate.
 */
val NotificationsDockPanel =
    DockPanelSpec(
        id = PanelId.NOTIFICATIONS,
        icon = MaterialSymbols.Notifications,
        title = Res.string.route_notifications,
        defaultWidth = NotificationPanelWidth.value,
        hidesOn = setOf(Route.Notification::class),
        onTitleClick = { nav -> nav.nav(Route.Notification()) },
        badge = { scope ->
            val missed = rememberMissedNotifications(scope.accountViewModel, scope.foldedAt)
            if (missed > 0) PanelBadge.Count(missed) else PanelBadge.None
        },
        actions = { scope -> NotificationPanelRefreshButton(scope.accountViewModel) },
        content = { scope ->
            NotificationSidePanel(
                accountViewModel = scope.accountViewModel,
                nav = scope.nav,
                modifier = Modifier.fillMaxSize(),
                showHeader = false,
            )
        },
    )
