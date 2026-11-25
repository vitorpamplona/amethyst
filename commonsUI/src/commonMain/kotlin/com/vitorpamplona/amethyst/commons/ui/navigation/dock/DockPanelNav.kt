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

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

/**
 * A panel's navigation: what it opens goes to the main window's [main] navigation, beside the
 * panel, so a link in the assistant's answer or a card of the notification feed opens there. A
 * panel has no history of its own: no back arrow, and finishing a job ([popBack]) leaves the main
 * screen alone.
 */
class DockPanelNav(
    private val main: INav,
) : INav {
    override val navigationScope: CoroutineScope get() = main.navigationScope
    override val drawerState: DrawerState get() = main.drawerState
    override val isDrawerDocked: Boolean get() = main.isDrawerDocked

    override fun closeDrawer() = main.closeDrawer()

    override fun openDrawer() = main.openDrawer()

    override fun nav(route: Route) = main.nav(route)

    override fun nav(computeRoute: suspend () -> Route?) = main.nav(computeRoute)

    override fun newStack(route: Route) = main.nav(route)

    override fun navBottomBar(route: Route) = main.navBottomBar(route)

    override fun navDrawer(route: Route) = main.navDrawer(route)

    override fun navDrawer(computeRoute: suspend () -> Route?) = main.navDrawer(computeRoute)

    @Composable
    override fun canPop(): Boolean = false

    @Composable
    override fun showsBottomBar(): Boolean = false

    @Composable
    override fun titleShownOutside(): Boolean = true

    override fun popBack() {}

    override fun <T : Route> popUpTo(
        route: Route,
        klass: KClass<T>,
    ) = main.nav(route)
}
