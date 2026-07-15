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
package com.vitorpamplona.amethyst.ui.navigation.bottombars

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vitorpamplona.amethyst.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.getRouteWithArguments
import com.vitorpamplona.amethyst.ui.navigation.topbars.LoggedInUserPictureDrawer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

/**
 * Medium-width windows: a left rail that carries the same user-configured destinations as the
 * phone bottom bar ([BottomBarEntry] list, built-ins and pinned favorites interleaved), so the
 * user's customization and new-item dots carry over. The header avatar opens the modal drawer,
 * mirroring the avatar button in the phone top bars. Re-tapping the selected item routes
 * through [TabReselectCoordinator] to the screen's own scroll-to-top/refresh handler.
 */
@Composable
fun AppNavigationRail(
    nav: Nav,
    accountViewModel: AccountViewModel,
) {
    val items by accountViewModel.settings.uiSettingsFlow.bottomBarItems
        .collectAsStateWithLifecycle()
    val favorites by FavoriteAppsRegistry.favorites.collectAsStateWithLifecycle()
    val favoritesById = remember(favorites) { favorites.associateBy { it.id } }

    val reselectCoordinator = LocalTabReselectCoordinator.current

    val navBackStackEntry by nav.controller.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationRail(
        containerColor = MaterialTheme.colorScheme.background,
        header = {
            // Same affordance as the phone top bars: the account avatar opens the drawer.
            LoggedInUserPictureDrawer(accountViewModel, nav::openDrawer)
        },
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Same entry list + resolution as the phone bottom bar; only the item chrome and the
            // selection source (live back stack vs the bar's passed-in route) differ per surface.
            items.forEach { entry ->
                val slot = rememberBottomBarSlot(entry, favoritesById, accountViewModel) ?: return@forEach
                val selected = remember(navBackStackEntry, slot.route) { railSelected(slot.route, nav.controller, currentDestination) }
                NavigationRailItem(
                    selected = selected,
                    onClick = {
                        if (selected) {
                            reselectCoordinator.reselect(slot.route)
                        } else {
                            nav.navBottomBar(slot.route)
                        }
                    },
                    icon = { slot.icon(selected) },
                )
            }
        }
    }
}

/**
 * Whether [route] is the rail's currently-selected destination. Parameterized routes (favorites and
 * pinned groups carry a url / coordinate / id) must match the full route — class matching alone would
 * light up every pinned app or group of the same kind; parameterless object routes match on class.
 */
private fun railSelected(
    route: Route,
    controller: NavHostController,
    currentDestination: NavDestination?,
): Boolean =
    when (route) {
        is Route.WebApp -> getRouteWithArguments(Route.WebApp::class, controller) == route
        is Route.NostrApp -> getRouteWithArguments(Route.NostrApp::class, controller) == route
        is Route.PublicChatChannel -> getRouteWithArguments(Route.PublicChatChannel::class, controller) == route
        is Route.RelayGroup -> getRouteWithArguments(Route.RelayGroup::class, controller) == route
        is Route.ConcordServer -> getRouteWithArguments(Route.ConcordServer::class, controller) == route
        else -> currentDestination?.hasRoute(route::class) == true
    }
