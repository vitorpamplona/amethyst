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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.twopane

import androidx.compose.runtime.mutableStateOf
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class TwoPaneNav(
    private val nav: INav,
    override val navigationScope: CoroutineScope,
) : INav by nav {
    val innerNav = mutableStateOf<Route?>(null)

    override fun nav(route: Route) {
        if (route is Route.Room || route is Route.PublicChatChannel) {
            innerNav.value = route
        } else {
            nav.nav(route)
        }
    }

    override fun nav(computeRoute: suspend () -> Route?) {
        navigationScope.launch {
            val route = computeRoute()
            if (route != null) {
                if (route is Route.Room || route is Route.PublicChatChannel) {
                    innerNav.value = route
                } else {
                    nav.nav(route)
                }
            }
        }
    }
}
