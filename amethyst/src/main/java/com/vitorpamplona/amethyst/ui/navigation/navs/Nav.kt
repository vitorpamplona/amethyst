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
package com.vitorpamplona.amethyst.ui.navigation.navs

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * The back-stack entry of the screen being composed, provided by the navigation display to each
 * destination. Null outside one (the shell chrome, the drawer).
 */
val LocalNavStackEntry = staticCompositionLocalOf<NavStackEntry?> { null }

@Stable
class Nav(
    val stacks: NavBackStacks,
    override val navigationScope: CoroutineScope,
    /**
     * Awaited before every transition below. Leaving a screen while the soft keyboard is still
     * animating strands `imePadding()` app-wide; see [ImeSettler]. Every in-app navigation goes
     * through this class, so this is the one place that has to get it right — no screen, top bar
     * or back handler needs to think about the keyboard on its way out.
     */
    private val ime: ImeSettler = ImeSettler.None,
) : INav {
    override val drawerState = DrawerState(DrawerValue.Closed)

    /** Set by the shell when the layout tier docks the drawer permanently. */
    override var isDrawerDocked: Boolean by mutableStateOf(false)

    /** The screen on top. Snapshot state: composables reading it recompose when it changes. */
    val currentRoute: Route get() = stacks.topRoute

    override fun closeDrawer() {
        navigationScope.launch { drawerState.close() }
    }

    override fun openDrawer() {
        // Nothing renders the modal drawer while it is docked; opening the state would
        // only strand an Open value for the next modal tier to trip over.
        if (isDrawerDocked) return
        navigationScope.launch { drawerState.open() }
    }

    override fun nav(route: Route) {
        navigationScope.launch {
            ime.settle()
            stacks.push(route)
        }
    }

    override fun nav(computeRoute: suspend () -> Route?) {
        navigationScope.launch {
            ime.settle()
            computeRoute()?.let { stacks.push(it) }
        }
    }

    /** Same push as [nav], marked [NavStackEntry.drawerRoot] so [showsBottomBar] keeps the bar on it. */
    override fun navDrawer(route: Route) {
        navigationScope.launch {
            ime.settle()
            stacks.push(route, drawerRoot = true)
        }
    }

    override fun navDrawer(computeRoute: suspend () -> Route?) {
        navigationScope.launch {
            ime.settle()
            computeRoute()?.let { stacks.push(it, drawerRoot = true) }
        }
    }

    override fun newStack(route: Route) {
        navigationScope.launch {
            ime.settle()
            stacks.newStack(route)
        }
    }

    /**
     * A nav-bar tap asks for a tab, never for whatever the user pushed on top of one; see
     * [NavBackStacks.switchTab]. On phones the bar shows only on tab roots and on drawer screens, but
     * the large-screen rail stays on screen the whole time and is routinely tapped from three deep.
     */
    override fun navBottomBar(route: Route) {
        navigationScope.launch {
            ime.settle()
            stacks.switchTab(route)
        }
    }

    @Composable
    override fun canPop(): Boolean {
        // Decide the back arrow / bottom-bar visibility from THIS screen's own entry, not the top
        // of the stack. A pop commits the moment it is accepted (most visibly during a predictive
        // back-swipe, whose exit animation is long and finger-driven): the top flips to the
        // destination while the screen being dismissed is still on screen, sliding out and still
        // composing its top bar. Reading the top there dropped the arrow before the outgoing screen
        // had finished leaving. An entry is intrinsic to its screen, so the arrow stays put until
        // the screen itself is gone. Outside a destination (shell chrome, drawer) use the top.
        return canPop(LocalNavStackEntry.current ?: stacks.top)
    }

    @Composable
    override fun showsBottomBar(): Boolean {
        val entry = LocalNavStackEntry.current ?: stacks.top
        // Drawer destinations can pop (they sit on whatever screen the drawer was opened over),
        // but they are top-level sections, so they keep the bar the tab roots have.
        return entry.drawerRoot || !canPop(entry)
    }

    /**
     * Hidden on tab roots (reached via the bottom nav) and on Home (the start destination): nothing
     * sits below either that a back arrow could return to. Every other entry is a push on top of
     * Home, so it can always pop.
     */
    private fun canPop(entry: NavStackEntry): Boolean = !entry.tabRoot && entry.route != Route.Home

    override fun popBack() {
        navigationScope.launch {
            ime.settle()
            stacks.pop()
        }
    }

    override fun <T : Route> popUpTo(
        route: Route,
        klass: KClass<T>,
    ) {
        navigationScope.launch {
            ime.settle()
            stacks.popUpTo(route, klass)
        }
    }
}
