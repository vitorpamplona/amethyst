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

import android.annotation.SuppressLint
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Stable
import androidx.navigation.NavHostController
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.getRouteWithArguments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

@Stable
class Nav(
    val controller: NavHostController,
    override val navigationScope: CoroutineScope,
) : INav {
    override val drawerState = DrawerState(DrawerValue.Closed)

    override fun closeDrawer() {
        navigationScope.launch { drawerState.close() }
    }

    override fun openDrawer() {
        navigationScope.launch { drawerState.open() }
    }

    override fun nav(route: Route) {
        navigationScope.launch {
            if (getRouteWithArguments(controller) != route) {
                controller.navigate(route)
            }
        }
    }

    override fun nav(computeRoute: suspend () -> Route?) {
        navigationScope.launch {
            val route = computeRoute()
            if (route != null && getRouteWithArguments(controller) != route) {
                controller.navigate(route)
            }
        }
    }

    override fun newStack(route: Route) {
        navigationScope.launch {
            controller.navigate(route) {
                popUpTo(route) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    override fun popBack() {
        navigationScope.launch {
            controller.navigateUp()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun <T : Route> popUpTo(
        route: Route,
        klass: KClass<T>,
    ) {
        navigationScope.launch {
            controller.navigate(route) {
                popUpTo<T>(klass) { inclusive = true }
            }
        }
    }
}
