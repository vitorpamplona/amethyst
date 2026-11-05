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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.openInBackgroundOnModifiedClick
import com.vitorpamplona.amethyst.commons.ui.navigation.tabs.routeTitle
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.EmojiPanelHost
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.EmojiPanelScope
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * Screen windows: in the windows layout every screen that is not a section of the main window, in
 * the tabs layout the tabs moved out to a window. Each is drawn by the platform through
 * [AppRoot.ScreenWindow], declared inside the main window's composition, so all share the account,
 * its subscriptions and the theme.
 */
@Composable
fun ScreenWindows(
    nav: Nav,
    accountViewModel: AccountViewModel,
    root: AppRoot,
) {
    nav.stacks.windows().forEach { entry ->
        key(entry.id) {
            val spec =
                ScreenWindowSpec(
                    id = entry.id,
                    openerId = nav.stacks.openerOf(entry)?.id,
                    kind = entry.route::class.simpleName ?: "",
                    title = stringRes(Res.string.navigation_window_title, routeTitle(entry.route, accountViewModel)),
                    focus =
                        nav.stacks.windowFocus
                            ?.takeIf { it.id == entry.id }
                            ?.serial,
                    isMovedOutTab = entry.detached,
                    onClose = { nav.stacks.close(entry.id) },
                )
            root.ScreenWindow(spec) {
                ScreenWindowContent(entry, nav, accountViewModel, root)
            }
        }
    }
}

/**
 * What the platform needs to draw a screen window: [id] is its entry, [openerId] the window or
 * section that opened it (to place it beside), [kind] its route's name (to size it like the last
 * one of its kind), [focus] a number that changes whenever it must come to the front. A
 * [isMovedOutTab] window can go back into the tab strip.
 */
@Immutable
data class ScreenWindowSpec(
    val id: Long,
    val openerId: Long?,
    val kind: String,
    val title: String,
    val focus: Int?,
    val isMovedOutTab: Boolean,
    val onClose: () -> Unit,
)

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
                // A Ctrl/Cmd-click here opens what it reaches without taking the focus.
                Box(Modifier.fillMaxSize().openInBackgroundOnModifiedClick(nav)) {
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
}

/**
 * A screen window's navigation: it holds one screen, so there is no back arrow and no bar. Going
 * back closes the window and the next step of a flow replaces its screen. Anything else opens, or
 * raises, another window placed beside this one; a section goes to the main window.
 */
private class ScreenWindowNav(
    private val entry: NavStackEntry,
    private val main: Nav,
) : INav by main {
    override fun nav(route: Route) = main.navFrom(entry.id, route)

    override fun nav(computeRoute: suspend () -> Route?) = main.navFrom(entry.id, computeRoute)

    override fun navDrawer(route: Route) = main.navFrom(entry.id, route)

    override fun navDrawer(computeRoute: suspend () -> Route?) = main.navFrom(entry.id, computeRoute)

    override fun newStack(route: Route) = main.navFrom(entry.id, route)

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
