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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.vitorpamplona.amethyst.ui.navigation.BOTTOM_NAV_ROOT_KEY
import com.vitorpamplona.amethyst.ui.navigation.isBottomNavRoot
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.getRouteWithArguments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

@Stable
class Nav(
    val controller: NavHostController,
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
            if (getRouteWithArguments(route::class, controller) != route) {
                controller.navigate(route)
            }
        }
    }

    override fun nav(computeRoute: suspend () -> Route?) {
        navigationScope.launch {
            ime.settle()
            val route = computeRoute()
            if (route != null && getRouteWithArguments(route::class, controller) != route) {
                controller.navigate(route)
            }
        }
    }

    override fun newStack(route: Route) {
        navigationScope.launch {
            ime.settle()
            controller.navigate(route) {
                popUpTo(route) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    override fun navBottomBar(route: Route) {
        navigationScope.launch {
            ime.settle()
            controller.navigate(route) {
                // Clear sibling bottom-nav entries but keep Home (the start
                // destination) below, so back-swipe from any tab returns to
                // Home and back-swipe from Home leaves the app.
                //
                // saveState/restoreState is what makes a tab survive being left. Without them the
                // popped entry is DESTROYED, taking its ViewModelStore with it — so every return to
                // a tab rebuilt its screen-scoped ViewModels from nothing and re-fetched. On the
                // Buzz community tab that is a visible ~1s of empty Direct Messages plus a channel
                // list that reshuffles as data lands; other tabs pay it as lost scroll position.
                popUpTo(Route.Home) {
                    inclusive = false
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            // saveState/restoreState are keyed by DESTINATION, and one destination covers every route
            // that differs only in its arguments: all web apps are `Route.WebApp/{url}`, all pinned chats
            // their own one pattern, and `Route.Notification?scrollToEventId={scrollToEventId}` is both the
            // tab (no id) and the deep link a tapped push notification lands on (the id to scroll to). So
            // the restore above can hand back an entry carrying *another* route's arguments — with two web
            // apps pinned, tapping the second one landed on the first one's URL; after a push notification,
            // tapping the Notifications tab restored the entry that scrolls to that one event.
            //
            // When what we landed on isn't the route that was asked for, take the tab fresh (no
            // restoreState, and no launchSingleTop — the top is the entry we do not want to reuse). Its
            // saved scroll/ViewModel state is not recoverable in that case, but the user lands on the tab
            // they tapped. Tabs the restore reproduces argument-for-argument still restore normally, which
            // is what this is here for.
            //
            // Read back what we landed on instead of looking the requested route up with
            // `getBackStackEntry(route)`: that lookup matches on arguments too and *throws* when they
            // differ, which is how this mismatch used to surface — `No destination with route
            // …Notification?scrollToEventId=null is on the NavController's back stack`.
            if (getRouteWithArguments(route::class, controller) != route) {
                controller.navigate(route) {
                    popUpTo(Route.Home) {
                        inclusive = false
                        saveState = true
                    }
                }
            }

            // Mark this entry as a tab root: hides the back arrow in canPop
            // and skips the horizontal slide in composableFromEnd.
            controller.currentBackStackEntry?.savedStateHandle?.set(BOTTOM_NAV_ROOT_KEY, true)
        }
    }

    @Composable
    override fun canPop(): Boolean {
        // Decide the back arrow / bottom-bar visibility from THIS screen's own
        // back-stack entry — the one the NavHost hands to each destination
        // through LocalViewModelStoreOwner — instead of the globally-current
        // entry.
        //
        // A pop commits the moment it is accepted (most visibly during a
        // predictive back-swipe, whose exit animation is long and finger-driven):
        // controller.currentBackStackEntry flips to the destination while the
        // screen being dismissed is still on screen, sliding out and still
        // composing its top bar. Reading the global entry there re-evaluated
        // canPop against the incoming destination and dropped the arrow before
        // the outgoing screen had finished leaving. An entry is intrinsic to its
        // screen and never changes for the life of that composition, so the arrow
        // now stays put until the screen itself is gone.
        //
        // Outside a NavHost destination (shell chrome, drawer) the current owner
        // is the account-scoped ViewModelStoreOwner, not an entry; fall back to
        // the globally-current entry so those callers keep their prior behavior.
        val entry =
            (LocalViewModelStoreOwner.current as? NavBackStackEntry)
                ?: controller.currentBackStackEntry
                ?: return false

        // Hidden on tab roots (reached via the bottom nav) and on Home (the
        // graph's start destination): nothing sits below either that a back
        // arrow could return to. Every other entry is a push on top of Home,
        // so it can always pop.
        if (entry.isBottomNavRoot()) return false
        return entry.destination.id != controller.graph.findStartDestination().id
    }

    override fun popBack() {
        navigationScope.launch {
            ime.settle()
            controller.navigateUp()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun <T : Route> popUpTo(
        route: Route,
        klass: KClass<T>,
    ) {
        navigationScope.launch {
            ime.settle()
            controller.navigate(route) {
                popUpTo(klass) { inclusive = true }
            }
        }
    }
}
