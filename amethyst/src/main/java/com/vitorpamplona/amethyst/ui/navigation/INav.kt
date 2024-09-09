/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Composable
fun rememberNav(): Nav {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    return remember(navController, scope) {
        Nav(navController, scope)
    }
}

@Composable
fun rememberExtendedNav(
    nav: INav,
    onClose: () -> Unit,
): INav = nav.onNavigate(onClose)

@Stable
interface INav {
    val drawerState: DrawerState

    fun nav(route: String)

    fun newStack(route: String)

    fun popBack()

    fun popUpTo(
        route: String,
        upTo: String,
    )

    fun closeDrawer()

    fun openDrawer()
}

@Stable
class Nav(
    val controller: NavHostController,
    val scope: CoroutineScope,
) : INav {
    override val drawerState = DrawerState(DrawerValue.Closed)

    override fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    override fun openDrawer() {
        scope.launch { drawerState.open() }
    }

    override fun nav(route: String) {
        scope.launch {
            if (getRouteWithArguments(controller) != route) {
                controller.navigate(route)
            }
        }
    }

    override fun newStack(route: String) {
        scope.launch {
            controller.navigate(route) {
                popUpTo(Route.Home.route)
                launchSingleTop = true
            }
        }
    }

    override fun popBack() {
        scope.launch {
            controller.navigateUp()
        }
    }

    override fun popUpTo(
        route: String,
        upTo: String,
    ) {
        scope.launch {
            controller.navigate(route) { popUpTo(route) { inclusive = true } }
        }
    }
}

@Stable
object EmptyNav : INav {
    override val drawerState = DrawerState(DrawerValue.Closed)

    override fun closeDrawer() {
        runBlocking {
            drawerState.close()
        }
    }

    override fun openDrawer() {
        runBlocking {
            drawerState.open()
        }
    }

    override fun nav(route: String) {
    }

    override fun newStack(route: String) {
    }

    override fun popBack() {
    }

    override fun popUpTo(
        route: String,
        upTo: String,
    ) {
    }
}

fun INav.onNavigate(runOnNavigate: () -> Unit): INav = ObservableNavigate(this, runOnNavigate)

class ObservableNavigate(
    val nav: INav,
    val onNavigate: () -> Unit,
) : INav {
    override val drawerState: DrawerState = nav.drawerState

    override fun nav(route: String) {
        onNavigate()
        nav.nav(route)
    }

    override fun newStack(route: String) {
        onNavigate()
        nav.newStack(route)
    }

    override fun popBack() {
        onNavigate()
        nav.popBack()
    }

    override fun popUpTo(
        route: String,
        upTo: String,
    ) {
        onNavigate()
        nav.popUpTo(route, upTo)
    }

    override fun closeDrawer() {
        nav.closeDrawer()
    }

    override fun openDrawer() {
        nav.openDrawer()
    }
}
