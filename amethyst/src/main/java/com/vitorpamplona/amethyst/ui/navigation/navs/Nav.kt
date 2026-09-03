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
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.CoroutineStart
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

    /** Number of [transition] blocks parked inside [ImeSettler.settle]. Drives [isNavigating]. */
    private var settling by mutableIntStateOf(0)

    override val isNavigating: Boolean get() = settling > 0

    /**
     * Runs [block] — the only code in the app that moves the NavController's back stack — once the
     * keyboard has settled.
     *
     * ### The window this is guarding
     *
     * `NavHost` does not read the back stack when a predictive back gesture starts. The system's
     * `onBackStarted` only *launches* a coroutine on the composition scope; its body runs a
     * main-thread message later, reads a `collectAsState` mirror of `ComposeNavigator.backStack`,
     * and calls `prepareForTransition` on the top two entries — which validates against the
     * NavController's live back queue and throws `IllegalStateException: Cannot transition entry
     * that is not in the back stack` when the two disagree. The throw lands on the UI dispatcher
     * with no handler above it, so it takes the app down.
     *
     * Every transition here runs on that same dispatcher, so a pop of ours that commits inside
     * that gap is exactly the disagreement it crashes on. Two things keep us out of it:
     *
     *  - [start]: [popBack] runs undispatched, so a back that has nothing to wait for commits on
     *    the caller's own message instead of the next one. Back is the one transition that
     *    competes with the gesture for the same entry, and it is only ever called from an event
     *    callback, so running it inline is safe. The rest stay dispatched — some, like the share
     *    intent handling in `NavigateIfIntentRequested`, are invoked straight from a composable
     *    body, where touching the back stack (and `ComposeNavigator.isPop`, which is snapshot
     *    state the NavHost reads) during composition is its own bug.
     *  - [isNavigating]: while [ImeSettler.settle] holds a transition back — up to
     *    [IME_SETTLE_TIMEOUT_MS], long enough for a user to give up on the tap and swipe instead —
     *    the shell hands the system back gesture to a no-op handler so `NavHost` never starts one
     *    against a stack that is about to move. Only the settle is counted: [nav]'s
     *    `computeRoute` can go to the network, and back must not be dead for that long.
     */
    private fun transition(
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend () -> Unit,
    ) {
        navigationScope.launch(start = start) {
            settling++
            try {
                ime.settle()
            } finally {
                settling--
            }
            block()
        }
    }

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
        transition {
            if (getRouteWithArguments(route::class, controller) != route) {
                controller.navigate(route)
            }
        }
    }

    override fun nav(computeRoute: suspend () -> Route?) {
        transition {
            val route = computeRoute()
            if (route != null && getRouteWithArguments(route::class, controller) != route) {
                controller.navigate(route)
            }
        }
    }

    override fun newStack(route: Route) {
        transition {
            controller.navigate(route) {
                popUpTo(route) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    override fun navBottomBar(route: Route) {
        transition {
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
            // Mark this entry as a tab root: hides the back arrow in canPop
            // and skips the horizontal slide in composableFromEnd.
            // saveState/restoreState are keyed by DESTINATION, and every pinned tab of one kind shares a
            // single destination — all web apps are `Route.WebApp/{url}`, all pinned chats their own one
            // pattern. So the restore above can hand back a *sibling* tab's saved entry: with two web apps
            // pinned, tapping the second one landed on the first one's URL, and the lookup below then threw
            // `No destination with route …WebApp/<url> is on the NavController's back stack`.
            //
            // When the entry we asked for isn't there, take the tab fresh (no restoreState, and no
            // launchSingleTop — the top is the sibling we do not want to reuse). Its saved scroll/ViewModel
            // state is not recoverable in that case, but the user lands on the tab they tapped. Tabs whose
            // destination nothing else shares still restore normally, which is what this is here for.
            val entry =
                runCatching { controller.getBackStackEntry(route) }.getOrNull()
                    ?: run {
                        controller.navigate(route) {
                            popUpTo(Route.Home) {
                                inclusive = false
                                saveState = true
                            }
                        }
                        runCatching { controller.getBackStackEntry(route) }.getOrNull()
                    }
            entry?.savedStateHandle?.set(BOTTOM_NAV_ROOT_KEY, true)
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
        // Undispatched so a back with a settled keyboard commits on the caller's own message —
        // see [transition]. Every call site is an event callback (top bar, BackHandler, dialog
        // dismissal), never a composable body, so there is no composition to run inside.
        transition(CoroutineStart.UNDISPATCHED) {
            controller.navigateUp()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun <T : Route> popUpTo(
        route: Route,
        klass: KClass<T>,
    ) {
        transition {
            controller.navigate(route) {
                popUpTo(klass) { inclusive = true }
            }
        }
    }
}
