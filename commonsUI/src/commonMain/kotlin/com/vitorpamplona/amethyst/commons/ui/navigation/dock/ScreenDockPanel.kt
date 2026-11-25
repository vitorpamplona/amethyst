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
package com.vitorpamplona.amethyst.commons.ui.navigation.dock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.vitorpamplona.amethyst.commons.model.dock.PanelId
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dock_pinned_screen
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.layouts.NavigationStyle
import com.vitorpamplona.amethyst.commons.ui.layouts.ScreenLayoutSpec
import com.vitorpamplona.amethyst.commons.ui.navigation.deck.DeckColumnNav
import com.vitorpamplona.amethyst.commons.ui.navigation.host.LocalNavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavigationHost
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.RegisterOnScreen
import com.vitorpamplona.amethyst.commons.ui.navigation.tabs.routeIcon
import com.vitorpamplona.amethyst.commons.ui.navigation.tabs.routeTitle
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.EmojiPanelHost
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/** How a pinned screen lays itself out: a column, as in a deck column. */
private val PanelScreenLayout = ScreenLayoutSpec(NavigationStyle.NAV_RAIL, hasRoomForNotificationPanel = false)

/**
 * Any screen pinned to the dock (a profile, a hashtag, a chat, a relay console), as a tool window
 * of its own: named and drawn with the screen's own title and icon, and showing the screen with a
 * history of its own, as a deck column does: what it opens stacks inside it, and its back arrow
 * goes back there. The drawer is still the app's.
 */
internal fun screenPanelSpec(
    id: PanelId,
    route: Route,
    accountViewModel: AccountViewModel,
): DockPanelSpec =
    DockPanelSpec(
        id = id,
        icon = routeIcon(route, accountViewModel),
        title = Res.string.dock_pinned_screen,
        defaultWidth = 400f,
        name = { routeTitle(route, it) },
        content = { scope -> ScreenPanel(route, scope) },
    )

@Composable
private fun ScreenPanel(
    route: Route,
    scope: DockPanelScope,
) {
    val coroutines = rememberCoroutineScope()
    // The history lives in the dock, so folding the panel or moving it to a window keeps where it was.
    val inner = remember(scope.id) { Nav(scope.dock.screenStacks(scope.id, route), coroutines) }
    RegisterOnScreen("Dock panel", inner)
    val destinations = LocalNavDestinations.current ?: return
    // Back and Esc stay with the main screen: the panel goes back through its own top bar's arrow.
    val backOwner = rememberNavigationEventDispatcherOwner(enabled = false)
    CompositionLocalProvider(
        LocalScreenLayout provides PanelScreenLayout,
        LocalNavigationEventDispatcherOwner provides backOwner,
    ) {
        EmojiPanelHost {
            NavigationHost(inner, destinations, navFor = { DeckColumnNav(inner.forEntry(it), scope.nav) })
        }
    }
}
