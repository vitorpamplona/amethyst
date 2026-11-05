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
package com.vitorpamplona.amethyst.commons.ui.navigation.windows

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.vitorpamplona.amethyst.commons.model.navigation.NavStackEntry
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.navigation_window_title
import com.vitorpamplona.amethyst.commons.ui.app.AppRoot
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.navigation.host.LocalNavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavigationHost
import com.vitorpamplona.amethyst.commons.ui.navigation.host.sharedDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.ui.navigation.tabs.routeTitle
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.EmojiPanelHost
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.EmojiPanelScope
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * The windows layout: every screen that is not a section of the main window gets an OS window of
 * its own, drawn by the platform through [AppRoot.ScreenWindow]. Declared inside the main window's
 * composition, so each window shares the account, its subscriptions and the theme.
 */
@Composable
fun ScreenWindows(
    nav: Nav,
    accountViewModel: AccountViewModel,
    root: AppRoot,
) {
    nav.stacks.windows().forEach { entry ->
        key(entry.id) {
            root.ScreenWindow(
                title = stringRes(Res.string.navigation_window_title, routeTitle(entry.route, accountViewModel)),
                focus =
                    nav.stacks.windowFocus
                        ?.takeIf { it.id == entry.id }
                        ?.serial,
                onClose = { nav.stacks.close(entry.id) },
            ) {
                ScreenWindowContent(entry, nav, accountViewModel, root)
            }
        }
    }
}

@Composable
private fun ScreenWindowContent(
    entry: NavStackEntry,
    nav: Nav,
    accountViewModel: AccountViewModel,
    root: AppRoot,
) {
    val windowNav = remember(entry, nav) { ScreenWindowNav(entry, nav) }
    val destinations =
        remember(windowNav) {
            NavDestinations().apply {
                sharedDestinations(accountViewModel, windowNav)
                root.registerDestinations(this, accountViewModel, nav)
            }
        }

    // The window's own ViewModels, gone with the window.
    val owner = remember(entry.id) { WindowViewModelStoreOwner() }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }

    CompositionLocalProvider(
        LocalViewModelStoreOwner provides owner,
        // Laid out by the window's own size: a narrow tiled window reads like a phone column.
        LocalScreenLayout provides root.rememberScreenLayoutSpec(),
        LocalNavDestinations provides destinations,
    ) {
        EmojiPanelScope {
            EmojiPanelHost {
                NavigationHost(
                    visible = listOf(entry),
                    retained = listOf(entry),
                    destinations = destinations,
                    animated = false,
                    onBack = {},
                    backArrows = false,
                )
            }
        }
    }
}

/**
 * A screen window's navigation: it holds one screen, so there is no back arrow and no bar. Going
 * back closes the window; the next step of a flow replaces its screen; everything else opens, or
 * raises, another window through the main [Nav].
 */
private class ScreenWindowNav(
    private val entry: NavStackEntry,
    private val main: Nav,
) : INav by main {
    @Composable
    override fun canPop(): Boolean = false

    @Composable
    override fun showsBottomBar(): Boolean = false

    override fun popBack() {
        main.navigationScope.launch { main.stacks.close(entry.id) }
    }

    override fun <T : Route> popUpTo(
        route: Route,
        klass: KClass<T>,
    ) {
        main.navigationScope.launch { main.stacks.replace(entry.id, route) }
    }
}

private class WindowViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}
