/**
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
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

class ObservableNav(
    val sourceNav: INav,
    override val scope: CoroutineScope,
    val onBeforeNavigate: () -> Unit,
) : INav {
    override val drawerState: DrawerState = sourceNav.drawerState

    override fun closeDrawer() {
        sourceNav.closeDrawer()
    }

    override fun openDrawer() {
        sourceNav.openDrawer()
    }

    override fun nav(route: Route) {
        scope.launch {
            onBeforeNavigate()
        }
        sourceNav.nav(route)
    }

    override fun nav(computeRoute: suspend () -> Route?) {
        scope.launch {
            onBeforeNavigate()
        }
        sourceNav.nav(computeRoute)
    }

    override fun newStack(route: Route) {
        scope.launch {
            onBeforeNavigate()
        }
        sourceNav.newStack(route)
    }

    override fun popBack() {
        scope.launch {
            onBeforeNavigate()
        }
        sourceNav.popBack()
    }

    override fun <T : Route> popUpTo(
        route: Route,
        upToClass: KClass<T>,
    ) {
        scope.launch {
            onBeforeNavigate()
        }
        sourceNav.popUpTo(route, upToClass)
    }
}
