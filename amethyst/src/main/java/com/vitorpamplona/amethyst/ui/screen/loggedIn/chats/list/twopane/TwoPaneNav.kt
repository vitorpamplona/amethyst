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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.list.twopane

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.mutableStateOf
import com.vitorpamplona.amethyst.ui.navigation.INav
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TwoPaneNav(
    val nav: INav,
    val scope: CoroutineScope,
) : INav {
    override val drawerState: DrawerState = nav.drawerState

    val innerNav = mutableStateOf<RouteId?>(null)

    override fun nav(route: String) {
        if (route.startsWith("Room/") || route.startsWith("Channel/")) {
            innerNav.value = RouteId(route.substringBefore("/"), route.substringAfter("/"))
        } else {
            nav.nav(route)
        }
    }

    override fun nav(routeMaker: suspend () -> String) {
        scope.launch(Dispatchers.Default) {
            val route = routeMaker()
            if (route.startsWith("Room/") || route.startsWith("Channel/")) {
                innerNav.value = RouteId(route.substringBefore("/"), route.substringAfter("/"))
            } else {
                nav.nav(route)
            }
        }
    }

    override fun newStack(route: String) {
        nav.newStack(route)
    }

    override fun popBack() {
        nav.popBack()
    }

    override fun popUpTo(
        route: String,
        upTo: String,
    ) {
        nav.popUpTo(route, upTo)
    }

    override fun closeDrawer() {
        nav.closeDrawer()
    }

    override fun openDrawer() {
        nav.openDrawer()
    }

    data class RouteId(
        val route: String,
        val id: String,
    )
}
