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

import androidx.compose.foundation.layout.Box
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
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppIcon
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.favorites.BrowserIconRegistry
import com.vitorpamplona.amethyst.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.favorites.rememberNappletIconModel
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.getRouteWithArguments
import com.vitorpamplona.amethyst.ui.navigation.topbars.LoggedInUserPictureDrawer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size25Modifier
import com.vitorpamplona.amethyst.ui.theme.Size27Modifier
import com.vitorpamplona.amethyst.ui.theme.onSurface65

/**
 * Medium-width windows: a left rail that carries the same user-configured destinations as the
 * phone bottom bar ([BottomBarEntry] list, built-ins and pinned favorites interleaved), so the
 * user's customization and new-item dots carry over. The header avatar opens the modal drawer,
 * mirroring the avatar button in the phone top bars.
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

    // Captured favicons, so a pinned web favorite shows the site's icon instead of the generic globe.
    val iconKeys by BrowserIconRegistry.keys.collectAsStateWithLifecycle()

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
            items.forEach { entry ->
                when (entry) {
                    is BottomBarEntry.BuiltIn -> {
                        val def = NavBarCatalog[entry.item] ?: return@forEach
                        val destination = remember(def, accountViewModel) { def.resolveRoute(accountViewModel) }
                        val selected = currentDestination?.hasRoute(destination::class) == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { if (!selected) nav.navBottomBar(destination) },
                            icon = {
                                Box(Size27Modifier, contentAlignment = Alignment.Center) {
                                    Icon(
                                        symbol = def.icon,
                                        contentDescription = stringRes(def.labelRes),
                                        modifier = Size25Modifier,
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface65,
                                    )
                                    AddNotifIconIfNeeded(destination, accountViewModel, Modifier.align(Alignment.TopEnd))
                                }
                            },
                        )
                    }

                    is BottomBarEntry.Favorite -> {
                        val fav = favoritesById[entry.favoriteId] ?: return@forEach
                        val destination =
                            when (fav) {
                                is FavoriteApp.WebApp -> Route.WebApp(fav.url)
                                is FavoriteApp.NostrApp -> Route.NostrApp(fav.coordinate)
                            }
                        // Favorites carry arguments (url / coordinate), so class matching alone
                        // would light up every pinned app of the same kind; compare the full route.
                        val selected =
                            remember(navBackStackEntry, destination) {
                                when (destination) {
                                    is Route.WebApp -> getRouteWithArguments(Route.WebApp::class, nav.controller) == destination
                                    is Route.NostrApp -> getRouteWithArguments(Route.NostrApp::class, nav.controller) == destination
                                    else -> false
                                }
                            }
                        val iconModel =
                            when (fav) {
                                is FavoriteApp.WebApp ->
                                    remember(fav, iconKeys) {
                                        OmniboxInput.hostOf(fav.url)?.let(BrowserIconRegistry::iconModelFor)
                                    }
                                is FavoriteApp.NostrApp -> rememberNappletIconModel(fav.coordinate)
                            }
                        NavigationRailItem(
                            selected = selected,
                            onClick = { if (!selected) nav.navBottomBar(destination) },
                            icon = {
                                Box(Size27Modifier, contentAlignment = Alignment.Center) {
                                    FavoriteAppIcon(
                                        app = fav,
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface65,
                                        modifier = Size25Modifier,
                                        iconModel = iconModel,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
